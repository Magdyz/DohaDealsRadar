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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent


/**
 * UI state for Report screen
 * ✅ ENHANCED: Added validation for high-severity reports
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
 * ✅ ENHANCED: Validates details for high-severity reasons
 */
class ReportViewModel(
    private val dealId: String,
    context: Context,
    private val repo: DealRepository = DealRepository()
) : ViewModel() {

    // ✅ FIXED: Use getInstance() to get singleton
    private val deviceIdManager = DeviceIdManager.getInstance(context)

    companion object {
        private const val MAX_REPORTS_PER_DAY = 5
        // ✅ NEW: Minimum character requirement for details
        private const val MIN_DETAIL_LENGTH = 30

        // ✅ NEW: High-severity reasons requiring details
        private val HIGH_SEVERITY_REASONS = setOf(
            ReportReason.SCAM,
            ReportReason.SPAM
        )
    }

    var uiState by mutableStateOf(ReportUiState())
        private set

    init {
        Log.d("Report", "🚨 ReportViewModel created for dealId: $dealId")
        checkReportStatus()
    }

    /**
     * Check if user has already reported or reached daily limit
     */
    private fun checkReportStatus() {
        val alreadyReported = deviceIdManager.hasReported(dealId)
        // ✅ FIXED: Use correct method name
        val todayCount = deviceIdManager.getTodayReportCount()
        val dailyLimitReached = todayCount >= MAX_REPORTS_PER_DAY

        uiState = uiState.copy(
            alreadyReported = alreadyReported,
            dailyLimitReached = dailyLimitReached
        )

        Log.d("Report", "📊 Report status check:")
        Log.d("Report", "   Already reported: $alreadyReported")
        Log.d("Report", "   Today's count: $todayCount/$MAX_REPORTS_PER_DAY")

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

        // ✅ NEW: Log if details will be required
        if (isDetailsRequired(reason)) {
            Log.d("Report", "   ⚠️ This reason requires additional details")
        }
    }

    /**
     * Update note text
     */
    fun updateNote(note: String) {
        uiState = uiState.copy(note = note, error = null)
    }

    /**
     * ✅ NEW: Check if details are required for selected reason
     */
    fun isDetailsRequired(): Boolean {
        return uiState.selectedReason?.let { isDetailsRequired(it) } ?: false
    }

    private fun isDetailsRequired(reason: ReportReason): Boolean {
        return HIGH_SEVERITY_REASONS.contains(reason)
    }

    /**
     * ✅ NEW: Get character count for details field
     */
    fun getDetailCharacterCount(): String {
        val count = uiState.note.trim().length
        return if (isDetailsRequired()) {
            "$count/$MIN_DETAIL_LENGTH"
        } else {
            "$count"
        }
    }

    /**
     * ✅ NEW: Check if details meet minimum requirement
     */
    private fun areDetailsValid(): Boolean {
        if (!isDetailsRequired()) return true
        return uiState.note.trim().length >= MIN_DETAIL_LENGTH
    }

    /**
     * ✅ ENHANCED: Submit the report with validation
     * Now requires details for high-severity reasons
     */
    fun submitReport() {
        Log.d("Report", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("Report", "🚨 Attempting to submit report")
        Log.d("Report", "   Reason: ${uiState.selectedReason?.displayName}")
        Log.d("Report", "   Has details: ${uiState.note.isNotBlank()}")
        Log.d("Report", "   Detail length: ${uiState.note.trim().length}")

        // Validation: Reason required
        if (uiState.selectedReason == null) {
            uiState = uiState.copy(error = "Please select a reason")
            Log.e("Report", "❌ Validation failed: No reason selected")
            return
        }

        // ✅ Validation: Details required for high-severity reasons
        if (isDetailsRequired() && !areDetailsValid()) {
            val reason = uiState.selectedReason!!.displayName
            uiState = uiState.copy(
                error = "For '$reason' reports, please provide at least $MIN_DETAIL_LENGTH characters explaining the issue"
            )
            Log.e("Report", "❌ Validation failed: Insufficient details for high-severity reason")
            Log.e("Report", "   Required: $MIN_DETAIL_LENGTH chars, Got: ${uiState.note.trim().length} chars")
            return
        }

        // Validation: Already reported (this shows AlreadyReported screen from init)
        if (uiState.alreadyReported || deviceIdManager.hasReported(dealId)) {
            uiState = uiState.copy(error = "You have already reported this deal")
            Log.e("Report", "❌ Validation failed: Already reported")
            return
        }

        // Validation: Daily limit
        if (uiState.dailyLimitReached) {
            uiState = uiState.copy(error = "Daily report limit reached")
            Log.e("Report", "❌ Validation failed: Daily limit reached")
            return
        }

        Log.d("Report", "✅ Validation passed, submitting report...")

        uiState = uiState.copy(loading = true, error = null)

        viewModelScope.launch {
            try {
                val deviceId = deviceIdManager.getDeviceId()
                val result = repo.reportDeal(
                    dealId = dealId,
                    deviceId = deviceId,
                    reason = uiState.selectedReason!!.value,
                    note = uiState.note.ifBlank { null }
                )

                // ✅ FIXED: Use local val to enable smart cast
                val reportData = result.data
                if (reportData != null && reportData.isNotEmpty()) {
                    deviceIdManager.recordReport(dealId)
                    deviceIdManager.incrementTodayReportCount()

                    uiState = uiState.copy(
                        loading = false,
                        success = true
                    )

                    Log.d("Report", "✅ Report submitted successfully")
                    Log.d("Report", "   Report ID: ${reportData.firstOrNull()?.id}")
                    Log.d("Report", "   Reason: ${uiState.selectedReason?.value}")
                    Log.d("Report", "   Details provided: ${uiState.note.isNotBlank()}")
                    Log.d("Report", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                } else {
                    uiState = uiState.copy(
                        loading = false,
                        error = result.error ?: "Failed to submit report"
                    )
                    Log.e("Report", "❌ Report failed: ${result.error}")
                }
            } catch (e: Exception) {
                Log.e("Report", "💥 Report submission error", e)

                // ✅ Handle duplicate key error gracefully
                val isDuplicate = e.message?.contains("duplicate key", ignoreCase = true) == true ||
                        e.message?.contains("reports_deal_id_device_id_key", ignoreCase = true) == true

                if (isDuplicate) {
                    Log.w("Report", "⚠️ Duplicate report caught by server")
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
                    error = "Network error. Please try again."
                )
            }
        }
    }}