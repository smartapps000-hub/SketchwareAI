package com.sketchware.ai.github;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * v1.9.8: the GitHub access token, encrypted with AES-256-GCM under a key that lives in the Android Keystore (it never
 * leaves the phone's keystore, and isn't part of a backup). The app's private preferences hold only the IV and the
 * ciphertext and the user's login and id. Not EncryptedSharedPreferences (deprecated): the Keystore directly.
 * <p>
 * v1.9.9 (owner's 12 Nov directive, "hardware-bound session"): the ciphertext is bound, as GCM associated data, to
 * SHA-256 of this phone's {@code Settings.Secure.ANDROID_ID}, the GitHub user id and the app's signing certificate
 * ({@code "v2:" + base64(iv) + ":" + base64(ciphertext+tag)}). App data copied to another phone, or a session of
 * another user id, doesn't decrypt: that is "signed out". (The Keystore key alone already can't leave the phone.)
 * v1 tokens of v1.9.8 read as signed out.
 */
public final class TokenStore {

    static final String PREFS = "sketchware_ai_github";
    static final String KEY_TOKEN = "token";
    static final String KEY_LOGIN = "login";
    static final String KEY_USER_ID = "user_id";
    static final String KEY_ALIAS = "sketchware_ai_github_token";

    /** Where the AES key comes from: the Android Keystore in the app, a software key in tests. */
    public interface KeySource {
        /** The key; when there is none, a new one if {@code create}, else null. */
        SecretKey key(boolean create) throws GeneralSecurityException;

        void delete();
    }

    /** What ties a session to this phone and this app: ANDROID_ID and the signing certificate in the app. */
    public interface DeviceBinding {
        String device();
    }

    private final SharedPreferences prefs;
    private final KeySource keys;
    private final DeviceBinding binding;

    public TokenStore(SharedPreferences prefs, KeySource keys, DeviceBinding binding) {
        this.prefs = prefs;
        this.keys = keys;
        this.binding = binding;
    }

    public static TokenStore get(Context context) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        return new TokenStore(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE), new AndroidKeystore(), () -> deviceOf(app));
    }

    /** ANDROID_ID + the SHA-256 of the app's signing certificate (each "?" when it can't be read). */
    static String deviceOf(Context context) {
        String androidId = null;
        try {
            androidId = android.provider.Settings.Secure.getString(context.getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID);
        } catch (RuntimeException ignored) {
        }
        String cert = null;
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            byte[] der;
            if (Build.VERSION.SDK_INT >= 28) {
                android.content.pm.PackageInfo info = pm.getPackageInfo(context.getPackageName(),
                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                der = info.signingInfo.getApkContentsSigners()[0].toByteArray();
            } else {
                @SuppressWarnings("deprecation")
                android.content.pm.PackageInfo info = pm.getPackageInfo(context.getPackageName(),
                        android.content.pm.PackageManager.GET_SIGNATURES);
                der = info.signatures[0].toByteArray();
            }
            cert = hex(java.security.MessageDigest.getInstance("SHA-256").digest(der));
        } catch (Exception ignored) {
        }
        return (androidId == null ? "?" : androidId) + "|" + (cert == null ? "?" : cert);
    }

    /** The associated data of a session: SHA-256 of device | user id. */
    private byte[] associatedData(String userId) throws GeneralSecurityException {
        String device = binding == null ? "" : binding.device();
        return java.security.MessageDigest.getInstance("SHA-256")
                .digest((device + "|" + (userId == null ? "" : userId)).getBytes(StandardCharsets.UTF_8));
    }

    /** Sign-in needs the Keystore's AES keys (Android 6.0+). */
    public static boolean supported() {
        return Build.VERSION.SDK_INT >= 23;
    }

    public synchronized void save(String token, String login, String userId) throws GeneralSecurityException {
        SecretKey key = keys.key(true);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD(associatedData(userId));
        byte[] iv = cipher.getIV();
        byte[] sealed = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        String value = "v2:" + b64(iv) + ":" + b64(sealed);
        if (!prefs.edit().putString(KEY_TOKEN, value).putString(KEY_LOGIN, login).putString(KEY_USER_ID, userId).commit()) {
            throw new GeneralSecurityException("Couldn't save the sign-in");
        }
    }

    /** The token, or null when signed out or when it can't be decrypted (the key is gone: then it is removed). */
    public synchronized String token() {
        String value = prefs.getString(KEY_TOKEN, null);
        if (value == null) return null;
        try {
            String[] parts = value.split(":");
            if (parts.length != 3 || !parts[0].equals("v2")) throw new NoKey("unknown format");
            SecretKey key = keys.key(false);
            if (key == null) throw new NoKey("no key");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, unb64(parts[1])));
            cipher.updateAAD(associatedData(prefs.getString(KEY_USER_ID, null)));
            return new String(cipher.doFinal(unb64(parts[2])), StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException | NoKey | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            // another phone, another user id, key deleted, or damaged: signed out
            prefs.edit().remove(KEY_TOKEN).remove(KEY_LOGIN).remove(KEY_USER_ID).apply();
            return null;
        } catch (GeneralSecurityException | RuntimeException e) {
            // a passing Keystore problem: not signed out, the next call tries again
            return null;
        }
    }

    public synchronized String login() {
        return prefs.getString(KEY_TOKEN, null) == null ? null : prefs.getString(KEY_LOGIN, null);
    }

    public synchronized String userId() {
        return prefs.getString(KEY_TOKEN, null) == null ? null : prefs.getString(KEY_USER_ID, null);
    }

    /** Signs out: the token, the login, the id and the key are deleted. */
    public synchronized void clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_LOGIN).remove(KEY_USER_ID).commit();
        keys.delete();
    }

    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return out.toString();
    }

    /** The stored token can never be read again (no key, unknown format): signed out. */
    static final class NoKey extends GeneralSecurityException {
        NoKey(String message) {
            super(message);
        }
    }

    private static String b64(byte[] bytes) {
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
    }

    private static byte[] unb64(String text) {
        return android.util.Base64.decode(text, android.util.Base64.NO_WRAP);
    }

    /** AES-256-GCM key in the Android Keystore, alias {@link #KEY_ALIAS}. */
    static final class AndroidKeystore implements KeySource {
        @Override
        public SecretKey key(boolean create) throws GeneralSecurityException {
            if (!supported()) throw new GeneralSecurityException("GitHub sign-in needs Android 6.0 or newer");
            try {
                KeyStore store = KeyStore.getInstance("AndroidKeyStore");
                store.load(null);
                KeyStore.Entry entry = store.getEntry(KEY_ALIAS, null);
                if (entry instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
                if (!create) return null;
                KeyGenerator generator = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                        "AndroidKeyStore");
                generator.init(new android.security.keystore.KeyGenParameterSpec.Builder(KEY_ALIAS,
                        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                return generator.generateKey();
            } catch (java.io.IOException e) {
                throw new GeneralSecurityException(e);
            }
        }

        @Override
        public void delete() {
            if (!supported()) return;
            try {
                KeyStore store = KeyStore.getInstance("AndroidKeyStore");
                store.load(null);
                store.deleteEntry(KEY_ALIAS);
            } catch (Exception ignored) {
            }
        }
    }
}
