from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Header, Query, Request, Response, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from sqlalchemy import text

from fitness_platform.core.security import request_fingerprint
from fitness_platform.domain.models import Exercise
from fitness_platform.presentation.dependencies import ContainerDep, CurrentUserId
from fitness_platform.presentation.schemas import (
    CatalogExerciseResponse,
    CatalogFacetResponse,
    ExerciseChange,
    ExercisePage,
    ExerciseResponse,
    ExerciseWrite,
    GuestSessionCreate,
    GuestSessionResponse,
    HealthResponse,
    PageMeta,
    ProfileResponse,
    ProfileUpdate,
    SyncPullResponse,
    SyncPushRequest,
    SyncPushResponse,
    SyncResult,
    WorkoutPage,
    WorkoutResponse,
    WorkoutWrite,
)

router = APIRouter()


@router.get(
    "/api/v1/catalog/exercises", response_model=list[CatalogExerciseResponse], tags=["catalog"]
)
async def list_catalog_exercises(
    container: ContainerDep,
    muscle: str | None = None,
    equipment: str | None = None,
) -> list[CatalogExerciseResponse]:
    return [
        CatalogExerciseResponse.from_domain(item)
        for item in await container.catalog.list(muscle, equipment)
    ]


@router.get(
    "/api/v1/catalog/exercises/{exercise_id}",
    response_model=CatalogExerciseResponse,
    tags=["catalog"],
)
async def get_catalog_exercise(
    exercise_id: UUID, container: ContainerDep
) -> CatalogExerciseResponse:
    return CatalogExerciseResponse.from_domain(await container.catalog.get(exercise_id))


@router.get("/api/v1/catalog/muscles", response_model=list[CatalogFacetResponse], tags=["catalog"])
async def list_catalog_muscles(container: ContainerDep) -> list[CatalogFacetResponse]:
    return [
        CatalogFacetResponse(slug=slug, name=name)
        for slug, name in await container.catalog.muscles()
    ]


@router.get(
    "/api/v1/catalog/equipment", response_model=list[CatalogFacetResponse], tags=["catalog"]
)
async def list_catalog_equipment(container: ContainerDep) -> list[CatalogFacetResponse]:
    return [
        CatalogFacetResponse(slug=slug, name=name)
        for slug, name in await container.catalog.equipment()
    ]


def _hash_model(payload: BaseModel) -> str:
    return request_fingerprint(payload.model_dump_json().encode())


@router.get("/health", response_model=HealthResponse, tags=["system"])
async def health(container: ContainerDep) -> HealthResponse:
    database_status = "up"
    redis_status = "up"
    try:
        async with container.database.engine.connect() as connection:
            await connection.execute(text("SELECT 1"))
    except Exception:  # health endpoints report degraded state instead of leaking details
        database_status = "down"
    try:
        await container.redis.ping()
    except Exception:
        redis_status = "down"
    overall = "ok" if database_status == "up" else "degraded"
    return HealthResponse(
        status=overall,
        database=database_status,
        redis=redis_status,
        version="0.1.0",
    )


@router.post(
    "/api/v1/guest-sessions",
    response_model=GuestSessionResponse,
    status_code=status.HTTP_201_CREATED,
    tags=["identity"],
)
async def create_guest_session(
    request: Request,
    payload: GuestSessionCreate,
    container: ContainerDep,
    idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None,
) -> Response:
    await container.rate_limiter.check(
        f"guest-session:{request.client.host if request.client else 'unknown'}",
        container.settings.rate_limit_guest_sessions_per_minute,
    )

    async def operation() -> tuple[int, dict[str, object]]:
        profile, token = await container.guests.create_guest(payload.display_name)
        response = GuestSessionResponse(
            guest_token=token,
            profile=ProfileResponse.from_domain(profile),
            expires_in_seconds=container.settings.guest_token_ttl_hours * 3600,
        )
        return status.HTTP_201_CREATED, response.model_dump(mode="json")

    response_status, body, replayed = await container.idempotency.execute(
        scope="create-guest-session",
        key=idempotency_key,
        request_hash=_hash_model(payload),
        operation=operation,
    )
    return JSONResponse(
        status_code=response_status,
        content=body,
        headers={"Idempotency-Replayed": str(replayed).lower()},
    )


