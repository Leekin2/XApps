package io.github.leekin2.xsecurity;

import android.view.WindowManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;

/**
 * Floating windows keep working while Google's motion cues are on. The motion cues dots are a
 * full-screen TYPE_VOLUME_OVERLAY window that takes no touches, but HyperOS adds every window of
 * that type to the floating window "touch exclude" region without checking, so the whole screen
 * became excluded and the caption bar (move, close, menu) ignored every touch. Only volume
 * overlays that take no touches are left out; the volume panel itself is unchanged.
 * Analysis and fix design by xrh0905.
 */
final class FreeformMotionCuesHooks {
    private static final int TYPE_VOLUME_OVERLAY = 2020;
    private static final int FLAG_NOT_TOUCHABLE = 0x10;
    private static final int INPUT_FEATURE_NO_INPUT_CHANNEL = 0x1;
    private static final String SERVICE = "com.android.server.wm.MiuiFreeFormManagerService";
    /** Hidden in the SDK; read once. Without it only the touch flag decides. */
    private static final Field INPUT_FEATURES = inputFeaturesField();
    private final Hooks hooks;
    private final ClassLoader loader;

    FreeformMotionCuesHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() {
        try {
            Class<?> service = Class.forName(SERVICE, false, loader);
            Class<?> display = Class.forName("com.android.server.wm.DisplayContent", false, loader);
            Class<?> window = Class.forName("com.android.server.wm.WindowState", false, loader);
            // Every target first: nothing is hooked when one of them is missing.
            Method statusBar = service.getDeclaredMethod("excludeStatusBarWindowType", WindowManager.LayoutParams.class, boolean.class);
            Method tapOut = service.getDeclaredMethod("excludeWindowTypeFromTapOutTask", WindowManager.LayoutParams.class, boolean.class);
            Method update = service.getDeclaredMethod("updateMiuiFreeformTouchExcludeRegion", display);
            Field statusBarWindows = display.getDeclaredField("mTapExcludedStatusBarWindows");
            Field tapWindows = display.getDeclaredField("mTapExcludedWindows");
            Field attrsField = window.getDeclaredField("mAttrs");
            statusBarWindows.setAccessible(true);
            tapWindows.setAccessible(true);
            attrsField.setAccessible(true);
            for (Method method : new Method[] {statusBar, tapOut}) {
                hooks.install(method, chain -> isUntouchableVolumeOverlay(chain.getArg(0)) ? Boolean.FALSE : chain.proceed());
            }
            // Overlays added before these hooks are taken out on the next update.
            hooks.install(update, chain -> {
                Object content = chain.getArg(0);
                if (content != null) {
                    for (Field list : new Field[] {statusBarWindows, tapWindows}) {
                        try {
                            if (list.get(content) instanceof Collection<?> windows) {
                                for (Iterator<?> it = windows.iterator(); it.hasNext(); ) {
                                    if (isUntouchableVolumeOverlay(attrsField.get(it.next()))) it.remove();
                                }
                            }
                        } catch (Throwable ignored) {
                            // The region is still built from the unchanged list.
                        }
                    }
                }
                return chain.proceed();
            });
            hooks.info("Freeform with motion cues: ready");
        } catch (Throwable failure) {
            hooks.error("Freeform with motion cues unavailable; stock behaviour kept", failure);
        }
    }

    private static boolean isUntouchableVolumeOverlay(Object value) {
        if (!(value instanceof WindowManager.LayoutParams attrs) || attrs.type != TYPE_VOLUME_OVERLAY) return false;
        return (attrs.flags & FLAG_NOT_TOUCHABLE) != 0 || (inputFeatures(attrs) & INPUT_FEATURE_NO_INPUT_CHANNEL) != 0;
    }

    private static Field inputFeaturesField() {
        try {
            Field field = WindowManager.LayoutParams.class.getDeclaredField("inputFeatures");
            field.setAccessible(true);
            return field;
        } catch (Throwable missing) {
            return null;
        }
    }

    private static int inputFeatures(WindowManager.LayoutParams attrs) {
        try {
            return INPUT_FEATURES == null ? 0 : INPUT_FEATURES.getInt(attrs);
        } catch (Throwable unreadable) {
            return 0;
        }
    }
}
