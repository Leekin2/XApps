package io.github.leekin2.xsecurity;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

/**
 * Xiaomi Find Device outside mainland China: the real street address reaches Xiaomi's server, map
 * points stay where they are, Route offers the installed map apps, and remote commands survive a
 * Turkish system language. Authentication and the app's own requests stay untouched. Logs never
 * hold coordinates, addresses or account data.
 */
final class FindDeviceHooks {
    private static final String ROOT = "com.xiaomi.finddevice";
    private static final String LOCATION = ROOT + ".coreserver.business.location.LocationResult";
    private static final String FDEVICE = ROOT + ".protocol.FDevice";
    /** China's mainland, roughly: longitude, latitude pairs. */
    private static final double[] MAINLAND = {73.5, 39.5, 80, 42, 80.5, 45, 87, 49.2, 91, 46, 96, 42.8,
            105, 41.6, 111.5, 45, 116, 47, 116, 49.8, 120, 53.6, 123.5, 53.6, 126, 50, 131, 48, 135, 48.3,
            131, 43, 128, 41.5, 124.3, 40, 122, 39, 123, 37, 122.5, 31, 122, 28, 120, 25.5, 117, 23,
            114.2, 22.5, 111, 20, 109, 18, 108, 21.5, 105.5, 23, 102, 21.2, 99, 22, 97.5, 24, 98.5, 28,
            92, 27.8, 86, 28, 81, 30, 78.5, 32.5, 78, 35.5, 74, 37};

    private final Hooks hooks;
    private final ClassLoader loader;
    private final Map<Object, Object> commandPoint = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, Long> replyStamp = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, Object> converterInput = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Long> reportStamp = new ThreadLocal<>();
    private final ThreadLocal<Boolean> reportAccepted = new ThreadLocal<>();
    private final ThreadLocal<Integer> builderDepth = new ThreadLocal<>();
    private Class<?> location;
    private Field lat;
    private Field lng;
    private Resolved cache;
    private long retryAfter;
    private volatile long lastRefreshCommand;

    FindDeviceHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install(String apk, String data) {
        if (!Features.enabled(Features.FIND_DEVICE)) return;
        try {
            location = loader.loadClass(LOCATION);
            lat = location.getField("lat");
            lng = location.getField("lng");
        } catch (Throwable failure) {
            hooks.error("Find Device: location type unavailable", failure);
            return;
        }
        step("location source", this::locationSource);
        step("server address", this::serverAddress);
        step("map points", this::mapPoints);
        step("refresh command", this::refreshCommand);
        try (DexTargets targets = new DexTargets(loader, apk, data)) {
            step("report result", () -> reportResult(targets));
            step("own address", () -> ownAddress(targets));
            step("route chooser", () -> routeChooser(targets));
            step("command language", () -> commandLanguage(targets));
        } catch (Throwable failure) {
            hooks.error("Find Device: scan unavailable", failure);
        }
    }

    private interface Step { void run() throws Throwable; }

    private void step(String name, Step step) {
        try {
            step.run();
            hooks.info("Find Device: " + name + " ready");
        } catch (Throwable failure) {
            hooks.error("Find Device: " + name + " unavailable", failure);
        }
    }

    /**
     * A leftover Baidu choice makes the app ask Baidu for the location; without GPS, abroad, that
     * answered a point thousands of kilometres away. The app then uses its normal source.
     */
    private void locationSource() throws Throwable {
        hooks.install(loader.loadClass(ROOT + ".v2.SpStorage").getDeclaredMethod("getString", Context.class, String.class), chain -> {
            if (!"debug_location_type".equals(chain.getArg(1))) return chain.proceed();
            Object value = chain.proceed();
            return "baidu".equals(value) ? chain.proceed(new Object[] {chain.getArg(0), "xapps_no_location_type"}) : value;
        });
    }

