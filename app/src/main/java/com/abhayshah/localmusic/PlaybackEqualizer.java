package com.abhayshah.localmusic;

import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;
import androidx.media3.common.Player;

/** Owns one effect on this player's audio session; failure must never interrupt playback. */
@androidx.media3.common.util.UnstableApi
final class PlaybackEqualizer implements Player.Listener, SharedPreferences.OnSharedPreferenceChangeListener {
    private final androidx.media3.exoplayer.ExoPlayer player;
    private final SharedPreferences settings;
    private Equalizer effect;
    private int session;

    PlaybackEqualizer(androidx.media3.exoplayer.ExoPlayer player, SharedPreferences settings) {
        this.player = player; this.settings = settings;
        status(false, "Start a song to use the equalizer.");
        player.addListener(this);
        settings.registerOnSharedPreferenceChangeListener(this);
        onAudioSessionIdChanged(player.getAudioSessionId());
    }

    @Override public void onAudioSessionIdChanged(int id) {
        if (id == session && effect != null) return;
        release(); session = id;
        if (id <= 0) { status(false, "Start a song to use the equalizer."); return; }
        try { effect = new Equalizer(0, id); apply(); }
        catch (RuntimeException unavailable) { unavailable(); }
    }

    private void apply() {
        if (effect == null) return;
        try {
            int[] levels = levels(settings);
            short[] range = effect.getBandLevelRange();
            for (short band = 0; band < effect.getNumberOfBands(); band++) {
                int level = EqualizerCurve.at(effect.getCenterFreq(band) / 1000, levels);
                effect.setBandLevel(band, (short) Math.max(range[0], Math.min(range[1], level)));
            }
            boolean enabled = settings.getBoolean("eq_enabled", false);
            int result = effect.setEnabled(enabled);
            if (result != android.media.audiofx.AudioEffect.SUCCESS || !effect.hasControl()) { unavailable(); return; }
            status(enabled, enabled ? "On" : "Off");
        } catch (RuntimeException unavailable) { unavailable(); }
    }

    static int[] levels(SharedPreferences settings) {
        int[] levels = new int[5];
        for (int i = 0; i < levels.length; i++) levels[i] = EqualizerCurve.safe(settings.getInt("eq_band_" + i, 0));
        return levels;
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences ignored, String key) {
        if ("eq_enabled".equals(key) || key != null && key.startsWith("eq_band_")) {
            if (effect == null && session > 0) onAudioSessionIdChanged(session); else apply();
        }
    }

    private void status(boolean active, String message) {
        settings.edit().putBoolean("eq_active", active).putString("eq_status", message).apply();
    }
    private void unavailable() { release(); status(false, "Equalizer isn't available on this audio output."); }
    private void release() {
        if (effect != null) { try { effect.release(); } catch (RuntimeException ignored) { } effect = null; }
    }
    void close() {
        settings.unregisterOnSharedPreferenceChangeListener(this);
        player.removeListener(this); release(); status(false, "Start a song to use the equalizer.");
    }
}
