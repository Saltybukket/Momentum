package at.fitnessplatform.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import at.fitnessplatform.core.database.DatabaseStartupProbe
import at.fitnessplatform.core.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import dagger.Lazy
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltAndroidApp
class FitnessPlatformApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var database: Lazy<DatabaseStartupProbe>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        val databaseCheck = Executors.newSingleThreadExecutor()
        databaseCheck.execute {
            runCatching { database.get().verifyStartup() }
                .onFailure {
                    Log.e(
                        "MomentumDatabase",
                        "Local database could not be opened. Install the current app version to preserve local data.",
                    )
                }
            databaseCheck.shutdown()
        }
        syncScheduler.enqueue()
    }
}
