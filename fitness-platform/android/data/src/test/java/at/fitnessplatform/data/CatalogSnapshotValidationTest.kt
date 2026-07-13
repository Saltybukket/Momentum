package at.fitnessplatform.data

import at.fitnessplatform.core.network.CatalogExerciseDto
import at.fitnessplatform.core.network.CatalogFacetDto
import at.fitnessplatform.core.network.CatalogMuscleDto
import at.fitnessplatform.core.network.CatalogSnapshotDto
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogSnapshotValidationTest {
    private val json = Json { encodeDefaults = true }

    @Test fun `complete release with matching canonical hash is accepted`() {
        val snapshot = validSnapshot()

        snapshot.validateCompleteRelease(json)

        assertEquals(snapshot.contentHash, snapshot.canonicalContentHash(json))
    }

    @Test fun `bundled release hash matches backend canonical contract`() {
        val snapshot = json.decodeFromString<CatalogSnapshotDto>(
            File("../../data/exercises/catalog-demo.json").readText(),
        )

        snapshot.validateCompleteRelease(json)

        assertEquals(
            "sha256:b3a3fec064548ccdcf1c9166d5641f1b8c9da46a19337feeb88ae923906f1fa7",
            snapshot.canonicalContentHash(json),
        )
    }

    @Test fun `exact backend API snapshot is accepted`() {
        val snapshot = json.decodeFromString<CatalogSnapshotDto>(
            File("../../data/exercises/catalog-demo-api-snapshot.json").readText(),
        )

        snapshot.validateCompleteRelease(json)

        assertEquals(snapshot.contentHash, snapshot.canonicalContentHash(json))
    }

    @Test fun `tampered release is rejected before persistence`() {
        val snapshot = validSnapshot()
        val tampered = snapshot.copy(
            exercises = snapshot.exercises.map { it.copy(name = "Tampered") },
        )

        assertThrows(CatalogSnapshotRejectedException::class.java) {
            tampered.validateCompleteRelease(json)
        }
    }

    @Test fun `semantic hash ignores input array order`() {
        val snapshot = validSnapshot().copy(
            muscles = listOf(CatalogFacetDto("legs", "Legs"), CatalogFacetDto("core", "Core")),
            equipment = listOf(CatalogFacetDto("rack", "Rack"), CatalogFacetDto("mat", "Mat")),
        )
        val exercise = snapshot.exercises.single().copy(
            muscles = listOf(
                CatalogMuscleDto("legs", "SECONDARY"),
                CatalogMuscleDto("core", "PRIMARY"),
            ),
            equipment = listOf("rack", "mat"),
        )
        val first = snapshot.copy(exercises = listOf(exercise))
        val reordered = first.copy(
            muscles = first.muscles.reversed(),
            equipment = first.equipment.reversed(),
            exercises = first.exercises.map {
                it.copy(muscles = it.muscles.reversed(), equipment = it.equipment.reversed())
            },
        )

        assertEquals(first.canonicalContentHash(json), reordered.canonicalContentHash(json))
    }

    @Test fun `unsafe text and non absolute https licenses are rejected`() {
        val snapshot = validSnapshot()
        listOf("https:foo", "https:/foo", "https://", "https://user:pass@example.com/").forEach { url ->
            val invalid = snapshot.copy(
                exercises = snapshot.exercises.map { it.copy(licenseUrl = url) },
            ).withCanonicalHash()
            assertThrows(CatalogSnapshotRejectedException::class.java) {
                invalid.validateCompleteRelease(json)
            }
        }
        val unsafe = snapshot.copy(
            exercises = snapshot.exercises.map { it.copy(name = "unsafe\u202ename") },
        ).withCanonicalHash()
        assertThrows(CatalogSnapshotRejectedException::class.java) {
            unsafe.validateCompleteRelease(json)
        }
    }

    @Test fun `partial release is rejected even with a matching hash`() {
        val snapshot = validSnapshot().copy(complete = false)
        val rehashed = snapshot.copy(contentHash = snapshot.canonicalContentHash(json))

        assertThrows(CatalogSnapshotRejectedException::class.java) {
            rehashed.validateCompleteRelease(json)
        }
    }

    @Test fun `unknown and duplicate relationships are rejected`() {
        val snapshot = validSnapshot()
        val invalidExercise = snapshot.exercises.single().copy(
            muscles = listOf(
                CatalogMuscleDto("unknown", "PRIMARY"),
                CatalogMuscleDto("unknown", "SECONDARY"),
            ),
        )
        val invalid = snapshot.copy(exercises = listOf(invalidExercise))
        val rehashed = invalid.copy(contentHash = invalid.canonicalContentHash(json))

        assertThrows(CatalogSnapshotRejectedException::class.java) {
            rehashed.validateCompleteRelease(json)
        }
    }

    private fun validSnapshot(): CatalogSnapshotDto {
        val snapshot = CatalogSnapshotDto(
            schemaVersion = "1",
            catalogVersion = "2026.07.13",
            contentHash = "sha256:" + "0".repeat(64),
            publishedAt = "2026-07-13T08:00:00Z",
            batchId = "demo-2026-07-13",
            total = 1,
            complete = true,
            sources = listOf("momentum-demo"),
            licenses = listOf("CC0-1.0"),
            muscles = listOf(CatalogFacetDto("core", "Core")),
            equipment = listOf(CatalogFacetDto("mat", "Mat")),
            exercises = listOf(
                CatalogExerciseDto(
                    id = "00000000-0000-0000-0000-000000000001",
                    externalId = "demo-plank",
                    source = "momentum-demo",
                    provenance = "Authored for Momentum",
                    licenseName = "CC0-1.0",
                    licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
                    version = "1",
                    status = "PUBLISHED",
                    reviewed = true,
                    name = "Plank",
                    description = "Hold a stable plank.",
                    trackingType = "DURATION",
                    muscles = listOf(CatalogMuscleDto("core", "PRIMARY")),
                    equipment = listOf("mat"),
                ),
            ),
        )
        return snapshot.copy(contentHash = snapshot.canonicalContentHash(json))
    }

    private fun CatalogSnapshotDto.withCanonicalHash() =
        copy(contentHash = canonicalContentHash(json))
}
