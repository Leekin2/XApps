package io.github.leekin2.xgoogle.migration
import io.github.leekin2.xgoogle.FeatureConfig
import io.github.leekin2.xgoogle.FeaturePolicy

/** Feature switches shared by the XGoogle settings and migrated hooks. */
internal object LspConfig {
    fun isAssistantNativeCircleEnabledXposed() = FeatureConfig.enabled(FeaturePolicy.NAVIGATION)
    fun isOkGoogleHotwordCompatibilityEnabledXposed() = FeatureConfig.enabled(FeaturePolicy.HEY_GOOGLE)
    fun isGmsRegionRestrictionBypassEnabledXposed() = FeatureConfig.enabled(FeaturePolicy.GMS)
}
