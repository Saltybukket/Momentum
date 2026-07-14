package at.fitnessplatform.data

import androidx.room.withTransaction
import at.fitnessplatform.core.database.AppDatabase
import at.fitnessplatform.core.model.PlanSchedule
import at.fitnessplatform.core.model.ScheduledWorkoutOccurrence
import at.fitnessplatform.domain.TrainingPlanCalendarCoordinator
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTrainingPlanCalendarCoordinator @Inject constructor(
    private val database: AppDatabase,
    private val plans: RoomTrainingPlanRepository,
    private val calendar: RoomTrainingCalendarRepository,
) : TrainingPlanCalendarCoordinator {
    override suspend fun activatePlanWithSchedule(
        planId: String,
        schedule: PlanSchedule,
        through: LocalDate,
    ): List<ScheduledWorkoutOccurrence> = database.withTransaction {
        val rows = calendar.saveAndMaterialize(schedule, through)
        plans.setActive(planId)
        rows
    }

    override suspend fun setArchived(planId: String, archived: Boolean) {
        database.withTransaction {
            if (archived) deactivateScheduleForPlan(planId)
            plans.setArchived(planId, archived)
        }
    }

    override suspend fun delete(planId: String) {
        database.withTransaction {
            deactivateScheduleForPlan(planId)
            plans.delete(planId)
        }
    }

    private suspend fun deactivateScheduleForPlan(planId: String) {
        val active = calendar.getActiveSchedule()
        if (active?.planId == planId) calendar.saveSchedule(active.copy(isActive = false))
    }
}
