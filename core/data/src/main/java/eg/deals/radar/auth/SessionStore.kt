package eg.deals.radar.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import eg.deals.radar.util.AppContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Supabase Auth session (access + refresh token).
 * `expiresAt` is epoch seconds.
 */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
) {
    val isExpiringSoon: Boolean
        get() = System.currentTimeMillis() / 1000 >= expiresAt - 60
}

/** Events the UI reacts to (e.g. show "Please log in again"). */
sealed interface AuthEvent {
    data object SessionExpired : AuthEvent
}

/**
 * ========================================
 * 🔐 SESSION STORE
 * ========================================
 * Stores the session encrypted with an AES-256-GCM key that lives in the
 * Android Keystore (never leaves secure hardware where available).
 * Tokens are never logged.
 */
object SessionStore {
    private const val TAG = "SessionStore"
    private const val PREFS = "secure_session"
    private const val KEY_BLOB = "session_blob"
    private const val KEY_ALIAS = "egyptdealradar_session_key"

    private val lock = Any()
    @Volatile private var cached: Session? = null
    @Volatile private var loaded = false

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private fun prefs(context: Context = AppContext.appContext) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(): Session? {
        if (loaded) return cached
        synchronized(lock) {
            if (!loaded) {
                cached = runCatching { read() }.onFailure { Log.w(TAG, "Could not read session") }.getOrNull()
                loaded = true
            }
            return cached
        }
    }

    val isLoggedIn: Boolean get() = current() != null

    fun save(session: Session) {
        synchronized(lock) {
            val json = JSONObject()
                .put("a", session.accessToken)
                .put("r", session.refreshToken)
                .put("e", session.expiresAt)
                .toString()
            prefs().edit().putString(KEY_BLOB, encrypt(json)).apply()
            cached = session
            loaded = true
        }
    }

    fun clear() {
        synchronized(lock) {
            prefs().edit().remove(KEY_BLOB).apply()
            cached = null
            loaded = true
        }
    }

    /** Called when the refresh token is rejected: log out and tell the UI. */
    fun expire() {
        clear()
        _events.tryEmit(AuthEvent.SessionExpired)
    }

    // ---------------------------------------------------------------- crypto

    private fun read(): Session? {
        val blob = prefs().getString(KEY_BLOB, null) ?: return null
        val json = JSONObject(decrypt(blob))
        return Session(json.getString("a"), json.getString("r"), json.getLong("e"))
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }
}
