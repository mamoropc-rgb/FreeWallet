package com.mamoro.karman

import org.junit.Assert.*
import org.junit.Test

class PremiumFxPolicyTest {
    @Test fun fastModeDoesNotRunContinuousEffects() {
        val profile = GlassFxPolicy.forMode("FAST")
        assertFalse(profile.animatedShimmer)
        assertEquals(0, profile.glowLayers)
        assertEquals(0, profile.elevationDp)
    }
    @Test fun balancedRunsLessWorkThanMax() {
        val max = GlassFxPolicy.forMode("MAX")
        val balanced = GlassFxPolicy.forMode("BALANCED")
        assertTrue(max.animatedShimmer)
        assertTrue(balanced.animatedShimmer)
        assertTrue(balanced.shimmerDurationMillis > max.shimmerDurationMillis)
        assertTrue(balanced.glowLayers < max.glowLayers)
    }
    @Test fun unsupportedPreferenceFallsBackToSafeStaticMode() {
        assertFalse(GlassFxPolicy.forMode("UNKNOWN").animatedShimmer)
    }
}
