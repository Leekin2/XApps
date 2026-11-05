package io.github.leekin2.xsecurity;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seconds on the status bar clock (HH:MM:SS). Idea from HyperCeiler's StatusBarClockNew
 * (AGPL-3.0, https://github.com/ReChronoRain/HyperCeiler), written anew: the clock keeps its own
 * text (12/24 hours, AM/PM, date parts) and only gets the seconds after the minutes, refreshed once
 * a second while it is on screen.
 */
final class ClockSecondsHooks {
    /** Hours and minutes, not followed by more digits or seconds. */
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}[:.]\\d{2})(?![\\d:.]\\d)");
    private final Hooks hooks;
    private final ClassLoader loader;
    private final Set<TextView> clocks = Collections.newSetFromMap(new WeakHashMap<>());
    private final Handler main = new Handler(Looper.getMainLooper());
    private Method updateTime;
    private boolean ticking;

    ClockSecondsHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() {
        if (!Features.enabled(Features.CLOCK_SECONDS)) return;
        try {
            Class<?> clock = loader.loadClass("com.android.systemui.statusbar.views.MiuiClock");
            if (!TextView.class.isAssignableFrom(clock)) throw new IllegalStateException("Unknown clock type");
            updateTime = clock.getDeclaredMethod("updateTime");
            updateTime.setAccessible(true);
            hooks.install(updateTime, chain -> {
                Object result = chain.proceed();
                TextView view = (TextView) chain.getThisObject();
                if (isStatusBarClock(view)) addSeconds(view);
                return result;
            });
            hooks.install(clock.getDeclaredMethod("onAttachedToWindow"), chain -> {
                Object result = chain.proceed();
                TextView view = (TextView) chain.getThisObject();
                if (isStatusBarClock(view)) {
                    clocks.add(view);
                    startTicking();
                }
                return result;
            });
            hooks.install(clock.getDeclaredMethod("onDetachedFromWindow"), chain -> {
                clocks.remove((TextView) chain.getThisObject());
                return chain.proceed();
            });
            hooks.info("Clock seconds ready");
        } catch (Throwable failure) {
            hooks.error("Clock seconds unavailable; stock clock kept", failure);
        }
    }

    /** Only the clock in the status bar, not the larger clocks in the control center. */
    private static boolean isStatusBarClock(TextView view) {
        try {
            return view.getId() != 0 && "clock".equals(view.getResources().getResourceEntryName(view.getId()));
        } catch (RuntimeException unnamed) {
            return false;
        }
    }

    private static void addSeconds(TextView view) {
        CharSequence text = view.getText();
        if (text == null) return;
        Matcher time = TIME.matcher(text);
        if (!time.find()) return;
        String seconds = String.format(java.util.Locale.ROOT, ":%02d", (System.currentTimeMillis() / 1000) % 60);
        view.setText(new StringBuilder(text).insert(time.end(1), seconds));
    }

    private void startTicking() {
        if (ticking) return;
        ticking = true;
        main.postDelayed(this::tick, 1000 - System.currentTimeMillis() % 1000);
    }

    /** Runs on the main thread once a second while a status bar clock is attached. */
    private void tick() {
        if (clocks.isEmpty()) {
            ticking = false;
            return;
        }
        for (TextView view : clocks.toArray(new TextView[0])) {
            if (view == null || !view.isAttachedToWindow()) continue;
            try {
                updateTime.invoke(view);
            } catch (Throwable ignored) {
                // The next second tries again; one missed update is harmless.
            }
        }
        main.postDelayed(this::tick, 1000 - System.currentTimeMillis() % 1000);
    }
}
