package com.abhayshah.localmusic;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.common.Player;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.media3.common.util.UnstableApi
public final class MainActivity extends Activity {
    private static final int PAGE_SIZE = 100;
    private static final int WINDOW_SIZE = 400;
    private final List<Track> tracks = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ArrayAdapter<String> adapter;
    private EditText search;
    private TextView status, nowPlaying;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private int offset;
    private boolean loading, exhausted;
    private String query = "";
    private String group = "";
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private Runnable seekTicker;
    private final Player.Listener uiPlayerListener = new Player.Listener() {
        @Override public void onMediaItemTransition(androidx.media3.common.MediaItem item, int reason) { updateNowPlaying(); }
        @Override public void onIsPlayingChanged(boolean isPlaying) { updateNowPlaying(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        nowPlaying = findViewById(R.id.nowPlaying);
        search = findViewById(R.id.search);
        ListView list = findViewById(R.id.tracks);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> playFrom(position));
        list.setOnItemLongClickListener((parent, view, position, id) -> { showTrackActions(position); return true; });
        list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(android.widget.AbsListView v, int state) { }
            @Override public void onScroll(android.widget.AbsListView v, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 8) loadNextPage();
            }
        });
        findViewById(R.id.playPause).setOnClickListener(v -> { if (controller != null) { if (controller.isPlaying()) controller.pause(); else controller.play(); } });
        findViewById(R.id.previous).setOnClickListener(v -> { if (controller != null) controller.seekToPreviousMediaItem(); });
        findViewById(R.id.next).setOnClickListener(v -> { if (controller != null) controller.seekToNextMediaItem(); });
        findViewById(R.id.queue).setOnClickListener(v -> showQueue());
        findViewById(R.id.shuffle).setOnClickListener(v -> toggleShuffle());
        findViewById(R.id.repeat).setOnClickListener(v -> toggleRepeat());
        findViewById(R.id.favorite).setOnClickListener(v -> toggleCurrentFavorite());
        findViewById(R.id.favorites).setOnClickListener(v -> loadSavedTracks("favorites", "Favorites"));
        findViewById(R.id.playlists).setOnClickListener(v -> showPlaylists());
        findViewById(R.id.sleep).setOnClickListener(v -> chooseSleepTimer());
        findViewById(R.id.equalizer).setOnClickListener(v -> openEqualizer());
        SeekBar seek = findViewById(R.id.seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { if (fromUser && controller != null && controller.getDuration() > 0) controller.seekTo((controller.getDuration() * progress) / 1000); }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        findViewById(R.id.songs).setOnClickListener(v -> setGroup(""));
        findViewById(R.id.albums).setOnClickListener(v -> showGroups(false));
        findViewById(R.id.artists).setOnClickListener(v -> showGroups(true));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); resetAndLoad(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) resetAndLoad();
        else requestPermissions(new String[]{permission}, 12);
    }

    private void connect() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try { controller = controllerFuture.get(); controller.addListener(uiPlayerListener); updateNowPlaying(); }
            catch (Exception e) { status.setText("Playback service could not be connected."); }
        }, command -> main.post(command));
    }

    @Override public void onStart() { super.onStart(); connect(); }
    @Override public void onStop() { ticker.removeCallbacksAndMessages(null); if (controller != null) controller.removeListener(uiPlayerListener); if (controllerFuture != null) MediaController.releaseFuture(controllerFuture); controller = null; super.onStop(); }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 12 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) resetAndLoad();
        else status.setText("Music permission denied. Nothing was scanned.");
    }

    private void setGroup(String value) { group = value; query = ""; search.setText(""); resetAndLoad(); }

    private void resetAndLoad() {
        offset = 0; exhausted = false; loading = false; tracks.clear(); adapter.clear();
        loadNextPage();
    }

    private void loadNextPage() {
        if (loading || exhausted || !group.isEmpty() || checkSelfPermission(Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return;
        loading = true;
        int pageOffset = offset;
        String pageQuery = query;
        io.execute(() -> {
            List<Track> page = new MediaStoreCatalogSource(getContentResolver()).page(pageQuery, pageOffset, PAGE_SIZE);
            main.post(() -> {
                loading = false;
                if (!pageQuery.equals(query)) return;
                if (tracks.size() + page.size() > WINDOW_SIZE) {
                    int trim = Math.min(PAGE_SIZE, tracks.size());
                    for (int i = 0; i < trim; i++) { tracks.remove(0); adapter.remove(adapter.getItem(0)); }
                }
                tracks.addAll(page); offset += page.size(); exhausted = page.size() < PAGE_SIZE;
                for (Track t : page) adapter.add(t.title + "\n" + t.artist + " · " + t.album);
                status.setText(tracks.size() + (exhausted ? " songs" : "+ songs") + " on this phone");
            });
        });
    }

    private void showGroups(boolean artists) {
        group = artists ? "artist" : "album";
        tracks.clear(); adapter.clear();
        io.execute(() -> {
            List<String> labels = new ArrayList<>();
            String[] columns = artists ? new String[]{android.provider.MediaStore.Audio.Artists._ID, android.provider.MediaStore.Audio.Artists.ARTIST}
                    : new String[]{android.provider.MediaStore.Audio.Albums._ID, android.provider.MediaStore.Audio.Albums.ALBUM, android.provider.MediaStore.Audio.Albums.ARTIST};
            android.net.Uri uri = artists ? android.provider.MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI : android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
            try (android.database.Cursor c = getContentResolver().query(uri, columns, null, null, columns[1] + " COLLATE NOCASE ASC")) {
                if (c != null) while (c.moveToNext() && labels.size() < 200) labels.add(c.getLong(0) + "\u0001" + (artists ? Track.clean(c.getString(1), "Unknown artist") : Track.clean(c.getString(1), "Unknown album") + " · " + Track.clean(c.getString(2), "Unknown artist")));
            }
            main.post(() -> { adapter.clear(); for (String label : labels) adapter.add(label.substring(label.indexOf('\u0001') + 1)); status.setText(labels.size() + (artists ? " artists" : " albums") + " (first 200)"); });
        });
    }

    private void playFrom(int position) {
        if (group.isEmpty()) {
            if (controller == null || tracks.isEmpty()) return;
            List<androidx.media3.common.MediaItem> queue = new ArrayList<>();
            int end = Math.min(tracks.size(), position + 200);
            for (int i = position; i < end; i++) queue.add(tracks.get(i).toMediaItem());
            controller.setMediaItems(queue, 0, 0); controller.prepare(); controller.play(); updateNowPlaying();
        } else {
            // Group selection narrows the song search using the selected group label.
            String selectedGroup = group;
            String value = adapter.getItem(position);
            int split = value.indexOf(" · ");
            setGroup("");
            search.setText(split >= 0 && selectedGroup.equals("album") ? value.substring(0, split) : value);
        }
    }

    private void updateNowPlaying() {
        if (controller == null || controller.getMediaMetadata().title == null) nowPlaying.setText("Nothing playing");
        else nowPlaying.setText(controller.getMediaMetadata().title + " — " + Track.clean(String.valueOf(controller.getMediaMetadata().artist), "Unknown artist"));
        refreshProgress();
    }

    private void refreshProgress() {
        if (controller == null) return;
        updateProgressOnly();
        if (seekTicker == null) seekTicker = () -> { updateProgressOnly(); ticker.postDelayed(seekTicker, 1000); };
        ticker.removeCallbacks(seekTicker); ticker.postDelayed(seekTicker, 1000);
    }

    private void updateProgressOnly() {
        if (controller == null) return;
        long duration = controller.getDuration(), position = controller.getCurrentPosition();
        SeekBar seek = findViewById(R.id.seek);
        seek.setProgress(duration > 0 ? (int) Math.min(1000, position * 1000 / duration) : 0);
    }

    private void showTrackActions(int position) {
        if (!group.isEmpty() || position < 0 || position >= tracks.size()) return;
        Track track = tracks.get(position);
        new AlertDialog.Builder(this).setTitle(track.title).setItems(new String[]{"Play now", "Play next", "Add to queue", "Add to favorites", "Add to playlist"}, (d, which) -> {
            if (controller == null) return;
            if (which == 0) playFrom(position);
            else if (which == 1 || which == 2) {
                androidx.media3.common.MediaItem item = track.toMediaItem();
                if (which == 1 && controller.getMediaItemCount() > 0) controller.addMediaItem(Math.min(controller.getCurrentMediaItemIndex() + 1, controller.getMediaItemCount()), item);
                else controller.addMediaItem(item);
                Toast.makeText(this, which == 1 ? "Queued next" : "Added to queue", Toast.LENGTH_SHORT).show();
            } else if (which == 3) { toggleFavorite(track.id); }
            else addTrackToPlaylist(track);
        }).show();
    }

    private void showQueue() {
        if (controller == null || controller.getMediaItemCount() == 0) { Toast.makeText(this, "Queue is empty", Toast.LENGTH_SHORT).show(); return; }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < controller.getMediaItemCount(); i++) {
            androidx.media3.common.MediaMetadata m = controller.getMediaItemAt(i).mediaMetadata;
            labels.add((i == controller.getCurrentMediaItemIndex() ? "▶  " : "") + Track.clean(String.valueOf(m.title), "Unknown title") + " — " + Track.clean(String.valueOf(m.artist), "Unknown artist"));
        }
        new AlertDialog.Builder(this).setTitle("Queue · tap to play, hold to edit")
                .setItems(labels.toArray(new String[0]), (dialog, index) -> controller.seekTo(index))
                .setOnCancelListener(d -> { }).show().getListView().setOnItemLongClickListener((parent, view, index, id) -> {
                    String[] actions = index == controller.getCurrentMediaItemIndex() ? new String[]{"Remove"} : new String[]{"Move up", "Move down", "Remove"};
                    new AlertDialog.Builder(this).setItems(actions, (a, choice) -> {
                        if (index == controller.getCurrentMediaItemIndex()) controller.removeMediaItem(index);
                        else if (choice == 0) controller.moveMediaItem(index, Math.max(0, index - 1));
                        else if (choice == 1) controller.moveMediaItem(index, Math.min(controller.getMediaItemCount() - 1, index + 1));
                        else controller.removeMediaItem(index);
                    }).show(); return true;
                });
    }

    private void toggleShuffle() {
        if (controller == null) return;
        boolean enabled = !controller.getShuffleModeEnabled(); controller.setShuffleModeEnabled(enabled);
        Toast.makeText(this, enabled ? "Shuffle on" : "Shuffle off", Toast.LENGTH_SHORT).show();
    }

    private void toggleRepeat() {
        if (controller == null) return;
        int nextMode = controller.getRepeatMode() == androidx.media3.common.Player.REPEAT_MODE_OFF ? androidx.media3.common.Player.REPEAT_MODE_ALL
                : controller.getRepeatMode() == androidx.media3.common.Player.REPEAT_MODE_ALL ? androidx.media3.common.Player.REPEAT_MODE_ONE : androidx.media3.common.Player.REPEAT_MODE_OFF;
        controller.setRepeatMode(nextMode);
        Toast.makeText(this, nextMode == 0 ? "Repeat off" : nextMode == 2 ? "Repeat all" : "Repeat one", Toast.LENGTH_SHORT).show();
    }

    private void saveFavorite(long id) {
        android.content.SharedPreferences prefs = getSharedPreferences("library", MODE_PRIVATE);
        java.util.Set<String> ids = new java.util.HashSet<>(prefs.getStringSet("favorites", new java.util.HashSet<>()));
        ids.add(Long.toString(id)); prefs.edit().putStringSet("favorites", ids).apply();
    }

    private void toggleFavorite(long id) {
        android.content.SharedPreferences prefs = getSharedPreferences("library", MODE_PRIVATE);
        java.util.Set<String> ids = new java.util.HashSet<>(prefs.getStringSet("favorites", new java.util.HashSet<>()));
        boolean removed = ids.remove(Long.toString(id));
        if (!removed) ids.add(Long.toString(id));
        prefs.edit().putStringSet("favorites", ids).apply();
        Toast.makeText(this, removed ? "Removed from favorites" : "Added to favorites", Toast.LENGTH_SHORT).show();
    }

    private void loadSavedTracks(String key, String label) {
        java.util.Set<String> ids = getSharedPreferences("library", MODE_PRIVATE).getStringSet(key, new java.util.HashSet<>());
        io.execute(() -> {
            List<Track> found = new ArrayList<>();
            int loaded = 0;
            for (String id : ids) {
                if (loaded++ >= 200) break;
                try (android.database.Cursor c = getContentResolver().query(android.net.Uri.withAppendedPath(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        new String[]{android.provider.MediaStore.Audio.Media._ID, android.provider.MediaStore.Audio.Media.TITLE, android.provider.MediaStore.Audio.Media.ARTIST, android.provider.MediaStore.Audio.Media.ALBUM, android.provider.MediaStore.Audio.Media.ALBUM_ID}, null, null, null)) {
                    if (c != null && c.moveToFirst()) found.add(new Track(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)));
                } catch (RuntimeException ignored) { }
            }
            main.post(() -> { group = ""; tracks.clear(); tracks.addAll(found); adapter.clear(); for (Track t : found) adapter.add(t.title + "\n" + t.artist + " · " + t.album); status.setText(found.size() + " " + label.toLowerCase()); });
        });
    }

    private void toggleCurrentFavorite() {
        if (controller == null || controller.getCurrentMediaItem() == null) return;
        String mediaId = controller.getCurrentMediaItem().mediaId;
        if (!mediaId.startsWith("local:")) return;
        android.content.SharedPreferences prefs = getSharedPreferences("library", MODE_PRIVATE);
        java.util.Set<String> ids = new java.util.HashSet<>(prefs.getStringSet("favorites", new java.util.HashSet<>()));
        String id = mediaId.substring(6);
        if (ids.remove(id)) Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show();
        else { ids.add(id); Toast.makeText(this, "Added to favorites", Toast.LENGTH_SHORT).show(); }
        prefs.edit().putStringSet("favorites", ids).apply();
    }

    private void addTrackToPlaylist(Track track) {
        android.content.SharedPreferences prefs = getSharedPreferences("library", MODE_PRIVATE);
        String name = prefs.getString("last_playlist", "");
        EditText input = new EditText(this); input.setHint("Playlist name"); input.setText(name);
        new AlertDialog.Builder(this).setTitle("Add to playlist").setView(input).setPositiveButton("Save", (d, w) -> {
            String playlist = input.getText().toString().trim(); if (playlist.isEmpty()) return;
            String key = "playlist:" + playlist;
            java.util.Set<String> ids = new java.util.HashSet<>(prefs.getStringSet(key, new java.util.HashSet<>()));
            ids.add(Long.toString(track.id)); prefs.edit().putStringSet(key, ids).putString("last_playlist", playlist).apply();
            Toast.makeText(this, "Added to " + playlist, Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Cancel", null).show();
    }

    private void showPlaylists() {
        android.content.SharedPreferences prefs = getSharedPreferences("library", MODE_PRIVATE);
        List<String> names = new ArrayList<>();
        for (String key : prefs.getAll().keySet()) if (key.startsWith("playlist:")) names.add(key.substring(9));
        names.add("+ Create playlist");
        new AlertDialog.Builder(this).setTitle("Playlists").setItems(names.toArray(new String[0]), (d, which) -> {
            String name = names.get(which);
            if (name.startsWith("+")) createPlaylist(); else loadPlaylist(name);
        }).show();
    }

    private void createPlaylist() {
        EditText input = new EditText(this); input.setHint("Playlist name");
        new AlertDialog.Builder(this).setTitle("New playlist").setView(input).setPositiveButton("Create", (d, w) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) getSharedPreferences("library", MODE_PRIVATE).edit().putStringSet("playlist:" + name, new java.util.HashSet<>()).putString("last_playlist", name).apply();
        }).setNegativeButton("Cancel", null).show();
    }

    private void loadPlaylist(String name) {
        loadSavedTracks("playlist:" + name, "songs in " + name);
    }

    private void chooseSleepTimer() {
        String[] choices = {"Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes"};
        long[] minutes = {0, 15, 30, 45, 60};
        new AlertDialog.Builder(this).setTitle("Sleep timer").setItems(choices, (d, which) -> {
            Intent timer = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_SET_SLEEP_TIMER)
                    .putExtra(PlaybackService.EXTRA_SLEEP_MINUTES, minutes[which]);
            startService(timer);
            Toast.makeText(this, minutes[which] == 0 ? "Sleep timer off" : "Playback will pause in " + minutes[which] + " minutes", Toast.LENGTH_SHORT).show();
        }).show();
    }

    @androidx.media3.common.util.UnstableApi
    private void openEqualizer() {
        int audioSessionId = getSharedPreferences(PlaybackService.PREFS, MODE_PRIVATE).getInt("audio_session_id", androidx.media3.common.C.AUDIO_SESSION_ID_UNSET);
        if (controller == null || audioSessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
            Toast.makeText(this, "Start playback first to open the system equalizer", Toast.LENGTH_SHORT).show(); return;
        }
        Intent intent = new Intent(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
        intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC);
        if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
        else Toast.makeText(this, "No system equalizer is available on this device", Toast.LENGTH_SHORT).show();
    }

    @Override public void onDestroy() { io.shutdown(); super.onDestroy(); }
}
