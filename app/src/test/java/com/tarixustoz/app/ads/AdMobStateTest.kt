package com.tarixustoz.app.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdMobStateTest {

    @Test
    fun `effectiveBannerUnitId returns test unit id when test mode is active`() {
        val resolvedTest = AdMobManager.effectiveBannerUnitId(
            requestedUnitId = AdMobManager.BANNER_AD_UNIT_ID,
            isTestMode = true
        )
        assertThat(resolvedTest).isEqualTo(AdMobManager.TEST_BANNER_AD_UNIT_ID)

        val resolvedLive = AdMobManager.effectiveBannerUnitId(
            requestedUnitId = AdMobManager.BANNER_AD_UNIT_ID,
            isTestMode = false
        )
        assertThat(resolvedLive).isEqualTo(AdMobManager.BANNER_AD_UNIT_ID)
    }

    @Test
    fun `setTestModeEnabled locks test mode and exposes diagnostics`() {
        AdMobManager.setTestModeEnabled(
            enabled = true,
            reason = "Jonli unit No fill (kod 3)"
        )
        val state = AdMobManager.diagnostics.value
        assertThat(state.isTestMode).isTrue()
        assertThat(state.liveProbeStatus).isEqualTo(AdProbeStatus.LIVE_FAILED_USING_TEST)
        assertThat(state.liveErrorSummary).contains("kod 3")
        assertThat(state.activeBannerUnitId).isEqualTo(AdMobManager.TEST_BANNER_AD_UNIT_ID)

        AdMobManager.setTestModeEnabled(enabled = false)
        val resetState = AdMobManager.diagnostics.value
        assertThat(resetState.isTestMode).isFalse()
        assertThat(resetState.liveProbeStatus).isEqualTo(AdProbeStatus.TESTING_LIVE)
        assertThat(resetState.activeBannerUnitId).isEqualTo(AdMobManager.BANNER_AD_UNIT_ID)
    }

    @Test
    fun `effectiveAdaptiveBannerUnitId resolves adaptive test unit in test mode`() {
        val adaptiveTest = AdMobManager.effectiveAdaptiveBannerUnitId(
            requestedUnitId = AdMobManager.BANNER_AD_UNIT_ID,
            isTestMode = true
        )
        assertThat(adaptiveTest).isEqualTo(AdMobManager.TEST_ADAPTIVE_BANNER_AD_UNIT_ID)

        val adaptiveLive = AdMobManager.effectiveAdaptiveBannerUnitId(
            requestedUnitId = AdMobManager.BANNER_AD_UNIT_ID,
            isTestMode = false
        )
        assertThat(adaptiveLive).isEqualTo(AdMobManager.BANNER_AD_UNIT_ID)
    }

    @Test
    fun `computeInlineAdaptiveHeightBucket quantizes and clamps responsive heights`() {
        // Compact height (<90dp) stays anchored without forcing inline minimum
        assertThat(AdMobManager.computeInlineAdaptiveHeightBucket(64)).isEqualTo(64)
        assertThat(AdMobManager.computeInlineAdaptiveHeightBucket(32)).isEqualTo(50)

        // Medium and tall inline adaptive buckets (16dp steps)
        assertThat(AdMobManager.computeInlineAdaptiveHeightBucket(150)).isEqualTo(144)
        assertThat(AdMobManager.computeInlineAdaptiveHeightBucket(260)).isEqualTo(256)
        assertThat(AdMobManager.computeInlineAdaptiveHeightBucket(500)).isEqualTo(416)
    }

    @Test
    fun `describeAdaptiveBannerTier classifies responsive banner dimensions`() {
        assertThat(AdMobManager.describeAdaptiveBannerTier(360, 260))
            .contains("Inline Adaptive XL")
        assertThat(AdMobManager.describeAdaptiveBannerTier(360, 160))
            .contains("Inline Adaptive L")
        assertThat(AdMobManager.describeAdaptiveBannerTier(360, 96))
            .contains("Inline Adaptive M")
        assertThat(AdMobManager.describeAdaptiveBannerTier(360, 64))
            .contains("Anchored Adaptive")
    }
}
