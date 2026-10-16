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
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

@androidx.media3.common.util.UnstableApi
public final class PlaybackService extends MediaSessionService {
    static final String PREFS = "playback_state";
    static final String ACTION_SET_SLEEP_TIMER = "com.abhayshah.localmusic.SET_SLEEP_TIMER";
    static final String EXTRA_SLEEP_MINUTES = "sleep_minutes";
    static final String ACTION_START_JAM = "com.abhayshah.localmusic.START_JAM";
    static final String ACTION_STOP_JAM = "com.abhayshah.localmusic.STOP_JAM";
    static final String EXTRA_JAM_CODE = "jam_code";
    static final String EXTRA_JAM_TOKEN = "jam_token";
    private ExoPlayer player;
    private MediaSession session;
    private SharedPreferences prefs;
    private final ExecutorService jamIo = Executors.newFixedThreadPool(3);
    private final ExecutorService preloadIo = Executors.newFixedThreadPool(2);
    private final Set<String> jamEntries = ConcurrentHashMap.newKeySet();
    private final Set<String> jamControls = ConcurrentHashMap.newKeySet();
    private final List<String> jamEntryOrder = new ArrayList<>();
    private final Map<String, ResolvedJamTrack> jamResolved = new ConcurrentHashMap<>();
    private int jamCommitCursor;
    private volatile JamClient.Room jamRoom;
    private volatile boolean jamRequestRunning;
    private final Handler stateHandler = new Handler(Looper.getMainLooper());
    private final Runnable sleepPause = () -> {
        if (player != null) player.pause();
        if (prefs != null) prefs.edit().remove("sleep_deadline").apply();
    };
    private final Runnable periodicSave = new Runnable() {
        @Override public void run() { saveState(); stateHandler.postDelayed(this, 15_000); }
    };
    private final Runnable debouncedSave = this::saveState;
    private final Runnable jamPoll = new Runnable() {
        @Override public void run() {
            JamClient.Room room = jamRoom;
            if (room == null || jamRequestRunning) return;
            jamRequestRunning = true;
            jamIo.execute(() -> {
                try {
                    JamClient.State state = new JamClient().state(room);
                    stateHandler.post(() -> {
                        for (JamClient.Entry entry : state.entries) if (jamEntries.add(entry.id)) addJamTrack(entry);
                        for (JamClient.Control control : state.controls) if (jamControls.add(control.id)) applyJamControl(control);
                        publishJamPlayback();
                    });
                } catch (Exception ignored) { }
                finally { jamRequestRunning = false; if (jamRoom != null) stateHandler.postDelayed(this, 700); }
            });
        }
    };
    private boolean currentItemRecorded;
    private final Runnable recordCurrentItem = () -> {
        if (player != null && player.isPlaying() && !currentItemRecorded) {
            ListeningHistory.record(PlaybackService.this, player.getCurrentMediaItem()); currentItemRecorded = true;
        }
    };
    private final Player.Listener persistenceListener = new Player.Listener() {
        @Override public void onMediaItemTransition(MediaItem item, int reason) {
            currentItemRecorded = false; stateHandler.removeCallbacks(recordCurrentItem);
            if (player != null && player.isPlaying()) stateHandler.postDelayed(recordCurrentItem, 30_000);
        }
        @Override public void onIsPlayingChanged(boolean isPlaying) {
            stateHandler.removeCallbacks(recordCurrentItem);
            if (isPlaying && !currentItemRecorded) stateHandler.postDelayed(recordCurrentItem, 30_000);
        }
        @Override public void onEvents(Player player, Player.Events events) {
            stateHandler.removeCallbacks(debouncedSave);
            stateHandler.postDelayed(debouncedSave, 750);
            if (jamRoom != null) stateHandler.postDelayed(PlaybackService.this::publishJamPlayback, 120);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory().setUserAgent("LocalMusic/0.5");
        DefaultDataSource.Factory upstream = new DefaultDataSource.Factory(this, http);
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(
                new DefaultMediaSourceFactory(PlaybackCache.dataSourceFactory(this, upstream))).build();
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
        } else if (intent != null && ACTION_START_JAM.equals(intent.getAction())) {
            jamEntries.clear(); jamControls.clear();
            jamEntryOrder.clear(); jamResolved.clear(); jamCommitCursor = 0;
            jamRoom = new JamClient.Room(intent.getStringExtra(EXTRA_JAM_CODE), intent.getStringExtra(EXTRA_JAM_TOKEN));
            stateHandler.removeCallbacks(jamPoll); stateHandler.post(jamPoll);
        } else if (intent != null && ACTION_STOP_JAM.equals(intent.getAction())) {
            jamRoom = null; stateHandler.removeCallbacks(jamPoll);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) { return session; }

    private void restoreQueue() {
        String saved = prefs.getString("queue", "");
        if (saved.isEmpty()) return;
        if (saved.contains("\"id\":\"b2:") || saved.contains("\"id\":\"online:")) {
            new Thread(() -> {
                List<MediaItem> restored = buildRestoredItems(saved);
                stateHandler.post(() -> applyRestoredItems(restored));
            }, "remote-queue-restore").start();
            return;
        }
        applyRestoredItems(buildRestoredItems(saved));
    }

    private List<MediaItem> buildRestoredItems(String saved) {
        List<MediaItem> items = new ArrayList<>();
        ContentResolver resolver = getContentResolver();
        if (saved.startsWith("[")) {
            try {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length() && items.size() < 200; i++) {
                    JSONObject item = array.getJSONObject(i); String id = item.getString("id");
                    if (id.startsWith("b2:")) {
                        B2Settings settings = new B2Settings(this);
                        if (!settings.configured()) continue;
                        Uri uri = new B2CatalogClient(settings).playbackUri(id.substring(3));
                        items.add(remoteItem(item, id, uri));
                    } else if (id.startsWith("online:")) {
                        Track unresolved = new Track(id, Uri.EMPTY, item.optString("title"), item.optString("artist"), item.optString("album"));
                        Track resolved = new OnlineCatalogSource().resolve(unresolved);
                        items.add(remoteItem(item, id, resolved.playbackUri));
                    } else if (!id.startsWith("local:")) items.add(remoteItem(item, id, Uri.parse(item.getString("uri"))));
                    else addLocalItem(items, resolver, Long.parseLong(id.substring(6)));
                }
            } catch (Exception ignored) { items.clear(); }
        } else {
        for (String raw : saved.split(",")) {
            try { addLocalItem(items, resolver, Long.parseLong(raw)); } catch (RuntimeException ignored) { }
            if (items.size() >= 200) break;
        }
        }
        return items;
    }

