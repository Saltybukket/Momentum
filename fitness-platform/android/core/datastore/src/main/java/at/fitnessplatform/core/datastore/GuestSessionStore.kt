package at.fitnessplatform.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class GuestCredentialState {
    READY,
    INVALIDATED,
    RECOVERY_REJECTED,
}

class GuestCredentialBlockedException(val state: GuestCredentialState) :
    IllegalStateException("Guest credentials require an explicit reset")

internal val Context.sessionDataStore by preferencesDataStore(name = "guest_session")

@Singleton
class GuestSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretStore: GuestSecretStore,
) {
    private val credentialMutex = Mutex()
    private object Keys {
        val token = stringPreferencesKey("guest_token")
        val syncEnabled = booleanPreferencesKey("sync_enabled")
        val exerciseCursor = stringPreferencesKey("exercise_sync_cursor")
        val installationId = stringPreferencesKey("installation_id")
        val recoverySecret = stringPreferencesKey("guest_recovery_secret")
        val credentialState = stringPreferencesKey("credential_state")
    }

    val syncEnabled: Flow<Boolean> = context.sessionDataStore.data.map { it[Keys.syncEnabled] ?: false }
    val credentialStates: Flow<GuestCredentialState> =
        context.sessionDataStore.data.map { it.credentialState() }

    suspend fun tokenOrNull(): String? {
        credentialMutex.withLock { migrateLegacySecrets() }
        return try {
            secretStore.tokenOrNull()
        } catch (exception: GuestCredentialInvalidatedException) {
            markCredentialState(GuestCredentialState.INVALIDATED)
            throw exception
        }
    }
    suspend fun isSyncEnabled(): Boolean = syncEnabled.first()
    suspend fun saveToken(value: String) = secretStore.saveToken(value)
    suspend fun clearToken() = secretStore.clearToken()
    suspend fun markRecoveryRejected() = markCredentialState(GuestCredentialState.RECOVERY_REJECTED)
    suspend fun credentialState(): GuestCredentialState = credentialStates.first()

    suspend fun resetCredentialsForNewIdentity(): Unit = credentialMutex.withLock {
        secretStore.clearAll()
        context.sessionDataStore.edit {
            it[Keys.installationId] = UUID.randomUUID().toString()
            it[Keys.credentialState] = GuestCredentialState.READY.name
            it.remove(Keys.token)
            it.remove(Keys.recoverySecret)
        }
        Unit
    }
    suspend fun setSyncEnabled(enabled: Boolean) {
        context.sessionDataStore.edit { it[Keys.syncEnabled] = enabled }
    }
    suspend fun clearLegacyExerciseCursor() {
        context.sessionDataStore.edit { it.remove(Keys.exerciseCursor) }
    }

    suspend fun bootstrapCredentials(): Pair<String, String> = credentialMutex.withLock {
        migrateLegacySecrets()
        val current = context.sessionDataStore.data.first()
        current.credentialState().takeUnless { it == GuestCredentialState.READY }?.let {
            throw GuestCredentialBlockedException(it)
        }
        val installation = current[Keys.installationId] ?: UUID.randomUUID().toString().also { id ->
            context.sessionDataStore.edit { it[Keys.installationId] = id }
        }
        try {
            installation to secretStore.recoverySecretOrCreate()
        } catch (exception: GuestCredentialInvalidatedException) {
            markCredentialState(GuestCredentialState.INVALIDATED)
            throw exception
        }
    }

    private suspend fun migrateLegacySecrets() {
        val current = context.sessionDataStore.data.first()
        try {
            current[Keys.token]?.let { secretStore.saveToken(it) }
            current[Keys.recoverySecret]?.let { secretStore.saveRecoverySecret(it) }
            if (current[Keys.token] != null || current[Keys.recoverySecret] != null) {
                check(current[Keys.token] == null || secretStore.tokenOrNull() == current[Keys.token])
                check(
                    current[Keys.recoverySecret] == null ||
                        secretStore.recoverySecretOrCreate() == current[Keys.recoverySecret],
                )
                context.sessionDataStore.edit {
                    it.remove(Keys.token)
                    it.remove(Keys.recoverySecret)
                }
            }
        } catch (exception: GuestCredentialInvalidatedException) {
            // Plaintext must not survive a failed legacy migration. The local
            // identity remains blocked until the caller explicitly resets it.
            context.sessionDataStore.edit {
                it.remove(Keys.token)
                it.remove(Keys.recoverySecret)
                it[Keys.credentialState] = GuestCredentialState.INVALIDATED.name
            }
            throw exception
        }
    }

    private suspend fun markCredentialState(state: GuestCredentialState) {
        context.sessionDataStore.edit { it[Keys.credentialState] = state.name }
    }

    private fun androidx.datastore.preferences.core.Preferences.credentialState(): GuestCredentialState =
        this[Keys.credentialState]
            ?.let { runCatching { GuestCredentialState.valueOf(it) }.getOrNull() }
            ?: GuestCredentialState.READY
}
