package io.github.leekin2.xsecurity;

import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Hooks in Battery and performance (com.miui.powerkeeper), which sets the screen refresh rate
 * per app. On the China ROM a game that is not on Xiaomi's high-FPS list (sent by Joyose) is put
 * at 60 Hz, even when you chose 120/144 Hz in Settings.
 */
final class PowerKeeperHooks {
    private static final String DISPLAY = "com.miui.powerkeeper.statemachine.DisplayFrameSetting";
    private static final String DFPS = "com.miui.powerkeeper.statemachine.DisplayDfpsHandler";
    private static final String THERMAL = "com.miui.powerkeeper.feedbackcontrol.ThermalManager";
    private static final String FILE_UTIL = "com.miui.powerkeeper.utils.FileUtil";
    private static final String CLOUD_RECEIVER = "com.miui.powerkeeper.cloudcontrol.CloudUpdateReceiver";
    private static final String CLOUD_HIDE_MODE = "com.miui.powerkeeper.cloudcontrol.CloudUpdateHideMode";
    private static final String CLOUD_INFO = "com.miui.powerkeeper.ui.CloudInfoActivity";
    /** Battery saver writes a CPU frequency cap here; an empty value leaves the CPU uncapped. */
    private static final String LIMIT_BUF = "/sys/powersave/limit_buf";
    private static final String THERMAL_LIMIT = "thermal_limit_refresh_rate";
    /** PowerKeeper's SchedAssi lifts surfaceflinger and system_server scheduling on supported devices. */
    private static final String TASK_BOOST = "persist.sys.schedassi.keytaskboost";
    private final Hooks hooks;
    private final ClassLoader loader;

    PowerKeeperHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() {
        // Security fix, always on while PowerKeeper is hooked: any app could send PowerKeeper a
        // broadcast that points its cloud config download at another server (setTestFunc).
        run("PowerKeeper test hook closed", () -> {
            Method test = Class.forName(CLOUD_HIDE_MODE, false, loader).getDeclaredMethod("setTestFunc", Context.class, Intent.class);
            hooks.install(test, chain -> null);
        });
        if (Features.enabled(Features.GAME_LOCK_MAX_FPS)) run("Refresh rate changes blocked", this::noRefreshChanges);
        if (Features.enabled(Features.FPS_THERMAL_OFF)) run("Thermal refresh rate drop off", () -> {
            Method code = Class.forName(THERMAL, false, loader).getDeclaredMethod("getDisplayCtrlCode");
            hooks.constant(code, 0);
            // Clear a cap written before the switch was on, once, before the hooks below swallow
            // the write: other apps reading Settings.System then see no cap either.
            try {
                Context app = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
                if (app != null && android.provider.Settings.System.getInt(app.getContentResolver(), THERMAL_LIMIT, 0) != 0) {
                    android.provider.Settings.System.putInt(app.getContentResolver(), THERMAL_LIMIT, 0);
                }
            } catch (Throwable ignored) { }
            // A 60 Hz cap PowerKeeper stored before the switch was turned on is never cleared by
            // itself; intercept every Settings.System read/write of thermal_limit_refresh_rate.
            for (Method m : android.provider.Settings.System.class.getDeclaredMethods()) {
                String n = m.getName();
                Class<?>[] p = m.getParameterTypes();
                if ((n.equals("getInt") || n.equals("getIntForUser")) && p.length >= 2 && p[1] == String.class) {
                    hooks.install(m, chain -> THERMAL_LIMIT.equals(chain.getArg(1)) ? (Object) 0 : chain.proceed());
                } else if ((n.equals("putInt") || n.equals("putIntForUser")) && p.length >= 2 && p[1] == String.class) {
                    hooks.install(m, chain -> THERMAL_LIMIT.equals(chain.getArg(1)) ? (Object) true : chain.proceed());
                }
            }
        });
        if (Features.enabled(Features.POWERKEEPER_TASK_BOOST)) run("Key task boost on", () -> {
            // SchedAssi reads this once, so the answer must be in place before its class loads.
            Class<?> sp = Class.forName("android.os.SystemProperties");
            hooks.install(sp.getDeclaredMethod("getBoolean", String.class, boolean.class),
                    chain -> TASK_BOOST.equals(chain.getArg(0)) ? (Object) true : chain.proceed());
            try {
                hooks.install(sp.getDeclaredMethod("get", String.class),
                        chain -> TASK_BOOST.equals(chain.getArg(0)) ? "true" : chain.proceed());
            } catch (NoSuchMethodException ignored) { }
            try {
                hooks.install(sp.getDeclaredMethod("get", String.class, String.class),
                        chain -> TASK_BOOST.equals(chain.getArg(0)) ? "true" : chain.proceed());
            } catch (NoSuchMethodException ignored) { }
        });
        if (Features.enabled(Features.POWER_SAVE_NO_CPU_CAP)) run("Battery saver CPU cap off", () -> {
            Class<?> fileUtil = Class.forName(FILE_UTIL, false, loader);
            Method write = fileUtil.getDeclaredMethod("writeToFile", String.class, String.class);
            hooks.install(write, chain -> LIMIT_BUF.equals(chain.getArg(0)) ? null : chain.proceed());
            try {
                Method read = fileUtil.getDeclaredMethod("readFromFile", String.class);
                hooks.install(read, chain -> LIMIT_BUF.equals(chain.getArg(0)) ? "" : chain.proceed());
            } catch (NoSuchMethodException ignored) { }
            try {
                Class<?> psHandler = Class.forName("com.miui.powerkeeper.statemachine.PowerSaveModeHandler", false, loader);
                for (Method m : psHandler.getDeclaredMethods()) {
                    if ("prepareCpuInfo".equals(m.getName()) && m.getReturnType() == void.class) {
                        hooks.install(m, chain -> null);
                    }
                }
            } catch (Throwable ignored) { }
        });
        if (Features.enabled(Features.POWERKEEPER_MENU)) run("PowerKeeper *#*#76937#*#* menu ready", this::hiddenMenu);
        if (Features.enabled(Features.GAME_FPS) && !Features.enabled(Features.GAME_LOCK_MAX_FPS)) {
            try {
                gameRefreshRate();
                hooks.info("Game refresh rate controls ready");
            } catch (Throwable failure) {
                hooks.error("game refresh rate unavailable; stock behaviour kept", failure);
            }
        }
    }

