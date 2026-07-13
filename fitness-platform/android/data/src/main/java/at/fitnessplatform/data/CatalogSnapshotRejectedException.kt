package at.fitnessplatform.data

import at.fitnessplatform.core.network.CatalogExerciseDto
import at.fitnessplatform.core.network.CatalogFacetDto
import at.fitnessplatform.core.network.CatalogSnapshotDto
import java.security.MessageDigest
import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class CatalogSnapshotRejectedException : IllegalArgumentException("Catalog snapshot is invalid")

internal fun CatalogSnapshotDto.validateCompleteRelease(json: Json) {
    rejectUnless(schemaVersion == "1")
    rejectUnless(complete)
    rejectUnless(total == null || total == exercises.size)
    rejectUnless(contentHash.matches(Regex("^sha256:[0-9a-f]{64}$")))
    rejectUnless(contentHash == canonicalContentHash(json))
    rejectUnless(muscles.map { it.slug }.toSet().size == muscles.size)
    rejectUnless(equipment.map { it.slug }.toSet().size == equipment.size)
    rejectUnless(exercises.map { it.id }.toSet().size == exercises.size)
    rejectUnless(isSafeSingleLine(catalogVersion) && isSafeSingleLine(batchId))
    rejectUnless(muscles.all { isSafeSingleLine(it.slug) && isSafeSingleLine(it.name) })
    rejectUnless(equipment.all { isSafeSingleLine(it.slug) && isSafeSingleLine(it.name) })
    val muscleSlugs = muscles.mapTo(mutableSetOf()) { it.slug }
    val equipmentSlugs = equipment.mapTo(mutableSetOf()) { it.slug }
    exercises.forEach { exercise ->
        rejectUnless(exercise.status == "PUBLISHED" && exercise.reviewed)
        rejectUnless(
            listOf(
                exercise.id,
                exercise.source,
                exercise.externalId,
                exercise.version,
                exercise.name,
                exercise.licenseName,
                exercise.status,
                exercise.trackingType,
            ).all(::isSafeSingleLine),
        )
        rejectUnless(isSafeMultiline(exercise.provenance) && exercise.provenance.isNotBlank())
        rejectUnless(isSafeMultiline(exercise.description))
        rejectUnless(isAbsoluteHttps(exercise.licenseUrl))
        rejectUnless(exercise.muscles.any { it.role == "PRIMARY" })
        rejectUnless(exercise.muscles.all { isSafeSingleLine(it.slug) && isSafeSingleLine(it.role) })
        rejectUnless(exercise.equipment.all(::isSafeSingleLine))
        rejectUnless(exercise.muscles.map { it.slug }.toSet().size == exercise.muscles.size)
        rejectUnless(exercise.equipment.toSet().size == exercise.equipment.size)
        rejectUnless(exercise.muscles.all { it.slug in muscleSlugs })
        rejectUnless(exercise.equipment.all { it in equipmentSlugs })
    }
}

internal fun CatalogSnapshotDto.canonicalContentHash(json: Json): String {
    val payload = CatalogHashPayload(
        schemaVersion,
        catalogVersion,
        publishedAt,
        batchId,
        muscles.sortedBy { it.slug },
        equipment.sortedBy { it.slug },
        exercises
            .map { exercise ->
                exercise.copy(
                    muscles = exercise.muscles.sortedWith(compareBy({ it.slug }, { it.role })),
                    equipment = exercise.equipment.sorted(),
                )
            }
            .sortedWith(compareBy({ it.source }, { it.externalId }, { it.id })),
    )
    val canonical = json.encodeToJsonElement(payload).canonical().toString().toByteArray()
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical)
    return "sha256:" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private val bidiControls = setOf('\u202a', '\u202b', '\u202c', '\u202d', '\u202e', '\u2066', '\u2067', '\u2068', '\u2069')

private fun isSafeSingleLine(value: String): Boolean =
    value.isNotBlank() && value == value.trim() && value.none { it in bidiControls || it.isISOControl() }

private fun isSafeMultiline(value: String): Boolean =
    value == value.trim() && value.none {
        it in bidiControls || it.isISOControl() && it != '\n' && it != '\t'
    }

private fun isAbsoluteHttps(value: String): Boolean =
    runCatching {
        val uri = URI(value)
        uri.scheme == "https" &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            isSafeSingleLine(value) &&
            (uri.port == -1 || uri.port in 1..65535)
    }.getOrDefault(false)

private fun rejectUnless(condition: Boolean) {
    if (!condition) throw CatalogSnapshotRejectedException()
}

private fun JsonElement.canonical(): JsonElement = when (this) {
    is JsonObject -> JsonObject(entries.sortedBy { it.key }.associate { it.key to it.value.canonical() })
    is JsonArray -> JsonArray(map { it.canonical() })
    is JsonPrimitive -> this
}

@Serializable
private data class CatalogHashPayload(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("catalog_version") val catalogVersion: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("batch_id") val batchId: String,
    val muscles: List<CatalogFacetDto>,
    val equipment: List<CatalogFacetDto>,
    val exercises: List<CatalogExerciseDto>,
)
