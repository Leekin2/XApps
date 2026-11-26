package io.github.leekin2.xsecurity;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Always-on display (com.miui.aod) options from kashi's HyperOS AOD mod (Mods Center, used with
 * the author's permission). Kashi's code is not included: each option is written anew as a hook
 * at the place his patch changes.
 */
final class AodHooks {
    private final Hooks hooks;
    private final ClassLoader loader;

    AodHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() {
        if (Features.enabled(Features.AOD_ALL)) run("AOD hidden features", this::allFeatures);
        if (Features.enabled(Features.AOD_GRAYSCALE)) run("AOD grayscale", () -> hooks.constant(bool(utils("isNeedToGrayScale")), true));
        if (Features.enabled(Features.AOD_LUNAR)) run("AOD lunar date in every language", () -> {
            Class<?> settings = Class.forName("com.miui.aod.widget.AODSettings", false, loader);
            Method dual = settings.getDeclaredMethod("isDualClock", Context.class);
            hooks.install(bool(settings.getDeclaredMethod("supportShowLunarDate", Context.class)),
                    chain -> !(boolean) dual.invoke(null, chain.getArg(0)));
        });
        if (Features.enabled(Features.AOD_EDITOR_UNLOCK)) run("AOD editor templates unlocked", () -> {
            Class<?> hide = Class.forName("com.miui.keyguard.editor.utils.TemplateHide", false, loader);
            hooks.install(bool(hide.getDeclaredMethod("shouldHide", int.class)), chain -> false);
        });
        if (Features.enabled(Features.AOD_FULLSCREEN)) run("full screen AOD (app only)", () ->
                hooks.constant(bool(utils("isFullAodSupport", Context.class)), true));
        if (Features.enabled(Features.AOD_FLAGSHIP)) run("AOD performance level", this::flagship);
        if (Features.enabled(Features.AOD_SCREEN_STATE)) run("AOD screen state", this::screenState);
        if (Features.enabled(Features.AOD_STANDARD_SENSORS)) run("standard AOD sensors", () -> AodStandardSensors.install(hooks, loader));
        if (Features.enabled(Features.AOD_SMART_SENSOR)) run("smart AOD with sensors", this::smartSensors);
        if (Features.enabled(Features.AOD_BURNIN)) run("AOD burn-in shift " + Features.intValue(Features.AOD_BURNIN_SECONDS, 10) + " s", () -> {
            Class<?> position = Class.forName("com.miui.aod.AODUpdatePositionController", false, loader);
            Field delay = position.getDeclaredField("UPDATE_POSITION_DELAY");
            delay.setAccessible(true);
            // A plain (not final) static the controller reads each time; at least 5 s, never 0.
            delay.setLong(null, Math.max(5, Features.intValue(Features.AOD_BURNIN_SECONDS, 10)) * 1000L);
        });
        if (Features.enabled(Features.AOD_TIMEOUT) || Features.enabled(Features.AOD_BATTERY_CUTOFF) || Features.enabled(Features.AOD_DARK_OFF))
            run("AOD timer, battery cutoff and darkness", this::guard);
        if (Features.enabled(Features.AOD_GLOW) || Features.enabled(Features.AOD_CHARGING) || Features.enabled(Features.AOD_NOW_PLAYING) || Features.enabled(Features.AOD_KITS))
            run("AOD extras", () -> new AodOverlay(hooks, loader).install());
    }

    /** In the lock screen editor's own process: its template unlock and the kits in the preview. */
    void installEditor() {
        if (Features.enabled(Features.AOD_ALL)) run("AOD editor hidden features", this::allFeatures);
        if (Features.enabled(Features.AOD_EDITOR_UNLOCK)) run("AOD editor templates unlocked", () -> {
            Class<?> hide = Class.forName("com.miui.keyguard.editor.utils.TemplateHide", false, loader);
            hooks.install(bool(hide.getDeclaredMethod("shouldHide", int.class)), chain -> false);
        });
        if (Features.enabled(Features.AOD_KITS)) run("lock screen editor kits", () -> EditorKits.install(hooks, loader));
    }

