package com.abhayshah.localmusic;

import android.content.SharedPreferences;
import androidx.media3.common.C;
import androidx.media3.common.Metadata;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.extractor.metadata.flac.VorbisComment;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;

/** ReplayGain is chosen once at the track boundary, never from changing PCM levels. */
@androidx.media3.common.util.UnstableApi
final class TrackVolume implements Player.Listener, SharedPreferences.OnSharedPreferenceChangeListener {
    private final Player player;
    private final SharedPreferences settings;
    private boolean decided;
    private float trackGain = 1f;

    TrackVolume(Player player, SharedPreferences settings) {
        this.player = player;
        this.settings = settings;
        player.addListener(this);
        settings.registerOnSharedPreferenceChangeListener(this);
    }

    @Override public void onEvents(Player ignored, Player.Events events) {
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            decided = false;
            trackGain = 1f;
        }
        if (!decided) {
            for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
                for (int i = 0; i < group.length; i++) if (group.isTrackSelected(i)) {
                    trackGain = fromMetadata(group.getTrackFormat(i).metadata);
                    decided = true;
                    break;
                }
                if (decided) break;
            }
            apply();
        }
    }

    static float fromMetadata(Metadata metadata) {
        if (metadata == null) return 1f;
        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);
            String key = null, value = null;
            if (entry instanceof VorbisComment) {
                VorbisComment comment = (VorbisComment) entry;
                key = comment.key; value = comment.value;
            } else if (entry instanceof TextInformationFrame) {
                TextInformationFrame frame = (TextInformationFrame) entry;
                if ("TXXX".equals(frame.id)) { key = frame.description; value = frame.value; }
            }
            if (key != null && "REPLAYGAIN_TRACK_GAIN".equalsIgnoreCase(key.trim())) return TrackGain.fromReplayGain(value);
        }
        return 1f;
    }

    private void apply() {
        float gain = settings.getBoolean("volume_leveling", true) ? trackGain : 1f;
        if (settings.getBoolean("eq_active", false)) gain *= EqualizerCurve.headroom(PlaybackEqualizer.levels(settings));
        if (player.getVolume() != gain) player.setVolume(gain);
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences ignored, String key) {
        // Only an explicit user toggle may change this gain within a song.
        if ("volume_leveling".equals(key) || "eq_active".equals(key) || key != null && key.startsWith("eq_band_")) apply();
    }

    void close() {
        settings.unregisterOnSharedPreferenceChangeListener(this);
        player.removeListener(this);
    }
}
