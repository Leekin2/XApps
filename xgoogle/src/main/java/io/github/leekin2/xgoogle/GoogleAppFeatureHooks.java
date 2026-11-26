package io.github.leekin2.xgoogle;

import java.lang.reflect.Method;

/**
 * Supplies native system feature declarations queried by the Google App (from Coogle 1.4.0):
 * - GEMINI_POWER_MENU so Gemini can invoke the system power menu.
 * - CAMERA_INTEGRATION / IMAGE_INTEGRATION + OEM package properties for Xiaomi Camera & Gallery Lens.
 */
final class GoogleAppFeatureHooks {
    private static final String CAMERA_INTEGRATION = "com.google.lens.feature.CAMERA_INTEGRATION";
    private static final String IMAGE_INTEGRATION = "com.google.lens.feature.IMAGE_INTEGRATION";
    private static final String GEMINI_POWER_MENU = "com.google.android.feature.GEMINI_POWER_MENU";
    private static final String CAMERA_PROPERTY = "ro.com.google.lens.oem_camera_package";
    private static final String IMAGE_PROPERTY = "ro.com.google.lens.oem_image_package";
    private static final String XIAOMI_CAMERA = "com.android.camera";
    private static final String XIAOMI_GALLERY = "com.miui.gallery";

    static void install(HookInstaller hooks, ClassLoader loader) {
        boolean camera = FeatureConfig.enabled(FeaturePolicy.CAMERA_LENS);
        boolean lens = FeatureConfig.enabled(FeaturePolicy.LENS);
        boolean powerMenu = FeatureConfig.enabled(FeaturePolicy.NAVIGATION) || FeatureConfig.enabled(FeaturePolicy.POWER_KEY);
        if (!camera && !lens && !powerMenu) return;
        int mark = hooks.mark();
        try {
            Class<?> pm = HookInstaller.target(loader, "android.app.ApplicationPackageManager");
            boolean installed = false;
            for (Method method : pm.getDeclaredMethods()) {
                if (!"hasSystemFeature".equals(method.getName()) || method.getReturnType() != boolean.class
                        || method.getParameterCount() < 1 || method.getParameterCount() > 2
                        || method.getParameterTypes()[0] != String.class) continue;
                boolean versioned = method.getParameterCount() == 2;
                if (versioned && method.getParameterTypes()[1] != int.class) continue;
                hooks.install(method, chain -> {
                    String name = (String) chain.getArg(0);
                    boolean enabled = name != null && switch (name) {
                        case CAMERA_INTEGRATION -> FeatureConfig.enabled(FeaturePolicy.CAMERA_LENS);
                        case IMAGE_INTEGRATION -> FeatureConfig.enabled(FeaturePolicy.LENS);
                        case GEMINI_POWER_MENU -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION)
                                || FeatureConfig.enabled(FeaturePolicy.POWER_KEY);
                        default -> false;
                    };
                    if (enabled && (!versioned || (int) chain.getArg(1) <= 0)) return true;
                    return chain.proceed();
                });
                installed = true;
            }
            if (!installed) throw new NoSuchMethodException("Google feature query missing");
            Method property = HookInstaller.target(loader, "android.os.SystemProperties")
                    .getDeclaredMethod("get", String.class, String.class);
            hooks.install(property, chain -> {
                Object original = chain.proceed();
                Object name = chain.getArg(0);
                if (IMAGE_PROPERTY.equals(name) && FeatureConfig.enabled(FeaturePolicy.LENS)) {
                    return append((String) original, XIAOMI_GALLERY);
                }
                if (CAMERA_PROPERTY.equals(name) && FeatureConfig.enabled(FeaturePolicy.CAMERA_LENS)) {
                    return append((String) original, XIAOMI_CAMERA);
                }
                return original;
            });
            hooks.info("Google App feature declarations installed (Gemini power menu + Lens integration)");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("Google App feature declarations unavailable", failure);
        }
    }

    private static String append(String value, String caller) {
        if (value == null || value.isEmpty()) return caller;
        for (String entry : value.split(",")) if (caller.equals(entry.trim())) return value;
        return value + "," + caller;
    }

    private GoogleAppFeatureHooks() { }
}
