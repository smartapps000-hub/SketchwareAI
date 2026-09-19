package mod.agus.jcoderz.lib;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Runs a native binary (AAPT2) and collects its output.
 * <p>
 * Contract kept from the original class: {@link #execute()} returns an empty string on
 * success and the tool's output on failure, and {@link #getLog()} always returns the output.
 * <p>
 * Fixes over the original implementation:
 * <ul>
 *     <li>stdout and stderr are merged and drained on a separate thread while the process runs.
 *     Previously only stderr was read and stdout was never drained, so a chatty process could
 *     fill the pipe buffer and hang the build forever.</li>
 *     <li>Success is decided by the exit code. Previously any text on stderr, including
 *     harmless warnings, failed the build, and a crash with no output counted as success.</li>
 *     <li>The process is killed after {@link #TIMEOUT_MINUTES} instead of blocking forever.</li>
 * </ul>
 */
public class BinaryExecutor {

    private static final long TIMEOUT_MINUTES = 10;

    private final ProcessBuilder mProcess = new ProcessBuilder();
    private final StringWriter mWriter = new StringWriter();
    private int exitCode = -1;

    public void setCommands(ArrayList<String> arrayList) {
        mProcess.command(arrayList);
    }

    public String execute() {
        mProcess.redirectErrorStream(true);
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            final Process process = mProcess.start();
            Thread reader = new Thread(() -> {
                byte[] buffer = new byte[8192];
                try (InputStream in = process.getInputStream()) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        synchronized (output) {
                            output.write(buffer, 0, read);
                        }
                    }
                } catch (IOException ignored) {
                    // Stream closed because the process ended or was killed.
                }
            }, "BinaryExecutor-output");
            reader.setDaemon(true);
            reader.start();

            if (!waitFor(process, TIMEOUT_MINUTES * 60_000L)) {
                process.destroy();
                reader.join(2000);
                mWriter.append(snapshot(output));
                mWriter.append(System.lineSeparator());
                mWriter.append("Process timed out after ").append(String.valueOf(TIMEOUT_MINUTES))
                        .append(" minutes: ").append(String.valueOf(mProcess.command()));
                return mWriter.toString();
            }
            reader.join(5000);
            exitCode = process.exitValue();
            mWriter.append(snapshot(output));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            mWriter.append("Interrupted while running ").append(String.valueOf(mProcess.command()));
            return mWriter.toString();
        } catch (Exception e) {
            e.printStackTrace(new PrintWriter(mWriter));
            return mWriter.toString();
        }

        if (exitCode == 0) {
            return "";
        }
        if (mWriter.toString().trim().isEmpty()) {
            mWriter.append(describeExitCode(exitCode));
        } else {
            mWriter.append(System.lineSeparator()).append(describeExitCode(exitCode));
        }
        return mWriter.toString();
    }

    public String getLog() {
        return mWriter.toString();
    }

    public int getExitCode() {
        return exitCode;
    }

    /**
     * Process#waitFor(long, TimeUnit) needs API 26, and the minApi21 flavor shares this code,
     * so poll exitValue() instead.
     *
     * @return true when the process ended within the timeout
     */
    private static boolean waitFor(Process process, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        long sleep = 5;
        while (true) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException stillRunning) {
                if (System.currentTimeMillis() >= deadline) return false;
                Thread.sleep(sleep);
                sleep = Math.min(sleep * 2, 100);
            }
        }
    }

    private static String snapshot(ByteArrayOutputStream output) {
        synchronized (output) {
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String describeExitCode(int code) {
        switch (code) {
            case 126:
                return "Exit code 126: the binary exists but cannot be executed. "
                        + "Reinstall the app from the universal APK.";
            case 127:
                return "Exit code 127: the binary could not be started on this CPU. "
                        + "Reinstall the app from the universal APK.";
            default:
                return "Exit code " + code + " from " + mProcess.command().get(0);
        }
    }
}