@router.get("/api/v1/profile", response_model=ProfileResponse, tags=["profile"])
async def get_profile(user_id: CurrentUserId, container: ContainerDep) -> ProfileResponse:
    return ProfileResponse.from_domain(await container.profiles.get(user_id))


@router.put("/api/v1/profile", response_model=ProfileResponse, tags=["profile"])
async def update_profile(
    payload: ProfileUpdate,
    user_id: CurrentUserId,
    container: ContainerDep,
) -> ProfileResponse:
    profile = await container.profiles.update(
        user_id=user_id,
        display_name=payload.display_name,
        unit_system=payload.unit_system,
        onboarding_status=payload.onboarding_status,
    )
    return ProfileResponse.from_domain(profile)


@router.get("/api/v1/exercises", response_model=ExercisePage, tags=["exercises"])
async def list_exercises(
    user_id: CurrentUserId,
    container: ContainerDep,
    limit: Annotated[int, Query(ge=1, le=100)] = 50,
    offset: Annotated[int, Query(ge=0)] = 0,
) -> ExercisePage:
    exercises = list(await container.exercises.list(user_id))
    page_items = exercises[offset : offset + limit]
    return ExercisePage(
        items=[ExerciseResponse.from_domain(item) for item in page_items],
        page=PageMeta(limit=limit, offset=offset, total=len(exercises)),
    )


@router.post(
    "/api/v1/exercises",
    response_model=ExerciseResponse,
    status_code=status.HTTP_201_CREATED,
    tags=["exercises"],
)
async def create_exercise(
    payload: ExerciseWrite,
    user_id: CurrentUserId,
    container: ContainerDep,
    idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None,
) -> Response:
    async def operation() -> tuple[int, dict[str, object]]:
        exercise = await container.exercises.create(
            user_id=user_id,
            exercise_id=payload.id,
            name=payload.name,
            description=payload.description,
            primary_muscle_group=payload.primary_muscle_group,
            equipment=payload.equipment,
            tracking_type=payload.tracking_type,
            notes=payload.notes,
        )
        response = ExerciseResponse.from_domain(exercise)
        return status.HTTP_201_CREATED, response.model_dump(mode="json")

    response_status, body, replayed = await container.idempotency.execute(
        scope=f"create-exercise:{user_id}",
        key=idempotency_key,
        request_hash=_hash_model(payload),
        operation=operation,
    )
    return JSONResponse(
        status_code=response_status,
        content=body,
        headers={"Idempotency-Replayed": str(replayed).lower()},
    )


@router.put("/api/v1/exercises/{exercise_id}", response_model=ExerciseResponse, tags=["exercises"])
async def update_exercise(
    exercise_id: UUID,
    payload: ExerciseWrite,
    user_id: CurrentUserId,
    container: ContainerDep,
) -> ExerciseResponse:
    exercise = await container.exercises.update(
        user_id=user_id,
        exercise_id=exercise_id,
        name=payload.name,
        description=payload.description,
        primary_muscle_group=payload.primary_muscle_group,
        equipment=payload.equipment,
        tracking_type=payload.tracking_type,
        notes=payload.notes,
    )
    return ExerciseResponse.from_domain(exercise)


