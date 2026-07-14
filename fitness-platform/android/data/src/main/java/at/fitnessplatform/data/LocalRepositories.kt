package at.fitnessplatform.data

import androidx.room.withTransaction
import android.content.Context
import at.fitnessplatform.core.database.*
import at.fitnessplatform.core.model.*
import at.fitnessplatform.domain.*
import at.fitnessplatform.core.network.CatalogExerciseDto
import at.fitnessplatform.core.network.CatalogSnapshotDto
import at.fitnessplatform.core.network.FitnessApi
import at.fitnessplatform.core.datastore.GuestSessionStore
import at.fitnessplatform.domain.GuestCredentialStatus
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private fun outboxEntity(
    ids: UuidProvider,
    clock: Clock,
    aggregateId: String,
    type: OutboxOperationType,
    payload: String,
) = OutboxEntity(
    id = ids.newUuid(),
    aggregateId = aggregateId,
    operationType = type.name,
    payloadJson = payload,
    createdAtEpochMs = clock.nowEpochMs(),
    status = SyncStatus.PENDING.name,
    retryCount = 0,
    lastError = null,
)

@Singleton
class RoomSyncPreferencesRepository @Inject constructor(
    private val sessionStore: GuestSessionStore,
    private val outboxDao: OutboxDao,
    private val syncEnqueuer: SyncEnqueuer,
) : SyncPreferencesRepository {
    override fun observeEnabled() = sessionStore.syncEnabled
    override fun observePendingCount() = outboxDao.observePendingCount()
    override fun observeCredentialState() = sessionStore.credentialStates.map {
        GuestCredentialStatus.valueOf(it.name)
    }
    override suspend fun setEnabled(enabled: Boolean) {
        sessionStore.setSyncEnabled(enabled)
        if (enabled) syncEnqueuer.enqueue() else syncEnqueuer.cancel()
    }
    override suspend fun resetCredentialsForNewIdentity() {
        sessionStore.resetCredentialsForNewIdentity()
        if (sessionStore.isSyncEnabled()) syncEnqueuer.enqueue()
    }
}

private fun CatalogExerciseDto.toCatalogModel() = CatalogExercise(
    id, externalId, source, provenance, licenseName, licenseUrl, version,
    CatalogStatus.valueOf(status), reviewed, name, description, TrackingType.valueOf(trackingType),
    muscles.map { CatalogMuscle(it.slug, MuscleRole.valueOf(it.role)) }, equipment,
)

@Singleton
class RoomCatalogRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val catalogDao: CatalogDao,
    private val api: FitnessApi,
    private val json: Json,
) : CatalogRepository {
    override fun observeCatalog(filter: CatalogFilter) =
        catalogDao.observe(filter.query, filter.muscle, filter.equipment).map { rows -> rows.map { it.toModel() } }
    override fun observeExercise(id: String) = catalogDao.observeOne(id).map { it?.toModel() }
    override fun observeMuscles() = catalogDao.observeMuscles().map { rows -> rows.map { Muscle(it.slug, it.name) } }
    override fun observeEquipment() = catalogDao.observeEquipment().map { rows -> rows.map { Equipment(it.slug, it.name) } }

    override suspend fun seedIfEmpty() {
        if (catalogDao.count() == 0) {
            val snapshot = context.assets.open("catalog-demo.json").bufferedReader().use {
                json.decodeFromString<CatalogSnapshotDto>(it.readText())
            }
            replace(snapshot, "bundled-seed")
        }
    }

    override suspend fun refresh() {
        replace(api.catalogSnapshot(), "network")
    }

    private suspend fun replace(snapshot: CatalogSnapshotDto, source: String) {
        snapshot.validateCompleteRelease(json)
        if (catalogDao.metadata()?.contentHash == snapshot.contentHash) return
        val exercises = snapshot.exercises.map { it.toCatalogModel() }
        database.withTransaction {
            catalogDao.deleteExercises()
            catalogDao.deleteMuscles()
            catalogDao.deleteEquipment()
            catalogDao.insertMuscles(snapshot.muscles.map { CatalogMuscleEntity(it.slug, it.name) })
            catalogDao.insertEquipment(snapshot.equipment.map { CatalogEquipmentEntity(it.slug, it.name) })
            catalogDao.insertExercises(exercises.map { it.toEntity() })
            catalogDao.insertExerciseMuscles(exercises.flatMap { it.toMuscleEntities() })
            catalogDao.insertExerciseEquipment(exercises.flatMap { it.toEquipmentEntities() })
            catalogDao.putMetadata(CatalogMetadataEntity(
                schemaVersion = snapshot.schemaVersion,
                catalogVersion = snapshot.catalogVersion,
                contentHash = snapshot.contentHash,
                retrievedAtEpochMs = System.currentTimeMillis(),
                source = source,
            ))
        }
    }
}

