package io.github.leekin2.xsecurity;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Uses the stock doze listener lifecycle with Android sensors when the Xiaomi sensors are missing. */
final class AodStandardSensors {
    private AodStandardSensors() {}

    static void install(Hooks hooks, ClassLoader loader) throws Throwable {
        String name = "com.miui.aod.doze.MiuiDozeScreenBrightnessController";
        Class<?> controller = Class.forName(name, false, loader);
        Field manager = controller.getField("mSensorManager");
        Field light = controller.getField("mLightSensor");
        Field prox = controller.getField("mNonUiSensor");
        Field lightListener = controller.getField("mSensorEventListener");
        Field proxListener = controller.getField("mNonUISensorEventListener");
        Field blocked = controller.getField("shouldBlockSensor");
        Method react = controller.getMethod("sensorReactChange", int.class);
        // New listeners are installed before the stock registration task first runs. Both sensor
        // fields, and their corresponding listeners, travel together through registration/cleanup.
        for (java.lang.reflect.Constructor<?> ctor : controller.getDeclaredConstructors()) {
            hooks.install(ctor, chain -> {
                Object result = chain.proceed();
                Object self = chain.getThisObject();
                SensorManager sensors = (SensorManager) manager.get(self);
                Sensor fallbackLight = light.get(self) == null ? find(sensors, Sensor.TYPE_LIGHT) : null;
                Sensor fallbackProx = prox.get(self) == null ? find(sensors, Sensor.TYPE_PROXIMITY) : null;
                if (fallbackLight == null && fallbackProx == null) return result;
                Reading reading = new Reading();
                if (fallbackLight != null) {
                    replace(self, light, lightListener, fallbackLight, blocked, event -> {
                        float lux = event.values[0];
                        if (!Float.isFinite(lux) || lux < 0f) return;
                        int bucket = lux <= 0.5f ? 3 : lux < 30f ? 5 : 4;
                        if (reading.light == bucket) return;
                        reading.light = bucket;
                        if (!reading.near) react.invoke(self, bucket);
                    });
                }
                if (fallbackProx != null) {
                    replace(self, prox, proxListener, fallbackProx, blocked, event -> {
                        float distance = event.values[0];
                        if (!Float.isFinite(distance) || distance < 0f) return;
                        boolean near = distance < fallbackProx.getMaximumRange();
                        if (near == reading.near) return;
                        reading.near = near;
                        react.invoke(self, near ? 1 : reading.light > 0 ? reading.light : 5);
                    });
                }
                hooks.info("AOD standard sensor fallback: light=" + (fallbackLight != null) + ", proximity=" + (fallbackProx != null));
                return result;
            });
        }
    }

    private static Sensor find(SensorManager manager, int type) {
        Sensor waking = manager.getDefaultSensor(type, true);
        return waking != null ? waking : manager.getDefaultSensor(type, false);
    }

    private static void replace(Object controller, Field field, Field listener, Sensor sensor,
            Field blocked, Delivery delivery) throws IllegalAccessException {
        SensorEventListener original = (SensorEventListener) listener.get(controller);
        listener.set(controller, new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) {
                if (event == null || event.sensor != sensor || event.values.length == 0) return;
                try {
                    if (!blocked.getBoolean(controller)) delivery.accept(event);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Drop this sample and let the next sensor event retry.
                }
            }

            @Override public void onAccuracyChanged(Sensor reported, int accuracy) {
                if (original != null) original.onAccuracyChanged(reported, accuracy);
            }
        });
        try {
            field.set(controller, sensor);
        } catch (IllegalAccessException | RuntimeException failure) {
            listener.set(controller, original);
            throw failure;
        }
    }

    private interface Delivery { void accept(SensorEvent event) throws ReflectiveOperationException; }

    private static final class Reading {
        int light = -1;
        boolean near;
    }
}
