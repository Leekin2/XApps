package io.github.leekin2.xgoogle;

import android.content.SharedPreferences;

public final class FeatureConfig {
    /** LSPosed settings group. */
    public static final String GROUP="xgoogle";
    /** The group's name before XApps; read until the app has moved its settings to GROUP. */
    public static final String OLD_GROUP="coogle";
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
