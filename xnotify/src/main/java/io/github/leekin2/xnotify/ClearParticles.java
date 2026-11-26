package io.github.leekin2.xnotify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.animation.LinearInterpolator;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * "Clear all" erodes each notification card from right to left into fine white dust that drifts
 * up and away, one card after another from the top, then System UI clears them as usual. Only
 * the look changes: System UI's own dismiss-all runs afterwards with the same arguments.
 */
final class ClearParticles {
    private static final String SB = "com.android.systemui.statusbar.";
    /** One card's erosion, the delay between cards, and how long a grain of dust lives. */
    private static final long SWEEP = 700, STAGGER_MAX = 330, STAGGER_SPAN = 700, LIFE_MAX = 900;
    /** Grains per card and in total, so a long list stays smooth. */
    private static final int PER_CARD = 6500, TOTAL = 16000;
    /** Alpha levels the dust is drawn in, one batched draw call each. */
    private static final int LEVELS = 8;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Cards being eroded → how much of their width is still shown (pixels from the left). */
    private static final Map<View, Integer> SHOWN = new WeakHashMap<>();
    private static boolean running;
    /** The row's own clip update, to give a kept card its full clip back. */
    private static Method rowClip;

    private final XposedInterface xposed;

    ClearParticles(XposedInterface xposed) {
        this.xposed = xposed;
    }

    private static boolean clipHooked;
    /** How long the erosion front takes to cross a card: 700 ms for clear all, 500 ms for one card. */
    private static long sweep = SWEEP;
    private static Method actualHeightOf;

    /**
     * One card turns to dust (the swipe menu's delete), then finish runs (System UI's own
     * dismiss, the card already invisible). False when the effect could not start.
     */
    static boolean dissolve(View row, float contentOffset, Runnable finish) {
        if (running || !(row.getParent() instanceof ViewGroup)) return false;
        List<View> rows = new ArrayList<>();
        rows.add(row);
        sweep = 500;
        return Effect.start((ViewGroup) row.getParent(), rows, actualHeightOf, contentOffset, () -> {
            row.setTransitionAlpha(0f);
            try { finish.run(); } catch (Throwable t) { Log.w("XNotify", "Dismiss after dust failed", t); }
            MAIN.postDelayed(() -> restore(rows), 1_000);
        });
    }

