package at.fitnessplatform.core.datastore

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface GuestSecretStore {
    suspend fun tokenOrNull(): String?
    suspend fun saveToken(value: String)
    suspend fun recoverySecretOrCreate(): String
    suspend fun saveRecoverySecret(value: String)
    suspend fun clearToken()
    suspend fun clearAll()
}

class GuestCredentialInvalidatedException : IllegalStateException("Encrypted guest credentials are unavailable")

@Singleton
@SuppressLint("UseKtx") // commit() is deliberate: credential writes must report durable persistence failure.
class KeystoreGuestSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) : GuestSecretStore {
    private val preferences = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun tokenOrNull(): String? = mutex.withLock { decryptOrReset(TOKEN) }

    override suspend fun saveToken(value: String) = mutex.withLock { putEncrypted(TOKEN, value) }

    override suspend fun recoverySecretOrCreate(): String = mutex.withLock {
        decryptOrReset(RECOVERY) ?: newRecoverySecret().also { putEncrypted(RECOVERY, it) }
    }

    override suspend fun saveRecoverySecret(value: String) = mutex.withLock {
        putEncrypted(RECOVERY, value)
    }

    override suspend fun clearToken() = mutex.withLock {
        check(
            preferences.edit()
                .remove("${TOKEN}_ciphertext")
                .remove("${TOKEN}_iv")
                .commit(),
        ) { "Unable to clear rejected guest token" }
    }

    override suspend fun clearAll() = mutex.withLock {
        clearEncryptedState()
        deleteKeyAlias()
    }

    private fun putEncrypted(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(
            preferences.edit()
                .putString("${name}_ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString("${name}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit(),
        ) { "Unable to persist encrypted guest credentials" }
    }

    @Suppress("ReturnCount")
    private fun decryptOrReset(name: String): String? {
        val ciphertext = preferences.getString("${name}_ciphertext", null)
        val iv = preferences.getString("${name}_iv", null)
        if (ciphertext == null && iv == null) return null
        if (ciphertext == null || iv == null) invalidateEncryptedState()
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(createIfMissing = false),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            // A missing/invalidated key must never silently create a credential that
            // differs from a secret already sent to the server.
            invalidateEncryptedState()
        }
    }

    private fun secretKey(createIfMissing: Boolean = true): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        if (!createIfMissing) throw GuestCredentialInvalidatedException()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private fun invalidateEncryptedState(): Nothing {
        clearEncryptedState()
        deleteKeyAlias()
        throw GuestCredentialInvalidatedException()
    }

    private fun clearEncryptedState() {
        check(preferences.edit().clear().commit()) { "Unable to clear invalid guest credentials" }
    }

    private fun deleteKeyAlias() {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private companion object {
        const val STORE_NAME = "guest_secrets_encrypted"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "momentum_guest_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TOKEN = "token"
        const val RECOVERY = "recovery"

        fun newRecoverySecret() = "${java.util.UUID.randomUUID()}-${java.util.UUID.randomUUID()}"
    }
}

class FakeGuestSecretStore : GuestSecretStore {
    private val mutex = Mutex()
    private var token: String? = null
    private var recovery: String? = null

    override suspend fun tokenOrNull() = mutex.withLock { token }
    override suspend fun saveToken(value: String) = mutex.withLock { token = value }
    override suspend fun recoverySecretOrCreate() = mutex.withLock {
        recovery ?: "fake-recovery-secret".also { recovery = it }
    }
    override suspend fun saveRecoverySecret(value: String) = mutex.withLock { recovery = value }
    override suspend fun clearToken() = mutex.withLock { token = null }
    override suspend fun clearAll() = mutex.withLock { token = null; recovery = null }
}
