package io.github.leekin2.xgoogle;

import android.content.SharedPreferences;

public final class FeatureConfig {
    /** LSPosed settings group; the name is kept so saved settings survive updates. */
    public static final String GROUP="coogle";
    private static volatile SharedPreferences preferences;
    public static void attach(SharedPreferences value) { preferences=value; }
    public static boolean enabled(String key) {
        SharedPreferences p=preferences;
        boolean requested=p==null ? FeaturePolicy.defaultValue(key) : p.getBoolean(key,FeaturePolicy.defaultValue(key));
        return FeaturePolicy.effective(key,requested,requested && FeaturePolicy.HEY_GOOGLE.equals(key)
                && p!=null && p.getBoolean("companion_ready",false));
    }
    private FeatureConfig() {}
}
