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
        if (prefs != null && BuildConfig.IS_PERSONAL && bundled() && !prefs.contains("key_id")) {
            prefs.edit().putString("key_id", BuildConfig.B2_KEY_ID)
                    .putString("application_key", BuildConfig.B2_APPLICATION_KEY)
                    .putString("bucket", BuildConfig.B2_BUCKET).apply();
        }
    }

    boolean available() { return prefs != null; }
    boolean configured() { return !keyId().isEmpty() && !applicationKey().isEmpty() && !bucket().isEmpty(); }
    boolean bundled() { return !BuildConfig.B2_KEY_ID.isEmpty() && !BuildConfig.B2_APPLICATION_KEY.isEmpty(); }
    String keyId() { return prefs == null ? BuildConfig.B2_KEY_ID : prefs.getString("key_id", BuildConfig.B2_KEY_ID); }
    String applicationKey() { return prefs == null ? BuildConfig.B2_APPLICATION_KEY : prefs.getString("application_key", BuildConfig.B2_APPLICATION_KEY); }
    String bucket() { return prefs == null ? BuildConfig.B2_BUCKET : prefs.getString("bucket", BuildConfig.B2_BUCKET); }

    void save(String keyId, String applicationKey, String bucket) {
        if (prefs == null) throw new IllegalStateException("Secure storage is unavailable");
        prefs.edit().putString("key_id", keyId.trim()).putString("application_key", applicationKey.trim())
                .putString("bucket", bucket.trim()).apply();
    }
}
