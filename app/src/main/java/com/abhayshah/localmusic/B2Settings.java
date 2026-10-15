package com.abhayshah.localmusic;

import android.content.Context;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

final class B2Settings {
    private final android.content.SharedPreferences prefs;

    B2Settings(Context context) {
        android.content.SharedPreferences encrypted = null;
        try {
            MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            encrypted = EncryptedSharedPreferences.create(context, "b2_music_credentials", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception ignored) { }
        prefs = encrypted;
    }

    boolean available() { return prefs != null; }
    boolean configured() { return !keyId().isEmpty() && !applicationKey().isEmpty() && !bucket().isEmpty(); }
    String keyId() { return prefs == null ? "" : prefs.getString("key_id", ""); }
    String applicationKey() { return prefs == null ? "" : prefs.getString("application_key", ""); }
    String bucket() { return prefs == null ? "LocalMusic" : prefs.getString("bucket", "LocalMusic"); }

    void save(String keyId, String applicationKey, String bucket) {
        if (prefs == null) throw new IllegalStateException("Secure storage is unavailable");
        prefs.edit().putString("key_id", keyId.trim()).putString("application_key", applicationKey.trim())
                .putString("bucket", bucket.trim()).apply();
    }
}
