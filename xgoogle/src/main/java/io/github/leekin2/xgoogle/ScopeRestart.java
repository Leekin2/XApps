package io.github.leekin2.xgoogle;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

/** Restarts the apps in XGoogle's scope without rebooting; system_server still needs a reboot. */
final class ScopeRestart {
    static String command(List<String> packages, int user) throws IOException {
        StringBuilder command = new StringBuilder();
        for (String name : new LinkedHashSet<>(packages)) {
            if (FeatureScopes.SYSTEM.equals(name) || "android".equals(name) || BuildConfig.APPLICATION_ID.equals(name)) continue;
            if (!name.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) throw new IOException("Invalid package " + name);
            // SystemUI restarts itself; force-stop would leave the phone without a status bar for a moment longer.
            if ("com.android.systemui".equals(name))
                command.append("for p in $(pidof com.android.systemui); do kill -TERM \"$p\" || failed=\"$failed com.android.systemui\"; done; ");
            else command.append("am force-stop --user ").append(user).append(" '").append(name)
                    .append("' || failed=\"$failed ").append(name).append("\"; ");
        }
        if (command.length() == 0) throw new IOException("No app scope to restart");
        // Report every app that could not be restarted instead of ending on success.
        return "failed=; " + command.append("[ -z \"$failed\" ] || { echo \"Not restarted:$failed\"; exit 1; }");
    }
    private ScopeRestart() { }
}
