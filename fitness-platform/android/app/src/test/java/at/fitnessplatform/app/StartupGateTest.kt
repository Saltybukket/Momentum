package at.fitnessplatform.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupGateTest {
    @Test
    fun `successful verification releases app and enqueues sync once`() {
        var syncCalls = 0
        val gate = StartupGate(verifyDatabase = {}, enqueueSync = { syncCalls++ })

        gate.verify()

        assertEquals(AppStartupState.READY, gate.state.value)
        assertEquals(1, syncCalls)
    }

    @Test
    fun `database failure blocks app and never enqueues sync`() {
        var syncCalls = 0
        val gate = StartupGate(
            verifyDatabase = { error("database unavailable") },
            enqueueSync = { syncCalls++ },
        )

        gate.verify()

        assertEquals(AppStartupState.BLOCKED, gate.state.value)
        assertEquals(0, syncCalls)
    }

    @Test
    fun `retry can recover after an initial database failure`() {
        var attempts = 0
        var syncCalls = 0
        val gate = StartupGate(
            verifyDatabase = {
                attempts++
                if (attempts == 1) error("database unavailable")
            },
            enqueueSync = { syncCalls++ },
        )

        gate.verify()
        gate.verify()

        assertEquals(AppStartupState.READY, gate.state.value)
        assertEquals(2, attempts)
        assertEquals(1, syncCalls)
    }

    @Test
    fun `sync scheduling failure does not hide a usable database`() {
        val gate = StartupGate(
            verifyDatabase = {},
            enqueueSync = { error("work manager unavailable") },
        )

        gate.verify()

        assertEquals(AppStartupState.READY, gate.state.value)
    }
}
