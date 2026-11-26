package io.github.leekin2.xnotify;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The music island's right side plays a canned wave animation that ignores the song. Replace it
 * with bars that follow the music actually playing (bass to treble), in the colours HyperOS
 * already takes from the album art. Paused music rests the bars low; if the audio cannot be read,
 * HyperOS's own animation stays.
 */
final class MusicSpectrum {
    private static final String TAG = "XNotify";
    private static final String HOLDER = "miui.systemui.dynamicisland.module.IslandIconViewHolder";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final XposedInterface xposed;

    MusicSpectrum(XposedInterface xposed) {
        this.xposed = xposed;
    }

    void install(ClassLoader systemUi) throws Exception {
        SystemUiPlugin.onLoaded(xposed, systemUi, this::hookPlugin);
    }

    private void hookPlugin(ClassLoader plugin) {
        Class<?> holder;
        try {
            holder = Class.forName(HOLDER, false, plugin);
        } catch (ClassNotFoundException e) {
            return;
        }
        try {
            Method play = holder.getDeclaredMethod("playAnimation");
            Field container = field(holder, "iconContainer");
            Field lottie = field(holder, "lottieView");
            Field picInfo = field(holder, "picInfo");
            Field top = field(holder, "gradientTopColor");
            Field bottom = field(holder, "gradientBottomColor");
            xposed.hook(play).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                Object self = chain.getThisObject();
                Runnable apply = () -> {
                    try {
                        Object info = picInfo.get(self);
                        String pic = info == null ? null : (String) info.getClass().getMethod("getPic").invoke(info);
                        apply((ViewGroup) container.get(self), (View) lottie.get(self), pic, top, bottom);
                    } catch (Throwable t) {
                        Log.w(TAG, "Music island: bars not shown", t);
                    }
                };
                if (Looper.myLooper() == MAIN.getLooper()) apply.run(); else MAIN.post(apply);
                return result;
            });
            Log.i(TAG, "Music island: live spectrum ready");
        } catch (Throwable t) {
            Log.w(TAG, "Music island: HyperOS layout differs, wave animation kept", t);
        }
    }

    private static void apply(ViewGroup container, View wave, String pic, Field top, Field bottom) {
        if (container == null) return;
        Bars existing = null;
        for (int i = 0; i < container.getChildCount(); i++) {
            if (container.getChildAt(i) instanceof Bars) existing = (Bars) container.getChildAt(i);
        }
        boolean music = "musicWave".equals(pic) || "musicPause".equals(pic);
        if (!music || wave == null || wave.getParent() != container) {
            if (existing != null) existing.detach();
            return;
        }
        if (existing == null) {
            existing = new Bars(container.getContext(), top, bottom);
            container.addView(existing, new FrameLayout.LayoutParams(wave.getLayoutParams()));
        }
        existing.bind(wave, "musicWave".equals(pic));
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** One shared capture of what the phone is playing; FFT only, never the sound itself. */
    private static final class Capture {
        private static Visualizer visualizer;
        private static final List<Bars> USERS = new ArrayList<>();
        static final float[] LEVELS = new float[Bars.COUNT];

        /** When opening the capture last failed; it is not retried on every frame. */
        private static long failedAt = -1;

        static boolean acquire(Bars bars) {
            if (visualizer == null && failedAt >= 0 && android.os.SystemClock.elapsedRealtime() - failedAt < 60_000) return false;
            if (!USERS.contains(bars)) USERS.add(bars);
            if (visualizer != null) return true;
            try {
                Visualizer v = new Visualizer(0);
                int[] range = Visualizer.getCaptureSizeRange();
                v.setCaptureSize(Math.min(1024, range[1]));
                v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
                int captureRate = Math.max(4000, Visualizer.getMaxCaptureRate() / 2);
                v.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                    @Override public void onWaveFormDataCapture(Visualizer vis, byte[] wave, int samplingRate) { }
                    @Override public void onFftDataCapture(Visualizer vis, byte[] fft, int samplingRate) { analyse(fft, samplingRate); }
                }, captureRate, false, true);
                v.setEnabled(true);
                visualizer = v;
                failedAt = -1;
                return true;
            } catch (Throwable t) {
                if (failedAt < 0) Log.w(TAG, "Music island: audio levels unavailable", t);
                failedAt = android.os.SystemClock.elapsedRealtime();
                USERS.remove(bars);
                return false;
            }
        }

        static void release(Bars bars) {
            USERS.remove(bars);
            if (USERS.isEmpty() && visualizer != null) {
                try { visualizer.setEnabled(false); } catch (Throwable ignored) { }
                try { visualizer.release(); } catch (Throwable ignored) { }
                visualizer = null;
                java.util.Arrays.fill(LEVELS, 0f);
            }
        }

        /** Bass, low-mid, high-mid and treble, each as 0..1 on a log scale. */
        private static void analyse(byte[] fft, int samplingRate) {
            int bins = fft.length / 2;
            if (bins < 8) return;
            float nyquist = samplingRate / 2000f; // Hz
            float[] edges = {40, 250, 1000, 4000, 14000};
            for (int band = 0; band < Bars.COUNT; band++) {
                int from = Math.max(1, (int) (edges[band] / nyquist * bins));
                int to = Math.max(from + 1, Math.min(bins - 1, (int) (edges[band + 1] / nyquist * bins)));
                float peak = 0;
                for (int k = from; k < to; k++) {
                    float re = fft[2 * k], im = fft[2 * k + 1];
                    peak = Math.max(peak, (float) Math.hypot(re, im));
                }
                // About 48 dB of range above the noise floor.
                double db = 20 * Math.log10(Math.max(1, peak));
                LEVELS[band] = (float) Math.max(0, Math.min(1, (db - 6) / 36));
            }
        }
    }

    /** The bars drawn in place of HyperOS's wave animation. */
    private static final class Bars extends View {
        static final int COUNT = 4;
        /** ~20 FPS: matches the FFT rate and avoids pinning SystemUI / LTPO display to 120 Hz. */
        private static final long FRAME_MS = 50L;
        private final Field top, bottom;
        private final android.os.PowerManager power;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] shown = new float[COUNT];
        private final Runnable nextFrame = this::invalidate;
        private View wave;
        private boolean playing, capturing;
        private int gradientTop, gradientBottom;

        Bars(Context context, Field top, Field bottom) {
            super(context);
            this.top = top;
            this.bottom = bottom;
            this.power = context.getSystemService(android.os.PowerManager.class);
        }

        private boolean screenOn() {
            return power == null || power.isInteractive();
        }

        void bind(View wave, boolean playing) {
            this.wave = wave;
            this.playing = playing;
            wave.setAlpha(0f);
            if (playing && isAttachedToWindow() && isShown() && screenOn() && !capturing) capturing = Capture.acquire(this);
            if (playing && !capturing && isAttachedToWindow() && isShown() && screenOn()) {
                // No audio levels: give the island its own animation back.
                detach();
                return;
            }
            removeCallbacks(nextFrame);
            invalidate();
        }

        void detach() {
            removeCallbacks(nextFrame);
            if (wave != null) wave.setAlpha(1f);
            if (capturing) Capture.release(this);
            capturing = false;
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) parent.removeView(this);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (playing && isShown() && screenOn() && !capturing) capturing = Capture.acquire(this);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(nextFrame);
            if (capturing) Capture.release(this);
            capturing = false;
            if (wave != null) wave.setAlpha(1f);
        }

        /** Screen off or island hidden: the capture is let go until the bars show again. */
        @Override public void onVisibilityAggregated(boolean visible) {
            super.onVisibilityAggregated(visible);
            if (!visible) {
                removeCallbacks(nextFrame);
                if (capturing) { Capture.release(this); capturing = false; }
            } else {
                invalidate();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            // Follow the wave animation's own visibility, so the bars never outlive it.
            if (wave == null || wave.getParent() != getParent() || wave.getVisibility() != VISIBLE || !isShown() || !screenOn()) {
                removeCallbacks(nextFrame);
                if (capturing) { Capture.release(this); capturing = false; }
                return;
            }
            if (playing && !capturing) capturing = Capture.acquire(this);
            boolean moving = false;
            for (int i = 0; i < COUNT; i++) {
                float target = playing ? 0.12f + 0.88f * Capture.LEVELS[i] : 0.12f;
                float rate = target > shown[i] ? 0.65f : 0.22f;
                shown[i] += (target - shown[i]) * rate;
                if (Math.abs(target - shown[i]) > 0.005f) moving = true;
            }
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            updatePaint(h);
            float gap = w * 0.12f;
            float bar = (w - gap * (COUNT + 1)) / COUNT;
            float radius = bar / 2;
            for (int i = 0; i < COUNT; i++) {
                float height = Math.max(bar, h * 0.8f * shown[i]);
                float left = gap + i * (bar + gap);
                float topY = (h - height) / 2;
                canvas.drawRoundRect(left, topY, left + bar, topY + height, radius, radius, paint);
            }
            if (playing || moving) {
                removeCallbacks(nextFrame);
                postDelayed(nextFrame, FRAME_MS);
            }
        }

        private void updatePaint(int h) {
            int t = -1, b = -1;
            try { t = top.getInt(null); b = bottom.getInt(null); } catch (Throwable ignored) { }
            if (t == -1 || b == -1) {
                t = 0xFFFFFFFF;
                b = 0xFFFFFFFF;
            }
            if (t != gradientTop || b != gradientBottom || paint.getShader() == null) {
                gradientTop = t;
                gradientBottom = b;
                paint.setShader(new LinearGradient(0, 0, 0, h, t, b, Shader.TileMode.CLAMP));
            }
        }
    }
}
