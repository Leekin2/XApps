package io.github.leekin2.xgoogle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Bounded root execution; tells a refused su apart from a failed command. */
final class RootShell {
    private static final String READY = "__XGOOGLE_ROOT_READY__\n";
    enum Reason { AUTHORIZATION, UNAVAILABLE, TIMEOUT, EXECUTION }
    static final class Failure extends IOException {
        final Reason reason;
        Failure(Reason reason) { super("Root " + reason.name().toLowerCase()); this.reason = reason; }
    }

    static String run(String command, int timeout) throws Exception {
        Process process;
        try {
            // The marker is printed only after su granted uid 0, so a missing marker means "denied".
            process = new ProcessBuilder("su", "-c", "[ \"$(id -u)\" = 0 ] || exit 1; "
                    + "printf '__XGOOGLE_ROOT_READY__\\n'; " + command).redirectErrorStream(true).start();
        } catch (IOException unavailable) {
            throw new Failure(Reason.UNAVAILABLE);
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        Thread drain = new Thread(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) >= 0)
                    if (output.size() < 65536) output.write(buffer, 0, Math.min(n, 65536 - output.size()));
            } catch (IOException ignored) { }
        }, "XGoogle-root-output");
        drain.setDaemon(true);
        drain.start();
        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly(); drain.join(1000); throw new Failure(Reason.TIMEOUT);
        }
        drain.join(1000);
        String result = output.toString(StandardCharsets.UTF_8);
        int ready = result.indexOf(READY);
        if (ready < 0) throw new Failure(Reason.AUTHORIZATION);
        if (process.exitValue() != 0) throw new Failure(Reason.EXECUTION);
        return result.substring(ready + READY.length());
    }
    private RootShell() { }
}
