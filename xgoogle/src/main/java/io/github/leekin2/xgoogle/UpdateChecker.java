package io.github.leekin2.xgoogle;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Reads the latest GitHub release of XApps; only runs when the user taps "Check for updates". */
final class UpdateChecker {
    static final String RELEASES = "https://github.com/Leekin2/XApps/releases/latest";
    private static final String API = "https://api.github.com/repos/Leekin2/XApps/releases/latest";

    record Release(String version, String url) { }

    static Release latest() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(API).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            String body;
            try (InputStream input = connection.getInputStream()) { body = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            JSONObject release = new JSONObject(body);
            String tag = release.getString("tag_name").replaceFirst("^[vV]", "");
            return new Release(tag, release.optString("html_url", RELEASES));
        } catch (org.json.JSONException invalid) {
            throw new IOException(invalid);
        } finally {
            connection.disconnect();
        }
    }

    /** Compares dotted versions numerically ("1.2.10" is newer than "1.2.9"). */
    static boolean isNewer(String candidate, String current) {
        String[] a = candidate.split("[^0-9]+"), b = current.split("[^0-9]+");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length && !b[i].isEmpty() ? Integer.parseInt(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }
    private UpdateChecker() { }
}
