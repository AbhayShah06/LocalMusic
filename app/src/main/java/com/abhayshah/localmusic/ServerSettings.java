package com.abhayshah.localmusic;

import android.content.Context;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

final class ServerSettings {
    private static final String PREFS = "navidrome_credentials";
    private final android.content.SharedPreferences prefs;

    ServerSettings(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            prefs = EncryptedSharedPreferences.create(context, PREFS, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception error) { throw new IllegalStateException("Secure credential storage is unavailable", error); }
    }

    boolean configured() { return !url().isEmpty() && !username().isEmpty() && !password().isEmpty(); }
    String url() { return prefs.getString("url", ""); }
    String username() { return prefs.getString("username", ""); }
    String password() { return prefs.getString("password", ""); }
    void save(String url, String username, String password) {
        String clean = url.trim(); while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        prefs.edit().putString("url", clean).putString("username", username.trim()).putString("password", password).apply();
    }
}
