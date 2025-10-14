package qa.deals.doha.feature.report

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import qa.deals.doha.datastore.DeviceIdManager
import qa.deals.doha.network.ReportReason
import qa.deals.doha.repository.DealRepository

/**
 * UI state for Report screen
 */
data class ReportUiState(
    val selectedReason: ReportReason? = null,
    val note: String = "",
    val loading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
    val alreadyReported: Boolean = false,
    val dailyLimitReached: Boolean = false
)

/**
 * ViewModel for the Report screen
 */
class ReportViewModel(
    private val dealId: String,
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val deviceIdManager: DeviceIdManager = DeviceIdManager.getInstance(context)
) : ViewModel() {

    var uiState by mutableStateOf(ReportUiState())
        private set

    companion object {
        private const val MAX_REPORTS_PER_DAY = 5
    }

    init {
        Log.d("Report", "🚨 ReportViewModel created for dealId: $dealId")
        checkReportStatus()
    }

    /**
     * Check if user has already reported this deal or reached daily limit
     */
    private fun checkReportStatus() {
        val alreadyReported = deviceIdManager.hasReported(dealId)
        val todayCount = deviceIdManager.getTodayReportCount()
        val dailyLimitReached = todayCount >= MAX_REPORTS_PER_DAY

        uiState = uiState.copy(
            alreadyReported = alreadyReported,
            dailyLimitReached = dailyLimitReached
        )

        if (alreadyReported) {
            Log.d("Report", "⚠️ User already reported this deal")
        }
        if (dailyLimitReached) {
            Log.d("Report", "⚠️ User reached daily report limit ($todayCount/$MAX_REPORTS_PER_DAY)")
        }
    }

    /**
     * Update selected reason
     */
    fun selectReason(reason: ReportReason) {
        uiState = uiState.copy(selectedReason = reason, error = null)
        Log.d("Report", "📝 Selected reason: ${reason.displayName}")
    }

    /**
     * Update note text
     */
    fun updateNote(note: String) {
        uiState = uiState.copy(note = note, error = null)
    }

    /**
     * Submit the report
     */
    fun submitReport() {
        // Validation
        if (uiState.selectedReason == null) {
            uiState = uiState.copy(error = "Please select a reason")
            return
        }

        if (uiState.alreadyReported || deviceIdManager.hasReported(dealId)) {
            uiState = uiState.copy(error = "You have already reported this deal")
            return
        }

        if (uiState.dailyLimitReached) {
            uiState = uiState.copy(error = "Daily report limit reached. Try again tomorrow.")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("Report", "🚨 Submitting report...")
                uiState = uiState.copy(loading = true, error = null)

                val result = repo.reportDeal(
                    dealId = dealId,
                    deviceId = deviceIdManager.getDeviceId(),
                    reason = uiState.selectedReason!!.value,
                    note = uiState.note.ifBlank { null }
                )

                // ✅ Store data in local variable
                val reportData = result.data

                // ✅ Backend doesn't return success field for reports, so check for data only
                if (reportData != null && reportData.isNotEmpty()) {
                    // Record report locally
                    deviceIdManager.recordReport(dealId)
                    deviceIdManager.incrementTodayReportCount()

                    uiState = uiState.copy(
                        loading = false,
                        success = true,
                        alreadyReported = true
                    )
                    Log.d("Report", "✅ Report submitted successfully")
                } else {
                    uiState = uiState.copy(
                        loading = false,
                        error = result.error ?: "Failed to submit report"
                    )
                    Log.e("Report", "❌ Report failed: ${result.error}")
                }
            } catch (e: Exception) {
                Log.e("Report", "💥 Error submitting report", e)

                // ✅ Handle duplicate key error gracefully
                val isDuplicate = e.message?.contains("duplicate key", ignoreCase = true) == true ||
                        e.message?.contains("reports_deal_id_device_id_key", ignoreCase = true) == true

                if (isDuplicate) {
                    deviceIdManager.recordReport(dealId)
                    uiState = uiState.copy(
                        loading = false,
                        alreadyReported = true,
                        error = "You have already reported this deal"
                    )
                    return@launch
                }

                uiState = uiState.copy(
                    loading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }
}