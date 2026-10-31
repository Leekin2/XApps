package io.github.leekin2.xgoogle;

import java.util.LinkedHashSet;
import java.util.Arrays;

public final class NativeQuickEntryPolicy {
    private NativeQuickEntryPolicy() {}

    public static String[] completeLockscreenEntries(String[] existing) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(Arrays.asList(existing));
        boolean hasNone = keys.remove("none");
        keys.add("assistant");
        keys.add("wallet");
        keys.add("qr_code_scanner");
        keys.add("home");
        if (hasNone) keys.add("none");
        return keys.toArray(new String[0]);
    }
}
