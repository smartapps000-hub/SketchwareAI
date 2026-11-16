package com.sketchware.ai.github;

import java.util.Locale;

/**
 * v2.0.5 (owner's 16 Nov directive "strengthen diagnostics to catch and explain sync failures, queue state or schema
 * mismatches"): one sentence of what to do after a failed backup, import or publish, from the failure's code
 * ({@link GitHubException#code}) or the thrown error. Added only when the message doesn't already say what to do, so a
 * line never repeats itself. Plain rules, no network: the Build Doctor's counterpart for GitHub sync.
 */
public final class SyncDoctor {

    private SyncDoctor() {
    }

    /** {@code message} plus the advice for {@code code}, when there is advice and the message has none yet. */
    public static String withAdvice(String code, String message) {
        String text = message == null ? "" : message.trim();
        String advice = advice(code, text);
        if (advice == null) return text;
        return text.isEmpty() ? advice : text + (text.endsWith(".") || text.endsWith(")") ? " " : ". ") + advice;
    }

    /** What to do after a failure with this code, or null (none known, or {@code message} says it already). */
    public static String advice(String code, String message) {
        if (code == null) return null;
        String said = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (said.contains("what to do") || said.contains("sign in again") || said.contains("try again")
                || said.contains("retry") || said.contains("waits")) {
            return null;
        }
        switch (code) {
            case "http_401":
            case "http_401_once":
            case "refused":
            case "unreadable":
                return "What to do: tap Sign in again (GitHub keeps 10 sign-ins per app and ends the least used one); "
                        + "the job runs again after it.";
            case "rate_limited":
            case "http_429":
                return "What to do: nothing; GitHub's hourly limit ends by itself and the backup waits for it.";
            case "http_403":
                return "What to do: sign in again (the sign-in may lack repository access), or unarchive the "
                        + "repository on GitHub.";
            case "http_404":
            case "not_found":
                return "What to do: if the repository was deleted or renamed, the next backup makes a new one.";
            case "http_409":
                return "What to do: Retry; the repository changed while the job ran.";
            case "http_422":
                return "What to do: Retry; the parts already uploaded are kept and reused.";
            case "too_large":
                return "What to do: leave out local libraries in the Back up sheet (GitHub takes 2 GB per file).";
            case "bad_backup":
            case "bad_part":
            case "bad_manifest":
            case "bad_index":
            case "bad_answer":
                return "What to do: pick another version; this one is incomplete or was made by a newer "
                        + "Sketchware AI (update the app).";
            case "keystore":
                return "What to do: close and reopen the app; the sign-in is kept.";
            default:
                if (code.startsWith("http_5")) {
                    return "What to do: Retry in a minute; GitHub had a server problem and the parts already "
                            + "uploaded are kept.";
                }
                return null;
        }
    }

    /** The advice for an error that isn't a {@link GitHubException}: memory, storage. Empty when none. */
    public static String forThrowable(Throwable t) {
        if (t instanceof OutOfMemoryError) {
            return " What to do: close other apps, or leave out local libraries in the Back up sheet (the project "
                    + "was too large for this phone's memory).";
        }
        if (isStorageFull(t)) return " What to do: free some space on the phone, then Retry.";
        return "";
    }

    /** "No space left on device" (ENOSPC) anywhere in the error or its causes. */
    public static boolean isStorageFull(Throwable t) {
        for (int i = 0; t != null && i < 8; i++, t = t.getCause()) {
            String m = t.getMessage();
            if (m != null && (m.contains("ENOSPC") || m.contains("No space left"))) return true;
        }
        return false;
    }
}
