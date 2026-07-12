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
import java.util.UUID

private val Context.sessionDataStore by preferencesDataStore(name = "guest_session")

@Singleton
class GuestSessionStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val token = stringPreferencesKey("guest_token")
        val syncEnabled = booleanPreferencesKey("sync_enabled")
        val exerciseCursor = stringPreferencesKey("exercise_sync_cursor")
        val installationId = stringPreferencesKey("installation_id")
        val recoverySecret = stringPreferencesKey("guest_recovery_secret")
    }

    val token: Flow<String?> = context.sessionDataStore.data.map { it[Keys.token] }
    val syncEnabled: Flow<Boolean> = context.sessionDataStore.data.map { it[Keys.syncEnabled] ?: false }

    suspend fun tokenOrNull(): String? = token.first()
    suspend fun isSyncEnabled(): Boolean = syncEnabled.first()
    suspend fun saveToken(value: String) = context.sessionDataStore.edit { it[Keys.token] = value }
    suspend fun clearToken() = context.sessionDataStore.edit { it.remove(Keys.token) }
    suspend fun setSyncEnabled(enabled: Boolean) {
        context.sessionDataStore.edit { it[Keys.syncEnabled] = enabled }
    }
    suspend fun exerciseCursor(): Long = context.sessionDataStore.data.first()[Keys.exerciseCursor]?.toLongOrNull() ?: 0L
    suspend fun saveExerciseCursor(value: Long) = context.sessionDataStore.edit { it[Keys.exerciseCursor] = value.toString() }

    suspend fun bootstrapCredentials(): Pair<String, String> {
        val current = context.sessionDataStore.data.first()
        val installation = current[Keys.installationId] ?: UUID.randomUUID().toString()
        val recovery = current[Keys.recoverySecret] ?: "${UUID.randomUUID()}-${UUID.randomUUID()}"
        context.sessionDataStore.edit {
            it[Keys.installationId] = installation
            it[Keys.recoverySecret] = recovery
        }
        return installation to recovery
    }
}