    private interface Step { void run() throws Throwable; }

    private void run(String name, Step step) {
        try {
            step.run();
            hooks.info(name + " ready");
        } catch (Throwable failure) {
            hooks.error(name + " unavailable; stock behaviour kept", failure);
        }
    }

    /** Only a method that really returns boolean is answered with one (a wrong type would crash System UI). */
    private static Method bool(Method m) throws NoSuchMethodException {
        if (m.getReturnType() != boolean.class) throw new NoSuchMethodException(m.getName() + " returns " + m.getReturnType());
        return m;
    }

    /** Only classes of the AOD code itself: the same name in System UI must not change all of System UI. */
    private Class<?> own(Class<?> c) throws ClassNotFoundException {
        if (c.getClassLoader() != loader) throw new ClassNotFoundException(c.getName() + " is not the AOD's own class");
        return c;
    }

    /** Skips a call while the motion sensor stands in for the attention camera; void methods only. */
    private void skipWhenEmulated(Method m) throws Throwable {
        if (m.getReturnType() == void.class) {
            hooks.install(m, chain -> emulate ? null : chain.proceed());
        } else if (m.getReturnType() == boolean.class) {
            hooks.install(m, chain -> emulate ? (Object) false : chain.proceed());
        }
    }

    private Method utils(String name, Class<?>... types) throws ReflectiveOperationException {
        return Class.forName("com.miui.aod.Utils", false, loader).getDeclaredMethod(name, types);
    }

    /** The checks kashi's AOD mod answers with a fixed value: class, method, answer. */
    private static final String[][] ALL = {
            {"com.miui.aod.Utils", "isSupportAodAnimateDevice", "true"},
            {"com.miui.aod.Utils", "isSupportLinkageStyle", "true"},
            {"com.miui.aod.Utils", "supportAod", "true"},
            {"com.miui.aod.Utils", "supportDisplayModePage", "true"},
            {"com.miui.aod.other.step.StepUtils", "supportStepComponent", "true"},
            {"com.miui.aod.widget.AODSettings", "isSupportAodWallpaper", "true"},
            {"com.miui.aod.widget.AODSettings", "isSupportedSmartCoverDevice", "true"},
            {"com.miui.aod.widget.AODSettings", "onlySupportKeycodeGoto", "false"},
            {"com.miui.keyguard.editor.utils.CommonUtils", "checkThemeSupportDownload", "true"},
            {"com.miui.keyguard.editor.utils.DeviceUtil", "supportAod", "true"},
            {"com.miui.keyguard.editor.utils.FileUtil", "isSupportVideoDepth", "true"},
    };

    /** Step counter, AOD wallpaper, video depth wallpapers, linkage styles and the rest, as in kashi's mod. */
    private void allFeatures() throws Throwable {
        int hooked = 0;
        for (String[] f : ALL) {
            try {
                for (Method m : Class.forName(f[0], false, loader).getDeclaredMethods()) {
                    if (!m.getName().equals(f[1]) || m.getReturnType() != boolean.class) continue;
                    hooks.constant(m, Boolean.parseBoolean(f[2]));
                    hooked++;
                }
            } catch (ClassNotFoundException ignored) { }
        }
        if (hooked == 0) throw new NoSuchMethodException("AOD feature checks not found");
    }

