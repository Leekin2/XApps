package io.github.leekin2.xgoogle;

/** Eligible platforms: ColorOS major version 17, or HyperOS. */
public final class SystemCompatibility {
    private SystemCompatibility() { }

    public static boolean isSupported() {
        return isColorOs17() || isHyperOs();
    }

    /** OEM-specific hooks resolve their classes optionally, so on HyperOS only the AOSP-level ones engage. */
    public static boolean isHyperOs() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            String version = (String) properties.getMethod("get", String.class)
                    .invoke(null, "ro.mi.os.version.name");
            return version != null && !version.trim().isEmpty();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    public static boolean isColorOs17() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            String version = (String) properties.getMethod("get", String.class)
                    .invoke(null, "ro.build.version.oplusrom");
            if (version == null || version.isEmpty()) {
                version = (String) properties.getMethod("get", String.class)
                        .invoke(null, "ro.build.version.oplusrom.display");
            }
            return matchesVersion(version);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    static boolean matchesVersion(String value) {
        return value != null && value.trim().matches("(?i)V?17(?:\\.[0-9]+)*");
    }
}
