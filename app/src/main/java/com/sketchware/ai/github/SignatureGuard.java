package com.sketchware.ai.github;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * v2.1.6 (owner's directive "ANTI-TAMPER / ANTI-HACK SECURITY HARDENING"): the APK's signing certificate is checked
 * at run time against the release certificate's SHA-256. A copy signed with another key (the usual result of
 * repacking, patching or "modding" an APK) keeps working as an editor, but GitHub sign-in, every request with the
 * GitHub token (backups, restores of private repositories, Play Store publishing, Follow) and the in-app updater are
 * off, with one message saying why.
 * <p>
 * What it can't do (said plainly to the owner): the release is signed with the repository's public test key
 * ({@code testkey.keystore}), so anyone can re-sign a changed copy with that same key and pass this check; real
 * protection needs a private release key kept off GitHub (the owner's decision: installs would then not update over
 * the current ones without a key rotation). A copy patched to skip this class also passes. When the certificate can't
 * be read at all (an unusual ROM, the desktop tests, the runtime harness) the check never locks anyone out.
 */
public final class SignatureGuard {

    /** SHA-256 of the certificate every release since v1.0 is signed with (apksigner: "Signer #1 certificate SHA-256"). */
    public static final String RELEASE_CERT_SHA256 = "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc";

    public static final String REFUSAL = "This copy of Sketchware AI isn't signed with the official release key, so "
            + "GitHub sign-in, backups to GitHub, Play Store publishing and in-app updates are turned off. Install the "
            + "official APK from github.com/smartapps000-hub/SketchwareAI.";

    /** null: not read (or unreadable): allowed. */
    private static volatile Boolean verdict;

    private SignatureGuard() {
    }

    /** Reads the certificate once (app start). */
    public static void init(Context context) {
        try {
            verdict = read(context.getApplicationContext());
        } catch (RuntimeException | Error e) {
            // unreadable: never a lock-out
            verdict = null;
        }
    }

    /** False only when the certificate was read and isn't the release's. */
    public static boolean trusted() {
        Boolean v = verdict;
        return v == null || v;
    }

    /** Any of the signer's certificates (its current one, or one in its rotation history) is the release's. */
    static boolean matches(String[] sha256s) {
        if (sha256s == null) return false;
        for (String s : sha256s) {
            if (s != null && RELEASE_CERT_SHA256.equals(s.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** For tests: the verdict as if read ({@code null}: not read). */
    static void setVerdictForTests(Boolean v) {
        verdict = v;
    }

    /**
     * The verdict from the package's signatures: one signer: its certificate or one in its rotation history is the
     * release's; several signers (never this app): every one must be. null when nothing could be read.
     */
    private static Boolean read(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return null;
                if (info.signingInfo.hasMultipleSigners()) {
                    Signature[] all = info.signingInfo.getApkContentsSigners();
                    if (all == null || all.length == 0) return null;
                    for (Signature s : all) if (!matches(new String[]{sha256(s.toByteArray())})) return false;
                    return true;
                }
                return verdictOf(info.signingInfo.getSigningCertificateHistory());
            }
            @SuppressWarnings("deprecation")
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            return verdictOf(info.signatures);
        } catch (Exception e) {
            return null;
        }
    }

    private static Boolean verdictOf(Signature[] signatures) throws java.security.NoSuchAlgorithmException {
        if (signatures == null || signatures.length == 0) return null;
        String[] out = new String[signatures.length];
        for (int i = 0; i < signatures.length; i++) out[i] = sha256(signatures[i].toByteArray());
        return matches(out);
    }

    static String sha256(byte[] der) throws java.security.NoSuchAlgorithmException {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(der);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return sb.toString();
    }
}
