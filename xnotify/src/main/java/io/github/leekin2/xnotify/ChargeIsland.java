package io.github.leekin2.xnotify;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * HyperOS's charging island shows the charger's rated maximum ("120W MAX"), and only above 50 W;
 * otherwise just "Charging". Show the power actually going into the battery instead, live, every
 * second while the island is up, and keep a wired island up for 10 seconds instead of 5.
 */
final class ChargeIsland {
    private static final String TAG = "XNotify";
    private static final String LISTENER = "com.android.systemui.devicenotification.listener.DeviceNotificationListenerImpl";
    private static final long WIRED_MS = 10_000, WIRELESS_MS = 15_000, TICK_MS = 1_000;

    private final XposedInterface xposed;
    private final Handler main = new Handler(Looper.getMainLooper());

    private Field context, showing, animator, leftField, textParamsField, textField, turnAnimField;
    private Method model, bundle, handle;
    // The last island HyperOS built: its owner, battery text, mode and status.
    private Object owner, status;
    private String percent;
    private int mode;
    private boolean wireless;
    private long shownAt;
    private boolean ticking;

    ChargeIsland(XposedInterface xposed) {
        this.xposed = xposed;
    }

    void install(ClassLoader loader) throws Exception {
        Class<?> listener = Class.forName(LISTENER, false, loader);
        Class<?> statusType = Class.forName("com.miui.systemui.charge.BatteryStatus", false, loader);
        model = listener.getDeclaredMethod("structModelForCharge", String.class, int.class, statusType);
        bundle = listener.getDeclaredMethod("structBundleForCharge", boolean.class);
        Class<?> modelType = model.getReturnType();
        handle = listener.getDeclaredMethod("handleDeviceNotification", Bundle.class, modelType);
        context = field(listener, "context");
        showing = field(listener, "chargeIslandShowing");
        animator = field(listener, "valueAnimator");
        leftField = field(modelType, "left");
        textParamsField = field(leftField.getType(), "textParams");
        textField = field(textParamsField.getType(), "text");
        try { turnAnimField = field(textParamsField.getType(), "turnAnim"); } catch (NoSuchFieldException ignored) { }
        model.setAccessible(true);
        bundle.setAccessible(true);
        handle.setAccessible(true);

        xposed.hook(model).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Object built = chain.proceed();
            if (built == null) return null;
            owner = chain.getThisObject();
            percent = (String) chain.getArg(0);
            mode = (int) chain.getArg(1);
            status = chain.getArg(2);
            String power = power((Context) context.get(owner));
            if (power != null) {
                Object left = leftField.get(built);
                Object params = left == null ? null : textParamsField.get(left);
                if (params != null) {
                    textField.set(params, power);
                    // The flip animation would blank the number on every one-second update.
                    if (turnAnimField != null) turnAnimField.set(params, Boolean.FALSE);
                }
            }
            return built;
        });

        xposed.hook(bundle).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Bundle built = (Bundle) chain.proceed();
            wireless = (boolean) chain.getArg(0);
            if (built != null) built.putLong("duration", wireless ? WIRELESS_MS : WIRED_MS);
            return built;
        });

        xposed.hook(handle).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Object args = chain.getArg(0);
            if (!(args instanceof Bundle) || !"charge".equals(((Bundle) args).getString("notifyId"))) return chain.proceed();
            Object self = chain.getThisObject();
            boolean wasShowing = (boolean) showing.get(self);
            long total = wireless ? WIRELESS_MS : WIRED_MS;
            if (wasShowing && shownAt > 0) {
                // HyperOS refreshes a showing island on every battery change and restarts its
                // timer each time: give it only what is left, so the island still leaves on time.
                long remaining = total - (SystemClock.uptimeMillis() - shownAt);
                ((Bundle) args).putLong("duration", Math.max(300, remaining));
            }
            Object result = chain.proceed();
            // HyperOS may call twice before it marks the island as showing: one island, one start.
            long now = SystemClock.uptimeMillis();
            if (!wasShowing && (shownAt == 0 || now - shownAt > total)) {
                shownAt = now;
                if (!ticking) {
                    ticking = true;
                    main.postDelayed(this::tick, TICK_MS);
                }
            }
            return result;
        });
    }

    /** Refreshes the power while the island is up, within its own display time. */
    private void tick() {
        try {
            long remaining = (wireless ? WIRELESS_MS : WIRED_MS) - (SystemClock.uptimeMillis() - shownAt);
            if (owner == null || !(boolean) showing.get(owner) || remaining < TICK_MS) {
                ticking = false;
                return;
            }
            // HyperOS's own percentage animation refreshes the island itself while it runs.
            if (animator.get(owner) == null && percent != null && status != null) {
                Object refreshed = model.invoke(owner, percent, mode, status);
                if (refreshed != null) {
                    // Each update restarts HyperOS's timer: give it only the time that is left.
                    Bundle args = (Bundle) bundle.invoke(null, wireless);
                    args.putLong("duration", remaining);
                    handle.invoke(owner, args, refreshed);
                }
            }
            main.postDelayed(this::tick, TICK_MS);
        } catch (Throwable t) {
            ticking = false;
            Log.w(TAG, "Charge island: live power stopped", t);
        }
    }

    /** Power into the battery as "27.4 W", or null when it cannot be read or is too small to mean much. */
    static String power(Context context) {
        if (context == null) return null;
        try {
            BatteryManager battery = context.getSystemService(BatteryManager.class);
            long microAmps = Math.abs((long) battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
            Intent sticky = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int milliVolts = sticky == null ? 0 : sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            if (microAmps <= 0 || microAmps == Integer.MIN_VALUE || milliVolts <= 0) return null;
            // Some kernels report milliamps; a phone never charges at under 20 mA while showing this.
            if (microAmps < 20_000) microAmps *= 1000;
            double watts = microAmps / 1e6 * milliVolts / 1e3;
            if (watts < 0.5 || watts > 300) return null;
            return String.format(Locale.US, watts >= 10 ? "%.0f W" : "%.1f W", watts);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