    /**
     * Xiaomi drops the address from command replies and keeps the record with the newest time. So the
     * addressed report goes first, stamped now, and the reply keeps a slightly older time: it can no
     * longer replace the addressed record.
     */
    private void serverAddress() throws Throwable {
        hooks.install(location.getMethod("toServerJson"), chain -> {
            Object result = chain.proceed();
            if (result instanceof JSONObject) {
                attach(chain.getThisObject(), (JSONObject) result);
                Long stamp = reportStamp.get();
                if (stamp != null) ((JSONObject) result).put("clientUpdateTime", stamp.longValue());
            }
            return result;
        });
        Class<?> locate = loader.loadClass(ROOT + ".v2.command.command.LocateCommand");
        for (Class<?> command : new Class<?>[] {locate, loader.loadClass(ROOT + ".v2.command.command.GetNearInfoCommand")}) {
            hooks.install(command.getDeclaredMethod("buildLocationInfo", location, int.class), chain -> {
                Object result = chain.proceed();
                commandPoint.put(chain.getThisObject(), chain.getArg(0));
                if (result instanceof JSONObject) attach(chain.getArg(0), (JSONObject) result);
                return result;
            });
        }
        Class<?> base = locate;
        Method reply = null;
        while (base != null && reply == null) {
            try { reply = base.getDeclaredMethod("reply", Object.class); }
            catch (NoSuchMethodException next) { base = base.getSuperclass(); }
        }
        if (reply == null) throw new NoSuchMethodException("Command reply unavailable");
        Method getContext = base.getMethod("getContext");
        hooks.install(reply, chain -> {
            Object command = chain.getThisObject();
            Object point = commandPoint.get(command);
            JSONObject gps = point != null && chain.getArg(0) instanceof JSONObject
                    && Looper.myLooper() != Looper.getMainLooper()
                    ? ((JSONObject) chain.getArg(0)).optJSONObject("gpsInfo") : null;
            boolean addressed = false;
            if (gps != null) {
                attach(point, gps);
                addressed = !gps.optString("address", "").isEmpty();
                if (addressed) {
                    long stamp = System.currentTimeMillis();
                    if (report(point, (Context) getContext.invoke(command), stamp)) {
                        replyStamp.put(command, stamp);
                        addressed = false;
                    }
                }
            }
            Object result = chain.proceed();
            // The first report failed: the record must still end up with the address.
            if (addressed && "NETWORK".equals(String.valueOf(result)))
                report(point, (Context) getContext.invoke(command), System.currentTimeMillis());
            return result;
        });
        int stamps = 0;
        for (Method method : base.getDeclaredMethods()) {
            if (method.getReturnType() != JSONObject.class || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != Object.class || Modifier.isAbstract(method.getModifiers())) continue;
            hooks.install(method, chain -> {
                Object result = chain.proceed();
                Long stamp = replyStamp.get(chain.getThisObject());
                if (stamp != null && result instanceof JSONObject && ((JSONObject) result).has("timestamp"))
                    ((JSONObject) result).put("timestamp", stamp - 1);
                return result;
            });
            stamps++;
        }
        if (stamps == 0) throw new NoSuchMethodException("Reply message unavailable");
    }