@Singleton
class RoomTrainingLocationRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: TrainingLocationDao,
    private val ids: UuidProvider,
    private val clock: Clock,
) : TrainingLocationRepository {
    override fun observeLocations(): Flow<List<TrainingLocation>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeActiveLocation(): Flow<TrainingLocation?> =
        dao.observeActive().map { it?.toModel() }

    override suspend fun getLocation(id: String): TrainingLocation? = dao.get(id)?.toModel()

    override suspend fun create(
        name: String,
        type: LocationType,
        equipmentSlugs: Set<String>,
    ): TrainingLocation {
        validateEquipment(equipmentSlugs)
        val now = clock.nowEpochMs()
        val location = database.withTransaction {
            val activate = dao.active() == null
            val created = TrainingLocation(
                id = ids.newUuid(),
                name = name,
                type = type,
                equipmentSlugs = equipmentSlugs,
                isActive = activate,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )
            dao.insert(created.toEntity())
            dao.replaceEquipment(created.id, equipmentSlugs)
            created
        }
        return location
    }

    override suspend fun update(location: TrainingLocation): TrainingLocation {
        validateEquipment(location.equipmentSlugs)
        return database.withTransaction {
            val current = requireNotNull(dao.get(location.id)?.toModel()) { "Training location does not exist." }
            check(current.deletedAtEpochMs == null) { "Deleted training locations cannot be updated." }
            val updated = location.copy(
                isActive = current.isActive,
                createdAtEpochMs = current.createdAtEpochMs,
                updatedAtEpochMs = clock.nowEpochMs(),
                revision = current.revision + 1,
                deletedAtEpochMs = null,
            )
            check(dao.update(updated.toEntity()) == 1)
            dao.replaceEquipment(updated.id, updated.equipmentSlugs)
            updated
        }
    }

    override suspend fun setActive(id: String) = dao.setActive(id, clock.nowEpochMs())

    override suspend fun replaceEquipment(id: String, equipmentSlugs: Set<String>) {
        validateEquipment(equipmentSlugs)
        database.withTransaction {
            val current = requireNotNull(dao.get(id)?.toModel()) { "Training location does not exist." }
            check(current.deletedAtEpochMs == null) { "Deleted training locations cannot be updated." }
            dao.replaceEquipment(id, equipmentSlugs)
            dao.update(
                current.copy(
                    equipmentSlugs = equipmentSlugs,
                    updatedAtEpochMs = clock.nowEpochMs(),
                    revision = current.revision + 1,
                ).toEntity(),
            )
        }
    }

    override suspend fun delete(id: String) = dao.softDelete(id, clock.nowEpochMs())

    private fun validateEquipment(slugs: Set<String>) {
        val unknown = slugs - EquipmentDefinitions.slugs
        require(unknown.isEmpty()) { "Unknown equipment: ${unknown.sorted().joinToString()}" }
        require(EquipmentDefinitions.NONE !in slugs) { "The none marker is implicit and must not be persisted." }
    }
}

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoomTrainingPlanRepository @Inject constructor(
    private val database: AppDatabase,
    private val profileDao: GuestProfileDao,
    private val exerciseDao: ExerciseDao,
    private val catalogDao: CatalogDao,
    private val dao: TrainingPlanDao,
    private val ids: UuidProvider,
    private val clock: Clock,
) : TrainingPlanRepository {
    override fun observePlans(): Flow<List<TrainingPlan>> = profileDao.observe().flatMapLatest { profile ->
        if (profile == null) flowOf(emptyList()) else dao.observeAll(profile.id).map { rows -> rows.map { it.toModel() } }
    }

    override fun observeActivePlan(): Flow<TrainingPlan?> = profileDao.observe().flatMapLatest { profile ->
        if (profile == null) flowOf(null) else dao.observeActive(profile.id).map { it?.toModel() }
    }

    override fun observePlan(id: String): Flow<TrainingPlan?> = profileDao.observe().flatMapLatest { profile ->
        if (profile == null) flowOf(null) else dao.observe(id, profile.id).map { it?.toModel() }
    }

    override suspend fun getPlan(id: String): TrainingPlan? {
        val owner = profileDao.get() ?: return null
        return dao.get(id, owner.id)?.toModel()?.takeIf { it.deletedAtEpochMs == null }
    }

    override suspend fun create(plan: TrainingPlan): TrainingPlan {
        val owner = requireNotNull(profileDao.get()) { "Create a guest profile before adding a plan." }
        require(plan.ownerProfileId == owner.id) { "Training plans must belong to the current profile." }
        require(dao.get(plan.id, owner.id) == null) { "Training plan already exists." }
        val now = clock.nowEpochMs()
        val created = resolveReferences(
            plan.copy(
                isActive = false,
                isArchived = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                revision = 0,
                deletedAtEpochMs = null,
            ),
        )
        validateTrainingPlan(created)
        database.withTransaction { insertAggregate(created) }
        return created
    }

    override suspend fun update(plan: TrainingPlan): TrainingPlan {
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        require(plan.ownerProfileId == owner.id) { "Training plans must belong to the current profile." }
        return database.withTransaction {
            val current = requireNotNull(dao.get(plan.id, owner.id)?.toModel()) { "Training plan does not exist." }
            check(current.deletedAtEpochMs == null) { "Deleted training plans cannot be updated." }
            val updated = resolveReferences(
                plan.copy(
                    ownerProfileId = owner.id,
                    isActive = current.isActive,
                    isArchived = current.isArchived,
                    sourceTemplateId = current.sourceTemplateId,
                    createdAtEpochMs = current.createdAtEpochMs,
                    updatedAtEpochMs = clock.nowEpochMs(),
                    revision = current.revision + 1,
                    deletedAtEpochMs = null,
                ),
            )
            validateTrainingPlan(updated)
            check(dao.updatePlan(updated.toEntity()) == 1)
            updateContentsDifferential(current, updated)
            updated
        }
    }

    override suspend fun copy(id: String, transform: (TrainingPlan) -> TrainingPlan): TrainingPlan {
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        val source = requireNotNull(dao.get(id, owner.id)?.toModel()) { "Training plan does not exist." }
        check(source.deletedAtEpochMs == null) { "Deleted training plans cannot be copied." }
        val now = clock.nowEpochMs()
        val copied = transform(source.deepCopy(
            id = ids.newUuid(),
            name = "${source.name} (Copy)",
            sourceTemplateId = source.sourceTemplateId ?: source.id,
            now = now,
            ids = ids,
        )).copy(
            ownerProfileId = owner.id,
            isActive = false,
            isArchived = false,
            sourceTemplateId = source.sourceTemplateId ?: source.id,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            revision = 0,
            deletedAtEpochMs = null,
        )
        validateTrainingPlan(copied)
        database.withTransaction { insertAggregate(copied) }
        return copied
    }

    override suspend fun setActive(id: String) {
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        dao.setActive(id, owner.id, clock.nowEpochMs())
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        check(dao.archive(id, owner.id, archived, clock.nowEpochMs()) == 1) { "Training plan does not exist." }
    }

    override suspend fun delete(id: String) {
        val owner = requireNotNull(profileDao.get()) { "Guest profile does not exist." }
        dao.softDelete(id, owner.id, clock.nowEpochMs())
    }

    override suspend fun seedStarterPlans() {
        val owner = profileDao.get() ?: return
        database.withTransaction {
            if (dao.count(owner.id) != 0) return@withTransaction
            val now = clock.nowEpochMs()
            listOf(
                starterPlan(
                    owner.id,
                    "Simple strength starter",
                    TrainingPlanGoal.STRENGTH,
                    listOf(
                        starterReference("bodyweight-squat", "Bodyweight Squat", TrackingType.REPS, "none", "quadriceps"),
                        starterReference("incline-push-up", "Incline Push-up", TrackingType.REPS, "none", "chest"),
                    ),
                    now,
                ),
                starterPlan(
                    owner.id,
                    "Simple core starter",
                    TrainingPlanGoal.GENERAL_FITNESS,
                    listOf(starterReference("front-plank", "Front Plank", TrackingType.DURATION, "mat", "core")),
                    now,
                ),
            ).forEach { plan ->
                val resolved = resolveReferences(plan)
                validateTrainingPlan(resolved)
                insertAggregate(resolved)
            }
        }
    }

    private suspend fun resolveReferences(plan: TrainingPlan): TrainingPlan = plan.copy(
        weeks = plan.weeks.map { week ->
            week.copy(days = week.days.map { day ->
                day.copy(blocks = day.blocks.map { block ->
                    block.copy(exercises = block.exercises.map { exercise ->
                        exercise.copy(reference = resolveReference(exercise.reference))
                    })
                })
            })
        },
    )

    private suspend fun resolveReference(reference: ExerciseReference): ExerciseReference = when (reference.kind) {
        ExerciseReferenceKind.CUSTOM -> {
            val custom = reference.customExerciseId?.let { exerciseDao.get(it) }
            reference.copy(
                resolutionStatus = if (custom == null || custom.deletedAtEpochMs != null) {
                    ExerciseResolutionStatus.DELETED_CUSTOM
                } else {
                    ExerciseResolutionStatus.RESOLVED
                },
            )
        }
        ExerciseReferenceKind.CATALOG -> {
            val source = reference.catalogSource
            val externalId = reference.catalogExternalId
            val catalog = if (source != null && externalId != null) {
                catalogDao.findByIdentity(source, externalId)
            } else {
                null
            }
            reference.copy(
                catalogExerciseId = catalog?.exercise?.id ?: reference.catalogExerciseId,
                resolutionStatus = when (catalog?.exercise?.status) {
                    CatalogStatus.PUBLISHED.name -> ExerciseResolutionStatus.RESOLVED
                    CatalogStatus.DEPRECATED.name -> ExerciseResolutionStatus.DEPRECATED
                    else -> ExerciseResolutionStatus.UNAVAILABLE
                },
            )
        }
    }

    private suspend fun insertAggregate(plan: TrainingPlan) {
        dao.insertPlan(plan.toEntity())
        insertContents(plan)
    }

    private suspend fun insertContents(plan: TrainingPlan) {
        val rows = plan.toRows()
        if (rows.weeks.isNotEmpty()) dao.insertWeeks(rows.weeks)
        if (rows.days.isNotEmpty()) dao.insertDays(rows.days)
        if (rows.blocks.isNotEmpty()) dao.insertBlocks(rows.blocks)
        if (rows.exercises.isNotEmpty()) dao.insertExercises(rows.exercises)
        if (rows.sets.isNotEmpty()) dao.insertSets(rows.sets)
    }

    private suspend fun updateContentsDifferential(current: TrainingPlan, updated: TrainingPlan) {
        val currentRows = current.toRows()
        val updatedRows = updated.toRows()
        val removedDayIds = currentRows.days.mapTo(mutableSetOf()) { it.id } -
            updatedRows.days.mapTo(mutableSetOf()) { it.id }
        val removedBlockIds = currentRows.blocks.mapTo(mutableSetOf()) { it.id } -
            updatedRows.blocks.mapTo(mutableSetOf()) { it.id }
        val removedExerciseIds = currentRows.exercises.mapTo(mutableSetOf()) { it.id } -
            updatedRows.exercises.mapTo(mutableSetOf()) { it.id }
        val blockDayIds = currentRows.blocks.associate { it.id to it.dayId }
        val affectedDayIds = buildSet {
            addAll(removedDayIds)
            addAll(currentRows.blocks.filter { it.id in removedBlockIds }.map { it.dayId })
            addAll(
                currentRows.exercises
                    .filter { it.id in removedExerciseIds }
                    .mapNotNull { blockDayIds[it.blockId] },
            )
        }
        if (affectedDayIds.isNotEmpty()) {
            val referenced = database.calendarDao().countRulesForPlanDays(affectedDayIds.toList()) > 0 ||
                database.calendarDao().countOccurrencesForPlanDays(affectedDayIds.toList()) > 0
            if (referenced) throw PlanRemovalDecisionRequiredException(affectedDayIds)
        }

        dao.reserveSetPositions(updated.id)
        dao.reserveExercisePositions(updated.id)
        dao.reserveBlockPositions(updated.id)
        dao.reserveDayPositions(updated.id)
        dao.reserveWeekPositions(updated.id)

        if (updatedRows.weeks.isNotEmpty()) dao.upsertWeeks(updatedRows.weeks)
        if (updatedRows.days.isNotEmpty()) dao.upsertDays(updatedRows.days)
        if (updatedRows.blocks.isNotEmpty()) dao.upsertBlocks(updatedRows.blocks)
        if (updatedRows.exercises.isNotEmpty()) dao.upsertExercises(updatedRows.exercises)
        if (updatedRows.sets.isNotEmpty()) dao.upsertSets(updatedRows.sets)

        deleteRemoved(currentRows.sets.map { it.id }, updatedRows.sets.map { it.id }, dao::deleteSets)
        deleteRemoved(currentRows.exercises.map { it.id }, updatedRows.exercises.map { it.id }, dao::deleteExercises)
        deleteRemoved(currentRows.blocks.map { it.id }, updatedRows.blocks.map { it.id }, dao::deleteBlocks)
        deleteRemoved(currentRows.days.map { it.id }, updatedRows.days.map { it.id }, dao::deleteDays)
        deleteRemoved(currentRows.weeks.map { it.id }, updatedRows.weeks.map { it.id }, dao::deleteWeeks)
    }

    private suspend fun deleteRemoved(
        before: List<String>,
        after: List<String>,
        delete: suspend (List<String>) -> Unit,
    ) {
        val removed = before.toSet() - after.toSet()
        if (removed.isNotEmpty()) delete(removed.toList())
    }

    private fun starterPlan(
        ownerProfileId: String,
        name: String,
        goal: TrainingPlanGoal,
        references: List<ExerciseReference>,
        now: Long,
    ) = TrainingPlan(
        id = ids.newUuid(),
        ownerProfileId = ownerProfileId,
        name = name,
        description = "Editable offline starter built from Momentum's self-authored CC0 demo catalog.",
        goal = goal,
        createdAtEpochMs = now,
        updatedAtEpochMs = now,
        weeks = listOf(
            PlanWeek(
                ids.newUuid(),
                0,
                "Week 1",
                listOf(
                    PlanDay(
                        ids.newUuid(),
                        0,
                        "Day 1",
                        listOf(
                            PlanBlock(
                                ids.newUuid(),
                                0,
                                PlanBlockType.MAIN,
                                "Main",
                                references.mapIndexed { position, reference ->
                                    PlanExercise(
                                        ids.newUuid(),
                                        position,
                                        reference,
                                        sets = listOf(
                                            SetPrescription(
                                                ids.newUuid(),
                                                0,
                                                repsMin = 8.takeIf {
                                                    reference.snapshot.trackingType == TrackingType.REPS
                                                },
                                                durationSeconds = 30.takeIf {
                                                    reference.snapshot.trackingType == TrackingType.DURATION
                                                },
                                                restSeconds = 90,
                                            ),
                                        ),
                                    )
                                },
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun starterReference(
        externalId: String,
        name: String,
        trackingType: TrackingType,
        equipment: String,
        primaryMuscle: String,
    ) = ExerciseReference(
        kind = ExerciseReferenceKind.CATALOG,
        catalogSource = "momentum-self-authored-demo",
        catalogExternalId = externalId,
        snapshot = ExerciseSnapshot(name, trackingType, setOf(equipment), primaryMuscle),
    )
}

private fun TrainingPlan.deepCopy(
    id: String,
    name: String,
    sourceTemplateId: String,
    now: Long,
    ids: UuidProvider,
) = copy(
    id = id,
    name = name,
    isActive = false,
    isArchived = false,
    sourceTemplateId = sourceTemplateId,
    createdAtEpochMs = now,
    updatedAtEpochMs = now,
    revision = 0,
    deletedAtEpochMs = null,
    weeks = weeks.map { week ->
        week.copy(
            id = ids.newUuid(),
            days = week.days.map { day ->
                day.copy(
                    id = ids.newUuid(),
                    blocks = day.blocks.map { block ->
                        block.copy(
                            id = ids.newUuid(),
                            exercises = block.exercises.map { exercise ->
                                exercise.copy(
                                    id = ids.newUuid(),
                                    sets = exercise.sets.map { set -> set.copy(id = ids.newUuid()) },
                                )
                            },
                        )
                    },
                )
            },
        )
    },
)

@Singleton
class RoomGuestProfileRepository @Inject constructor(
    private val database: AppDatabase,
    private val profileDao: GuestProfileDao,
    private val outboxDao: OutboxDao,
    private val ids: UuidProvider,
    private val clock: Clock,
    private val events: DomainEventDispatcher,
    private val syncEnqueuer: SyncEnqueuer,
) : GuestProfileRepository {
    override fun observeProfile(): Flow<GuestProfile?> = profileDao.observe().map { it?.toModel() }
    override suspend fun getProfile(): GuestProfile? = profileDao.get()?.toModel()

    override suspend fun create(displayName: String): GuestProfile {
        profileDao.get()?.let { return it.toModel() }
        val now = clock.nowEpochMs()
        val profile = GuestProfile(ids.newUuid(), displayName, now, syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            profileDao.insert(profile.toEntity())
            outboxDao.insert(profileOutbox(profile))
        }
        events.publish(GuestProfileCreated(ids.newUuid(), now, profile.id))
        syncEnqueuer.enqueue()
        return profile
    }

    override suspend fun update(profile: GuestProfile): GuestProfile {
        val updated = profile.copy(syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            profileDao.update(updated.toEntity())
            outboxDao.insert(profileOutbox(updated))
        }
        syncEnqueuer.enqueue()
        return updated
    }

    private fun profileOutbox(profile: GuestProfile): OutboxEntity {
        val payload = buildJsonObject {
            put("display_name", profile.displayName)
            put("unit_system", profile.unitSystem.name)
            put("onboarding_status", profile.onboardingStatus.name)
        }.toString()
        return outboxEntity(ids, clock, profile.id, OutboxOperationType.UPSERT_PROFILE, payload)
    }
}

@Singleton
class RoomExerciseRepository @Inject constructor(
    private val database: AppDatabase,
    private val profileDao: GuestProfileDao,
    private val exerciseDao: ExerciseDao,
    private val conflictDao: ExerciseConflictDao,
    private val outboxDao: OutboxDao,
    private val ids: UuidProvider,
    private val clock: Clock,
    private val events: DomainEventDispatcher,
    private val syncEnqueuer: SyncEnqueuer,
) : ExerciseRepository {
    override fun observeExercises(): Flow<List<CustomExercise>> = exerciseDao.observeActive().map { rows -> rows.map { it.toModel() } }
    override fun observeExerciseConflicts() = conflictDao.observeOpen().map { rows -> rows.map { it.toModel() } }
    override suspend fun getExercise(id: String): CustomExercise? = exerciseDao.get(id)?.toModel()

    override suspend fun create(
        name: String,
        description: String,
        primaryMuscleGroup: String,
        requiredEquipment: String,
        trackingType: TrackingType,
        notes: String,
    ): CustomExercise {
        val profile = profileDao.get() ?: error("Create a guest profile before adding exercises.")
        val now = clock.nowEpochMs()
        val exercise = CustomExercise(
            id = ids.newUuid(), ownerProfileId = profile.id, name = name, description = description,
            primaryMuscleGroup = primaryMuscleGroup, requiredEquipment = requiredEquipment,
            trackingType = trackingType, notes = notes, createdAtEpochMs = now, updatedAtEpochMs = now,
        )
        database.withTransaction {
            exerciseDao.insert(exercise.toEntity())
            outboxDao.insert(exerciseOutbox(exercise, OutboxOperationType.UPSERT_EXERCISE))
        }
        events.publish(ExerciseCreated(ids.newUuid(), now, exercise.id))
        syncEnqueuer.enqueue()
        return exercise
    }

    override suspend fun update(exercise: CustomExercise): CustomExercise {
        require(exercise.syncStatus != SyncStatus.CONFLICT) { "Resolve the sync conflict before editing this exercise." }
        val updated = exercise.copy(updatedAtEpochMs = clock.nowEpochMs(), syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            exerciseDao.update(updated.toEntity())
            outboxDao.insert(exerciseOutbox(updated, OutboxOperationType.UPSERT_EXERCISE))
        }
        syncEnqueuer.enqueue()
        return updated
    }

    override suspend fun delete(id: String) {
        val current = exerciseDao.get(id)?.toModel() ?: return
        require(current.syncStatus != SyncStatus.CONFLICT) { "Resolve the sync conflict before deleting this exercise." }
        if (current.deletedAtEpochMs != null) return
        val deleted = current.copy(deletedAtEpochMs = clock.nowEpochMs(), syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            exerciseDao.update(deleted.toEntity())
            outboxDao.insert(exerciseOutbox(deleted, OutboxOperationType.DELETE_EXERCISE))
        }
        syncEnqueuer.enqueue()
    }

    override suspend fun resolveConflict(
        exerciseId: String,
        resolution: ExerciseConflictResolution,
        mergedExercise: CustomExercise?,
    ) {
        val conflict = conflictDao.getOpenForExercise(exerciseId)?.toModel()
            ?: error("No open conflict exists for this exercise.")
        val current = exerciseDao.get(exerciseId) ?: error("Conflicted exercise is missing locally.")
        val remote = conflict.remoteSnapshot
        database.withTransaction {
            when (resolution) {
                ExerciseConflictResolution.TAKE_SERVER -> {
                    exerciseDao.replace(remote.copy(ownerProfileId = current.ownerProfileId, syncStatus = SyncStatus.SYNCED).toEntity())
                    outboxDao.deleteUnacknowledgedForAggregate(exerciseId)
                    conflictDao.updateStatus(exerciseId, ConflictResolutionStatus.RESOLVED.name, clock.nowEpochMs())
                }
                ExerciseConflictResolution.KEEP_LOCAL,
                ExerciseConflictResolution.MERGE -> {
                    val selected = when (resolution) {
                        ExerciseConflictResolution.KEEP_LOCAL -> conflict.localSnapshot
                        ExerciseConflictResolution.MERGE -> requireNotNull(mergedExercise) { "A merged exercise is required." }
                        ExerciseConflictResolution.TAKE_SERVER -> error("Handled above")
                    }
                    val resolved = selected.copy(
                        id = exerciseId,
                        ownerProfileId = current.ownerProfileId,
                        updatedAtEpochMs = clock.nowEpochMs(),
                        syncStatus = SyncStatus.PENDING,
                        serverId = remote.serverId ?: exerciseId,
                        conflictVersion = remote.conflictVersion,
                    )
                    outboxDao.deleteUnacknowledgedForAggregate(exerciseId)
                    exerciseDao.replace(resolved.toEntity())
                    outboxDao.insert(exerciseOutbox(resolved, if (resolved.deletedAtEpochMs == null) OutboxOperationType.UPSERT_EXERCISE else OutboxOperationType.DELETE_EXERCISE))
                    conflictDao.updateStatus(exerciseId, ConflictResolutionStatus.PENDING_CONFIRMATION.name, null)
                }
            }
        }
        if (resolution != ExerciseConflictResolution.TAKE_SERVER) syncEnqueuer.enqueue()
    }

    private fun exerciseOutbox(exercise: CustomExercise, type: OutboxOperationType): OutboxEntity {
        val payload = buildJsonObject {
            put("id", exercise.id)
            put("name", exercise.name)
            put("description", exercise.description)
            put("primary_muscle_group", exercise.primaryMuscleGroup)
            put("equipment", exercise.requiredEquipment)
            put("tracking_type", exercise.trackingType.name)
            put("notes", exercise.notes)
            put("base_revision", exercise.conflictVersion)
        }.toString()
        return outboxEntity(ids, clock, exercise.id, type, payload)
    }
}

@Singleton
class RoomWorkoutRepository @Inject constructor(
    private val database: AppDatabase,
    private val profileDao: GuestProfileDao,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val outboxDao: OutboxDao,
    private val ids: UuidProvider,
    private val clock: Clock,
    private val events: DomainEventDispatcher,
    private val syncEnqueuer: SyncEnqueuer,
) : WorkoutRepository {
    override fun observeWorkouts(): Flow<List<Workout>> = workoutDao.observeAll().map { rows -> rows.map { it.toModel() } }
    override suspend fun getWorkout(id: String): Workout? = workoutDao.get(id)?.toModel()

    override suspend fun create(title: String, exerciseIds: List<String>, notes: String): Workout {
        val profile = profileDao.get() ?: error("Create a guest profile before adding workouts.")
        exerciseIds.forEach { requireNotNull(exerciseDao.get(it)) { "Exercise $it does not exist." } }
        val now = clock.nowEpochMs()
        val workoutId = ids.newUuid()
        val workout = Workout(
            id = workoutId, ownerProfileId = profile.id, title = title, notes = notes,
            exercises = exerciseIds.mapIndexed { index, exerciseId ->
                WorkoutExercise(ids.newUuid(), workoutId, exerciseId, index)
            },
            createdAtEpochMs = now, updatedAtEpochMs = now,
        )
        database.withTransaction {
            workoutDao.insertWorkout(workout.toEntity())
            workoutDao.insertExercises(workout.exercises.map { it.toEntity() })
            outboxDao.insert(workoutOutbox(workout, OutboxOperationType.UPSERT_WORKOUT))
        }
        events.publish(WorkoutCreated(ids.newUuid(), now, workout.id))
        syncEnqueuer.enqueue()
        return workout
    }

    override suspend fun start(id: String): Workout {
        val current = requireNotNull(workoutDao.get(id)?.toModel()) { "Workout not found." }
        if (current.status == WorkoutStatus.IN_PROGRESS || current.status == WorkoutStatus.COMPLETED) return current
        val now = clock.nowEpochMs()
        val updated = current.copy(status = WorkoutStatus.IN_PROGRESS, startTimeEpochMs = now, updatedAtEpochMs = now, syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            workoutDao.updateWorkout(updated.toEntity())
            outboxDao.insert(workoutOutbox(updated, OutboxOperationType.START_WORKOUT))
        }
        events.publish(WorkoutStarted(ids.newUuid(), now, id))
        syncEnqueuer.enqueue()
        return updated
    }

    override suspend fun complete(id: String): Workout {
        val current = requireNotNull(workoutDao.get(id)?.toModel()) { "Workout not found." }
        if (current.status == WorkoutStatus.COMPLETED) return current
        require(current.status == WorkoutStatus.IN_PROGRESS) { "Only a started workout can be completed." }
        val now = clock.nowEpochMs()
        val updated = current.copy(status = WorkoutStatus.COMPLETED, endTimeEpochMs = now, updatedAtEpochMs = now, syncStatus = SyncStatus.PENDING)
        database.withTransaction {
            workoutDao.updateWorkout(updated.toEntity())
            outboxDao.insert(workoutOutbox(updated, OutboxOperationType.COMPLETE_WORKOUT))
        }
        val deterministicEventId = UUID.nameUUIDFromBytes("WorkoutCompleted:$id".toByteArray(StandardCharsets.UTF_8)).toString()
        events.publish(WorkoutCompleted(deterministicEventId, now, id))
        syncEnqueuer.enqueue()
        return updated
    }

    private fun workoutOutbox(workout: Workout, type: OutboxOperationType): OutboxEntity {
        val payload = buildJsonObject {
            put("id", workout.id)
            if (type == OutboxOperationType.UPSERT_WORKOUT) {
                put("title", workout.title)
                put("notes", workout.notes)
                putJsonArray("exercise_ids") { workout.exercises.sortedBy { it.position }.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.exerciseId)) } }
            }
        }.toString()
        return outboxEntity(ids, clock, workout.id, type, payload)
    }
}
