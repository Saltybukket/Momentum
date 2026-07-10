package at.fitnessplatform.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import at.fitnessplatform.domain.SyncEnqueuer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) : SyncEnqueuer {
    override fun enqueue() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private companion object { const val UNIQUE_NAME = "fitness-platform-outbox-sync" }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindingModule {
    @Binds abstract fun bindSyncEnqueuer(implementation: SyncScheduler): SyncEnqueuer
}