    /** Animations, clock transform and effect templates of a flagship, whatever the phone reports. */
    private void flagship() throws Throwable {
        Class<?> device = own(Class.forName("miuix.device.DeviceUtils", false, loader));
        // 1 balanced, 2 balanced with flagship effects, 3 flagship (kashi's levels).
        int level = Features.intValue(Features.AOD_PERF_LEVEL, 3);
        Field tier = device.getDeclaredField(level >= 3 ? "HIGH" : "MIDDLE");
        tier.setAccessible(true);
        for (Method m : device.getDeclaredMethods()) {
            if (!m.getName().startsWith("getDeviceLevel") || m.getReturnType() != int.class) continue;
            hooks.install(m, chain -> tier.getInt(null));
        }
        Class<?> util = own(Class.forName("com.miui.keyguard.editor.utils.DeviceUtil", false, loader));
        boolean effects = level >= 2;
        hooks.install(bool(util.getDeclaredMethod("isCpuAndGpuHighLevel")), chain -> effects);
        hooks.install(bool(util.getDeclaredMethod("isLiteDevice")), chain -> false);
        hooks.install(bool(util.getDeclaredMethod("isLowComputeDevice")), chain -> false);
    }

    /**
     * The display state the AOD asks for while dozing: Doze, Doze suspend (stock) or plain On,
     * which keeps an LCD lit when it shows black in doze.
     */
    private void screenState() throws Throwable {
        int wanted = Features.intValue(Features.AOD_SCREEN_STATE_VALUE, 4);
        Class<?> state = Class.forName("com.miui.aod.doze.DozeMachine$State", false, loader);
        Method screen = state.getDeclaredMethod("screenState");
        if (screen.getReturnType() != int.class) throw new NoSuchMethodException("screenState does not return int");
        hooks.install(screen, chain -> {
            Object value = chain.proceed();
            return value instanceof Integer && ((Integer) value == 3 || (Integer) value == 4) ? (Object) wanted : value;
        });
    }

    // --- Smart AOD without the attention camera ------------------------------------------------

    private static volatile boolean emulate;

    /**
     * Smart mode asks an attention camera whether someone looks at the phone. Where there is none,
     * the motion sensor answers: a phone held up (not lying flat) or just moved counts as looked at.
     * Phones with the camera keep it.
     */
    private void smartSensors() throws Throwable {
        Class<?> manager = Class.forName("com.miui.aod.attention.AttentionManager", false, loader);
        Class<?> connection = Class.forName("com.miui.aod.attention.AttentionManager$ConnectionCallback", false, loader);
        Class<?> callback = Class.forName("com.miui.aod.attention.AttentionManager$AttentionCallback", false, loader);
        Method success = null;
        for (Method m : callback.getMethods()) if (m.getName().equals("onSuccess")) success = m;
        if (success == null) throw new NoSuchMethodException("Attention callback");
        Method onSuccess = success;
        hooks.install(bool(manager.getDeclaredMethod("supportAttention", Context.class)), chain -> {
            Object real = chain.proceed();
            if (Boolean.TRUE.equals(real)) return true;
            emulate = true;
            return true;
        });
        skipWhenEmulated(manager.getDeclaredMethod("bind", connection));
        skipWhenEmulated(manager.getDeclaredMethod("bind"));
        skipWhenEmulated(manager.getDeclaredMethod("unbind"));
        for (Method m : manager.getDeclaredMethods()) {
            if (m.getName().equals("checkAttention") && m.getParameterCount() >= 1 && m.getParameterTypes()[0] == callback) {
                hooks.install(m, chain -> {
                    if (!emulate || m.getReturnType() != void.class) return chain.proceed();
                    Object cb = chain.getArg(0);
                    Context context = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
                    looked(context, held -> {
                        try { onSuccess.invoke(cb, null, held ? 1 : 0, android.os.SystemClock.elapsedRealtime()); }
                        catch (Throwable t) { android.util.Log.w("XSecurity", "smart AOD", t); }
                    });
                    return null;
                });
            }
            if (m.getName().equals("cancelAttentionCheck")) skipWhenEmulated(m);
        }
    }

    private interface Answer { void held(boolean held); }

