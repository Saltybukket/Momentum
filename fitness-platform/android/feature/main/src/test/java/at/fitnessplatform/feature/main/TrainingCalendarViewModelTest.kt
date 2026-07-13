package at.fitnessplatform.feature.main

import at.fitnessplatform.core.model.*
import at.fitnessplatform.core.testing.MainDispatcherRule
import at.fitnessplatform.domain.*
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingCalendarViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `state exposes offline occurrences conflicts and finite operations`() = runTest {
        val calendar = CalendarStateRepository()
        val plan = CalendarPlanRepository()
        val ids = object : UuidProvider { override fun newUuid() = "new-id" }
        val clock = object : Clock { override fun nowEpochMs() = 1_768_176_000_000L }
        val viewModel = TrainingCalendarViewModel(
            calendar,
            ObserveActiveTrainingPlanUseCase(plan),
            CreatePlanScheduleUseCase(calendar, ids, clock),
            MoveOccurrenceUseCase(calendar),
            UpdateScheduleRuleUseCase(calendar),
            ChangeOccurrenceStatusUseCase(calendar),
            DetectCalendarConflictsUseCase(),
            calendar,
            CalendarLocationRepository(),
            ids,
            clock,
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertEquals(1, viewModel.state.value.occurrences.size)
        assertTrue(viewModel.state.value.conflicts.any { it.type == CalendarConflictType.OUTSIDE_AVAILABILITY })
        viewModel.setMode(CalendarDisplayMode.AGENDA)
        viewModel.selectDate(LocalDate.of(2026, 1, 13))
        viewModel.skip("occurrence")
        advanceUntilIdle()
        assertEquals(CalendarDisplayMode.AGENDA, viewModel.state.value.mode)
        assertEquals(ScheduledWorkoutStatus.SKIPPED, calendar.rows.value.single().status)
        assertFalse(viewModel.state.value.saving)
    }
}

private class CalendarLocationRepository : TrainingLocationRepository {
    override fun observeLocations(): Flow<List<TrainingLocation>> = MutableStateFlow(emptyList())
    override fun observeActiveLocation(): Flow<TrainingLocation?> = MutableStateFlow(null)
    override suspend fun getLocation(id: String): TrainingLocation? = null
    override suspend fun create(name: String, type: LocationType, equipmentSlugs: Set<String>) = error("unused")
    override suspend fun update(location: TrainingLocation) = error("unused")
    override suspend fun setActive(id: String) = Unit
    override suspend fun replaceEquipment(id: String, equipmentSlugs: Set<String>) = Unit
    override suspend fun delete(id: String) = Unit
}

private class CalendarStateRepository : TrainingCalendarRepository {
    val rows = MutableStateFlow(listOf(
        ScheduledWorkoutOccurrence(
            "occurrence",
            "owner",
            titleSnapshot = "Strength",
            scheduledLocalDate = LocalDate.of(2026, 1, 12),
            scheduledLocalStartTime = LocalTime.of(8, 0),
            timeZoneId = "Europe/Berlin",
            plannedDurationMinutes = 90,
            createdAtEpochMs = 0,
            updatedAtEpochMs = 0,
        ),
    ))
    override fun observeOccurrences(from: LocalDate, to: LocalDate): Flow<List<ScheduledWorkoutOccurrence>> = rows
    override fun observeActiveSchedule(): Flow<PlanSchedule?> = MutableStateFlow(null)
    override fun observeAvailability(): Flow<List<AvailabilityRule>> = MutableStateFlow(listOf(
        AvailabilityRule("availability", "owner", java.time.DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), 60),
    ))
    override fun observeOverrides(): Flow<List<ScheduleOverride>> = MutableStateFlow(emptyList())
    override suspend fun saveSchedule(schedule: PlanSchedule) = schedule
    override suspend fun materialize(scheduleId: String, through: LocalDate) = rows.value
    override suspend fun replaceFuturePlanned(scheduleId: String, from: LocalDate, through: LocalDate) = rows.value
    override suspend fun saveOccurrence(occurrence: ScheduledWorkoutOccurrence) = occurrence.also { row ->
        rows.value = rows.value.filterNot { it.id == row.id } + row
    }
    override suspend fun copyOccurrence(id: String) = rows.value.single().copy(id = "copy")
    override suspend fun changeStatus(id: String, status: ScheduledWorkoutStatus) {
        rows.value = rows.value.map { if (it.id == id) it.copy(status = status) else it }
    }
    override suspend fun saveAvailability(rule: AvailabilityRule) = Unit
    override suspend fun saveOverride(override: ScheduleOverride) = Unit
}

private class CalendarPlanRepository : TrainingPlanRepository {
    private val plan = TrainingPlan("plan", "owner", "Plan", createdAtEpochMs = 0, updatedAtEpochMs = 0)
    override fun observePlans(): Flow<List<TrainingPlan>> = MutableStateFlow(listOf(plan))
    override fun observeActivePlan(): Flow<TrainingPlan?> = MutableStateFlow(plan)
    override fun observePlan(id: String): Flow<TrainingPlan?> = MutableStateFlow(plan)
    override suspend fun getPlan(id: String) = plan
    override suspend fun create(plan: TrainingPlan) = plan
    override suspend fun update(plan: TrainingPlan) = plan
    override suspend fun copy(id: String, transform: (TrainingPlan) -> TrainingPlan) = transform(plan)
    override suspend fun setActive(id: String) = Unit
    override suspend fun setArchived(id: String, archived: Boolean) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun seedStarterPlans() = Unit
}
