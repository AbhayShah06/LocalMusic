package com.abhayshah.localmusic;

import android.content.Context;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

final class CloudSettings {
    private final android.content.SharedPreferences prefs;
    CloudSettings(Context context) {
        android.content.SharedPreferences encrypted = null;
        try {
            MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            encrypted = EncryptedSharedPreferences.create(context, "cloud_music_credentials", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception ignored) { }
        prefs = encrypted;
    }
    boolean configured() { return !url().isEmpty() && !token().isEmpty(); }
    boolean available() { return prefs != null; }
    String url() { return prefs == null ? "" : prefs.getString("url", ""); }
    String token() { return prefs == null ? "" : prefs.getString("token", ""); }
    void save(String url, String token) { if (prefs == null) throw new IllegalStateException("Secure storage is unavailable"); String clean = url.trim(); while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1); prefs.edit().putString("url", clean).putString("token", token.trim()).apply(); }
}
