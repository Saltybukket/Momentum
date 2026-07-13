package at.fitnessplatform.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestSessionStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun rejectedRecoveryBlocksRepeatedBootstrapUntilExplicitReset() = runTest {
        val secrets = FakeGuestSecretStore()
        val sessions = GuestSessionStore(context, secrets)
        sessions.resetCredentialsForNewIdentity()
        sessions.setSyncEnabled(true)
        val original = sessions.bootstrapCredentials()

        sessions.markRecoveryRejected()

        val blocked = runCatching { sessions.bootstrapCredentials() }.exceptionOrNull()
        assertTrue(blocked is GuestCredentialBlockedException)
        assertEquals(GuestCredentialState.RECOVERY_REJECTED, sessions.credentialState())

        sessions.resetCredentialsForNewIdentity()
        val replacement = sessions.bootstrapCredentials()
        assertNotEquals(original.first, replacement.first)
        assertEquals(GuestCredentialState.READY, sessions.credentialState())
        assertTrue(sessions.isSyncEnabled())
    }

    @Test
    fun invalidatedCipherDoesNotSilentlyRotateKnownInstallation() = runTest {
        val initialSecrets = FakeGuestSecretStore()
        val initialSessions = GuestSessionStore(context, initialSecrets)
        initialSessions.resetCredentialsForNewIdentity()
        val originalInstallation = initialSessions.bootstrapCredentials().first
        val invalidatedSessions = GuestSessionStore(context, InvalidatedGuestSecretStore())

        val failure = runCatching { invalidatedSessions.bootstrapCredentials() }.exceptionOrNull()

        assertTrue(failure is GuestCredentialInvalidatedException)
        assertEquals(GuestCredentialState.INVALIDATED, invalidatedSessions.credentialState())
        val blocked = runCatching { invalidatedSessions.bootstrapCredentials() }.exceptionOrNull()
        assertTrue(blocked is GuestCredentialBlockedException)
        initialSessions.resetCredentialsForNewIdentity()
        assertNotEquals(originalInstallation, initialSessions.bootstrapCredentials().first)
    }

    @Test
    fun failedLegacyMigrationRemovesEveryPlaintextSecretAndBlocksIdentity() = runTest {
        val legacyToken = stringPreferencesKey("guest_token")
        val legacyRecovery = stringPreferencesKey("guest_recovery_secret")
        context.sessionDataStore.edit {
            it[legacyToken] = "plaintext-token"
            it[legacyRecovery] = "plaintext-recovery"
        }
        val sessions = GuestSessionStore(context, InvalidatedGuestSecretStore())

        val failure = runCatching { sessions.tokenOrNull() }.exceptionOrNull()

        assertTrue(failure is GuestCredentialInvalidatedException)
        val stored = context.sessionDataStore.data.first()
        assertEquals(null, stored[legacyToken])
        assertEquals(null, stored[legacyRecovery])
        assertEquals(GuestCredentialState.INVALIDATED, sessions.credentialState())
    }
}

private class InvalidatedGuestSecretStore : GuestSecretStore {
    override suspend fun tokenOrNull(): String? = throw GuestCredentialInvalidatedException()
    override suspend fun saveToken(value: String): Unit = throw GuestCredentialInvalidatedException()
    override suspend fun recoverySecretOrCreate(): String = throw GuestCredentialInvalidatedException()
    override suspend fun saveRecoverySecret(value: String): Unit = throw GuestCredentialInvalidatedException()
    override suspend fun clearToken() = Unit
    override suspend fun clearAll() = Unit
}
