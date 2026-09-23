package eg.deals.radar.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import eg.deals.radar.core.data.BuildConfig
import java.security.MessageDigest
import java.util.UUID

/**
 * ========================================
 * 🔐 SIGN IN WITH GOOGLE
 * ========================================
 * Uses Android Credential Manager, so the account picker is the system one and
 * no password or email is ever typed into the app.
 *
 * The token is verified on our backend (sign_in_with_google), which exchanges
 * it for a Supabase session. We never store the Google email: the account is
 * identified only by its internal auth id, and the app shows a random username.
 *
 * Replay protection: we generate a random nonce, hand Google its SHA-256, and
 * send the raw value to the backend, which checks that the token matches.
 *
 * Two passes: the account picker first, then the full "Sign in with Google"
 * flow, which can also add an account to the device. Google reports both an
 * empty device and an unregistered app signature as NoCredentialException, so
 * the message shown after both passes fail has to cover either cause.
 */
object GoogleAuth {

    private const val TAG = "GoogleAuth"

    /** Outcome of the system account picker. */
    sealed interface Result {
        data class Success(val idToken: String, val rawNonce: String) : Result
        /** The user dismissed the picker: no error should be shown. */
        object Cancelled : Result
        /** No usable account: none on the device, or this build isn't authorised. */
        object NoAccount : Result
        data class Failed(val reason: String) : Result
    }

    /** True when the app was built with a Google client id configured. */
    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /**
     * Shows the Google account picker. [activityContext] must be an Activity
     * context: Credential Manager needs one to show the system sheet.
     */
    suspend fun signIn(activityContext: Context): Result {
        if (!isConfigured) return Result.Failed("Google sign-in is not configured in this build.")

        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = sha256(rawNonce)

        // Pass 1: pick from the accounts already on the device.
        val picker = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            // false = also offer accounts that never used this app, so first-time
            // sign-in works without a second "sign up" step.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(hashedNonce)
            .build()

        val first = request(activityContext, picker, rawNonce)
        if (first !is Result.NoAccount) return first

        // Pass 2: the full flow, which offers "add another account" on a device
        // that has none.
        Log.w(TAG, "Account picker returned nothing; trying the full sign-in flow")
        val fullFlow = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(hashedNonce)
            .build()
        return request(activityContext, fullFlow, rawNonce)
    }

    private suspend fun request(
        activityContext: Context,
        option: CredentialOption,
        rawNonce: String,
    ): Result {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val credential = response.credential
            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return Result.Failed("Unexpected credential type")
            }
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            if (token.isBlank()) Result.Failed("Empty token") else Result.Success(token, rawNonce)
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: NoCredentialException) {
            // Either the device has no Google account, or this build's signing
            // certificate is not registered against the OAuth client.
            Log.w(TAG, "No credential available: ${e.message}")
            Result.NoAccount
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager failed: ${e.javaClass.simpleName} ${e.message}")
            Result.Failed(e.javaClass.simpleName)
        } catch (e: Exception) {
            Log.w(TAG, "Google sign-in failed: ${e.javaClass.simpleName}")
            Result.Failed(e.javaClass.simpleName)
        }
    }

    /** Clears the picker's "remembered choice" so the next sign-in asks again. */
    suspend fun clearSelection(context: Context) {
        runCatching {
            CredentialManager.create(context)
                .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        }.onFailure { Log.w(TAG, "clearCredentialState failed: ${it.javaClass.simpleName}") }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
