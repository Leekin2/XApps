package io.github.leekin2.xgoogle;

/**
 * WGS-84 (GPS) to GCJ-02, the offset coordinates maps of mainland China are drawn in. The
 * transform is the widely published one on the Krasovsky ellipsoid; outside the mainland box,
 * and in Hong Kong, Macao and Taiwan, coordinates stay as they are.
 */
final class Gcj02 {
    private static final double A = 6378245.0, EE = 0.00669342162296594323;

    static boolean applies(double lat, double lng) {
        return lat >= 0.8293 && lat <= 55.8271 && lng >= 72.004 && lng <= 137.8347 && !WgsRegions.contains(lat, lng);
    }

    /** {latitude, longitude}. */
    static double[] shift(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || !applies(lat, lng)) return new double[]{lat, lng};
        double x = lng - 105.0, y = lat - 35.0;
        double dLat = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
                + waves(x) + (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
                + (160.0 * Math.sin(y / 12.0 * Math.PI) + 320.0 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        double dLng = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
                + waves(x) + (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
                + (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        double radLat = lat / 180.0 * Math.PI;
        double magic = 1 - EE * Math.sin(radLat) * Math.sin(radLat);
        double sqrtMagic = Math.sqrt(magic);
        dLat = dLat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI);
        dLng = dLng * 180.0 / (A / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[]{lat + dLat, lng + dLng};
    }

    private static double waves(double x) {
        return (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
    }

    private Gcj02() { }
}
