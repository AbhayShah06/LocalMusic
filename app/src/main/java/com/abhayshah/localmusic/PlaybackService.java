package com.abhayshah.localmusic;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

@androidx.media3.common.util.UnstableApi
public final class PlaybackService extends MediaSessionService {
    static final String PREFS = "playback_state";
    static final String ACTION_SET_SLEEP_TIMER = "com.abhayshah.localmusic.SET_SLEEP_TIMER";
    static final String EXTRA_SLEEP_MINUTES = "sleep_minutes";
    private ExoPlayer player;
    private MediaSession session;
    private SharedPreferences prefs;
    private final Handler stateHandler = new Handler(Looper.getMainLooper());
    private final Runnable sleepPause = () -> {
        if (player != null) player.pause();
        if (prefs != null) prefs.edit().remove("sleep_deadline").apply();
    };
    private final Runnable periodicSave = new Runnable() {
        @Override public void run() { saveState(); stateHandler.postDelayed(this, 15_000); }
    };
    private final Player.Listener persistenceListener = new Player.Listener() {
        @Override public void onEvents(Player player, Player.Events events) { saveState(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        player = new ExoPlayer.Builder(this).build();
        prefs.edit().putInt("audio_session_id", player.getAudioSessionId()).apply();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true);
        session = new MediaSession.Builder(this, player).build();
        player.addListener(persistenceListener);
        restoreQueue();
        restoreSleepTimer();
        stateHandler.postDelayed(periodicSave, 15_000);
    }

    @Override public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SET_SLEEP_TIMER.equals(intent.getAction())) {
            setSleepTimer(intent.getLongExtra(EXTRA_SLEEP_MINUTES, 0));
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) { return session; }

    private void restoreQueue() {
        String saved = prefs.getString("queue", "");
        if (saved.isEmpty()) return;
        List<MediaItem> items = new ArrayList<>();
        ContentResolver resolver = getContentResolver();
        if (saved.startsWith("[")) {
            try {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length() && items.size() < 200; i++) {
                    JSONObject item = array.getJSONObject(i); String id = item.getString("id");
                    if (id.startsWith("remote:")) items.add(new MediaItem.Builder().setMediaId(id).setUri(item.getString("uri"))
                            .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder().setTitle(item.optString("title"))
                                    .setArtist(item.optString("artist")).setAlbumTitle(item.optString("album")).build()).build());
                    else addLocalItem(items, resolver, Long.parseLong(id.substring(6)));
                }
            } catch (Exception ignored) { items.clear(); }
        } else {
        for (String raw : saved.split(",")) {
            try { addLocalItem(items, resolver, Long.parseLong(raw)); } catch (RuntimeException ignored) { }
            if (items.size() >= 200) break;
        }
        }
        if (!items.isEmpty()) {
            int index = Math.min(Math.max(0, prefs.getInt("index", 0)), items.size() - 1);
            player.setMediaItems(items, index, Math.max(0, prefs.getLong("position", 0)));
            player.setShuffleModeEnabled(prefs.getBoolean("shuffle", false));
            player.setRepeatMode(prefs.getInt("repeat", Player.REPEAT_MODE_OFF));
            player.prepare();
            // Resume the queue in a paused state. Playback begins only on an explicit user action.
        }
    }

    private static void addLocalItem(List<MediaItem> items, ContentResolver resolver, long id) {
        Uri uri = Uri.withAppendedPath(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.toString(id));
        try (android.database.Cursor c = resolver.query(uri, new String[]{android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST, android.provider.MediaStore.Audio.Media.ALBUM}, null, null, null)) {
            if (c != null && c.moveToFirst()) items.add(new MediaItem.Builder().setMediaId("local:" + id).setUri(uri)
                    .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder().setTitle(Track.clean(c.getString(0), "Unknown title"))
                            .setArtist(Track.clean(c.getString(1), "Unknown artist")).setAlbumTitle(Track.clean(c.getString(2), "Unknown album")).build()).build());
        }
    }

    private void saveState() {
        if (player == null || prefs == null) return;
        JSONArray saved = new JSONArray();
        int count = Math.min(player.getMediaItemCount(), 200);
        for (int i = 0; i < count; i++) {
            MediaItem item = player.getMediaItemAt(i); JSONObject value = new JSONObject();
            try { value.put("id", item.mediaId); value.put("uri", item.localConfiguration == null ? "" : item.localConfiguration.uri.toString());
                value.put("title", String.valueOf(item.mediaMetadata.title)); value.put("artist", String.valueOf(item.mediaMetadata.artist)); value.put("album", String.valueOf(item.mediaMetadata.albumTitle)); saved.put(value); } catch (Exception ignored) { }
        }
        prefs.edit().putString("queue", saved.toString()).putInt("index", Math.max(0, player.getCurrentMediaItemIndex()))
                .putLong("position", Math.max(0, player.getCurrentPosition())).putBoolean("shuffle", player.getShuffleModeEnabled())
                .putInt("repeat", player.getRepeatMode()).apply();
    }

    private void setSleepTimer(long minutes) {
        stateHandler.removeCallbacks(sleepPause);
        if (minutes <= 0) { prefs.edit().remove("sleep_deadline").apply(); return; }
        long deadline = System.currentTimeMillis() + minutes * 60_000L;
        prefs.edit().putLong("sleep_deadline", deadline).apply();
        stateHandler.postDelayed(sleepPause, Math.max(1, deadline - System.currentTimeMillis()));
    }

    private void restoreSleepTimer() {
        long deadline = prefs.getLong("sleep_deadline", 0);
        if (deadline <= 0) return;
        long delay = deadline - System.currentTimeMillis();
        if (delay <= 0) sleepPause.run(); else stateHandler.postDelayed(sleepPause, delay);
    }

    @Override public void onTaskRemoved(android.content.Intent rootIntent) {
        if (player != null && !player.getPlayWhenReady()) stopSelf();
    }

    @Override public void onDestroy() {
        stateHandler.removeCallbacks(periodicSave);
        stateHandler.removeCallbacks(sleepPause);
        saveState();
        if (player != null) player.removeListener(persistenceListener);
        if (session != null) { session.release(); session = null; }
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