    /**
     * The refresh button only asks the server for the device's record and leaves it to the server
     * to wake the other device, which often does not happen. The alarm sends a command and works
     * every time, so refresh now sends the app's own location command the same way.
     */
    private void refreshCommand() throws Throwable {
        Class<?> device = loader.loadClass(FDEVICE);
        Class<?> manager = loader.loadClass(ROOT + ".v2.FindDeviceStatusManagerInternal");
        Method get = manager.getMethod("get", Context.class);
        Method send = null;
        for (Method method : manager.getDeclaredMethods()) {
            if ("sendCommandToOtherDevice".equals(method.getName()) && method.getParameterCount() == 3) send = method;
        }
        if (send == null) throw new NoSuchMethodException("Command sender unavailable");
        Method sender = send;
        sender.setAccessible(true);
        Object locate = enumValue(sender.getParameterTypes()[2], "LOCATION");
        Method isPadOrPhone = device.getMethod("isPadOrPhone");
        // Only remote devices get this mission; the device itself reports on another path.
        Constructor<?> mission = loader.loadClass(ROOT + ".coreserver.business.refresh.DeviceDetailRefreshMission")
                .getDeclaredConstructors()[0];
        hooks.install(mission, chain -> {
            Object result = chain.proceed();
            Object target = chain.getArg(0);
            if (device.isInstance(target) && Boolean.TRUE.equals(isPadOrPhone.invoke(target))) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastRefreshCommand > 15000) {
                    lastRefreshCommand = now;
                    new Thread(() -> {
                        try {
                            Context context = application();
                            sender.invoke(get.invoke(null, context), context, target, locate);
                        } catch (Throwable failure) {
                            hooks.info("Find Device: refresh command failed: " + failure.getClass().getSimpleName());
                        }
                    }, "XAppsFindDeviceRefresh").start();
                }
            }
            return result;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private boolean report(Object point, Context context, long stamp) {
        try {
            Class<?> type = loader.loadClass(ROOT + ".coreserver.business.device.ReportLocationMission$ReportLocationTask");
            Constructor<?> constructor = type.getDeclaredConstructor(location, Context.class);
            constructor.setAccessible(true);
            Method run = type.getDeclaredMethod("run", Object.class);
            run.setAccessible(true);
            reportStamp.set(stamp);
            reportAccepted.remove();
            run.invoke(constructor.newInstance(point, context), new Object[] {null});
            return reportAccepted.get() != null;
        } catch (Throwable failure) {
            hooks.info("Find Device: address report failed: " + failure.getClass().getSimpleName());
            return false;
        } finally {
            reportStamp.remove();
            reportAccepted.remove();
        }
    }

    /** Tells whether Xiaomi accepted the report; without it the report simply repeats after the reply. */
    private void reportResult(DexTargets targets) throws Throwable {
        List<Class<?>> apis = targets.classesUsing("find_device_api", "REPORT_IDENTIFIED", "GET_ADDRESS_LIST");
        if (apis.size() != 1) throw new NoSuchMethodException("Server request list unavailable");
        String api = apis.get(0).getName();
        Method request = single(targets.matching("find_device_request", () -> MethodMatcher.create()
                .paramTypes(api, "java.util.Map", "java.util.Map", "int")));
        hooks.install(request, chain -> {
            Object result = chain.proceed();
            if ("REPORT_IDENTIFIED".equals(String.valueOf(chain.getArg(0))) && result instanceof JSONObject
                    && ((JSONObject) result).optInt("code", -1) == 0) reportAccepted.set(Boolean.TRUE);
            return result;
        });
    }

    /** The app's own address request answers empty abroad; the device's list then shows nothing. */
    private void ownAddress(DexTargets targets) throws Throwable {
        Method lookup = single(targets.matching("find_device_own_address", () -> MethodMatcher.create()
                .paramTypes(LOCATION).returnType("java.util.List")));
        hooks.install(lookup, chain -> {
            Object result = chain.proceed();
            if (Looper.myLooper() == Looper.getMainLooper() || !(result instanceof List)) return result;
            List<?> values = (List<?>) result;
            if (values.size() < 2 || !(values.get(0) instanceof String) || !((String) values.get(0)).isEmpty()) return result;
            Resolved resolved = resolve(chain.getArg(0));
            if (resolved == null) return result;
            List<String> replacement = new ArrayList<>(2);
            replacement.add(resolved.line);
            replacement.add(resolved.component);
            return replacement;
        });
    }

    /** The map moves non-Baidu points with a China-only formula: hundreds of metres off abroad. */
    private void mapPoints() throws Throwable {
        Class<?> converter = loader.loadClass("com.baidu.mapapi.utils.CoordinateConverter");
        Class<?> latLng = loader.loadClass("com.baidu.mapapi.model.LatLng");
        Field latitude = latLng.getField("latitude");
        Field longitude = latLng.getField("longitude");
        hooks.install(converter.getMethod("coord", latLng), chain -> {
            converterInput.put(chain.getThisObject(), chain.getArg(0));
            return chain.proceed();
        });
        hooks.install(converter.getMethod("convert"), chain -> {
            Object result = chain.proceed();
            Object input = converterInput.get(chain.getThisObject());
            return input != null && !inMainland(latitude.getDouble(input), longitude.getDouble(input)) ? input : result;
        });
        // The device's own pin takes the same formula on its own path when the source is Autonavi.
        Class<?> list = loader.loadClass(ROOT + ".coreserver.business.device.DeviceListBusiness");
        Class<?> device = loader.loadClass(FDEVICE);
        Field selfLat = field(list, "selfDeviceLat");
        Field selfLng = field(list, "selfDeviceLng");
        Method setLat = device.getMethod("setLocalDevicePositionLat", double.class);
        Method setLng = device.getMethod("setLocalDevicePositionLng", double.class);
        hooks.install(list.getDeclaredMethod("mergeSelfLocation", boolean.class, device,
                loader.loadClass(ROOT + ".protocol.ComponentInfo")), chain -> {
            Object result = chain.proceed();
            if (Boolean.TRUE.equals(chain.getArg(0)) && chain.getArg(1) != null) {
                double y = selfLat.getDouble(chain.getThisObject());
                double x = selfLng.getDouble(chain.getThisObject());
                if (!inMainland(y, x)) {
                    setLat.invoke(chain.getArg(1), y);
                    setLng.invoke(chain.getArg(1), x);
                }
            }
            return result;
        });
        hooks.install(list.getDeclaredMethod("convertCoordinate", String.class, String.class, double[].class), chain -> {
            double[] point = (double[]) chain.getArg(2); // longitude, latitude
            return point != null && point.length == 2 && !inMainland(point[1], point[0]) ? point.clone() : chain.proceed();
        });
    }

    private void routeChooser(DexTargets targets) throws Throwable {
        Method route = single(targets.matching("find_device_route", () -> MethodMatcher.create()
                .paramTypes("android.content.Context", "java.util.List", "java.lang.String", "int", FDEVICE)
                .returnType("void")));
        Class<?> gpsInfo = loader.loadClass(ROOT + ".protocol.GpsInfo");
        Field type = field(gpsInfo, "coordinateType");
        Field latitude = field(gpsInfo, "latitude");
        Field longitude = field(gpsInfo, "longitude");
        hooks.install(route, chain -> {
            Context context = (Context) chain.getArg(0);
            List<?> points = (List<?>) chain.getArg(1);
            Object target = null;
            if (points != null) for (Object point : points) {
                if (target == null) target = point;
                if ("google".equals(type.get(point))) { target = point; break; }
            }
            if (target == null) return chain.proceed();
            double y = latitude.getDouble(target);
            double x = longitude.getDouble(target);
            if ((y == 0 && x == 0) || inMainland(y, x)) return chain.proceed();
            String place = y + "," + x;
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + place + "?q=" + place));
            if (view.resolveActivity(context.getPackageManager()) == null) return chain.proceed();
            Intent chooser = Intent.createChooser(view, chooserTitle());
            if (!(context instanceof android.app.Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
            return null;
        });
    }

    private static String chooserTitle() {
        switch (Locale.getDefault().getLanguage()) {
            case "tr": return "Haritada aç";
            case "ru": return "Открыть на карте";
            case "es": return "Abrir en el mapa";
            case "zh": return "在地图中打开";
            default: return "Open in map";
        }
    }

    /**
     * The app lowercases command names with the system language: under Turkish NOISE becomes "noıse"
     * and the alarm is refused as an unknown command. Fixed only inside the original command builder,
     * so its signature, account and sequence checks keep running.
     */
    private void commandLanguage(DexTargets targets) throws Throwable {
        Method builder = null;
        for (Class<?> type : targets.classesUsing("find_device_builder", "get_near_info", "stop_noise", "online_ping_fcm")) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterCount() > 0 && method.getParameterTypes()[0] == Bundle.class
                        && !Modifier.isStatic(method.getModifiers())) builder = method;
            }
        }
        Method name = null;
        for (Class<?> type : targets.classesUsing("find_device_commands", "NOISE", "STOP_NOISE")) {
            if (!type.isEnum()) continue;
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == String.class
                        && method.getParameterCount() == 1 && method.getParameterTypes()[0] == int.class) name = method;
            }
        }
        if (builder == null || name == null) throw new NoSuchMethodException("Command builder unavailable");
        hooks.install(builder, chain -> {
            Integer depth = builderDepth.get();
            builderDepth.set(depth == null ? 1 : depth + 1);
            try {
                return chain.proceed();
            } finally {
                if (depth == null) builderDepth.remove(); else builderDepth.set(depth);
            }
        });
        hooks.install(name, chain -> {
            Object result = chain.proceed();
            return builderDepth.get() != null && result instanceof String
                    ? ((String) result).toLowerCase(Locale.ROOT) : result;
        });
    }

    private void attach(Object point, JSONObject payload) throws JSONException {
        if (!payload.optString("address", "").isEmpty()) return;
        Resolved resolved = resolve(point);
        if (resolved == null) return;
        payload.put("address", resolved.line);
        payload.put("addressComponent", resolved.component);
    }

    /** Null on the main thread, in mainland China (Xiaomi answers there) or without a real address. */
    private synchronized Resolved resolve(Object point) {
        if (Looper.myLooper() == Looper.getMainLooper()) return null;
        try {
            double y = lat.getDouble(point);
            double x = lng.getDouble(point);
            if (!Double.isFinite(y) || !Double.isFinite(x) || Math.abs(y) > 90 || Math.abs(x) > 180
                    || (y == 0 && x == 0) || inMainland(y, x)) return null;
            long now = SystemClock.elapsedRealtime();
            if (cache != null && now - cache.time < 300000 && distance(y, x, cache.lat, cache.lng) < 20) return cache;
            // Network locations jump; a short geocoder failure must not empty the record.
            Resolved near = cache != null && now - cache.time < 600000
                    && distance(y, x, cache.lat, cache.lng) < 500 ? cache : null;
            Context context = application();
            if (context == null || now < retryAfter || !Geocoder.isPresent()) return near;
            retryAfter = now + 15000;
            AtomicReference<List<Address>> response = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            new Geocoder(context, Locale.getDefault()).getFromLocation(y, x, 1, new Geocoder.GeocodeListener() {
                @Override public void onGeocode(List<Address> addresses) { response.set(addresses); done.countDown(); }
                @Override public void onError(String ignored) { done.countDown(); }
            });
            if (!done.await(6, TimeUnit.SECONDS)) return near;
            List<Address> addresses = response.get();
            if (addresses == null || addresses.isEmpty()) return near;
            Address address = addresses.get(0);
            String line = address.getAddressLine(0);
            if (line == null || line.trim().isEmpty()) return near;
            if (address.hasLatitude() && address.hasLongitude()
                    && distance(y, x, address.getLatitude(), address.getLongitude()) > 250) return near;
            String street = address.getThoroughfare();
            if (street == null || street.isEmpty()) street = address.getLocality();
            if (street == null || street.isEmpty()) street = address.getAdminArea();
            if (street == null || street.isEmpty()) return near;
            String building = address.getSubThoroughfare();
            // Two parts: the app's formatter then keeps the whole address.
            String component = new JSONArray().put(street).put(building == null ? "" : building).toString();
            cache = new Resolved(y, x, line, component, now);
            retryAfter = 0;
            return cache;
        } catch (Throwable failure) {
            hooks.info("Find Device: address unavailable: " + failure.getClass().getSimpleName());
            return null;
        }
    }

    private static Context application() throws ReflectiveOperationException {
        return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
    }

    private static Method single(List<Method> methods) throws NoSuchMethodException {
        if (methods.size() != 1) throw new NoSuchMethodException("Expected one target, found " + methods.size());
        return methods.get(0);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** Ray casting over {@link #MAINLAND}. */
    static boolean inMainland(double latitude, double longitude) {
        boolean inside = false;
        int count = MAINLAND.length / 2;
        for (int i = 0, j = count - 1; i < count; j = i++) {
            double xi = MAINLAND[i * 2], yi = MAINLAND[i * 2 + 1];
            double xj = MAINLAND[j * 2], yj = MAINLAND[j * 2 + 1];
            if ((yi > latitude) != (yj > latitude)
                    && longitude < (xj - xi) * (latitude - yi) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
    }

    private static double distance(double y, double x, double otherY, double otherX) {
        double a = Math.sin(Math.toRadians(y - otherY) / 2);
        double b = Math.sin(Math.toRadians(x - otherX) / 2);
        double h = a * a + Math.cos(Math.toRadians(y)) * Math.cos(Math.toRadians(otherY)) * b * b;
        return 12742000 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private static final class Resolved {
        final double lat, lng;
        final String line, component;
        final long time;

        Resolved(double lat, double lng, String line, String component, long time) {
            this.lat = lat;
            this.lng = lng;
            this.line = line;
            this.component = component;
            this.time = time;
        }
    }
}
