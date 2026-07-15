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
        require(schedule.isActive) { "Schedule must be active." }
        require(schedule.planId == planId) { "Schedule plan ID must match the target plan." }
        require(!through.isBefore(schedule.startDate)) {
            "Through date must be on or after the schedule start date."
        }
        val plan = requireNotNull(plans.getPlan(planId)) { "Training plan does not exist." }
        require(schedule.ownerProfileId == plan.ownerProfileId) {
            "Schedule owner must match plan owner."
        }

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
