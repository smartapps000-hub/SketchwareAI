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
 * ciphertext ({@code "v1:" + base64(iv) + ":" + base64(ciphertext+tag)}), the user's login and the public OAuth Client
 * ID. Not EncryptedSharedPreferences (deprecated): the Keystore directly.
 */
public final class TokenStore {

    static final String PREFS = "sketchware_ai_github";
    static final String KEY_TOKEN = "token";
    static final String KEY_LOGIN = "login";
    static final String KEY_CLIENT_ID = "client_id";
    static final String KEY_ALIAS = "sketchware_ai_github_token";

    /** Where the AES key comes from: the Android Keystore in the app, a software key in tests. */
    public interface KeySource {
        /** The key; when there is none, a new one if {@code create}, else null. */
        SecretKey key(boolean create) throws GeneralSecurityException;

        void delete();
    }

    private final SharedPreferences prefs;
    private final KeySource keys;

    public TokenStore(SharedPreferences prefs, KeySource keys) {
        this.prefs = prefs;
        this.keys = keys;
    }

    public static TokenStore get(Context context) {
        return new TokenStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), new AndroidKeystore());
    }

    /** Sign-in needs the Keystore's AES keys (Android 6.0+). */
    public static boolean supported() {
        return Build.VERSION.SDK_INT >= 23;
    }

    public synchronized void save(String token, String login) throws GeneralSecurityException {
        SecretKey key = keys.key(true);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] sealed = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        String value = "v1:" + b64(iv) + ":" + b64(sealed);
        if (!prefs.edit().putString(KEY_TOKEN, value).putString(KEY_LOGIN, login).commit()) {
            throw new GeneralSecurityException("Couldn't save the sign-in");
        }
    }

    /** The token, or null when signed out or when it can't be decrypted (the key is gone: then it is removed). */
    public synchronized String token() {
        String value = prefs.getString(KEY_TOKEN, null);
        if (value == null) return null;
        try {
            String[] parts = value.split(":");
            if (parts.length != 3 || !parts[0].equals("v1")) throw new GeneralSecurityException("unknown format");
            SecretKey key = keys.key(false);
            if (key == null) throw new GeneralSecurityException("no key");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, unb64(parts[1])));
            return new String(cipher.doFinal(unb64(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            // restored to another phone, key deleted, or damaged: signed out
            prefs.edit().remove(KEY_TOKEN).remove(KEY_LOGIN).apply();
            return null;
        }
    }

    public synchronized String login() {
        return prefs.getString(KEY_TOKEN, null) == null ? null : prefs.getString(KEY_LOGIN, null);
    }

    /** Signs out: the token, the login and the key are deleted. The Client ID stays. */
    public synchronized void clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_LOGIN).commit();
        keys.delete();
    }

    /** The OAuth app's public Client ID: the one typed in the GitHub screen, else the build's own (strings.xml). */
    public String clientId(String builtIn) {
        String typed = prefs.getString(KEY_CLIENT_ID, null);
        return typed != null && !typed.trim().isEmpty() ? typed.trim() : builtIn == null ? "" : builtIn.trim();
    }

    public void setClientId(String clientId) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId == null ? "" : clientId.trim()).apply();
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
