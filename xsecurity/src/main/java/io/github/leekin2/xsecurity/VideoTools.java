package io.github.leekin2.xsecurity;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Two buttons in the video toolbox, as in kashi's Security Center mod: a sleep timer that pauses
 * the video after 15/30/60/90 minutes or when this video ends, and a touch lock that ignores taps
 * until its lock icon is held. Written anew.
 */
final class VideoTools {
    private static final int SLEEP = 1, LOCK = 2;
    private static final int[] MINUTES = {0, 15, 30, 60, 90, -1};
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Object, Integer> OURS = Collections.synchronizedMap(new WeakHashMap<>());
    private static int sleepStep;
    private static long sleepAt;
    private static final Runnable PAUSE = VideoTools::pause;
    private static View lockView;
    private final Hooks hooks;
    private final ClassLoader loader;

    VideoTools(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() throws Throwable {
        Class<?> item = Class.forName("jb.h", false, loader);
        Class<?> group = Class.forName("jb.i", false, loader);
        Class<?> type = Class.forName("ib.b", false, loader);
        Class<?> list = Class.forName("hb.i", false, loader);
        Constructor<?> make = item.getDeclaredConstructor(int.class, int.class, type);
        // Any type will do: our cells are drawn and tapped by our own hooks below.
        Object anyType = type.getEnumConstants()[0];
        Method addItem = null;
        for (Method m : group.getDeclaredMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(item) && m.getReturnType() == void.class) addItem = m;
        }
        if (addItem == null) throw new NoSuchMethodException("Video toolbox group add");
        Method add = addItem;
        int hooked = 0;
        for (Method m : list.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getReturnType() != group || p.length != 3 || p[0] != Context.class || p[1] != boolean.class || p[2] != boolean.class) continue;
            hooks.install(m, chain -> {
                Object result = chain.proceed();
                if (result != null) {
                    Context ctx = (Context) chain.getArg(0);
                    int title = ctx.getResources().getIdentifier("vb_settings", "string", "com.miui.securitycenter");
                    Object sleep = make.newInstance(title, 0, anyType);
                    Object lock = make.newInstance(title, 0, anyType);
                    OURS.put(sleep, SLEEP);
                    OURS.put(lock, LOCK);
                    add.invoke(result, sleep);
                    add.invoke(result, lock);
                }
                return result;
            });
            hooked++;
        }
        if (hooked == 0) throw new NoSuchMethodException("Video toolbox quick functions");

