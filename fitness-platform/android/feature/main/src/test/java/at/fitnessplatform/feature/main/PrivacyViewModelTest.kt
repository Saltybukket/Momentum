package at.fitnessplatform.feature.main

import at.fitnessplatform.core.testing.MainDispatcherRule
import at.fitnessplatform.domain.SyncPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `opt in and opt out reflect persisted consent`() = runTest {
        val repository = FakeSyncPreferencesRepository()
        val viewModel = PrivacyViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }

        viewModel.setSyncEnabled(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.syncEnabled)
        assertEquals(listOf(true), repository.changes)

        viewModel.setSyncEnabled(false)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.syncEnabled)
        assertEquals(listOf(true, false), repository.changes)
        job.cancel()
    }

    @Test
    fun `preference failure is visible and not reported as success`() = runTest {
        val repository = FakeSyncPreferencesRepository().apply { fail = true }
        val viewModel = PrivacyViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }

        viewModel.setSyncEnabled(true)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.syncEnabled)
        assertFalse(viewModel.state.value.changing)
        assertEquals("SYNC_PREFERENCE_FAILED", viewModel.state.value.error)
        job.cancel()
    }
}

private class FakeSyncPreferencesRepository : SyncPreferencesRepository {
    private val enabled = MutableStateFlow(false)
    private val pending = MutableStateFlow(2)
    val changes = mutableListOf<Boolean>()
    var fail = false

    override fun observeEnabled() = enabled
    override fun observePendingCount() = pending
    override suspend fun setEnabled(enabled: Boolean) {
        if (fail) error("failure")
        changes += enabled
        this.enabled.value = enabled
    }
}
