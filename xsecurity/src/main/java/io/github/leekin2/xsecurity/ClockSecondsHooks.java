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
    private java.lang.reflect.Field controllerField;
    private boolean ticking;
    /** The time the clock text was made from, so the seconds match the hours and minutes. */
    private long shownAt;

    ClockSecondsHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() {
        if (!Features.enabled(Features.CLOCK_SECONDS)) return;
        try {
            Class<?> clock = loader.loadClass("com.android.systemui.statusbar.views.MiuiClock");
            if (!TextView.class.isAssignableFrom(clock)) throw new IllegalStateException("Unknown clock type");
            // Every target is looked up first, so nothing is hooked when one of them is missing.
            updateTime = clock.getDeclaredMethod("updateTime");
            updateTime.setAccessible(true);
            Method attach = clock.getDeclaredMethod("onAttachedToWindow");
            Method detach = clock.getDeclaredMethod("onDetachedFromWindow");
            controllerField = clock.getDeclaredField("mMiuiStatusBarClockController");
            controllerField.setAccessible(true);
            hooks.install(updateTime, chain -> {
                Object result = chain.proceed();
                TextView view = (TextView) chain.getThisObject();
                if (isStatusBarClock(view)) addSeconds(view);
                return result;
            });
            hooks.install(attach, chain -> {
                Object result = chain.proceed();
                TextView view = (TextView) chain.getThisObject();
                if (isStatusBarClock(view)) {
                    clocks.add(view);
                    startTicking();
                }
                return result;
            });
            hooks.install(detach, chain -> {
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

    private void addSeconds(TextView view) {
        CharSequence text = view.getText();
        if (text == null) return;
        // A date such as "06.11" also looks like a time, before or after the real one. A time
        // with ":" wins; only without one is the last "." match taken (locales writing 14.05).
        Matcher time = TIME.matcher(text);
        int colon = -1, any = -1;
        while (time.find()) {
            any = time.end(1);
            if (time.group(1).indexOf(':') >= 0) colon = time.end(1);
        }
        int end = colon >= 0 ? colon : any;
        if (end < 0) return;
        long at = shownAt != 0 ? shownAt : System.currentTimeMillis();
        String seconds = String.format(java.util.Locale.ROOT, ":%02d", (at / 1000) % 60);
        view.setText(new StringBuilder(text).insert(end, seconds));
    }

    /** Moves the clock's calendar to now; it is otherwise moved only by the minute tick. */
    private void setClockTime(TextView view, long now) {
        try {
            Object controller = controllerField.get(view);
            if (controller == null) return;
            java.lang.reflect.Field calendarField = controller.getClass().getDeclaredField("mCalendar");
            calendarField.setAccessible(true);
            Object calendar = calendarField.get(controller);
            if (calendar != null) calendar.getClass().getMethod("setTimeInMillis", long.class).invoke(calendar, now);
        } catch (Throwable ignored) {
            // Without it the clock still shows the right minute from its own tick.
        }
    }

    private void startTicking() {
        if (ticking) return;
        ticking = true;
        main.postDelayed(this::tick, 1000 - System.currentTimeMillis() % 1000);
    }

    private static boolean screenOn(TextView view) {
        android.view.Display display = view.getDisplay();
        return display != null && display.getState() == android.view.Display.STATE_ON;
    }

    /** Runs on the main thread once a second while a status bar clock is attached and the screen is on. */
    private void tick() {
        if (clocks.isEmpty()) {
            ticking = false;
            return;
        }
        long now = System.currentTimeMillis();
        for (TextView view : clocks.toArray(new TextView[0])) {
            if (view == null || !view.isAttachedToWindow() || !screenOn(view)) continue;
            try {
                setClockTime(view, now);
                shownAt = now;
                updateTime.invoke(view);
            } catch (Throwable ignored) {
                // The next second tries again; one missed update is harmless.
            } finally {
                shownAt = 0;
            }
        }
        main.postDelayed(this::tick, 1000 - System.currentTimeMillis() % 1000);
    }
}
