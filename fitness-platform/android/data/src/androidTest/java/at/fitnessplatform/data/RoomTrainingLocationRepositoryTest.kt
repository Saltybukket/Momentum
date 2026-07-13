package at.fitnessplatform.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.fitnessplatform.core.database.AppDatabase
import at.fitnessplatform.core.model.Clock
import at.fitnessplatform.core.model.LocationType
import at.fitnessplatform.core.model.UuidProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTrainingLocationRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTrainingLocationRepository
    private var now = 100L
    private var sequence = 0

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomTrainingLocationRepository(
            database,
            database.trainingLocationDao(),
            object : UuidProvider { override fun newUuid() = "location-${sequence++}" },
            object : Clock { override fun nowEpochMs() = now++ },
        )
    }

    @After fun tearDown() = database.close()

    @Test fun firstLocationIsActiveAndSwitchSurvivesAtomicEquipmentReplacement() = runTest {
        val home = repository.create("Home", LocationType.HOME, setOf("mat"))
        val gym = repository.create("Gym", LocationType.FITNESS_CENTER, setOf("barbell"))
        assertTrue(home.isActive)
        assertEquals("Home", repository.observeActiveLocation().first()?.name)

        repository.setActive(gym.id)
        repository.replaceEquipment(gym.id, setOf("dumbbells", "bench"))
        val active = repository.observeActiveLocation().first()
        assertEquals("Gym", active?.name)
        assertEquals(setOf("dumbbells", "bench"), active?.equipmentSlugs)

        repository.delete(gym.id)
        assertEquals("Home", repository.observeActiveLocation().first()?.name)
        assertEquals(listOf(home.id), repository.observeLocations().first().map { it.id })
    }

    @Test fun repositoryRejectsUnknownAndImplicitNoneEquipmentWithoutPartialWrites() = runTest {
        assertTrue(runCatching {
            repository.create("Bad", LocationType.CUSTOM, setOf("unknown-machine"))
        }.isFailure)
        assertTrue(runCatching {
            repository.create("Bad", LocationType.CUSTOM, setOf("none"))
        }.isFailure)
        assertTrue(repository.observeLocations().first().isEmpty())
    }
}
