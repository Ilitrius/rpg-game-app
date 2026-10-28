package com.idlerpg.game.domain.world

/**
 * Biomes of the generated zones 6..100. Each biome covers [STAGES] consecutive zones; within a
 * biome the zones escalate (stage 0 = outskirts .. stage 4 = heart). Zone ids are `<id>_<stage+1>`.
 * Looks (colors, silhouettes, creature shapes) are defined in the UI (`BiomeStyles`), names in
 * `strings_spielwelt_welten.xml`.
 */
enum class Biome(val id: String) {
    HEIDE("heide"), WUESTE("wueste"), VULKAN("vulkan"), GRUFT("gruft"), FROST("frost"),
    DSCHUNGEL("dschungel"), RUINEN("ruinen"), GLUTPILZ("glutpilz"), KUESTE("kueste"), KNOCHEN("knochen"),
    BLUTGARTEN("blutgarten"), HIMMEL("himmel"), UNTERWELT("unterwelt"), SCHATTEN("schatten"), UHRWERK("uhrwerk"),
    TRAUM("traum"), ASTRAL("astral"), LEERE("leere"), ENDE("ende");

    companion object {
        const val STAGES = 5
    }
}
