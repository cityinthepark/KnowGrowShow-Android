package com.stoneofhope.stoneofhope.data.account

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseBootstrap
import kotlinx.coroutines.tasks.await

/** The three Firebase Auth calls `linkOrSignIn` needs, so its branching can be tested without Firebase. */
interface AuthPort {
    val hasCurrentUser: Boolean
    suspend fun signIn(credential: AuthCredential)
    suspend fun link(credential: AuthCredential)
}

class FirebaseAuthPort(private val bootstrap: FirebaseBootstrap) : AuthPort {
    private val auth: FirebaseAuth get() = bootstrap.auth
    override val hasCurrentUser: Boolean get() = auth.currentUser != null
    override suspend fun signIn(credential: AuthCredential) { auth.signInWithCredential(credential).await() }
    override suspend fun link(credential: AuthCredential) {
        val user = auth.currentUser ?: throw SocialSignInException(SocialSignInException.Kind.NoUser)
        user.linkWithCredential(credential).await()
    }
}

internal const val ERROR_CREDENTIAL_ALREADY_IN_USE = "ERROR_CREDENTIAL_ALREADY_IN_USE"
internal const val ERROR_EMAIL_ALREADY_IN_USE = "ERROR_EMAIL_ALREADY_IN_USE"
internal const val ERROR_PROVIDER_ALREADY_LINKED = "ERROR_PROVIDER_ALREADY_LINKED"
internal const val ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL = "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"

internal val Exception.authErrorCode: String? get() = (this as? FirebaseAuthException)?.errorCode
internal val Exception.updatedCredential: AuthCredential? get() = (this as? FirebaseAuthUserCollisionException)?.updatedCredential
