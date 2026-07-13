package at.fitnessplatform.core.model

enum class LocationType { FITNESS_CENTER, HOME, OUTDOOR, HOTEL, UNIVERSITY, CUSTOM }

data class TrainingLocation(
    val id: String,
    val name: String,
    val type: LocationType,
    val equipmentSlugs: Set<String>,
    val isActive: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long = 0,
    val deletedAtEpochMs: Long? = null,
) {
    val availableEquipment: Set<String>
        get() = equipmentSlugs + EquipmentDefinitions.NONE
}

data class EquipmentDefinition(
    val slug: String,
    val displayNameKey: String,
    val category: String,
    val aliases: Set<String> = emptySet(),
    val isPhysical: Boolean = true,
)

object EquipmentDefinitions {
    const val NONE = "none"
    const val OPEN_FLOOR = "open-floor"

    val all: List<EquipmentDefinition> = listOf(
        definition(NONE, "equipment_none", "general", isPhysical = false),
        definition("mat", "equipment_mat", "accessories"),
        definition("resistance-bands", "equipment_resistance_bands", "accessories", setOf("bands")),
        definition("dumbbells", "equipment_dumbbells", "free_weights"),
        definition("adjustable-dumbbells", "equipment_adjustable_dumbbells", "free_weights"),
        definition("barbell", "equipment_barbell", "free_weights"),
        definition("plates", "equipment_plates", "free_weights"),
        definition("bench", "equipment_bench", "stations"),
        definition("squat-rack", "equipment_squat_rack", "racks"),
        definition("power-rack", "equipment_power_rack", "racks"),
        definition("smith-machine", "equipment_smith_machine", "machines"),
        definition("cable-machine", "equipment_cable_machine", "machines"),
        definition("pull-up-bar", "equipment_pull_up_bar", "stations"),
        definition("dip-bars", "equipment_dip_bars", "stations"),
        definition("kettlebell", "equipment_kettlebell", "free_weights"),
        definition("suspension-trainer", "equipment_suspension_trainer", "accessories"),
        definition("chest-press", "equipment_chest_press", "machines"),
        definition("shoulder-press", "equipment_shoulder_press", "machines"),
        definition("lat-pulldown", "equipment_lat_pulldown", "machines"),
        definition("row-machine", "equipment_row_machine", "machines"),
        definition("leg-press", "equipment_leg_press", "machines"),
        definition("hack-squat", "equipment_hack_squat", "machines"),
        definition("leg-extension", "equipment_leg_extension", "machines"),
        definition("leg-curl", "equipment_leg_curl", "machines"),
        definition("calf-machine", "equipment_calf_machine", "machines"),
        definition("adductor-machine", "equipment_adductor_machine", "machines"),
        definition("abductor-machine", "equipment_abductor_machine", "machines"),
        definition("treadmill", "equipment_treadmill", "cardio"),
        definition("exercise-bike", "equipment_exercise_bike", "cardio"),
        definition("cross-trainer", "equipment_cross_trainer", "cardio"),
        definition("rowing-ergometer", "equipment_rowing_ergometer", "cardio"),
        definition("stair-climber", "equipment_stair_climber", "cardio"),
        definition(OPEN_FLOOR, "equipment_open_floor", "space"),
    )

    val slugs: Set<String> = all.mapTo(linkedSetOf()) { it.slug }

    private fun definition(
        slug: String,
        key: String,
        category: String,
        aliases: Set<String> = emptySet(),
        isPhysical: Boolean = true,
    ) = EquipmentDefinition(slug, key, category, aliases, isPhysical)
}

enum class LocationPreset { HOME_BASIC, GYM_FULL, OUTDOOR_MINIMAL, EMPTY_CUSTOM }

object LocationPresets {
    fun equipment(preset: LocationPreset): Set<String> = when (preset) {
        LocationPreset.HOME_BASIC -> setOf("mat", "resistance-bands", "dumbbells", EquipmentDefinitions.OPEN_FLOOR)
        LocationPreset.GYM_FULL -> EquipmentDefinitions.slugs - EquipmentDefinitions.NONE
        LocationPreset.OUTDOOR_MINIMAL -> setOf(EquipmentDefinitions.OPEN_FLOOR, "pull-up-bar")
        LocationPreset.EMPTY_CUSTOM -> emptySet()
    }
}