@router.delete(
    "/api/v1/exercises/{exercise_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    tags=["exercises"],
)
async def delete_exercise(
    exercise_id: UUID,
    user_id: CurrentUserId,
    container: ContainerDep,
) -> Response:
    await container.exercises.delete(user_id=user_id, exercise_id=exercise_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/api/v1/workouts", response_model=WorkoutPage, tags=["workouts"])
async def list_workouts(
    user_id: CurrentUserId,
    container: ContainerDep,
    limit: Annotated[int, Query(ge=1, le=100)] = 50,
    offset: Annotated[int, Query(ge=0)] = 0,
) -> WorkoutPage:
    workouts = list(await container.workouts.list(user_id))
    page_items = workouts[offset : offset + limit]
    return WorkoutPage(
        items=[WorkoutResponse.from_domain(item) for item in page_items],
        page=PageMeta(limit=limit, offset=offset, total=len(workouts)),
    )


@router.post(
    "/api/v1/workouts",
    response_model=WorkoutResponse,
    status_code=status.HTTP_201_CREATED,
    tags=["workouts"],
)
async def create_workout(
    payload: WorkoutWrite,
    user_id: CurrentUserId,
    container: ContainerDep,
    idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None,
) -> Response:
    async def operation() -> tuple[int, dict[str, object]]:
        workout = await container.workouts.create(
            user_id=user_id,
            workout_id=payload.id,
            title=payload.title,
            notes=payload.notes,
            exercise_ids=payload.exercise_ids,
        )
        response = WorkoutResponse.from_domain(workout)
        return status.HTTP_201_CREATED, response.model_dump(mode="json")

    response_status, body, replayed = await container.idempotency.execute(
        scope=f"create-workout:{user_id}",
        key=idempotency_key,
        request_hash=_hash_model(payload),
        operation=operation,
    )
    return JSONResponse(
        status_code=response_status,
        content=body,
        headers={"Idempotency-Replayed": str(replayed).lower()},
    )


@router.put("/api/v1/workouts/{workout_id}", response_model=WorkoutResponse, tags=["workouts"])
async def update_workout(
    workout_id: UUID,
    payload: WorkoutWrite,
    user_id: CurrentUserId,
    container: ContainerDep,
) -> WorkoutResponse:
    workout = await container.workouts.update(
        user_id=user_id,
        workout_id=workout_id,
        title=payload.title,
        notes=payload.notes,
        exercise_ids=payload.exercise_ids,
        status=payload.status,
    )
    return WorkoutResponse.from_domain(workout)


@router.post(
    "/api/v1/workouts/{workout_id}/start",
    response_model=WorkoutResponse,
    tags=["workouts"],
)
async def start_workout(
    workout_id: UUID,
    user_id: CurrentUserId,
    container: ContainerDep,
) -> WorkoutResponse:
    return WorkoutResponse.from_domain(
        await container.workouts.start(user_id=user_id, workout_id=workout_id)
    )


@router.post(
    "/api/v1/workouts/{workout_id}/complete",
    response_model=WorkoutResponse,
    tags=["workouts"],
)
async def complete_workout(
    workout_id: UUID,
    user_id: CurrentUserId,
    container: ContainerDep,
) -> Response:
    workout, emitted = await container.workouts.complete(user_id=user_id, workout_id=workout_id)
    return JSONResponse(
        content=WorkoutResponse.from_domain(workout).model_dump(mode="json"),
        headers={"Domain-Event-Emitted": str(emitted).lower()},
    )


@router.post("/api/v1/sync/push", response_model=SyncPushResponse, tags=["sync"])
async def sync_push(
    payload: SyncPushRequest,
    user_id: CurrentUserId,
    container: ContainerDep,
    idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None,
) -> Response:
    async def operation() -> tuple[int, dict[str, object]]:
        raw_operations = [operation.model_dump(mode="json") for operation in payload.operations]
        raw_results = await container.sync.push(user_id=user_id, operations=raw_operations)
        response = SyncPushResponse(
            results=[SyncResult.model_validate(item) for item in raw_results]
        )
        return status.HTTP_200_OK, response.model_dump(mode="json")

    response_status, body, replayed = await container.idempotency.execute(
        scope=f"sync-push:{user_id}",
        key=idempotency_key,
        request_hash=_hash_model(payload),
        operation=operation,
    )
    return JSONResponse(
        status_code=response_status,
        content=body,
        headers={"Idempotency-Replayed": str(replayed).lower()},
    )


@router.get("/api/v1/sync/exercises", response_model=SyncPullResponse, tags=["sync"])
async def sync_pull_exercises(
    user_id: CurrentUserId,
    container: ContainerDep,
    cursor: Annotated[int, Query(ge=0)] = 0,
    limit: Annotated[int, Query(ge=1, le=100)] = 50,
) -> SyncPullResponse:
    changes, next_cursor, has_more = await container.sync.pull(
        user_id=user_id, cursor=cursor, limit=limit
    )
    serialized_changes = []
    for change in changes:
        cursor_value = change["cursor"]
        exercise_value = change["exercise"]
        if not isinstance(cursor_value, int) or not isinstance(exercise_value, Exercise):
            raise RuntimeError("Exercise sync change violated its internal contract.")
        serialized_changes.append(
            ExerciseChange(
                cursor=cursor_value,
                deleted=bool(change["deleted"]),
                exercise=ExerciseResponse.from_domain(exercise_value),
            )
        )
    return SyncPullResponse(changes=serialized_changes, next_cursor=next_cursor, has_more=has_more)
