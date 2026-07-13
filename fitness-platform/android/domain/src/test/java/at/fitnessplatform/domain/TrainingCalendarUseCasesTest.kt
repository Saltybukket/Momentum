package at.fitnessplatform.domain

import at.fitnessplatform.core.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class TrainingCalendarUseCasesTest {
    @Test fun materializationIsBoundedStableAndKeepsCivilTimeAcrossDst() {
        val plan = plan()
        val schedule = schedule(
            rules = listOf(
                rule("rule-a", "day-a", DayOfWeek.SUNDAY, LocalTime.of(2, 30), 0),
                rule("rule-b", "day-b", DayOfWeek.SUNDAY, LocalTime.of(18, 0), 1),
            ),
        )
        val through = LocalDate.of(2026, 4, 5)
        val first = materializeSchedule(schedule, plan, through, 100) { ruleId, date -> "$ruleId:$date" }
        val second = materializeSchedule(schedule, plan, through, 100) { ruleId, date -> "$ruleId:$date" }

        assertEquals(first, second)
        assertEquals(4, first.size)
        assertEquals(setOf(LocalTime.of(2, 30), LocalTime.of(18, 0)), first.take(2).map { it.scheduledLocalStartTime }.toSet())
        assertEquals("Europe/Berlin", first.first().timeZoneId)
        assertEquals(2, first.groupBy { it.scheduledLocalDate }.values.first().size)
    }

    @Test fun materializationRejectsUnknownPlanDaysAndInvalidZones() {
        assertTrue(runCatching {
            materializeSchedule(
                schedule(rules = listOf(rule("rule", "unknown", DayOfWeek.MONDAY, null, 0))),
                plan(),
                LocalDate.of(2026, 3, 30),
                0,
            ) { _, _ -> "id" }
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(
            runCatching { validateSchedule(schedule().copy(timeZoneId = "GMT+99")) }
                .exceptionOrNull() is ValidationException,
        )
    }

    @Test fun conflictsCombineAvailabilityOverridesAndOverlapWithoutPersistingAuthority() {
        val date = LocalDate.of(2026, 7, 13)
        val first = occurrence("one", date, LocalTime.of(8, 0), 90, "home")
        val second = occurrence("two", date, LocalTime.of(9, 0), 90, null)
        val conflicts = detectCalendarConflicts(
            listOf(first, second),
            listOf(AvailabilityRule("availability", "owner", DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), 60, "gym")),
            emptyList(),
        )

        assertTrue(conflicts.any { it.occurrenceId == "one" && it.type == CalendarConflictType.OUTSIDE_AVAILABILITY })
        assertTrue(conflicts.any { it.occurrenceId == "one" && it.type == CalendarConflictType.DURATION_EXCEEDED })
        assertTrue(conflicts.any { it.occurrenceId == "two" && it.type == CalendarConflictType.LOCATION_REQUIRED })
        assertTrue(conflicts.any { it.occurrenceId == "two" && it.type == CalendarConflictType.OVERLAP })
    }

    @Test fun moveRejectsTerminalRowsAndFutureRuleReplacementIsExplicit() = runTest {
        val repository = FakeCalendarRepository()
        val completed = occurrence("completed", LocalDate.of(2026, 7, 13), LocalTime.NOON, 60, null)
            .copy(status = ScheduledWorkoutStatus.COMPLETED)
        assertTrue(runCatching {
            MoveOccurrenceUseCase(repository)(completed, completed.scheduledLocalDate.plusDays(1), null, 60, null)
        }.exceptionOrNull() is ValidationException)

        val result = ReplaceFutureScheduleUseCase(repository)(schedule(), LocalDate.of(2026, 7, 14))
        assertTrue(repository.replaced)
        assertEquals(repository.rows, result)
    }

    private fun plan() = TrainingPlan(
        id = "plan",
        ownerProfileId = "owner",
        name = "Plan",
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
        weeks = listOf(PlanWeek("week", 0, "Week", listOf(
            PlanDay("day-a", 0, "Morning"),
            PlanDay("day-b", 1, "Evening"),
        ))),
    )

    private fun schedule(rules: List<PlanDayScheduleRule> = emptyList()) = PlanSchedule(
        "schedule",
        "owner",
        "plan",
        LocalDate.of(2026, 3, 29),
        "Europe/Berlin",
        true,
        0,
        0,
        rules = rules,
    )

    private fun rule(
        id: String,
        dayId: String,
        weekday: DayOfWeek,
        time: LocalTime?,
        position: Int,
    ) = PlanDayScheduleRule(id, "schedule", dayId, weekday, time, 60, null, position)

    private fun occurrence(
        id: String,
        date: LocalDate,
        time: LocalTime?,
        duration: Int,
        location: String?,
    ) = ScheduledWorkoutOccurrence(
        id,
        "owner",
        titleSnapshot = "Workout",
        scheduledLocalDate = date,
        scheduledLocalStartTime = time,
        timeZoneId = "Europe/Berlin",
        plannedDurationMinutes = duration,
        trainingLocationId = location,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
    )
}

private class FakeCalendarRepository : TrainingCalendarRepository {
    var replaced = false
    val rows = listOf(
        ScheduledWorkoutOccurrence(
            "row",
            "owner",
            titleSnapshot = "Workout",
            scheduledLocalDate = LocalDate.of(2026, 7, 14),
            timeZoneId = "Europe/Berlin",
            plannedDurationMinutes = 60,
            createdAtEpochMs = 0,
            updatedAtEpochMs = 0,
        ),
    )
    override fun observeOccurrences(from: LocalDate, to: LocalDate): Flow<List<ScheduledWorkoutOccurrence>> =
        MutableStateFlow(rows)
    override fun observeActiveSchedule(): Flow<PlanSchedule?> = MutableStateFlow(null)
    override fun observeAvailability(): Flow<List<AvailabilityRule>> = MutableStateFlow(emptyList())
    override fun observeOverrides(): Flow<List<ScheduleOverride>> = MutableStateFlow(emptyList())
    override suspend fun saveSchedule(schedule: PlanSchedule) = schedule
    override suspend fun materialize(scheduleId: String, through: LocalDate) = rows
    override suspend fun replaceFuturePlanned(scheduleId: String, from: LocalDate, through: LocalDate): List<ScheduledWorkoutOccurrence> {
        replaced = true
        return rows
    }
    override suspend fun saveOccurrence(occurrence: ScheduledWorkoutOccurrence) = occurrence
    override suspend fun copyOccurrence(id: String) = rows.first()
    override suspend fun changeStatus(id: String, status: ScheduledWorkoutStatus) = Unit
    override suspend fun saveAvailability(rule: AvailabilityRule) = Unit
    override suspend fun saveOverride(override: ScheduleOverride) = Unit
}
