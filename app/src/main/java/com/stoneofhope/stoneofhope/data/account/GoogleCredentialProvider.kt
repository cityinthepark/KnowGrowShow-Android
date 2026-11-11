package com.stoneofhope.stoneofhope.data.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Why a social sign-in did not finish; the UI maps each to a localized message (iOS `SocialRegistrationError`). */
class SocialSignInException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { MissingConfig, MissingToken, Cancelled, NoUser }
}

/** Asks the user to pick a Google account and returns the Firebase credential (iOS `GIDSignIn.signIn`). */
interface GoogleCredentialSource {
    /** [activity] must be an Activity context: Credential Manager shows its account picker on top of it. */
    suspend fun credential(activity: Context): AuthCredential
}

@Singleton
class GoogleCredentialProvider @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : GoogleCredentialSource {

    /**
     * The OAuth *web* client id the google-services plugin generates from `google-services.json`
     * (iOS reads `FirebaseApp.options.clientID`). Missing file ⇒ the resource does not exist.
     */
    private fun webClientId(): String? {
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        return if (id == 0) null else appContext.getString(id).takeIf { it.isNotBlank() }
    }

    override suspend fun credential(activity: Context): AuthCredential {
        val clientId = webClientId() ?: throw SocialSignInException(SocialSignInException.Kind.MissingConfig)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw SocialSignInException(SocialSignInException.Kind.Cancelled, e)
        }
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw SocialSignInException(SocialSignInException.Kind.MissingToken)
        }
        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw SocialSignInException(SocialSignInException.Kind.MissingToken, e)
        }
        return GoogleAuthProvider.getCredential(idToken, null)
    }
}
