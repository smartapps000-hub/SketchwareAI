package com.sketchware.ai.project;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;

import kellinwood.security.zipsigner.optional.KeyStoreFileManager;
import mod.jbk.build.BuiltInLibraries;

/**
 * JAR signature of an exported .aab (v1.8.5).
 * <p>
 * An app bundle carries only a JAR (v1) signature: META-INF/MANIFEST.MF, CERT.SF, CERT.RSA. Upstream signed it with
 * kellinwood ZipSigner, which always writes SHA-1 digests. JDK 17 (and every jarsigner since 17.0.5 / 11.0.17 / 8u351)
 * treats such a bundle as unsigned, and Play Console has been seen refusing it ("The Android App Bundle was not
 * signed"). Here the bundle is signed with apksig, v1 only, with a minimum SDK of 24 so that apksig uses SHA-256:
 * SHA-256-Digest entries and a SHA256withRSA (or SHA256withECDSA) signature block, like jarsigner
 * {@code -digestalg SHA-256 -sigalg SHA256withRSA}. No APK Signature Scheme v2/v3 block is added (a bundle has none).
 * APK signing (ReleaseSigner, ApkSigner) is not touched.
 */
public final class BundleSigner {

    /** apksig picks SHA-256 for the JAR signature from API 18 on; 24 matches the lowest Android this targets. */
    private static final int SIGNATURE_MIN_SDK = 24;

    private BundleSigner() {
    }

    /** Signs with the public test key (testkey.pk8 / testkey.x509.pem, extracted with the built-in libraries). */
    public static void signWithTestKey(String inputBundle, String outputBundle) throws Exception {
        File directory = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "testkey");
        PrivateKey key = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(readAll(new File(directory, "testkey.pk8"))));
        X509Certificate certificate;
        try (InputStream in = new FileInputStream(new File(directory, "testkey.x509.pem"))) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        sign(inputBundle, outputBundle, key, certificate);
    }

    /** Signs with a key of the user's keystore (.jks or .bks, as the export screen loads it). */
    public static void signWithKeyStore(String inputBundle, String outputBundle, String keystorePath,
                                        char[] keystorePassword, String alias, char[] keyPassword) throws Exception {
        KeyStore keystore = KeyStoreFileManager.loadKeyStore(keystorePath, keystorePassword);
        X509Certificate certificate = (X509Certificate) keystore.getCertificate(alias);
        PrivateKey key = (PrivateKey) keystore.getKey(alias, keyPassword);
        if (certificate == null || key == null) {
            throw new IllegalStateException("The keystore has no key named \"" + alias + "\".");
        }
        sign(inputBundle, outputBundle, key, certificate);
    }

    static void sign(String inputBundle, String outputBundle, PrivateKey key, X509Certificate certificate)
            throws Exception {
        File output = new File(outputBundle);
        File temporary = new File(output.getParentFile(), output.getName() + ".signing");
        temporary.delete();
        com.android.apksig.ApkSigner.SignerConfig signer = new com.android.apksig.ApkSigner.SignerConfig.Builder(
                "CERT", key, Collections.singletonList(certificate)).build();
        try {
            new com.android.apksig.ApkSigner.Builder(Collections.singletonList(signer))
                    .setInputApk(new File(inputBundle))
                    .setOutputApk(temporary)
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(false)
                    .setV3SigningEnabled(false)
                    // a bundle has no root AndroidManifest.xml to read it from; >= 18 makes the digests SHA-256
                    .setMinSdkVersion(SIGNATURE_MIN_SDK)
                    .setCreatedBy("Sketchware AI")
                    .build()
                    .sign();
            if (output.exists() && !output.delete()) {
                throw new IOException("Can't replace " + outputBundle);
            }
            if (!temporary.renameTo(output)) {
                throw new IOException("Can't move the signed bundle to " + outputBundle);
            }
        } finally {
            temporary.delete();
        }
    }

    private static byte[] readAll(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
