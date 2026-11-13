package io.github.leekin2.xsecurity;

import android.app.Activity;
import android.content.Context;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.json.JSONObject;

/** Adapted from HyperCeiler's DisableGameBoosterAds (AGPL-3.0). */
final class GameCenterHooks {
    static void install(Hooks hooks, ClassLoader loader) throws ReflectiveOperationException {
        Method config = loader.loadClass("com.miui.gamebooster.SysInfoUtil")
                .getDeclaredMethod("getConfigrationJson", Context.class);
        Method offer = loader.loadClass("com.miui.gamebooster.xunyou.XunyouNewUserDialogHelper")
                .getDeclaredMethod("showXunyouNewUserDialog", Activity.class);
        if (!Modifier.isStatic(config.getModifiers()) || config.getReturnType() != String.class
                || offer.getReturnType() != void.class) {
            throw new NoSuchMethodException("Game Center methods changed");
        }
        hooks.install(config, chain -> {
            Object result = chain.proceed();
            if (!Features.enabled(Features.GAME_CENTER_ADS_OFF) || !(result instanceof String json)) return result;
            try {
                JSONObject settings = new JSONObject(json);
                if (!settings.has("is_international")) return result;
                return settings.put("is_international", true).toString();
            } catch (org.json.JSONException malformed) {
                return result;
            }
        });
        hooks.install(offer, chain -> Features.enabled(Features.GAME_CENTER_ADS_OFF) ? null : chain.proceed());
        hooks.info("Game Center promotion rules ready");
    }

    private GameCenterHooks() { }
}
