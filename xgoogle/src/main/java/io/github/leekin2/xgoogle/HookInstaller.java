package io.github.leekin2.xgoogle;

import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class HookInstaller {
    private static final String TAG = "AssistantBridge";
    private final XposedModule module;
    private final List<XposedInterface.HookHandle> handles = new ArrayList<>();

    HookInstaller(XposedModule module) { this.module = module; }

    int mark() { return handles.size(); }

    XposedInterface.HookHandle untracked(Executable method, XposedInterface.Hooker hooker) {
        return module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
    }

    boolean deoptimize(Executable method) { return module.deoptimize(method); }

    static Class<?> target(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    static Method booleanMethod(Class<?> type, String name) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name);
        if (method.getReturnType() != boolean.class || !Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException(type.getName() + "." + name + " incompatible signature");
        }
        return method;
    }

    void install(Executable method, XposedInterface.Hooker hooker) {
        method.setAccessible(true);
        handles.add(untracked(method, hooker));
    }

    void unhookSince(int firstHandle) {
        for (int i = handles.size() - 1; i >= firstHandle; i--) {
            try { handles.get(i).unhook(); } catch (Throwable ignored) { }
            handles.remove(i);
        }
    }

    int deoptimizeCallers(Class<?> type, String... names) {
        int count = 0;
        List<String> callers = List.of(names);
        for (Method method : type.getDeclaredMethods()) {
            if (callers.contains(method.getName()) && !Modifier.isNative(method.getModifiers())
                    && !Modifier.isAbstract(method.getModifiers()) && deoptimize(method)) count++;
        }
        return count;
    }

    void info(String message) {
        Log.i(TAG, message);
        module.log(Log.INFO, TAG, message);
    }

    void error(String message, Throwable error) {
        Log.e(TAG, message, error);
        module.log(Log.ERROR, TAG, message, error);
    }

}