    /** Keeps an eroded card cut off while the dust runs (the row resets its clip on layout). */
    void installClip(ClassLoader loader) throws Exception {
        if (clipHooked) return;
        Class<?> row = Class.forName(SB + "notification.row.ExpandableNotificationRow", false, loader);
        Method updateClipping = row.getDeclaredMethod("updateClipping");
        updateClipping.setAccessible(true);
        rowClip = updateClipping;
        actualHeightOf = findActualHeight(row);
        xposed.hook(updateClipping).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Object result = chain.proceed();
            View card = (View) chain.getThisObject();
            Integer shown = SHOWN.get(card);
            if (shown != null) applyClip(card, shown);
            return result;
        });
        clipHooked = true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void install(ClassLoader loader) throws Exception {
        Class<?> layout = Class.forName(SB + "notification.stack.NotificationStackScrollLayout", false, loader);
        Class<?> row = Class.forName(SB + "notification.row.ExpandableNotificationRow", false, loader);
        Method dismissAll = layout.getDeclaredMethod("performDismissAllAnimations", ArrayList.class, boolean.class, Consumer.class);
        installClip(loader);
        Method actualHeight = findActualHeight(row);
        XposedInterface.Invoker original = xposed.getInvoker(dismissAll).setType(XposedInterface.Invoker.Type.ORIGIN);

        xposed.hook(dismissAll).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            if (running) return chain.proceed();
            ViewGroup stack = (ViewGroup) chain.getThisObject();
            List<View> rows = new ArrayList<>();
            Object list = chain.getArg(0);
            if (list instanceof List) {
                for (Object view : (List<?>) list) {
                    if (row.isInstance(view) && ((View) view).isAttachedToWindow() && ((View) view).getWidth() > 0
                            && ((View) view).getVisibility() == View.VISIBLE) rows.add((View) view);
                }
            }
            if (rows.isEmpty()) return chain.proceed();
            // Top card first, as on screen.
            rows.sort((a, b) -> Float.compare(a.getY(), b.getY()));
            Object[] args = chain.getArgs().toArray();
            Consumer<Object> done = (Consumer<Object>) args[2];
            // System UI's own dismiss-all (slide-out, removal, closing the shade) runs once the
            // cards are gone; they are invisible by then, so only the end result shows.
            Runnable finish = () -> {
                for (View card : rows) card.setTransitionAlpha(0f);
                if (done != null) {
                    args[2] = (Consumer<Object>) value -> {
                        done.accept(value);
                        restore(rows);
                    };
                } else {
                    MAIN.postDelayed(() -> restore(rows), 1_000);
                }
                try {
                    original.invoke(stack, args);
                } catch (Throwable t) {
                    restore(rows);
                    if (done != null) done.accept(Boolean.TRUE);
                }
            };
            sweep = SWEEP;
            if (!Effect.start(stack, rows, actualHeight, 0f, finish)) return chain.proceed();
            return null;
        });
    }

    private static void restore(List<View> rows) {
        for (View card : rows) {
            if (SHOWN.remove(card) != null && rowClip != null) {
                try { rowClip.invoke(card); } catch (Throwable ignored) { card.setClipBounds(null); }
            }
            card.setTransitionAlpha(1f);
        }
    }

    private static void applyClip(View card, int shown) {
        Rect clip = card.getClipBounds();
        if (clip == null) clip = new Rect(0, 0, card.getWidth(), card.getHeight());
        clip.right = Math.min(clip.right, Math.max(0, shown));
        card.setClipBounds(clip);
    }

    private static Method findActualHeight(Class<?> row) {
        for (Class<?> c = row; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("getActualHeight");
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /** The erosion front on each card and the dust it leaves, drawn on the shade window's overlay. */
    private static final class Effect extends Drawable {
        private final Paint paint = new Paint();
        private final List<View> cards;
        private final int[] cardWidth;
        private final long[] cardStart;
        // Per grain: card, origin, the moment the front reaches it, velocity, life, size class, brightness.
        private final int[] card;
        private final float[] x, y, born, vx, vy, life, alpha;
        private final boolean[] small;
        private final int count;
        private final float dot;
        private final long total;
        private final float[][] points = new float[LEVELS * 2][];
        private final int[] used = new int[LEVELS * 2];
        private long now;

        private Effect(List<View> cards, int[] cardWidth, long[] cardStart, int[] card, float[] x, float[] y,
                float[] born, float[] vx, float[] vy, float[] life, float[] alpha, boolean[] small, int count, float dot, long total) {
            this.cards = cards; this.cardWidth = cardWidth; this.cardStart = cardStart; this.card = card;
            this.x = x; this.y = y; this.born = born; this.vx = vx; this.vy = vy; this.life = life; this.alpha = alpha;
            this.small = small; this.count = count; this.dot = dot; this.total = total;
            for (int i = 0; i < points.length; i++) points[i] = new float[count * 2];
            paint.setColor(0xFFFFFFFF);
            paint.setStrokeCap(Paint.Cap.SQUARE);
            paint.setAntiAlias(false);
        }

        static boolean start(ViewGroup stack, List<View> rows, Method actualHeight, float contentOffset, Runnable finish) {
            try {
                if (!(stack.getRootView() instanceof ViewGroup)) return false;
                ViewGroup root = (ViewGroup) stack.getRootView();
                float density = stack.getResources().getDisplayMetrics().density;
                int[] rootAt = new int[2];
                root.getLocationInWindow(rootAt);
                int n = rows.size();
                long stagger = n <= 1 ? 0 : Math.min(STAGGER_MAX, STAGGER_SPAN / (n - 1));
                int perCard = Math.max(400, Math.min(PER_CARD, TOTAL / n));
                int[] card = new int[TOTAL];
                float[] x = new float[TOTAL], y = new float[TOTAL], born = new float[TOTAL], vx = new float[TOTAL];
                float[] vy = new float[TOTAL], life = new float[TOTAL], alpha = new float[TOTAL];
                boolean[] small = new boolean[TOTAL];
                int[] widths = new int[n];
                long[] starts = new long[n];
                Random random = new Random();
                float radius = 20 * density;
                int count = 0;
                for (int c = 0; c < n; c++) {
                    View view = rows.get(c);
                    int w = view.getWidth();
                    int h = view.getHeight();
                    if (actualHeight != null) {
                        try { h = Math.min(h, (int) actualHeight.invoke(view)); } catch (Throwable ignored) { }
                    }
                    Rect clip = view.getClipBounds();
                    int top = clip == null ? 0 : Math.max(0, clip.top);
                    if (clip != null) h = Math.min(h, clip.bottom);
                    starts[c] = c * stagger;
                    int[] at = new int[2];
                    view.getLocationInWindow(at);
                    float left = at[0] - rootAt[0], originTop = at[1] - rootAt[1];
                    int right = Math.min(w, Math.round(w + contentOffset));
                    if (clip != null) right = Math.min(right, clip.right);
                    int visibleLeft = Math.max(0, Math.max(Math.round(-left), clip == null ? 0 : clip.left));
                    int visibleWidth = right - visibleLeft;
                    widths[c] = right;
                    if (visibleWidth <= 0 || h <= top) continue;
                    for (int k = 0; k < perCard && count < TOTAL; k++) {
                        float gx = visibleLeft + random.nextFloat() * visibleWidth, gy = top + random.nextFloat() * (h - top);
                        if (outsideCorner(gx, gy - top, w, h - top, radius)) continue;
                        card[count] = c;
                        x[count] = left + gx;
                        y[count] = originTop + gy;
                        // The front sweeps right to left; a little jitter keeps its edge ragged.
                        float front = (right - gx) / Math.max(1f, right);
                        born[count] = starts[c] + sweep * Math.max(0f, Math.min(1f, front + 0.12f * (random.nextFloat() - 0.5f)));
                        // Blown up and to the left, some grains drifting back right; they slow down as they go.
                        vx[count] = density * (40 - 300 * random.nextFloat());
                        vy[count] = -density * (40 + 260 * random.nextFloat());
                        life[count] = 450 + (LIFE_MAX - 450) * random.nextFloat();
                        alpha[count] = 0.55f + 0.45f * random.nextFloat();
                        small[count] = random.nextFloat() < 0.6f;
                        count++;
                    }
                }
                if (count == 0) return false;
                long total = starts[n - 1] + sweep + LIFE_MAX;
                // System UI's own clear starts once the last card is gone; the dust fades meanwhile.
                long handOver = starts[n - 1] + sweep + 60;
                Effect effect = new Effect(rows, widths, starts, card, x, y, born, vx, vy, life, alpha, small, count, density, total);
                effect.setBounds(0, 0, root.getWidth(), root.getHeight());
                ViewGroupOverlay overlay = root.getOverlay();
                overlay.add(effect);
                running = true;
                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f).setDuration(total);
                animator.setInterpolator(new LinearInterpolator());
                boolean[] handed = {false};
                Runnable handOff = () -> {
                    if (handed[0]) return;
                    handed[0] = true;
                    running = false;
                    finish.run();
                };
                animator.addUpdateListener(a -> {
                    effect.now = a.getCurrentPlayTime();
                    // Once handed over, the cards are System UI's again.
                    if (!handed[0]) effect.updateCards();
                    effect.invalidateSelf();
                    if (effect.now >= handOver) handOff.run();
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        overlay.remove(effect);
                        handOff.run();
                    }
                });
                animator.start();
                return true;
            } catch (Throwable t) {
                running = false;
                restore(rows);
                return false;
            }
        }

        /** Cuts each card back to the part the front has not reached yet. */
        private void updateCards() {
            for (int c = 0; c < cards.size(); c++) {
                View view = cards.get(c);
                float progress = (now - cardStart[c]) / (float) sweep;
                if (progress <= 0f) continue;
                int shown = progress >= 1f ? 0 : Math.round(cardWidth[c] * (1f - progress));
                Integer before = SHOWN.put(view, shown);
                if (before == null || before != shown) applyClip(view, shown);
                if (shown == 0) view.setTransitionAlpha(0f);
            }
        }

        private static boolean outsideCorner(float cx, float cy, int w, int h, float r) {
            float dx = cx < r ? r - cx : cx > w - r ? cx - (w - r) : 0;
            float dy = cy < r ? r - cy : cy > h - r ? cy - (h - r) : 0;
            return dx * dx + dy * dy > r * r;
        }

        @Override
        public void draw(Canvas canvas) {
            java.util.Arrays.fill(used, 0);
            for (int i = 0; i < count; i++) {
                float age = now - born[i];
                if (age < 0f || age >= life[i]) continue;
                float t = age / 1000f;
                float k = age / life[i];
                // Drag: the distance grows quickly at first and then levels off.
                // A soft start: grains hang at the edge for a moment before they are blown away.
                float eased = t * t / (t + 0.1f);
                float travel = (1f - (float) Math.exp(-2.2f * eased)) / 2.2f;
                float px = x[i] + vx[i] * travel;
                float py = y[i] + vy[i] * travel;
                float a = alpha[i] * (1f - k * k);
                int level = Math.min(LEVELS - 1, (int) (a * LEVELS));
                if (level <= 0) continue;
                int bucket = level * 2 + (small[i] ? 1 : 0);
                float[] p = points[bucket];
                int u = used[bucket];
                p[u] = px;
                p[u + 1] = py;
                used[bucket] = u + 2;
            }
            for (int level = 1; level < LEVELS; level++) {
                paint.setAlpha((255 * level + 255) / LEVELS);
                for (int s = 0; s < 2; s++) {
                    int bucket = level * 2 + s;
                    if (used[bucket] == 0) continue;
                    paint.setStrokeWidth(s == 1 ? 0.9f * dot : 1.4f * dot);
                    canvas.drawPoints(points[bucket], 0, used[bucket], paint);
                }
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
