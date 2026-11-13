package io.github.leekin2.xsecurity;

import android.app.Activity;
import android.content.Intent;
import android.widget.Button;
import java.lang.reflect.Method;

/** Optional one-time approval, limited to Xiaomi's validated app-to-app launch dialog.
 * Inspired by HyperCeiler RemoveOpenAppConfirmationPopup (AGPL-3.0), without a global TextView hook.
 */
final class ChainStartHooks {
    static boolean accepts(String action, int type, boolean restricted, boolean store) {
        return ("android.app.action.CHECK_ALLOW_START_ACTIVITY".equals(action)
                || "android.app.action.CHECK_ALLOW_START_ACTIVITY_PAD".equals(action))
                && type != 10034 && !restricted && !store;
    }

    static void install(Hooks hooks, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> activity = loader.loadClass("com.miui.wakepath.ui.ConfirmStartActivity");
        Class<?> dialog = loader.loadClass("miuix.appcompat.app.AlertDialog");
        Method created = activity.getDeclaredMethod("onDialogCreated", dialog);
        Method button = dialog.getMethod("getButton", int.class);
        hooks.install(created, chain -> {
            Object result = chain.proceed(); // Xiaomi performs validation and builds the dialog first.
            Activity owner = (Activity) chain.getThisObject();
            Intent intent = owner.getIntent();
            if (!Features.enabled(Features.CHAIN_START_ONCE) || intent == null
                    || !accepts(intent.getAction(), intent.getIntExtra("dialogType", -1),
                        intent.getBooleanExtra("restrictForChain", false), intent.getBooleanExtra("restrictForGetApps", false))) return result;
            Object view = button.invoke(chain.getArg(0), 3); // Inspected Xiaomi button: Allow once.
            if (!(view instanceof Button once) || once.getId() != 3) return result;
            int label = owner.getResources().getIdentifier("button_text_once_accept", "string", owner.getPackageName());
            if (label == 0 || !owner.getString(label).contentEquals(once.getText())) return result;
            once.post(() -> {
                if (Features.enabled(Features.CHAIN_START_ONCE) && !owner.isFinishing() && !owner.isDestroyed()
                        && once.isAttachedToWindow() && once.isShown() && once.isEnabled()) once.performClick();
            });
            return result;
        });
        hooks.info("App-to-app launch: one-time approval ready");
    }

    private ChainStartHooks() { }
}
