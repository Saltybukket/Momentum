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

private val Context.sessionDataStore by preferencesDataStore(name = "guest_session")

@Singleton
class GuestSessionStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val token = stringPreferencesKey("guest_token")
        val syncEnabled = booleanPreferencesKey("sync_enabled")
    }

    val token: Flow<String?> = context.sessionDataStore.data.map { it[Keys.token] }
    val syncEnabled: Flow<Boolean> = context.sessionDataStore.data.map { it[Keys.syncEnabled] ?: true }

    suspend fun tokenOrNull(): String? = token.first()
    suspend fun isSyncEnabled(): Boolean = syncEnabled.first()
    suspend fun saveToken(value: String) = context.sessionDataStore.edit { it[Keys.token] = value }
    suspend fun clearToken() = context.sessionDataStore.edit { it.remove(Keys.token) }
    suspend fun setSyncEnabled(enabled: Boolean) = context.sessionDataStore.edit { it[Keys.syncEnabled] = enabled }
}
