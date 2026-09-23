package eg.deals.radar.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import eg.deals.radar.network.ClientErrorReportRequest
import eg.deals.radar.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException

/**
 * ========================================
 * 🩺 CRASH + ERROR REPORTING
 * ========================================
 * Two channels for the same signal:
 * 1. Firebase Crashlytics (crash-on-previous-execution + non-fatal recordException)
 *    so Crashlytics keeps its own stack-trace-rich history.
 * 2. A tiny "report_client_error" beacon so the backend can show admins a
 *    privacy-safe App Health summary (counts only, no stack traces, no user ids).
 *
 * PRIVACY: never call setUserId / setCustomKey with anything that identifies a
 * user or device. Only kind/area/code/app_version/os_version are ever sent.
 */
object ErrorReporter {
    private const val TAG = "ErrorReporter"
    private const val PREFS_FILE = "error_reporter_prefs"
    private const val KEY_DAY = "report_day"
    private const val KEY_COUNT = "report_count"
    private const val MAX_REPORTS_PER_DAY = 20

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appVersion: String? = null
    private var prefs: SharedPreferences? = null

    /** Call once from Application.onCreate(), after Firebase is initialized. */
    fun onAppStart(context: Context, appVersion: String) {
        this.appVersion = appVersion
        val appContext = context.applicationContext
        this.prefs = appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

        runCatching {
            if (FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution()) {
                send(kind = "crash", area = "app", code = null)
            }
        }.onFailure { Log.w(TAG, "onAppStart check failed: ${it.javaClass.simpleName}") }
    }

    /**
     * Report a non-fatal exception. Offline / connectivity failures are NOT app
     * bugs, so they're ignored (no Crashlytics record, no beacon).
     */
    fun nonFatal(area: String, t: Throwable) {
        if (isIgnorable(t)) return
        runCatching { FirebaseCrashlytics.getInstance().recordException(t) }
            .onFailure { Log.w(TAG, "recordException failed: ${it.javaClass.simpleName}") }
        send(kind = "error", area = area, code = t.javaClass.simpleName)
    }

    /**
     * Report a backend error envelope, but only when it looks like an actual
     * server bug (null code, or the explicit SERVER_ERROR code) — validation,
     * rate-limit, duplicate, etc. are expected, user-facing outcomes, not bugs.
     */
    fun serverError(area: String, code: String?) {
        if (code != null && code != "SERVER_ERROR") return
        send(kind = "error", area = area, code = code ?: "SERVER_ERROR")
    }

    private fun isIgnorable(t: Throwable): Boolean = when (t) {
        is UnknownHostException, is SocketTimeoutException, is IOException, is CancellationException -> true
        else -> false
    }

    private fun send(kind: String, area: String, code: String?) {
        scope.launch {
            try {
                if (!allowReportToday()) {
                    Log.d(TAG, "Daily report cap reached, dropping $kind/$area")
                    return@launch
                }
                val body = ClientErrorReportRequest(
                    kind = kind,
                    area = area,
                    code = code,
                    appVersion = appVersion,
                    osVersion = Build.VERSION.RELEASE
                )
                NetworkModule.clientErrorApi.reportClientError(body)
            } catch (e: Throwable) {
                // Fire-and-forget: never let reporting itself crash or surface an error.
                Log.w(TAG, "Failed to send client error report: ${e.javaClass.simpleName}")
            }
        }
    }

    /** True (and increments the counter) when we're still under today's cap. */
    @Synchronized
    private fun allowReportToday(): Boolean {
        val p = prefs ?: return true // not initialized yet (shouldn't happen post onAppStart); allow best-effort
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val storedDay = p.getString(KEY_DAY, null)
        val count = if (storedDay == today) p.getInt(KEY_COUNT, 0) else 0
        if (count >= MAX_REPORTS_PER_DAY) return false
        p.edit().putString(KEY_DAY, today).putInt(KEY_COUNT, count + 1).apply()
        return true
    }
}
