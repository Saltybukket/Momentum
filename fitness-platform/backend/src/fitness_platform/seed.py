import asyncio
from uuid import NAMESPACE_URL, UUID, uuid5

from fitness_platform.container import AppContainer
from fitness_platform.core.config import get_settings
from fitness_platform.domain.enums import TrackingType

DEMO_EXERCISES = [
    ("Bodyweight Squat", "Quadriceps", "None", TrackingType.REPS),
    ("Push-up", "Chest", "None", TrackingType.REPS),
    ("Dumbbell Row", "Back", "Dumbbells", TrackingType.REPS_WEIGHT),
    ("Plank", "Core", "Mat", TrackingType.DURATION),
    ("Walking", "Cardiovascular", "None", TrackingType.DISTANCE_DURATION),
    ("Dumbbell Romanian Deadlift", "Hamstrings", "Dumbbells", TrackingType.REPS_WEIGHT),
    ("Band Pull-apart", "Upper back", "Resistance band", TrackingType.REPS),
    ("Goblet Squat", "Quadriceps", "Kettlebell", TrackingType.REPS_WEIGHT),
    ("Overhead Press", "Shoulders", "Dumbbells", TrackingType.REPS_WEIGHT),
    ("Dead Bug", "Core", "Mat", TrackingType.REPS),
]


async def seed() -> None:
    container = AppContainer.build(get_settings())
    profile, _token, _recovered = await container.guests.create_guest(
        "Demo Guest",
        uuid5(NAMESPACE_URL, "momentum-demo-installation"),
        "momentum-demo-recovery-secret-000000000000",
    )
    exercise_ids: list[UUID] = []
    for name, muscle, equipment, tracking_type in DEMO_EXERCISES:
        exercise = await container.exercises.create(
            user_id=profile.user_id,
            exercise_id=None,
            name=name,
            description="Technical demo data; not a reviewed training prescription.",
            primary_muscle_group=muscle,
            equipment=equipment,
            tracking_type=tracking_type,
            notes="DEMO DATA",
        )
        exercise_ids.append(exercise.id)
    await container.workouts.create(
        user_id=profile.user_id,
        workout_id=None,
        title="Demo Full Body",
        notes="DEMO DATA",
        exercise_ids=exercise_ids[:4],
    )
    await container.workouts.create(
        user_id=profile.user_id,
        workout_id=None,
        title="Demo Mobility & Walk",
        notes="DEMO DATA",
        exercise_ids=[exercise_ids[3], exercise_ids[4]],
    )
    print(f"Created demo guest {profile.user_id}; token intentionally not printed.")
    await container.redis.close()
    await container.database.dispose()


def run() -> None:
    asyncio.run(seed())


if __name__ == "__main__":
    run()
