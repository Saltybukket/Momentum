package at.fitnessplatform.app

import android.util.Log
import at.fitnessplatform.core.database.DatabaseStartupProbe
import at.fitnessplatform.core.sync.SyncScheduler
import dagger.Lazy
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppStartupState {
    CHECKING,
    READY,
    BLOCKED,
}

internal class StartupGate(
    private val verifyDatabase: () -> Unit,
    private val enqueueSync: () -> Unit,
) {
    private val mutableState = MutableStateFlow(AppStartupState.CHECKING)
    val state: StateFlow<AppStartupState> = mutableState.asStateFlow()

    fun verify() {
        mutableState.value = AppStartupState.CHECKING
        runCatching(verifyDatabase)
            .onSuccess {
                mutableState.value = AppStartupState.READY
                runCatching(enqueueSync)
            }
            .onFailure { mutableState.value = AppStartupState.BLOCKED }
    }
}

@Singleton
class AppStartupCoordinator @Inject constructor(
    database: Lazy<DatabaseStartupProbe>,
    syncScheduler: SyncScheduler,
) {
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val gate = StartupGate(
        verifyDatabase = { database.get().verifyStartup() },
        enqueueSync = syncScheduler::enqueue,
    )

    val state: StateFlow<AppStartupState> = gate.state

    fun start() {
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            gate.verify()
            if (gate.state.value == AppStartupState.BLOCKED) {
                Log.e("MomentumDatabase", "Local database startup verification failed.")
            }
            running.set(false)
        }
    }
}