    private static MediaItem remoteItem(JSONObject item, String id, Uri uri) {
        MediaItem.Builder value = new MediaItem.Builder().setMediaId(id).setUri(uri)
                .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder().setTitle(item.optString("title"))
                        .setArtist(item.optString("artist")).setAlbumTitle(item.optString("album")).build());
        if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) value.setCustomCacheKey(id);
        return value.build();
    }

    private void applyRestoredItems(List<MediaItem> items) {
        if (!items.isEmpty() && player != null) {
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
            try { value.put("id", item.mediaId); value.put("uri", item.mediaId.startsWith("b2:") || item.localConfiguration == null ? "" : item.localConfiguration.uri.toString());
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

    private void addJamTrack(JamClient.Entry entry) {
        if (player == null) return;
        if (!entry.trackId.startsWith("online:")) return;
        jamEntryOrder.add(entry.id);
        Track unresolved = new Track(entry.trackId, Uri.EMPTY, entry.title, entry.artist, "YouTube Music");
        jamIo.execute(() -> {
            try {
                Track resolved = new OnlineCatalogSource().resolve(unresolved);
                jamResolved.put(entry.id, new ResolvedJamTrack(entry, resolved));
                stateHandler.post(this::flushResolvedJamTracks);
            } catch (RuntimeException ignored) {
                jamResolved.put(entry.id, new ResolvedJamTrack(entry, null));
                stateHandler.post(this::flushResolvedJamTracks);
            }
        });
    }

    /** Parallel resolution is committed in cloud queue order, so web indexes always match playback. */
    private void flushResolvedJamTracks() {
        while (jamCommitCursor < jamEntryOrder.size()) {
            ResolvedJamTrack item = jamResolved.remove(jamEntryOrder.get(jamCommitCursor));
            if (item == null) return;
            jamCommitCursor += 1;
            if (player == null || item.track == null) continue;
            MediaItem media = item.track.toMediaItem();
            if ("next".equals(item.entry.placement)) player.addMediaItem(Math.min(player.getCurrentMediaItemIndex() + 1, player.getMediaItemCount()), media);
            else player.addMediaItem(media);
            preloadIo.execute(() -> { try { PlaybackCache.preload(media); } catch (Exception ignored) { } });
        }
        publishJamPlayback();
    }

    private void applyJamControl(JamClient.Control control) {
        if (player == null) return;
        if ("previous".equals(control.action)) player.seekToPreviousMediaItem();
        else if ("next".equals(control.action)) player.seekToNextMediaItem();
        else if ("toggle".equals(control.action)) { if (player.isPlaying()) player.pause(); else player.play(); }
        else if ("play".equals(control.action) && control.index >= 0) {
            int target = player.getCurrentMediaItemIndex() + 1 + control.index;
            if (target >= 0 && target < player.getMediaItemCount()) { player.seekTo(target, 0); player.play(); }
        }
        else if ("reorder".equals(control.action) && control.index >= 0 && control.toIndex >= 0) {
            int base = player.getCurrentMediaItemIndex() + 1;
            int from = base + control.index, to = base + control.toIndex;
            if (from >= base && from < player.getMediaItemCount() && to >= base && to < player.getMediaItemCount() && from != to) player.moveMediaItem(from, to);
        }
        else if ("move".equals(control.action) && control.index >= 0 && Math.abs(control.direction) == 1) {
            int from = player.getCurrentMediaItemIndex() + 1 + control.index, to = from + control.direction;
            if (from >= 0 && from < player.getMediaItemCount() && to > player.getCurrentMediaItemIndex() && to < player.getMediaItemCount()) player.moveMediaItem(from, to);
        }
    }

    private void publishJamPlayback() {
        JamClient.Room room = jamRoom;
        if (room == null || player == null || player.getCurrentMediaItem() == null) return;
        androidx.media3.common.MediaMetadata current = player.getMediaMetadata();
        String title = Track.clean(String.valueOf(current.title), "Unknown title"), artist = Track.clean(String.valueOf(current.artist), "Unknown artist");
        List<String[]> upcoming = new ArrayList<>(); int start = Math.max(0, player.getCurrentMediaItemIndex() + 1);
        for (int i = start; i < Math.min(player.getMediaItemCount(), start + 20); i++) { androidx.media3.common.MediaMetadata item = player.getMediaItemAt(i).mediaMetadata; upcoming.add(new String[]{Track.clean(String.valueOf(item.title), "Unknown title"), Track.clean(String.valueOf(item.artist), "Unknown artist")}); }
        boolean playing = player.isPlaying(); jamIo.execute(() -> { try { new JamClient().publishPlayback(room, title, artist, playing, upcoming); } catch (Exception ignored) { } });
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
        stateHandler.removeCallbacks(debouncedSave);
        stateHandler.removeCallbacks(recordCurrentItem);
        stateHandler.removeCallbacks(sleepPause);
        stateHandler.removeCallbacks(jamPoll);
        jamRoom = null;
        jamIo.shutdownNow();
        preloadIo.shutdownNow();
        saveState();
        if (player != null) player.removeListener(persistenceListener);
        if (session != null) { session.release(); session = null; }
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }

    private static final class ResolvedJamTrack {
        final JamClient.Entry entry; final Track track;
        ResolvedJamTrack(JamClient.Entry entry, Track track) { this.entry = entry; this.track = track; }
    }
}
