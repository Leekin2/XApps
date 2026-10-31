package io.github.leekin2.xgoogle;

import java.lang.reflect.Method;

final class SignalModelHooks {
    static void install(HookInstaller hooks, ClassLoader loader) {
        if (!FeatureConfig.enabled(FeaturePolicy.HEY_GOOGLE)) return;
        try {
            Class<?> helper = Class.forName("com.android.server.soundtrigger.SoundTriggerHelper", false, loader);
            Class<?> model = Class.forName("android.hardware.soundtrigger.SoundTrigger$KeyphraseSoundModel", false, loader);
            Class<?> phrases = Class.forName("[Landroid.hardware.soundtrigger.SoundTrigger$Keyphrase;", false, loader);
            Method data = model.getMethod("getData");
            Method uuid = model.getMethod("getUuid");
            Method vendor = model.getMethod("getVendorUuid");
            Method version = model.getMethod("getVersion");
            Method keyphrases = model.getMethod("getKeyphrases");
            var constructor = model.getConstructor(java.util.UUID.class, java.util.UUID.class,
                    byte[].class, phrases, int.class);
            Method start = helper.getDeclaredMethod("startKeyphraseRecognition", int.class, model,
                    Class.forName("android.hardware.soundtrigger.IRecognitionStatusCallback", false, loader),
                    Class.forName("android.hardware.soundtrigger.SoundTrigger$RecognitionConfig", false, loader), boolean.class);
            hooks.install(start, chain -> {
                if (!FeatureConfig.enabled(FeaturePolicy.HEY_GOOGLE)) return chain.proceed();
                Object source = chain.getArg(1);
                Object replacement = null;
                if (Integer.valueOf(101).equals(chain.getArg(0)) && source != null) {
                    try {
                        if ("7038ddc8-30f2-11e6-b0ac-40a8f03d3f15".equals(String.valueOf(vendor.invoke(source)))) {
                            byte[] original = (byte[]) data.invoke(source);
                            byte[] adapted = SignalModelAdapter.adapt(original);
                            replacement = constructor.newInstance(uuid.invoke(source), vendor.invoke(source),
                                    adapted, keyphrases.invoke(source), version.invoke(source));
                            hooks.info("SIGNAL_MODEL adapted in memory id=101 bytes=" + original.length + "->" + adapted.length);
                        }
                    } catch (Exception failure) {
                        hooks.error("SIGNAL_MODEL unchanged: adaptation rejected", failure);
                    }
                }
                if (replacement == null) return chain.proceed();
                Object[] arguments = chain.getArgs().toArray();
                arguments[1] = replacement;
                return chain.proceed(arguments);
            });
            hooks.info("SIGNAL_MODEL READY constructor and startKeyphraseRecognition verified; known model only");
        } catch (Throwable failure) {
            hooks.error("SIGNAL_MODEL SKIP incompatible framework", failure);
        }
    }
}
