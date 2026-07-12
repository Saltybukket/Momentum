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

private val Context.sessionDataStore by preferencesDataStore(name = "guest_session")

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
    }

    val syncEnabled: Flow<Boolean> = context.sessionDataStore.data.map { it[Keys.syncEnabled] ?: false }

    suspend fun tokenOrNull(): String? {
        credentialMutex.withLock { migrateLegacySecrets() }
        return try {
            secretStore.tokenOrNull()
        } catch (_: GuestCredentialInvalidatedException) {
            rotateInstallationAfterCredentialLoss()
            null
        }
    }
    suspend fun isSyncEnabled(): Boolean = syncEnabled.first()
    suspend fun saveToken(value: String) = secretStore.saveToken(value)
    suspend fun clearCredentials() = secretStore.clear()
    suspend fun setSyncEnabled(enabled: Boolean) {
        context.sessionDataStore.edit { it[Keys.syncEnabled] = enabled }
    }
    suspend fun clearLegacyExerciseCursor() {
        context.sessionDataStore.edit { it.remove(Keys.exerciseCursor) }
    }

    suspend fun bootstrapCredentials(): Pair<String, String> = credentialMutex.withLock {
        migrateLegacySecrets()
        val current = context.sessionDataStore.data.first()
        val installation = current[Keys.installationId] ?: UUID.randomUUID().toString().also { id ->
            context.sessionDataStore.edit { it[Keys.installationId] = id }
        }
        try {
            installation to secretStore.recoverySecretOrCreate()
        } catch (_: GuestCredentialInvalidatedException) {
            rotateInstallationAfterCredentialLoss()
            val rotated = context.sessionDataStore.data.first()[Keys.installationId]
                ?: error("Installation identity rotation failed")
            rotated to secretStore.recoverySecretOrCreate()
        }
    }

    private suspend fun migrateLegacySecrets() {
        val current = context.sessionDataStore.data.first()
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
    }

    private suspend fun rotateInstallationAfterCredentialLoss() {
        secretStore.clear()
        context.sessionDataStore.edit { it[Keys.installationId] = UUID.randomUUID().toString() }
    }
}