    /** Reads the accelerometer for half a second: tilted up or moving means the phone is in hand. */
    private static void looked(Context context, Answer answer) {
        android.hardware.SensorManager sensors = context.getSystemService(android.hardware.SensorManager.class);
        android.hardware.Sensor accel = sensors == null ? null : sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
        if (accel == null) { MAIN.post(() -> answer.held(true)); return; }
        float[] min = {Float.MAX_VALUE}, max = {-Float.MAX_VALUE}, last = {0, 0, 9.8f};
        android.hardware.SensorEventListener listener = new android.hardware.SensorEventListener() {
            @Override public void onSensorChanged(android.hardware.SensorEvent e) {
                float g = (float) Math.sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]);
                min[0] = Math.min(min[0], g);
                max[0] = Math.max(max[0], g);
                System.arraycopy(e.values, 0, last, 0, 3);
            }
            @Override public void onAccuracyChanged(android.hardware.Sensor s, int a) { }
        };
        sensors.registerListener(listener, accel, android.hardware.SensorManager.SENSOR_DELAY_GAME, MAIN);
        MAIN.postDelayed(() -> {
            sensors.unregisterListener(listener);
            boolean tilted = Math.abs(last[2]) < 8.0f;      // not lying flat on its back or face
            boolean moving = max[0] - min[0] > 1.2f;
            answer.held(tilted || moving);
        }, 500);
    }

    // --- Auto-off timer and battery cutoff ------------------------------------------------------

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable pendingHide;

    /**
     * The AOD hides itself a while after it appears (until the screen is woken again), and does not
     * appear below the chosen battery level unless the phone is charging.
     */
    private void guard() throws Throwable {
        Class<?> controller = Class.forName("com.miui.aod.doze.MiuiShowStyleController", false, loader);
        Method show = controller.getDeclaredMethod("onShowDoze", boolean.class, boolean.class);
        Method hide = controller.getDeclaredMethod("hideDoze");
        hide.setAccessible(true);
        boolean timer = Features.enabled(Features.AOD_TIMEOUT);
        boolean cutoff = Features.enabled(Features.AOD_BATTERY_CUTOFF);
        boolean dark = Features.enabled(Features.AOD_DARK_OFF);
        long timeout = Features.intValue(Features.AOD_TIMEOUT_SECONDS, 60) * 1000L;
        int level = Features.intValue(Features.AOD_BATTERY_CUTOFF_PERCENT, 15);
        hooks.install(show, chain -> {
            boolean visible = (boolean) chain.getArg(0);
            Object self = chain.getThisObject();
            synchronized (AodHooks.class) {
                if (pendingHide != null) cancelLater(pendingHide);
                pendingHide = null;
            }
            if (dark) watchDarkness(self, show, hide, visible);
            if (visible && ((cutoff && lowBattery(level)) || (dark && inDark))) {
                Object[] args = chain.getArgs().toArray();
                args[0] = false;
                return chain.proceed(args);
            }
            Object result = chain.proceed();
            if (visible && timer) {
                Runnable later = () -> {
                    try { hide.invoke(self); } catch (Throwable t) { android.util.Log.w("XSecurity", "AOD timer", t); }
                };
                synchronized (AodHooks.class) { pendingHide = later; }
                runLater(later, timeout);
            }
            return result;
        });
    }

    // --- A timer that also runs in doze -----------------------------------------------------------

    private static final java.util.Map<Runnable, android.app.AlarmManager.OnAlarmListener> ALARMS = new java.util.HashMap<>();

    /**
     * Handler delays use uptime, which stands still while the phone dozes, so the AOD could stay
     * on for hours. A wake-up alarm on the elapsed clock fires on time; the handler is only the
     * fallback when no alarm can be set.
     */
    private static void runLater(Runnable task, long delay) {
        try {
            Context context = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            android.app.AlarmManager alarms = context.getSystemService(android.app.AlarmManager.class);
            android.app.AlarmManager.OnAlarmListener listener = () -> {
                synchronized (ALARMS) { ALARMS.remove(task); }
                task.run();
            };
            alarms.setExact(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, android.os.SystemClock.elapsedRealtime() + delay,
                    "xapps:aod-timeout", listener, MAIN);
            synchronized (ALARMS) { ALARMS.put(task, listener); }
        } catch (Throwable t) {
            MAIN.postDelayed(task, delay);
        }
    }

    private static void cancelLater(Runnable task) {
        MAIN.removeCallbacks(task);
        android.app.AlarmManager.OnAlarmListener listener;
        synchronized (ALARMS) { listener = ALARMS.remove(task); }
        if (listener == null) return;
        try {
            Context context = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            context.getSystemService(android.app.AlarmManager.class).cancel(listener);
        } catch (Throwable ignored) { }
    }

    // --- Screen off in darkness or a pocket -----------------------------------------------------

    private static volatile boolean inDark;
    private static android.hardware.SensorEventListener darkListener;
    private static Runnable darkChange;

    /**
     * While the AOD may show, the light and proximity sensors are watched: in a dark room or a
     * pocket for two seconds the AOD hides, and it comes back when it is light again.
     */
    private static android.hardware.SensorManager darkSensors;
    private static Object darkController;

    private static synchronized void stopDarkness() {
        if (darkListener != null && darkSensors != null) {
            try { darkSensors.unregisterListener(darkListener); } catch (Throwable ignored) { }
        }
        if (darkChange != null) { MAIN.removeCallbacks(darkChange); darkChange = null; }
        darkListener = null;
        darkController = null;
    }

    private static boolean screenOn(Context context) {
        try {
            android.os.PowerManager pm = context.getSystemService(android.os.PowerManager.class);
            return pm != null && pm.isInteractive();
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized void watchDarkness(Object controller, Method show, Method hide, boolean visible) {
        Context context;
        try {
            context = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return;
        }
        // Sensors only while the AOD is shown with the screen off, and for the latest controller.
        if (screenOn(context) || (!visible && !inDark)) { stopDarkness(); return; }
        if (darkListener != null && darkController == controller) return;
        stopDarkness();
        try {
            android.hardware.SensorManager sensors = context.getSystemService(android.hardware.SensorManager.class);
            android.hardware.Sensor light = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT);
            android.hardware.Sensor near = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY);
            if (light == null && near == null) return;
            float[] lux = {100f};
            boolean[] covered = {false};
            darkListener = new android.hardware.SensorEventListener() {
                @Override public void onSensorChanged(android.hardware.SensorEvent e) {
                    if (screenOn(context)) { inDark = false; stopDarkness(); return; }
                    if (e.sensor.getType() == android.hardware.Sensor.TYPE_LIGHT) lux[0] = e.values[0];
                    else covered[0] = e.values[0] < e.sensor.getMaximumRange();
                    boolean nowDark = lux[0] < 1.5f || covered[0];
                    if (nowDark == inDark) {
                        if (darkChange != null) { MAIN.removeCallbacks(darkChange); darkChange = null; }
                        return;
                    }
                    if (darkChange != null) return;
                    darkChange = () -> {
                        darkChange = null;
                        inDark = nowDark;
                        // Never bring the AOD back over a screen that is already on.
                        if (screenOn(context)) { inDark = false; stopDarkness(); return; }
                        try {
                            if (nowDark) hide.invoke(controller); else show.invoke(controller, true, true);
                        } catch (Throwable t) {
                            android.util.Log.w("XSecurity", "AOD darkness", t);
                        }
                    };
                    MAIN.postDelayed(darkChange, 2000);
                }
                @Override public void onAccuracyChanged(android.hardware.Sensor s, int a) { }
            };
            darkSensors = sensors;
            darkController = controller;
            if (light != null) sensors.registerListener(darkListener, light, android.hardware.SensorManager.SENSOR_DELAY_NORMAL, MAIN);
            if (near != null) sensors.registerListener(darkListener, near, android.hardware.SensorManager.SENSOR_DELAY_NORMAL, MAIN);
        } catch (Throwable t) {
            android.util.Log.w("XSecurity", "AOD darkness sensors", t);
        }
    }

    private static boolean lowBattery(int level) {
        try {
            Context context = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return false;
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) return false;
            int now = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) * 100 / Math.max(1, battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
            return now < level;
        } catch (Throwable t) {
            return false;
        }
    }
}
