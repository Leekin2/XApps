package io.github.leekin2.xgoogle;

/** XGoogle runs on HyperOS only. */
public final class SystemCompatibility {
    private SystemCompatibility() { }

    public static boolean isSupported() {
        return isHyperOs();
    }

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
}
