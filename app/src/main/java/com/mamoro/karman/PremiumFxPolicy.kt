package com.mamoro.karman

/** Pure configuration: keeps expensive frame-by-frame effects out of FAST mode. */
internal data class GlassFxProfile(
    val animatedShimmer: Boolean,
    val shimmerDurationMillis: Int,
    val glowLayers: Int,
    val elevationDp: Int
)

internal object GlassFxPolicy {
    fun forMode(mode: String): GlassFxProfile = when (mode) {
        "MAX" -> GlassFxProfile(animatedShimmer = true, shimmerDurationMillis = 6400,
            glowLayers = 3, elevationDp = 12)
        "BALANCED" -> GlassFxProfile(animatedShimmer = true, shimmerDurationMillis = 14000,
            glowLayers = 1, elevationDp = 3)
        else -> GlassFxProfile(animatedShimmer = false, shimmerDurationMillis = 0,
            glowLayers = 0, elevationDp = 0)
    }
}
