package io.github.leekin2.xnotify;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Optional presentation changes for the HyperOS notification shade. */
final class ShadeOptions {
    private ShadeOptions() {}

    static void roundClear(XposedInterface xposed, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> controller = Class.forName("com.android.systemui.shade.NotificationDismissViewController", false, loader);
        Field dismissView = controller.getField("dismissView");
        Method init = controller.getDeclaredMethod("onInit");
        xposed.hook(init).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Object result = chain.proceed();
            View view = (View) dismissView.get(chain.getThisObject());
            view.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline outline) {
                    int width = v.getWidth(), height = v.getHeight();
                    int size = Math.min(width, height);
                    int x = (width - size) / 2, y = (height - size) / 2;
                    if (size > 0) outline.setOval(x, y, x + size, y + size);
                    else outline.setEmpty();
                }
            });
            view.setClipToOutline(true);
            return result;
        });
    }

    static void carrier(XposedInterface xposed, ClassLoader loader, String label) throws ReflectiveOperationException {
        Class<?> controller = Class.forName("com.android.systemui.statusbar.policy.MiuiCarrierTextController", false, loader);
        Method changed = controller.getMethod("onCustomCarrierChanged", String[].class);
        xposed.hook(changed).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            String[] nativeNames = (String[]) chain.getArg(0);
            if (nativeNames == null || nativeNames.length == 0) return chain.proceed();
            String[] names = new String[nativeNames.length];
            Arrays.fill(names, label);
            // HyperOS still decides which SIMs exist and when to show no service or emergency.
            return chain.proceed(new Object[]{names});
        });
    }
}
