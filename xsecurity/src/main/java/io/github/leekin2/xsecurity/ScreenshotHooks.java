package io.github.leekin2.xsecurity;

import android.graphics.Bitmap;
import java.io.OutputStream;
import java.lang.reflect.Method;

/**
 * Hooks for HyperOS Screenshot (com.miui.screenshot):
 * - Maximum screenshot quality (100%): upgrades Bitmap.compress JPEG quality and DraftManager's
 *   getCompressQuality to 100 so raw and edited screenshots stay crisp.
 * - No minimum crop size limit: relaxes ScreenCropView$ResizeDetector.calculateMinSize() so you
 *   can crop screenshots as small as you want (same target as HyperCeiler / Kashi Screenshot mod).
 */
final class ScreenshotHooks {
    private static final String DRAFT_MANAGER = "com.miui.gallery.editor.photo.app.DraftManager";
    private static final String RESIZE_DETECTOR = "com.miui.gallery.editor.photo.screen.crop.ScreenCropView$ResizeDetector";

    private final Hooks hooks;
    private final ClassLoader loader;

    ScreenshotHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() {
        if (Features.enabled(Features.SCREENSHOT_MAX_QUALITY)) {
            try {
                Method compress = Bitmap.class.getDeclaredMethod("compress", Bitmap.CompressFormat.class, int.class, OutputStream.class);
                hooks.install(compress, chain -> {
                    Object format = chain.getArg(0);
                    int quality = (int) chain.getArg(1);
                    if (format == Bitmap.CompressFormat.JPEG && quality < 100) {
                        Object[] args = chain.getArgs().toArray();
                        args[1] = 100;
                        return chain.proceed(args);
                    }
                    return chain.proceed();
                });
                try {
                    Class<?> dm = Class.forName(DRAFT_MANAGER, false, loader);
                    Method q = dm.getDeclaredMethod("getCompressQuality", int.class, int.class);
                    hooks.constant(q, 100);
                } catch (Throwable ignored) { }
                hooks.info("Screenshot maximum quality (100%) ready");
            } catch (Throwable failure) {
                hooks.error("Screenshot quality hook unavailable", failure);
            }
        }
        if (Features.enabled(Features.SCREENSHOT_NO_CROP_LIMIT)) {
            try {
                Class<?> detector = Class.forName(RESIZE_DETECTOR, false, loader);
                Method minSize = detector.getDeclaredMethod("calculateMinSize");
                if (minSize.getReturnType() != int.class) throw new NoSuchMethodException("Unexpected calculateMinSize return type");
                hooks.constant(minSize, 1);
                hooks.info("Screenshot crop limit removed");
            } catch (Throwable failure) {
                hooks.error("Screenshot crop limit hook unavailable", failure);
            }
        }
    }
}
