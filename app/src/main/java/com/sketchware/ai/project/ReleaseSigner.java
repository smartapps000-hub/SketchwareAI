package com.sketchware.ai.project;

import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

import kellinwood.security.zipsigner.optional.KeyStoreFileManager;

/**
 * Signs an exported APK with the user's own keystore the way Android 11 and newer require:
 * APK Signature Scheme v2 and v3, plus the old JAR signature (v1), which Android versions
 * below 7.0 need. The export used to write the JAR signature only; such an APK is refused at install
 * on Android 11+ once the app targets API 30 or higher. Uncompressed entries are aligned by the
 * signer itself, so no separate zipalign pass runs afterwards (one after signing would break
 * the v2 signature).
 */
public final class ReleaseSigner {

    private ReleaseSigner() {
    }

    /**
     * The bundled zipalign writes whole 8192-byte blocks when no entry had to be moved, which
     * leaves padding after the end of the zip; Android's zip reader refuses such a file. This
     * cuts the file off where the zip ends. A file that is a well-formed zip is not touched.
     *
     * @return true if bytes were cut off
     */
    public static boolean trimAfterZipEnd(File zip) {
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(zip, "rw")) {
            long length = file.length();
            int window = (int) Math.min(length, 65557 + 16384);
            byte[] tail = new byte[window];
            file.seek(length - window);
            file.readFully(tail);
            for (int i = window - 22; i >= 0; i--) {
                if (tail[i] != 0x50 || tail[i + 1] != 0x4b || tail[i + 2] != 0x05 || tail[i + 3] != 0x06) continue;
                long recordAt = length - window + i;
                long directorySize = u32(tail, i + 12);
                long directoryAt = u32(tail, i + 16);
                int comment = (tail[i + 20] & 0xff) | ((tail[i + 21] & 0xff) << 8);
                // The real record: the central directory ends exactly where it starts.
                if (directoryAt + directorySize != recordAt) continue;
                long end = recordAt + 22 + comment;
                if (end >= length) return false;
                file.setLength(end);
                return true;
            }
        } catch (Exception e) {
            // Left as it is.
        }
        return false;
    }

    private static long u32(byte[] b, int at) {
        return (b[at] & 0xffL) | ((b[at + 1] & 0xffL) << 8) | ((b[at + 2] & 0xffL) << 16) | ((b[at + 3] & 0xffL) << 24);
    }

    /**
     * @param keystorePath the .jks/.bks the user exports with
     * @param alias        the key's alias in it
     */
    public static void sign(String inputApk, String outputApk, String keystorePath, char[] keystorePassword,
                            String alias, char[] keyPassword) throws Exception {
        KeyStore keystore = KeyStoreFileManager.loadKeyStore(keystorePath, keystorePassword);
        X509Certificate certificate = (X509Certificate) keystore.getCertificate(alias);
        PrivateKey key = (PrivateKey) keystore.getKey(alias, keyPassword);
        if (certificate == null || key == null) {
            throw new IllegalStateException("The keystore has no key named \"" + alias + "\".");
        }
        com.android.apksig.ApkSigner.SignerConfig signer = new com.android.apksig.ApkSigner.SignerConfig.Builder(
                "CERT", key, Collections.singletonList(certificate)).build();
        File output = new File(outputApk);
        if (output.exists() && !output.delete()) throw new java.io.IOException("Can't replace " + outputApk);
        new com.android.apksig.ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(new File(inputApk))
                .setOutputApk(output)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setCreatedBy("Sketchware AI")
                .build()
                .sign();
    }
}
