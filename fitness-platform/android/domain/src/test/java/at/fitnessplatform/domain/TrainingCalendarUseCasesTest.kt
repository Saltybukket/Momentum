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

    @Test fun materializationCyclesDeterministicallyAcrossOneTwoFourAndTwelveWeeks() {
        listOf(1, 2, 4, 12).forEach { weekCount ->
            val start = LocalDate.of(2026, 7, 15)
            val plan = cyclePlan(weekCount)
            val schedule = PlanSchedule(
                id = "schedule",
                ownerProfileId = "owner",
                planId = plan.id,
                startDate = start,
                timeZoneId = "Europe/Berlin",
                isActive = true,
                createdAtEpochMs = 0,
                updatedAtEpochMs = 0,
                rules = plan.weeks.mapIndexed { index, week ->
                    rule("rule-$index", week.days.single().id, DayOfWeek.THURSDAY, LocalTime.NOON, index)
                },
            )
            val through = start.plusWeeks(weekCount.toLong()).plusDays(1)

            val rows = materializeSchedule(schedule, plan, through, 1) { ruleId, date -> "$ruleId:$date" }

            assertEquals((0 until weekCount).toList() + 0, rows.map { it.planWeekIndexSnapshot })
            assertEquals(rows.map { it.id }.distinct().size, rows.size)
            assertEquals(
                rows,
                materializeSchedule(schedule, plan, through, 1) { ruleId, date -> "$ruleId:$date" },
            )
        }
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
        assertTrue(listOf("one", "two").all { id ->
            conflicts.any { it.occurrenceId == id && it.type == CalendarConflictType.OVERLAP }
        })
    }

    @Test fun conflictsUseOccurrenceSnapshotsAndMarkEveryNestedAndCrossDayOverlapParticipant() {
        val date = LocalDate.of(2026, 7, 13)
        val nested = listOf(
            occurrence("outer", date, LocalTime.of(8, 0), 240, "gym"),
            occurrence("inner", date, LocalTime.of(9, 0), 30, "gym"),
            occurrence("late", date, LocalTime.of(11, 30), 90, "gym"),
            occurrence("after-midnight", date, LocalTime.of(23, 30), 120, "gym"),
            occurrence("next-day", date.plusDays(1), LocalTime.of(0, 30), 30, "gym"),
        ).map { row ->
            if (row.id == "outer") {
                row.copy(
                    requiredEquipmentSnapshot = setOf("barbell", "bench"),
                    hasUnavailableExerciseSnapshot = true,
                )
            } else {
                row
            }
        }
        val conflicts = detectCalendarConflicts(
            occurrences = nested,
            availability = emptyList(),
            overrides = emptyList(),
            equipmentByLocation = mapOf("gym" to setOf("barbell")),
        )

        assertTrue(setOf("outer", "inner", "late", "after-midnight", "next-day").all { id ->
            conflicts.any { it.occurrenceId == id && it.type == CalendarConflictType.OVERLAP }
        })
        assertTrue(conflicts.any { it.occurrenceId == "outer" && it.type == CalendarConflictType.MISSING_EQUIPMENT })
        assertTrue(conflicts.any {
            it.occurrenceId == "outer" && it.type == CalendarConflictType.UNAVAILABLE_EXERCISE
        })
        val noLocation = detectCalendarConflicts(
            occurrences = listOf(nested.first().copy(trainingLocationId = null)),
            availability = emptyList(),
            overrides = emptyList(),
        )
        assertTrue(noLocation.any { it.type == CalendarConflictType.LOCATION_REQUIRED })
    }

    @Test fun conflictsRejectDstGapAndUseEarlierOffsetForDstOverlap() {
        val gap = occurrence(
            "gap",
            LocalDate.of(2026, 3, 29),
            LocalTime.of(2, 30),
            30,
            null,
        )
        val overlapDate = LocalDate.of(2026, 10, 25)
        val ambiguous = listOf(
            occurrence("ambiguous-a", overlapDate, LocalTime.of(2, 30), 60, null),
            occurrence("ambiguous-b", overlapDate, LocalTime.of(2, 45), 30, null),
        )

        val conflicts = detectCalendarConflicts(listOf(gap) + ambiguous, emptyList(), emptyList())

        assertTrue(conflicts.any { it.occurrenceId == "gap" && it.type == CalendarConflictType.INVALID_LOCAL_TIME })
        assertTrue(listOf("ambiguous-a", "ambiguous-b").all { id ->
            conflicts.any { it.occurrenceId == id && it.type == CalendarConflictType.OVERLAP }
        })
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

    @Test fun ensureHorizonTargetsTodayThroughFiftyFiveDaysAndIsDelegatedIdempotently() = runTest {
        val repository = FakeCalendarRepository()
        val today = LocalDate.of(2026, 7, 14)
        val useCase = EnsureCalendarHorizonUseCase(repository)

        assertEquals(repository.rows, useCase("schedule", today))
        assertEquals(today to today.plusDays(55), repository.ensuredRange)
        assertEquals(repository.rows, useCase("schedule", today))
        assertEquals(2, repository.ensureCalls)
    }

    @Test fun permanentRuleChangeRequiresPreviewBeforeConfirmedReplacement() = runTest {
        val repository = FakeCalendarRepository()
        val useCase = UpdateScheduleRuleUseCase(repository)
        val schedule = schedule(
            rules = listOf(rule("rule", "day-a", DayOfWeek.MONDAY, LocalTime.of(8, 0), 0)),
        )

        val preview = useCase.preview(
            schedule = schedule,
            planDayId = "day-a",
            effectiveDate = LocalDate.of(2026, 7, 15),
            time = LocalTime.of(10, 0),
            durationMinutes = 75,
            locationId = "gym",
        )

        assertEquals(repository.rows.map { it.id }, preview.affectedOccurrenceIds)
        assertTrue(!repository.replaced)
        assertEquals(null, repository.savedSchedule)
        useCase.confirm(preview)
        assertTrue(repository.savedAndReplaced)
        assertEquals(LocalTime.of(10, 0), repository.savedSchedule?.rules?.single()?.defaultStartTime)
    }

    @Test fun actionPolicyNeverOffersMutationsThatTheLifecycleRejects() {
        assertTrue(CalendarAction.MOVE in allowedCalendarActions(ScheduledWorkoutStatus.PLANNED))
        assertTrue(allowedCalendarActions(ScheduledWorkoutStatus.IN_PROGRESS).isEmpty())
        listOf(
            ScheduledWorkoutStatus.COMPLETED,
            ScheduledWorkoutStatus.SKIPPED,
            ScheduledWorkoutStatus.CANCELLED,
        ).forEach { status ->
            assertEquals(setOf(CalendarAction.VIEW, CalendarAction.COPY), allowedCalendarActions(status))
        }
    }

    @Test fun occurrenceTextPolicyRejectsControlsBidiNewlinesAndUnnormalizedWhitespace() {
        val base = occurrence("safe", LocalDate.of(2026, 7, 13), LocalTime.NOON, 60, null)
        listOf("Unsafe\u0000", "Unsafe\u202e", "Two\nlines", " Two  spaces ").forEach { title ->
            assertTrue(
                runCatching { validateOccurrence(base.copy(titleSnapshot = title)) }
                    .exceptionOrNull() is IllegalArgumentException,
            )
        }
        assertEquals("Two lines", canonicalCalendarText(" Two  lines ", singleLine = true))
        assertEquals("Line one\nLine two", canonicalCalendarText(" Line  one\r\nLine two ", singleLine = false))
    }

    @Test fun scheduleSetupRequiresExplicitConfigurationForEveryPlanDay() = runTest {
        val repository = FakeCalendarRepository()
        var next = 0
        val useCase = CreatePlanScheduleUseCase(
            repository,
            object : UuidProvider { override fun newUuid() = "id-${next++}" },
            object : Clock { override fun nowEpochMs() = 1L },
        )
        val onlyOne = listOf(ScheduleRuleDraft("day-a", DayOfWeek.MONDAY, null, 60, null))
        assertTrue(
            runCatching { useCase(plan(), LocalDate.of(2026, 7, 13), "Europe/Berlin", onlyOne) }
                .exceptionOrNull() is ValidationException,
        )
        val allDays = onlyOne + ScheduleRuleDraft("day-b", DayOfWeek.THURSDAY, LocalTime.NOON, 45, "gym")
        assertEquals(
            repository.rows,
            useCase(plan(), LocalDate.of(2026, 7, 13), "Europe/Berlin", allDays),
        )
    }

    @Test fun scheduleSetupPreviewDetectsConflictsWithoutPersisting() {
        val drafts = listOf(
            ScheduleRuleDraft("day-a", DayOfWeek.MONDAY, LocalTime.of(10, 0), 60, null),
            ScheduleRuleDraft("day-b", DayOfWeek.TUESDAY, LocalTime.of(11, 0), 45, null),
        )
        val availability = listOf(
            AvailabilityRule(
                "availability",
                "owner",
                DayOfWeek.MONDAY,
                LocalTime.NOON,
                LocalTime.of(14, 0),
            ),
        )
        val preview = PreviewPlanScheduleUseCase(object : Clock {
            override fun nowEpochMs() = 100L
        })(
            plan = plan(),
            startDate = LocalDate.of(2026, 7, 13),
            timeZoneId = "Europe/Berlin",
            drafts = drafts,
            availability = availability,
            overrides = emptyList(),
            locations = emptyList(),
        )

        assertEquals(drafts, preview.drafts)
        assertTrue(preview.occurrences.isNotEmpty())
        assertTrue(preview.conflicts.any { it.type == CalendarConflictType.OUTSIDE_AVAILABILITY })
        assertEquals(
            preview,
            PreviewPlanScheduleUseCase(object : Clock { override fun nowEpochMs() = 100L })(
                plan(),
                LocalDate.of(2026, 7, 13),
                "Europe/Berlin",
                drafts,
                availability,
                emptyList(),
                emptyList(),
            ),
        )
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

    private fun cyclePlan(weekCount: Int) = TrainingPlan(
        id = "plan",
        ownerProfileId = "owner",
        name = "Cycle",
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
        weeks = (0 until weekCount).map { index ->
            PlanWeek(
                id = "week-$index",
                position = index,
                weekIndex = index,
                title = "Week ${index + 1}",
                days = listOf(PlanDay("day-$index", 0, "Day ${index + 1}")),
            )
        },
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
    var ensuredRange: Pair<LocalDate, LocalDate>? = null
    var ensureCalls = 0
    var savedSchedule: PlanSchedule? = null
    var savedAndReplaced = false
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
    override suspend fun saveSchedule(schedule: PlanSchedule) = schedule.also { savedSchedule = it }
    override suspend fun saveAndMaterialize(schedule: PlanSchedule, through: LocalDate) =
        rows.also { savedSchedule = schedule }
    override suspend fun materialize(scheduleId: String, through: LocalDate) = rows
    override suspend fun ensureHorizon(scheduleId: String, from: LocalDate, through: LocalDate): List<ScheduledWorkoutOccurrence> {
        ensuredRange = from to through
        ensureCalls += 1
        return rows
    }
    override suspend fun generatedOccurrences(scheduleId: String, from: LocalDate, through: LocalDate) = rows
    override suspend fun replaceFuturePlanned(scheduleId: String, from: LocalDate, through: LocalDate): List<ScheduledWorkoutOccurrence> {
        replaced = true
        return rows
    }
    override suspend fun saveAndReplaceFuturePlanned(
        schedule: PlanSchedule,
        from: LocalDate,
        through: LocalDate,
    ) = rows.also {
        savedSchedule = schedule
        savedAndReplaced = true
    }
    override suspend fun saveOccurrence(occurrence: ScheduledWorkoutOccurrence) = occurrence
    override suspend fun copyOccurrence(id: String) = rows.first()
    override suspend fun changeStatus(id: String, status: ScheduledWorkoutStatus) = Unit
    override suspend fun saveAvailability(rule: AvailabilityRule) = Unit
    override suspend fun saveOverride(override: ScheduleOverride) = Unit
    override suspend fun deleteAvailability(id: String) = Unit
    override suspend fun deleteOverride(id: String) = Unit
}