    private interface Step { void run() throws Throwable; }

    private void run(String name, Step step) {
        try {
            step.run();
            hooks.info(name);
        } catch (Throwable failure) {
            hooks.error(name + ": unavailable; stock behaviour kept", failure);
        }
    }

    /**
     * Maximum refresh rate everywhere: besides the per-app request (see gameRefreshRate), the
     * other ways PowerKeeper lowers the rate do nothing (from kashi's FPS Limitations patcher).
     */
    private void noRefreshChanges() throws Throwable {
        Class<?> display = Class.forName(DISPLAY, false, loader);
        hooks.install(display.getDeclaredMethod("setScreenEffect", String.class, int.class, int.class), chain -> null);
        hooks.install(display.getDeclaredMethod("setScreenEffect", int.class, int.class), chain -> null);
        hooks.install(display.getDeclaredMethod("setScreenEffectInternal", int.class, int.class, String.class), chain -> null);
        Class<?> foreground = Class.forName("miui.process.ForegroundInfo", false, loader);
        hooks.install(Class.forName(DFPS, false, loader).getDeclaredMethod("onForegroundChanged", foreground), chain -> null);
    }

    /** Dialing *#*#76937#*#* opens PowerKeeper's cloud settings page on stable builds too. */
    private void hiddenMenu() throws Throwable {
        Method receive = Class.forName(CLOUD_RECEIVER, false, loader).getDeclaredMethod("onReceive", Context.class, Intent.class);
        hooks.install(receive, chain -> {
            Context context = (Context) chain.getArg(0);
            Intent intent = (Intent) chain.getArg(1);
            if (intent == null || !"android.provider.Telephony.SECRET_CODE".equals(intent.getAction())
                    || intent.getData() == null || !"76937".equals(intent.getData().getHost())) return chain.proceed();
            Intent open = new Intent().setClassName(context, CLOUD_INFO)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("caller", "contacts");
            context.startActivity(open);
            return null;
        });
    }

    /** A game gets at least the refresh rate you chose in Settings. */
    private void gameRefreshRate() throws Throwable {
        Class<?> display = Class.forName(DISPLAY, false, loader);
        Method setScreenEffect = display.getDeclaredMethod("setScreenEffect", String.class, int.class, int.class);
        if (setScreenEffect.getReturnType() != void.class) throw new NoSuchMethodException("Unexpected display effect return type");
        Field gameCookie = display.getDeclaredField("COOKIE_GAME");
        Field userFps = display.getDeclaredField("mUserFps");
        gameCookie.setAccessible(true);
        userFps.setAccessible(true);
        hooks.install(setScreenEffect, chain -> {
            // HyperCeiler LockMaxFps suppresses PowerKeeper's display effect requests.
            if (Features.enabled(Features.GAME_LOCK_MAX_FPS)) return null;
            if (!Features.enabled(Features.GAME_FPS)) return chain.proceed();
            Object self = chain.getThisObject();
            int fps = (int) chain.getArg(1);
            int cookie = (int) chain.getArg(2);
            int chosen = userFps.getInt(self);
            if (cookie != gameCookie.getInt(self) || fps >= chosen) return chain.proceed();
            Object[] args = chain.getArgs().toArray();
            args[1] = chosen;
            return chain.proceed(args);
        });
    }
}
