package com.example.d_r_i_v_e.frontend

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.d_r_i_v_e.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

private const val TAG = "GoogleSignIn"

sealed interface GoogleSignInResult {
    data class Success(
        val name: String,
        val email: String,
        val photoUrl: String?
    ) : GoogleSignInResult

    data object Cancelled : GoogleSignInResult
    data object NoAccount : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
}

/**
 * Shows the system Google account chooser (real accounts on the device,
 * plus the "Add account" option) and returns the selected account.
 */
suspend fun signInWithGoogle(
    context: Context,
    serverClientId: String
): GoogleSignInResult {
    Log.d(TAG, "Starting sign-in. serverClientId=$serverClientId")

    val credentialManager = CredentialManager.create(context)
    val googleOption = GetGoogleIdOption.Builder()
        .setServerClientId(serverClientId)
        .setFilterByAuthorizedAccounts(false)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleOption)
        .build()

    return try {
        val response = credentialManager.getCredential(context = context, request = request)
        val credential = response.credential
        Log.d(TAG, "Credential received. type=${credential.type}")

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleSignInResult.Success(
                name = google.displayName ?: google.id.substringBefore("@"),
                email = google.id,
                photoUrl = google.profilePictureUri?.toString()
            )
        } else {
            Log.e(TAG, "Unexpected credential type: ${credential.type}")
            GoogleSignInResult.Error("Unexpected credential type: ${credential.type}")
        }
    } catch (e: GetCredentialCancellationException) {
        Log.e(TAG, "Cancelled: ${e.message}", e)
        GoogleSignInResult.Cancelled
    } catch (e: NoCredentialException) {
        Log.e(TAG, "NoCredential: ${e.message}", e)
        GoogleSignInResult.NoAccount
    } catch (e: GoogleIdTokenParsingException) {
        Log.e(TAG, "Parsing: ${e.message}", e)
        GoogleSignInResult.Error("Invalid Google ID token: ${e.message}")
    } catch (e: GetCredentialException) {
        Log.e(TAG, "GetCredential [${e.type}]: ${e.message}", e)
        GoogleSignInResult.Error("${e.type}: ${e.message}")
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error: ${e.message}", e)
        GoogleSignInResult.Error(e.message ?: "Unexpected error.")
    }
}

/**
 * Returns a function you can call from any onClick (e.g. the Google logo/button).
 *
 * val startGoogleSignIn = rememberGoogleSignIn { name, email -> ... }
 * Button(onClick = startGoogleSignIn) { ... }
 */
@Composable
fun rememberGoogleSignIn(
    onAccountSelected: (name: String, email: String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverClientId = stringResource(R.string.google_web_client_id)
    val currentOnSelected by rememberUpdatedState(onAccountSelected)

    return remember(context, serverClientId) {
        {
            scope.launch {
                when (val result = signInWithGoogle(context, serverClientId)) {
                    is GoogleSignInResult.Success -> {
                        Log.d(TAG, "Success: ${result.email}")
                        currentOnSelected(result.name, result.email)
                    }

                    GoogleSignInResult.Cancelled ->
                        Toast.makeText(context, "Sign-in cancelled.", Toast.LENGTH_SHORT).show()

                    GoogleSignInResult.NoAccount ->
                        Toast.makeText(
                            context,
                            "Walang Google account sa device. Mag-add muna ng account sa Settings.",
                            Toast.LENGTH_LONG
                        ).show()

                    is GoogleSignInResult.Error ->
                        Toast.makeText(
                            context,
                            "Sign-in failed: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
            Unit
        }
    }
}
