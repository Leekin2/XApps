package io.github.leekin2.xgoogle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

/**
 * Circle to Search live (scroll) translation. Google App offers it only on devices that
 * report CONTEXTUAL_SEARCH_LIVE_TRANSLATE (declared by the companion module) and whose
 * live-translate action reports the capability. The action class is obfuscated, so it is
 * located by the constant it reports from its int getter.
 */
final class LiveTranslateHooks {
    private static final int ACTION_ID = 271520;
    private static volatile boolean nativeLoaded;

    static void install(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir) {
        if (apkPath == null || apkPath.isEmpty()) {
            hooks.info("LIVE_TRANSLATE skip: Google App path unavailable");
            return;
        }
        try {
            Class<?> action = cachedAction(hooks, loader, apkPath, dataDir);
            if (action == null) {
                hooks.info("LIVE_TRANSLATE skip: action class not uniquely found");
                return;
            }
            Method visible = booleanGetter(action, "i");
            Method capable = capability(action);
            if (visible == null || capable == null) {
                hooks.info("LIVE_TRANSLATE skip: incompatible action " + action.getName());
                return;
            }
            hooks.install(capable, chain ->
                    FeatureConfig.enabled(FeaturePolicy.LIVE_TRANSLATE) ? Boolean.TRUE : chain.proceed());
            // The visibility predicate reads the capability; drop compiled copies so it re-evaluates.
            boolean deoptimized = hooks.deoptimize(visible);
            hooks.info("LIVE_TRANSLATE READY action=" + action.getName() + " capability=" + capable
                    + " visibilityDeoptimized=" + deoptimized);
        } catch (Throwable error) {
            hooks.error("LIVE_TRANSLATE skip: incompatible Google App", error);
        }
    }

    /**
     * Scanning Google App's dex costs a few hundred milliseconds of CPU in every Google process,
     * so the result is kept in the app's own data directory until the APK changes.
     */
    private static Class<?> cachedAction(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir)
            throws ClassNotFoundException {
        File apk = new File(apkPath);
        String key = apkPath + ":" + apk.lastModified() + ":" + apk.length();
        File cache = dataDir == null ? null : new File(dataDir, "xgoogle_live_translate.cache");
        if (cache != null && cache.isFile()) {
            try {
                String[] stored = new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8).split("\n", -1);
                if (stored.length == 2 && stored[0].equals(key)) {
                    if (stored[1].isEmpty()) return null;
                    // A stored class this process cannot load is stale: scan again.
                    Class<?> action = null;
                    try { action = Class.forName(stored[1], false, loader); }
                    catch (ClassNotFoundException | LinkageError stale) { }
                    if (action != null && booleanGetter(action, "i") != null && capability(action) != null) {
                        hooks.info("LIVE_TRANSLATE cache hit, dex scan skipped");
                        return action;
                    }
                }
            } catch (IOException | RuntimeException unreadable) {
                // Fall through to a fresh scan.
            }
        }
        Class<?> action = findAction(loader, apkPath);
        if (cache != null) {
            try {
                File temporary = File.createTempFile("xgoogle_live_translate", ".tmp", new File(dataDir));
                Files.write(temporary.toPath(), (key + "\n" + (action == null ? "" : action.getName()))
                        .getBytes(StandardCharsets.UTF_8));
                if (!temporary.renameTo(cache)) temporary.delete();
            } catch (IOException | RuntimeException unwritable) {
                hooks.info("LIVE_TRANSLATE cache not written: " + unwritable);
            }
        }
        return action;
    }

    private static Class<?> findAction(ClassLoader loader, String apkPath) throws ClassNotFoundException {
        if (!nativeLoaded) {
            synchronized (LiveTranslateHooks.class) {
                if (!nativeLoaded) {
                    System.loadLibrary("dexkit");
                    nativeLoaded = true;
                }
            }
        }
        Set<String> owners = new LinkedHashSet<>();
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            for (MethodData method : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .returnType("int").paramCount(0).usingNumbers(ACTION_ID)))) {
                owners.add(method.getClassName());
            }
        }
        Class<?> found = null;
        for (String owner : owners) {
            Class<?> candidate = Class.forName(owner, false, loader);
            if (booleanGetter(candidate, "i") == null || capability(candidate) == null) continue;
            if (found != null) return null;
            found = candidate;
        }
        return found;
    }

    /** The action's sole three-argument constructor takes the capability holder second. */
    private static Method capability(Class<?> action) {
        Constructor<?> only = null;
        for (Constructor<?> constructor : action.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != 3) continue;
            if (only != null) return null;
            only = constructor;
        }
        return only == null ? null : booleanGetter(only.getParameterTypes()[1], "a");
    }

    private static Method booleanGetter(Class<?> type, String name) {
        try {
            Method method = type.getDeclaredMethod(name);
            if (method.getReturnType() != boolean.class || Modifier.isAbstract(method.getModifiers())) return null;
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    private LiveTranslateHooks() {}
}