        // Drawing a cell: (position, view). Ours get their own icon and title.
        hooks.install(item.getDeclaredMethod("h", int.class, View.class), chain -> {
            Integer which = OURS.get(chain.getThisObject());
            if (which == null) return chain.proceed();
            bind((View) chain.getArg(1), which);
            return null;
        });
        hooks.install(item.getDeclaredMethod("onClick", View.class), chain -> {
            Integer which = OURS.get(chain.getThisObject());
            if (which == null) return chain.proceed();
            View view = (View) chain.getArg(0);
            if (which == SLEEP) sleep(view.getContext()); else lock(view.getContext());
            bind(view, which);
            return null;
        });
        for (String name : new String[] {"d", "k"}) {
            try {
                hooks.install(item.getDeclaredMethod(name), chain -> {
                    if (OURS.get(chain.getThisObject()) == null) return chain.proceed();
                    return name.equals("d");
                });
            } catch (NoSuchMethodException ignored) { }
        }
    }

    /** Sleep timer label, off, "pauses in %d min", "pauses when this video ends", no length, touch lock, locked, unlocked. */
    private static String[] words() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr": return new String[] {"Uyku zamanlayıcısı", "Uyku zamanlayıcısı kapalı", "Video %d dk sonra durur", "Video bu bölüm bitince durur", "Video süresi bilinmiyor, zamanlayıcı kapalı", "Dokunma kilidi", "Dokunma kilitlendi. Açmak için kilide basılı tutun", "Dokunma kilidi açıldı"};
            case "ru": return new String[] {"Таймер сна", "Таймер сна выключен", "Видео остановится через %d мин", "Видео остановится в конце", "Длина видео неизвестна, таймер выключен", "Блокировка касаний", "Касания заблокированы. Удерживайте замок, чтобы снять", "Блокировка снята"};
            case "es": return new String[] {"Temporizador", "Temporizador apagado", "El vídeo se pausa en %d min", "El vídeo se pausa al terminar", "Duración desconocida, temporizador apagado", "Bloqueo táctil", "Toques bloqueados. Mantén el candado para desbloquear", "Toques desbloqueados"};
            case "zh": return new String[] {"睡眠定时", "睡眠定时已关闭", "视频将在 %d 分钟后暂停", "本视频结束后暂停", "无法获取视频时长，定时已关闭", "触摸锁", "已锁定触摸，长按锁图标解锁", "已解锁触摸"};
            default: return new String[] {"Sleep timer", "Sleep timer off", "Video pauses in %d min", "Video pauses when this one ends", "No video length to wait for, sleep timer off", "Touch lock", "Touch locked. Hold the lock icon to unlock", "Touch unlocked"};
        }
    }

    private static void bind(View cell, int which) {
        try {
            Object holder = cell.getTag();
            if (holder == null) return;
            String[] w = words();
            boolean on = which == SLEEP ? sleepStep != 0 : lockView != null;
            String label = which == SLEEP ? (sleepStep == 0 ? w[0] : MINUTES[sleepStep] > 0
                    ? String.format(java.util.Locale.getDefault(), "%d'", Math.max(1, (sleepAt - SystemClock.elapsedRealtime()) / 60_000 + 1)) : "▶|") : w[5];
            for (Field f : holder.getClass().getFields()) {
                Object v = f.get(holder);
                if (v instanceof TextView) { ((TextView) v).setText(label); ((TextView) v).setSelected(on); }
                if (v instanceof ImageView) {
                    ((ImageView) v).setImageDrawable(new Glyph(which == LOCK));
                    ((ImageView) v).setSelected(on);
                    ((ImageView) v).setEnabled(true);
                    ((ImageView) v).setAlpha(on ? 1f : 0.75f);
                }
            }
            cell.setEnabled(true);
        } catch (Throwable ignored) { }
    }

    // --- Sleep timer --------------------------------------------------------------------------

    private static void sleep(Context context) {
        String[] w = words();
        MAIN.removeCallbacks(PAUSE);
        sleepStep = (sleepStep + 1) % MINUTES.length;
        int minutes = MINUTES[sleepStep];
        if (minutes == 0) {
            Toast.makeText(context, w[1], Toast.LENGTH_SHORT).show();
            return;
        }
        long delay;
        if (minutes > 0) {
            delay = minutes * 60_000L;
            Toast.makeText(context, String.format(w[2], minutes), Toast.LENGTH_SHORT).show();
        } else {
            delay = remaining(context);
            if (delay <= 0) {
                sleepStep = 0;
                Toast.makeText(context, w[4], Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(context, w[3], Toast.LENGTH_SHORT).show();
        }
        sleepAt = SystemClock.elapsedRealtime() + delay;
        MAIN.postDelayed(PAUSE, delay);
    }

    /** Time left in the video playing now, from its media session. */
    private static long remaining(Context context) {
        try {
            for (MediaController c : context.getSystemService(MediaSessionManager.class).getActiveSessions(null)) {
                PlaybackState s = c.getPlaybackState();
                MediaMetadata m = c.getMetadata();
                if (s == null || m == null || s.getState() != PlaybackState.STATE_PLAYING) continue;
                long duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                long position = s.getPosition() + (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
                if (duration > 0 && duration > position) return duration - position - 1500;
            }
        } catch (Throwable ignored) { }
        return -1;
    }

    private static void pause() {
        sleepStep = 0;
        try {
            Context context = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            AudioManager audio = context.getSystemService(AudioManager.class);
            long now = SystemClock.uptimeMillis();
            audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
            audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
        } catch (Throwable t) {
            android.util.Log.w("XSecurity", "sleep timer pause", t);
        }
    }

    // --- Touch lock ---------------------------------------------------------------------------

    private static void lock(Context context) {
        String[] w = words();
        if (lockView != null) { unlock(context); return; }
        Context app = context.getApplicationContext();
        WindowManager wm = app.getSystemService(WindowManager.class);
        float dp = app.getResources().getDisplayMetrics().density;
        FrameLayout shield = new FrameLayout(app);
        shield.setOnTouchListener((v, e) -> true); // swallow every touch
        ImageView icon = new ImageView(app);
        icon.setImageDrawable(new Glyph(true));
        int size = (int) (48 * dp);
        int pad = (int) (10 * dp);
        icon.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0x66000000);
        bg.setCornerRadius(size / 2f);
        icon.setBackground(bg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.CENTER_VERTICAL | Gravity.END);
        lp.rightMargin = (int) (24 * dp);
        shield.addView(icon, lp);
        // Holding the lock for a second unlocks.
        Runnable release = () -> unlock(app);
        icon.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) { v.setAlpha(0.5f); MAIN.postDelayed(release, 1000); }
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { v.setAlpha(1f); MAIN.removeCallbacks(release); }
            return true;
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        wm.addView(shield, params);
        lockView = shield;
        Toast.makeText(app, w[6], Toast.LENGTH_LONG).show();
    }

    private static void unlock(Context context) {
        View view = lockView;
        lockView = null;
        if (view == null) return;
        try { context.getApplicationContext().getSystemService(WindowManager.class).removeView(view); } catch (Throwable ignored) { }
        Toast.makeText(context, words()[7], Toast.LENGTH_SHORT).show();
    }

    /** Moon (sleep) or padlock, in white like the toolbox icons. */
    private static final class Glyph extends Drawable {
        private final boolean lock;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Glyph(boolean lock) {
            this.lock = lock;
            paint.setColor(0xFFFFFFFF);
        }

        @Override public void draw(Canvas canvas) {
            float w = getBounds().width(), h = getBounds().height(), s = Math.min(w, h);
            float l = getBounds().left + (w - s) / 2, t = getBounds().top + (h - s) / 2;
            if (lock) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new RectF(l + s * 0.22f, t + s * 0.45f, l + s * 0.78f, t + s * 0.88f), s * 0.08f, s * 0.08f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.09f);
                canvas.drawArc(new RectF(l + s * 0.32f, t + s * 0.15f, l + s * 0.68f, t + s * 0.6f), 180, 180, false, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
                cut.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
                int save = canvas.saveLayer(l, t, l + s, t + s, null);
                canvas.drawCircle(l + s * 0.5f, t + s * 0.5f, s * 0.32f, paint);
                canvas.drawCircle(l + s * 0.65f, t + s * 0.38f, s * 0.27f, cut);
                canvas.restoreToCount(save);
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
