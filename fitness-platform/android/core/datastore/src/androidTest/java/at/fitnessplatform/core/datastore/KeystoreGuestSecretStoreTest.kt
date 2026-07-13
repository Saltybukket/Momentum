package at.fitnessplatform.core.datastore

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreGuestSecretStoreTest {
    private lateinit var context: Context
    private lateinit var store: KeystoreGuestSecretStore

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        store = KeystoreGuestSecretStore(context)
        store.clearAll()
    }

    @After
    fun tearDown() = runTest { store.clearAll() }

    @Test
    fun tokenClearPreservesRecoveryProof() = runTest {
        store.saveToken("rejected-token")
        store.saveRecoverySecret("stable-recovery")

        store.clearToken()

        assertNull(store.tokenOrNull())
        assertEquals("stable-recovery", store.recoverySecretOrCreate())
    }

    @Test
    fun missingKeyForExistingCiphertextInvalidatesCredentialPair() = runTest {
        store.saveRecoverySecret("server-known-proof")
        androidKeyStore().deleteEntry(KEY_ALIAS)

        assertInvalidated { store.recoverySecretOrCreate() }
        assertEncryptedPreferencesCleared()
        assertEquals(false, androidKeyStore().containsAlias(KEY_ALIAS))
    }

    @Test
    fun missingIvIsCorruptionAndNeverCreatesReplacementProof() = runTest {
        encryptedPreferences().edit()
            .putString("recovery_ciphertext", Base64.encodeToString(byteArrayOf(1), Base64.NO_WRAP))
            .remove("recovery_iv")
            .commit()

        assertInvalidated { store.recoverySecretOrCreate() }
        assertEncryptedPreferencesCleared()
    }

    @Test
    fun missingCiphertextIsCorruptionAndResetIsRepeatable() = runTest {
        encryptedPreferences().edit()
            .putString("recovery_iv", Base64.encodeToString(byteArrayOf(1), Base64.NO_WRAP))
            .remove("recovery_ciphertext")
            .commit()

        assertInvalidated { store.recoverySecretOrCreate() }
        store.clearAll()
        store.clearAll()
        assertEncryptedPreferencesCleared()
    }

    @Test
    fun corruptCiphertextDeletesCipherAndAlias() = runTest {
        store.saveRecoverySecret("server-known-proof")
        encryptedPreferences().edit()
            .putString("recovery_ciphertext", Base64.encodeToString(byteArrayOf(9, 8, 7), Base64.NO_WRAP))
            .commit()

        assertInvalidated { store.recoverySecretOrCreate() }
        assertEncryptedPreferencesCleared()
        assertEquals(false, androidKeyStore().containsAlias(KEY_ALIAS))
    }

    private fun encryptedPreferences() = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    private fun androidKeyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun assertEncryptedPreferencesCleared() {
        assertEquals(emptyMap<String, Any>(), encryptedPreferences().all)
    }

    private suspend fun assertInvalidated(block: suspend () -> Unit) {
        assertTrue(runCatching { block() }.exceptionOrNull() is GuestCredentialInvalidatedException)
    }

    private companion object {
        const val STORE_NAME = "guest_secrets_encrypted"
        const val KEY_ALIAS = "momentum_guest_credentials_v1"
    }
}
