package com.stoneofhope.stoneofhope.data.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.stoneofhope.stoneofhope.data.sync.FakeKeyVault
import com.stoneofhope.stoneofhope.data.sync.FakeRemoteStore
import com.stoneofhope.stoneofhope.data.sync.TestDevice
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** iOS `SocialRegistrationService.linkOrSignIn`: which Firebase call each situation ends up making. */
@RunWith(AndroidJUnit4::class)
class LinkOrSignInTest {

    private class FakeAuthPort(var hasUser: Boolean, var linkError: Exception? = null) : AuthPort {
        val calls = mutableListOf<String>()
        val signedInWith = mutableListOf<AuthCredential>()
        override val hasCurrentUser: Boolean get() = hasUser
        override suspend fun signIn(credential: AuthCredential) { calls += "signIn"; signedInWith += credential; hasUser = true }
        override suspend fun link(credential: AuthCredential) { calls += "link"; linkError?.let { throw it } }
    }

    private val device = TestDevice("a", FakeRemoteStore(), FakeKeyVault())
    private val google = GoogleAuthProvider.getCredential("id-token", null)

    @After
    fun tearDown() = device.close()

    private fun port(hasUser: Boolean, error: Exception? = null) =
        FakeAuthPort(hasUser, error).also { device.account.authPort = it }

    // The SDK class is final and only `zza` (its internal setter) can attach the updated credential.
    private fun collision(code: String, updated: AuthCredential? = null) =
        FirebaseAuthUserCollisionException(code, "collision").let { if (updated != null) it.zza(updated) else it }

    private fun assertCompleted() {
        assertTrue(device.flags.hasLinkedCloudAccount)
        assertFalse(device.flags.isCloudSyncPaused)
    }

    @Test fun withoutASessionItSignsIn() = runTest {
        val port = port(hasUser = false)
        device.account.linkOrSignIn(google)
        assertEquals(listOf("signIn"), port.calls)
        assertCompleted()
    }

    @Test fun withASessionItLinksTheCredential() = runTest {
        val port = port(hasUser = true)
        device.account.linkOrSignIn(google)
        assertEquals(listOf("link"), port.calls)
        assertCompleted()
    }

    @Test fun aCredentialOwnedByAnotherAccountSignsIntoThatAccount() = runTest {
        for (code in listOf(ERROR_CREDENTIAL_ALREADY_IN_USE, ERROR_EMAIL_ALREADY_IN_USE)) {
            val port = port(hasUser = true, error = collision(code))
            device.account.linkOrSignIn(google)
            assertEquals(code, listOf("link", "signIn"), port.calls)
            assertSame(google, port.signedInWith.single())
        }
        assertCompleted()
    }

    @Test fun anExistingAccountWithAnotherProviderUsesTheUpdatedCredential() = runTest {
        val updated = GoogleAuthProvider.getCredential("updated-token", null)
        val port = port(hasUser = true, error = collision(ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL, updated))
        device.account.linkOrSignIn(google)
        assertEquals(listOf("link", "signIn"), port.calls)
        assertSame(updated, port.signedInWith.single())
        assertCompleted()
    }

    @Test fun anAlreadyLinkedProviderJustCompletes() = runTest {
        val port = port(hasUser = true, error = FirebaseAuthException(ERROR_PROVIDER_ALREADY_LINKED, "linked"))
        device.account.linkOrSignIn(google)
        assertEquals(listOf("link"), port.calls)
        assertCompleted()
    }

    @Test fun anyOtherLinkFailureIsPassedOnAndSignsNothingIn() = runTest {
        val boom = FirebaseAuthException("ERROR_NETWORK_REQUEST_FAILED", "offline")
        val port = port(hasUser = true, error = boom)
        try {
            device.account.linkOrSignIn(google)
            fail("expected the failure")
        } catch (e: FirebaseAuthException) {
            assertSame(boom, e)
        }
        assertEquals(listOf("link"), port.calls)
        assertFalse(device.flags.hasLinkedCloudAccount)
    }

    @Test fun anUnfinishedSignOutIsSettledBeforeSigningIn() = runTest {
        device.flags.isCloudSyncPaused = true
        port(hasUser = false)
        device.account.linkOrSignIn(google)
        assertCompleted()
    }
}
