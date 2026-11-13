package io.github.leekin2.xgoogle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.wrap.DexMethod;

/** One lazy bridge per package initialization; persistent descriptors avoid repeated scans.
 * API reference: https://luckypray.org/DexKit/en/guide/example.html
 */
final class AppDexTargets implements AutoCloseable {
    private static final int CACHE_SCHEMA = 2;
    private static volatile boolean nativeLoaded;
    private final String apkPath;
    private final ClassLoader loader;
    private final File cache;
    private final Properties stored = new Properties();
    private DexKitBridge bridge;
    private boolean dirty;

    AppDexTargets(ClassLoader loader, String apkPath, String dataDir) throws IOException {
        if (apkPath == null || apkPath.isEmpty()) throw new IOException("Scoped app APK unavailable");
        this.apkPath = apkPath;
        this.loader = loader;
        cache = dataDir == null ? null : new File(dataDir, "xgoogle_app_targets_v1.cache");
        File apk = new File(apkPath);
        String key = CACHE_SCHEMA + ":" + apkPath + ":" + apk.lastModified() + ":" + apk.length();
        if (cache != null && cache.isFile() && cache.length() < 65536) {
            try (var input = Files.newInputStream(cache.toPath())) { stored.load(input); }
            catch (IOException | IllegalArgumentException invalid) { stored.clear(); }
        }
        if (!key.equals(stored.getProperty("apk"))) stored.clear();
        stored.setProperty("apk", key);
    }

    List<Method> resolve(String feature) throws ReflectiveOperationException {
        // A revised Lens locator must not reuse the old one's miss for the same Google APK.
        String cacheKey = FeaturePolicy.LENS.equals(feature) ? feature + ".controllers-v6" : feature;
        if (!cacheKey.equals(feature) && stored.remove(feature) != null) dirty = true;
        if (FeaturePolicy.LENS.equals(feature) && stored.remove(feature + ".controllers-v2") != null) dirty = true;
        String saved = stored.getProperty(cacheKey);
        if (saved != null) {
            if (saved.isEmpty()) return List.of();
            try {
                List<Method> methods = new ArrayList<>();
                for (String descriptor : saved.split("\n"))
                    methods.add(new DexMethod(descriptor).getMethodInstance(loader));
                if (valid(feature, methods)) return methods;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError invalid) {
                // Stale or incompatible cached targets require a fresh scan.
            }
            stored.remove(cacheKey);
            dirty = true;
        }
        if (bridge == null) {
            loadNative();
            bridge = DexKitBridge.create(apkPath);
        }
        List<Method> methods = switch (feature) {
            case FeaturePolicy.LENS -> findLens();
            default -> throw new IllegalArgumentException("Unsupported dynamic feature");
        };
        if (methods.isEmpty()) {
            // Only deterministic misses are cached; thrown scan/binding errors are retried.
            stored.setProperty(cacheKey, "");
            dirty = true;
            return methods;
        }
        if (!valid(feature, methods)) return List.of();
        List<String> descriptors = new ArrayList<>();
        for (Method method : methods) descriptors.add(new DexMethod(method).serialize());
        stored.setProperty(cacheKey, String.join("\n", descriptors));
        dirty = true;
        return methods;
    }

    private static boolean valid(String feature, List<Method> methods) {
        if (methods.size() < 2 || methods.size() > 514) return false;
        if (FeaturePolicy.LENS.equals(feature)) {
            return LensBinding.isPair(methods.get(0), methods.get(1))
                    || LensBinding.is1775Pair(methods.get(0), methods.get(1));
        }
        return false;
    }

    private List<Method> findLens() throws ReflectiveOperationException {
        var anchors = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                // Google changes this diagnostic event number between app builds; the text,
                // coroutine method shape, unique gate, and eligibility graph provide the anchors.
                .returnType("java.lang.Object").paramCount(4)
                .usingStrings(List.of("Checked: %s"))));
        if (anchors.size() != 1) return List.of();
        MethodData gate = null;
        for (MethodData method : anchors.get(0).getDeclaredClass().getMethods()) {
            if (!method.getReturnTypeName().equals("boolean") || !method.getParamTypeNames().equals(List.of("boolean"))) continue;
            if (gate != null) return List.of();
            gate = method;
        }
        if (gate == null) return List.of();
        Map<Method, MethodData> candidates = new LinkedHashMap<>();
        for (MethodData caller : gate.getCallers()) {
            if (caller.getParamCount() == 0 && caller.getReturnTypeName().equals("boolean"))
                candidates.put(caller.getMethodInstance(loader), caller);
        }
        Method[] selected = LensBinding.select(candidates.keySet(), gate.getClassInstance(loader));
        if (selected == null && gate.getClassInstance(loader).getName().equals("chmv")) {
            // In 17.75 Talk uses the synchronous eligibility method on the gate itself.
            // Check the inspected descriptors and keep other versions on the structural locator.
            for (var method : gate.getDeclaredClass().getMethods()) {
                if (method.getName().equals("c") && method.getParamCount() == 0
                        && method.getReturnTypeName().equals("boolean")) {
                    Method talk = method.getMethodInstance(loader);
                    for (Method create : candidates.keySet()) {
                        if (LensBinding.is1775Pair(create, talk)) {
                            selected = new Method[]{create, talk};
                            candidates.put(talk, method);
                            break;
                        }
                    }
                    break;
                }
            }
        }
        if (selected == null) return List.of();
        List<Method> result = new ArrayList<>(List.of(selected));
        List<MethodData> frontier = new ArrayList<>(List.of(candidates.get(selected[0]), candidates.get(selected[1])));
        Set<String> visited = new LinkedHashSet<>();
        for (MethodData method : frontier) visited.add(method.getDescriptor());
        // Follow the inspected eligibility callers, replacing the old fixed caller-name list.
        for (int depth = 0; depth < 3; depth++) {
            List<MethodData> next = new ArrayList<>();
            for (MethodData method : frontier) {
                for (MethodData caller : method.getCallers()) {
                    if (!caller.isMethod() || Modifier.isAbstract(caller.getModifiers()) || Modifier.isNative(caller.getModifiers())
                            || !visited.add(caller.getDescriptor())) continue;
                    if (visited.size() > 512) return List.of();
                    result.add(caller.getMethodInstance(loader));
                    next.add(caller);
                }
            }
            frontier = next;
        }
        return result;
    }

    static void loadNative() {
        if (nativeLoaded) return;
        synchronized (AppDexTargets.class) {
            if (!nativeLoaded) { System.loadLibrary("dexkit"); nativeLoaded = true; }
        }
    }

    @Override public void close() {
        try {
            if (bridge != null) bridge.close();
        } finally {
            if (dirty && cache != null) {
                File temporary = null;
                try {
                    temporary = File.createTempFile("xgoogle_google_targets", ".tmp", cache.getParentFile());
                    try (var output = Files.newOutputStream(temporary.toPath())) { stored.store(output, "XGoogle DexKit targets"); }
                    Files.move(temporary.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException | RuntimeException unavailable) {
                    // Unwritable cache must not prevent feature installation.
                } finally {
                    if (temporary != null) temporary.delete();
                }
            }
        }
    }
}
