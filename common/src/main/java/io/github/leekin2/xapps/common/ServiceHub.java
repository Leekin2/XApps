package io.github.leekin2.xapps.common;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * XposedServiceHelper keeps a single listener and hands an early service only once, so XApps
 * registers it here, once, and every module part (Google, Security, Notify) listens through this.
 * A listener added after the bind gets the current service straight away.
 */
public final class ServiceHub {
    private static final List<XposedServiceHelper.OnServiceListener> listeners = new CopyOnWriteArrayList<>();
    private static volatile XposedService service;
    private static boolean registered;

    public static synchronized void register(XposedServiceHelper.OnServiceListener listener) {
        listeners.add(listener);
        if (!registered) {
            registered = true;
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override public void onServiceBind(XposedService value) {
                    service = value;
                    for (XposedServiceHelper.OnServiceListener target : listeners) target.onServiceBind(value);
                }
                @Override public void onServiceDied(XposedService value) {
                    if (service == value) service = null;
                    for (XposedServiceHelper.OnServiceListener target : listeners) target.onServiceDied(value);
                }
            });
        } else if (service != null) {
            listener.onServiceBind(service);
        }
    }

    /** For screens: stop listening when they close. */
    public static void unregister(XposedServiceHelper.OnServiceListener listener) {
        listeners.remove(listener);
    }

    /** The bound service, or null. */
    public static XposedService current() { return service; }

    private ServiceHub() { }
}
