package at.fitnessplatform.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.fitnessplatform.core.model.*
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val name = "database-restart-test.db"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
        database = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
    }

    @After fun tearDown() { database.close(); context.deleteDatabase(name) }

    @Test fun guestProfileSurvivesDatabaseRestart() = runTest {
        database.guestProfileDao().insert(GuestProfile("p1", "Guest", 1).toEntity())
        database.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        assertEquals("Guest", database.guestProfileDao().get()?.displayName)
    }

    @Test fun exerciseCanBeCreatedEditedAndSoftDeleted() = runTest {
        database.guestProfileDao().insert(GuestProfile("p1", "Guest", 1).toEntity())
        val original = CustomExercise("e1", "p1", "Squat", "", "Legs", "None", TrackingType.REPS, "", 1, 1)
        database.exerciseDao().insert(original.toEntity())
        database.exerciseDao().update(original.copy(name = "Air squat", updatedAtEpochMs = 2).toEntity())
        assertEquals("Air squat", database.exerciseDao().get("e1")?.name)
        database.exerciseDao().update(original.copy(deletedAtEpochMs = 3).toEntity())
        assertEquals(0, database.exerciseDao().observeActive().first().size)
    }

    @Test fun workoutAndOutboxAreStored() = runTest {
        database.guestProfileDao().insert(GuestProfile("p1", "Guest", 1).toEntity())
        database.exerciseDao().insert(CustomExercise("e1", "p1", "Squat", "", "Legs", "None", TrackingType.REPS, "", 1, 1).toEntity())
        val workout = Workout("w1", "p1", "Session", exercises = listOf(WorkoutExercise("we1", "w1", "e1", 0)), createdAtEpochMs = 1, updatedAtEpochMs = 1)
        database.workoutDao().insertWorkout(workout.toEntity())
        database.workoutDao().insertExercises(workout.exercises.map { it.toEntity() })
        database.outboxDao().insert(OutboxEntity("o1", "w1", OutboxOperationType.UPSERT_WORKOUT.name, "{}", 1, SyncStatus.PENDING.name, 0, null))
        assertNotNull(database.workoutDao().get("w1"))
        assertEquals(1, database.outboxDao().pending().size)
    }
}
