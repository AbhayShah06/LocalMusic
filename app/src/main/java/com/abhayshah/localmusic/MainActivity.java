package com.abhayshah.localmusic;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

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
    private LibraryAdapter adapter;
    private EditText search;
    private TextView status, nowPlaying, nowPlayingArtist;
    private View permissionPanel;
    private static final int AUDIO_PERMISSION_REQUEST = 12;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 13;
    private static final int IMPORT_PLAYLIST_REQUEST = 14;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private int offset;
    private boolean loading, exhausted;
    private boolean remoteMode;
    private boolean b2Mode;
    private ServerSettings serverSettings;
    private B2Settings b2Settings;
    private String query = "";
    private String group = "";
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private Runnable seekTicker;
    private final Handler jamTicker = new Handler(Looper.getMainLooper());
    private JamClient.Room activeJam;
    private final java.util.Map<String, Track> jamCatalog = new java.util.HashMap<>();
    private final java.util.Set<String> seenJamEntries = new java.util.HashSet<>();
    private final Player.Listener uiPlayerListener = new Player.Listener() {
        @Override public void onMediaItemTransition(androidx.media3.common.MediaItem item, int reason) { updateNowPlaying(); }
        @Override public void onIsPlayingChanged(boolean isPlaying) { updateNowPlaying(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        UpdateManager.attach(this, findViewById(R.id.root));
        status = findViewById(R.id.status);
        nowPlaying = findViewById(R.id.nowPlaying);
        nowPlayingArtist = findViewById(R.id.nowPlayingArtist);
        permissionPanel = findViewById(R.id.permissionPanel);
        search = findViewById(R.id.search);
        serverSettings = new ServerSettings(this);
        b2Settings = new B2Settings(this);
        findViewById(R.id.source).setOnClickListener(v -> chooseSource());
        ListView list = findViewById(R.id.tracks);
        adapter = new LibraryAdapter(this, tracks);
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
        findViewById(R.id.playlists).setOnClickListener(v -> showPlaylists());
        findViewById(R.id.more).setOnClickListener(v -> showMoreMenu());
        findViewById(R.id.miniPlayer).setOnClickListener(v -> showNowPlaying());
        findViewById(R.id.grantPermission).setOnClickListener(v -> requestAudioPermission());
        findViewById(R.id.permissionSettings).setOnClickListener(v -> openAppSettings());
        SeekBar seek = findViewById(R.id.seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { if (fromUser && controller != null && controller.getDuration() > 0) controller.seekTo((controller.getDuration() * progress) / 1000); }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        findViewById(R.id.songs).setOnClickListener(v -> { selectTab(R.id.songs); setGroup(""); });
        findViewById(R.id.albums).setOnClickListener(v -> { selectTab(R.id.albums); showGroups(false); });
        findViewById(R.id.artists).setOnClickListener(v -> { selectTab(R.id.artists); showGroups(true); });
        findViewById(R.id.playlists).setOnClickListener(v -> { selectTab(R.id.playlists); showPlaylists(); });
        selectTab(R.id.songs);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); resetAndLoad(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        if (b2Settings.bundled()) {
            remoteMode = true; b2Mode = true; permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE);
            ((TextView)findViewById(R.id.source)).setText("Backblaze  ▾"); resetAndLoad();
        } else refreshPermissionState();
    }

    private void connect() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try { controller = controllerFuture.get(); controller.addListener(uiPlayerListener); updateNowPlaying(); }
            catch (Exception e) { status.setText("Playback service could not be connected."); }
        }, command -> main.post(command));
    }

    private void reconnectPlaybackService() {
        if (controller != null) controller.removeListener(uiPlayerListener);
        if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
        controller = null; controllerFuture = null;
        stopService(new Intent(this, PlaybackService.class));
        main.postDelayed(this::connect, 300);
    }

    @Override public void onStart() { super.onStart(); connect(); }
    @Override public void onStop() { ticker.removeCallbacksAndMessages(null); if (controller != null) controller.removeListener(uiPlayerListener); if (controllerFuture != null) MediaController.releaseFuture(controllerFuture); controller = null; super.onStop(); }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == AUDIO_PERMISSION_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                refreshPermissionState();
                requestNotificationPermissionIfNeeded();
            } else showPermissionPanel(true);
        }
    }

    @Override protected void onResume() { super.onResume(); refreshPermissionState(); }

    private String audioPermission() { return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE; }

    private boolean hasAudioPermission() { return checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED; }

    private void requestAudioPermission() { requestPermissions(new String[]{audioPermission()}, AUDIO_PERMISSION_REQUEST); }

    private void refreshPermissionState() {
        if (permissionPanel == null || remoteMode) return;
        if (hasAudioPermission()) {
            permissionPanel.setVisibility(View.GONE);
            findViewById(R.id.tracks).setVisibility(View.VISIBLE);
            if (tracks.isEmpty() && !loading) resetAndLoad();
        } else showPermissionPanel(!shouldShowRequestPermissionRationale(audioPermission()));
    }

    private void showPermissionPanel(boolean showSettings) {
        permissionPanel.setVisibility(View.VISIBLE);
        findViewById(R.id.tracks).setVisibility(View.GONE);
        findViewById(R.id.permissionSettings).setVisibility(showSettings ? View.VISIBLE : View.GONE);
        status.setText("Music access is needed for your on-device library");
    }

    private void openAppSettings() {
        startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
    }

    private void selectTab(int selectedId) {
        int[] ids = {R.id.songs, R.id.albums, R.id.artists, R.id.playlists};
        for (int id : ids) findViewById(id).setSelected(id == selectedId);
    }

    private void setGroup(String value) { group = value; query = ""; search.setText(""); resetAndLoad(); }

    private void resetAndLoad() {
        offset = 0; exhausted = false; loading = false; tracks.clear(); adapter.clear();
        loadNextPage();
    }

    private void loadNextPage() {
        if (loading || exhausted || !group.isEmpty()) return;
        if (!remoteMode && checkSelfPermission(Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return;
        loading = true;
        int pageOffset = offset;
        String pageQuery = query;
        io.execute(() -> {
            List<Track> page;
            try { page = b2Mode ? new B2CatalogClient(b2Settings).page(pageQuery, pageOffset, PAGE_SIZE) : remoteMode ? new OpenSubsonicClient(serverSettings).page(pageQuery, pageOffset, PAGE_SIZE) : new MediaStoreCatalogSource(getContentResolver()).page(pageQuery, pageOffset, PAGE_SIZE); }
            catch (RuntimeException error) { main.post(() -> { loading = false; status.setText("Could not reach this music source. Check its connection settings."); }); return; }
            main.post(() -> {
                loading = false;
                if (!pageQuery.equals(query)) return;
                if (tracks.size() + page.size() > WINDOW_SIZE) {
                    int trim = Math.min(PAGE_SIZE, tracks.size());
                    for (int i = 0; i < trim; i++) { tracks.remove(0); adapter.remove(adapter.getItem(0)); }
                }
                tracks.addAll(page); offset += page.size(); exhausted = page.size() < PAGE_SIZE;
                for (Track t : page) adapter.add(t.title + "\n" + t.artist + " · " + t.album);
                status.setText(tracks.size() + (exhausted ? " songs" : "+ songs") + (b2Mode ? " in Backblaze" : remoteMode ? " from Navidrome" : " on this phone"));
            });
        });
    }

    private void showGroups(boolean artists) {
        if (remoteMode) { Toast.makeText(this, "Use search to find remote albums and artists for now.", Toast.LENGTH_SHORT).show(); return; }
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
        if (controller == null || controller.getMediaMetadata().title == null) {
            nowPlaying.setText("Nothing playing"); nowPlayingArtist.setText("Choose a song");
        } else {
            nowPlaying.setText(controller.getMediaMetadata().title);
            nowPlayingArtist.setText(Track.clean(String.valueOf(controller.getMediaMetadata().artist), "Unknown artist"));
        }
        ((TextView)findViewById(R.id.playPause)).setText(controller != null && controller.isPlaying() ? "Ⅱ" : "▶");
        refreshProgress();
    }

    private void showNowPlaying() {
        if (controller == null || controller.getCurrentMediaItem() == null) { Toast.makeText(this, "Choose a song first", Toast.LENGTH_SHORT).show(); return; }
        String title = Track.clean(String.valueOf(controller.getMediaMetadata().title), "Now playing");
        String artist = Track.clean(String.valueOf(controller.getMediaMetadata().artist), "Unknown artist");
        String[] actions = {controller.isPlaying() ? "Pause" : "Play", "Previous", "Next", "Queue", "Shuffle", "Repeat", "Favorite", "Sleep timer", "Equalizer"};
        new AlertDialog.Builder(this).setTitle(title).setMessage(artist).setItems(actions, (d, which) -> {
            if (which == 0) { if (controller.isPlaying()) controller.pause(); else controller.play(); }
            else if (which == 1) controller.seekToPreviousMediaItem();
            else if (which == 2) controller.seekToNextMediaItem();
            else if (which == 3) showQueue();
            else if (which == 4) toggleShuffle();
            else if (which == 5) toggleRepeat();
            else if (which == 6) toggleCurrentFavorite();
            else if (which == 7) chooseSleepTimer();
            else openEqualizer();
        }).show();
    }

    private void showMoreMenu() {
        String[] actions = {"Start a Jam", "Music source", "Import playlist", "Favorites", "Playlists", "Sleep timer", "Equalizer", "Music permission"};
        new AlertDialog.Builder(this).setTitle("Local Music").setItems(actions, (d, which) -> {
            if (which == 0) startJam();
            else if (which == 1) chooseSource();
            else if (which == 2) choosePlaylistFile();
            else if (which == 3) { selectTab(R.id.playlists); loadSavedTracks("favorites", "Favorites"); }
            else if (which == 4) { selectTab(R.id.playlists); showPlaylists(); }
            else if (which == 5) chooseSleepTimer();
            else if (which == 6) openEqualizer();
            else if (hasAudioPermission()) Toast.makeText(this, "Music access is allowed", Toast.LENGTH_SHORT).show();
            else requestAudioPermission();
        }).show();
    }

    private void startJam() {
        if (activeJam != null) { showJam(activeJam); return; }
        status.setText("PREPARING JAM");
        io.execute(() -> {
            try {
                List<Track> catalog = loadJamCatalog();
                if (catalog.isEmpty()) throw new IllegalStateException("Your selected library is empty");
                JamClient.Room room = new JamClient().create(catalog);
                jamCatalog.clear(); for (Track track : catalog) jamCatalog.put(track.stableId, track);
                seenJamEntries.clear(); activeJam = room;
                main.post(() -> { status.setText(catalog.size() + " SONGS READY FOR JAM"); showJam(room); pollJam(); });
            } catch (Exception error) { main.post(() -> { status.setText("JAM COULD NOT START"); Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }); }
        });
    }

    private List<Track> loadJamCatalog() {
        if (b2Mode) return new B2CatalogClient(b2Settings).page("", 0, 1200);
        if (remoteMode) return new OpenSubsonicClient(serverSettings).page("", 0, 1200);
        List<Track> all = new ArrayList<>(); MediaStoreCatalogSource source = new MediaStoreCatalogSource(getContentResolver());
        for (int offset = 0; offset < 1200; offset += 200) { List<Track> page = source.page("", offset, 200); all.addAll(page); if (page.size() < 200) break; }
        return all;
    }

    private void showJam(JamClient.Room room) {
        float density = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout content = new android.widget.LinearLayout(this); content.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(22 * density); content.setPadding(pad, (int)(8 * density), pad, 0);
        android.widget.ImageView qr = new android.widget.ImageView(this); int size = (int)Math.min(280 * density, getResources().getDisplayMetrics().widthPixels - 80 * density);
        try { qr.setImageBitmap(JamClient.qr(room.joinUrl(), size)); } catch (Exception ignored) { }
        content.addView(qr, new android.widget.LinearLayout.LayoutParams(-1, size));
        TextView code = new TextView(this); code.setText(room.code); code.setGravity(android.view.Gravity.CENTER); code.setTextSize(26); code.setTextColor(android.graphics.Color.WHITE); code.setLetterSpacing(.18f); code.setPadding(0, (int)(14*density), 0, (int)(8*density)); content.addView(code);
        TextView help = new TextView(this); help.setText("Friends scan this code, enter a name, search your library, and add songs. You can leave this window open or close it while the Jam keeps running."); help.setTextColor(android.graphics.Color.LTGRAY); help.setGravity(android.view.Gravity.CENTER); content.addView(help);
        new AlertDialog.Builder(this).setTitle("Jam is live").setView(content).setNeutralButton("Share link", (d, w) -> {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, room.joinUrl()); startActivity(Intent.createChooser(share, "Share Jam"));
        }).setNegativeButton("End Jam", (d, w) -> endJam()).setPositiveButton("Done", null).show();
    }

    private void pollJam() {
        jamTicker.removeCallbacksAndMessages(null);
        if (activeJam == null) return;
        io.execute(() -> {
            try {
                List<JamClient.Entry> entries = new JamClient().queue(activeJam.code);
                List<JamClient.Entry> fresh = new ArrayList<>();
                for (JamClient.Entry entry : entries) if (seenJamEntries.add(entry.id)) fresh.add(entry);
                main.post(() -> {
                    if (controller != null) for (JamClient.Entry entry : fresh) { Track track = jamCatalog.get(entry.trackId); if (track != null) { controller.addMediaItem(track.toMediaItem()); Toast.makeText(this, entry.guest + " added " + track.title, Toast.LENGTH_SHORT).show(); } }
                    if (activeJam != null) jamTicker.postDelayed(this::pollJam, 2000);
                });
            } catch (Exception error) { main.post(() -> { if (activeJam != null) jamTicker.postDelayed(this::pollJam, 4000); }); }
        });
    }

    private void endJam() {
        JamClient.Room room = activeJam; activeJam = null; jamTicker.removeCallbacksAndMessages(null);
        if (room != null) io.execute(() -> { try { new JamClient().close(room); } catch (Exception ignored) { } });
        Toast.makeText(this, "Jam ended", Toast.LENGTH_SHORT).show();
    }

    private void choosePlaylistFile() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "text/comma-separated-values", "audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain"});
        startActivityForResult(pick, IMPORT_PLAYLIST_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_PLAYLIST_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri file = data.getData(); status.setText("Importing playlist…");
        io.execute(() -> {
            try {
                PlaylistImporter.Result result = new PlaylistImporter(getContentResolver()).importFile(file);
                android.content.SharedPreferences prefs = getSharedPreferences("library", MODE_PRIVATE);
                prefs.edit().putStringSet("playlist:" + result.name, new java.util.LinkedHashSet<>(result.localIds)).putString("last_playlist", result.name).apply();
                main.post(() -> { status.setText(result.matched + " matched · " + result.missed + " not found"); Toast.makeText(this, "Imported " + result.name, Toast.LENGTH_LONG).show(); });
            } catch (Exception error) { main.post(() -> { status.setText("Import failed"); Toast.makeText(this, "Use a Spotify, Apple Music, Soundiiz, TuneMyMusic CSV, or M3U playlist.", Toast.LENGTH_LONG).show(); }); }
        });
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
        String[] actions = track.id < 0 ? new String[]{"Play now", "Play next", "Add to queue"} : new String[]{"Play now", "Play next", "Add to queue", "Add to favorites", "Add to playlist"};
        new AlertDialog.Builder(this).setTitle(track.title).setItems(actions, (d, which) -> {
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
        if (!mediaId.startsWith("local:")) { Toast.makeText(this, "Remote favorites are managed by Navidrome.", Toast.LENGTH_SHORT).show(); return; }
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

    private void chooseSource() {
        String[] options = new String[]{"This phone", b2Settings.configured() ? "Backblaze library" : "Set up Backblaze", serverSettings.configured() ? "Navidrome" : "Set up Navidrome", "Backblaze settings", "Navidrome settings"};
        new AlertDialog.Builder(this).setTitle("Music library").setItems(options, (dialog, which) -> {
            if (which == 0) { remoteMode = false; b2Mode = false; ((TextView)findViewById(R.id.source)).setText("This phone  ▾"); refreshPermissionState(); setGroup(""); }
            else if (which == 1 && b2Settings.configured()) { remoteMode = true; b2Mode = true; permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE); ((TextView)findViewById(R.id.source)).setText("Backblaze  ▾"); setGroup(""); }
            else if (which == 1) configureBackblaze();
            else if (which == 2 && serverSettings.configured()) { remoteMode = true; b2Mode = false; permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE); ((TextView)findViewById(R.id.source)).setText("Navidrome  ▾"); setGroup(""); }
            else if (which == 2 || which == 4) configureServer();
            else configureBackblaze();
        }).show();
    }

    private void configureBackblaze() {
        if (!b2Settings.available()) { Toast.makeText(this, "Android secure storage is unavailable. Restart the phone and try again.", Toast.LENGTH_LONG).show(); return; }
        android.widget.LinearLayout form = new android.widget.LinearLayout(this); form.setOrientation(android.widget.LinearLayout.VERTICAL); int pad = (int)(20 * getResources().getDisplayMetrics().density); form.setPadding(pad, 0, pad, 0);
        EditText keyId = new EditText(this); keyId.setHint("Application key ID"); keyId.setSingleLine(); keyId.setText(b2Settings.keyId()); form.addView(keyId);
        EditText applicationKey = new EditText(this); applicationKey.setHint("Application key"); applicationKey.setSingleLine(); applicationKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); form.addView(applicationKey);
        EditText bucket = new EditText(this); bucket.setHint("Bucket name"); bucket.setSingleLine(); bucket.setText(b2Settings.bucket()); form.addView(bucket);
        new AlertDialog.Builder(this).setTitle("Backblaze music").setMessage("Use a read-only key restricted to this bucket. Credentials are encrypted on this phone and never included in the APK.").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Connect", (d, w) -> {
            String id = keyId.getText().toString().trim(), secret = applicationKey.getText().toString().trim(), bucketName = bucket.getText().toString().trim();
            if (id.isEmpty() || secret.isEmpty() || bucketName.isEmpty()) { Toast.makeText(this, "Enter the key ID, application key, and bucket name.", Toast.LENGTH_LONG).show(); return; }
            b2Settings.save(id, secret, bucketName); status.setText("Connecting to Backblaze…"); io.execute(() -> { boolean ok = new B2CatalogClient(b2Settings).ping(); main.post(() -> { if (ok) { reconnectPlaybackService(); remoteMode = true; b2Mode = true; permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE); ((TextView)findViewById(R.id.source)).setText("Backblaze  ▾"); setGroup(""); } else status.setText("Backblaze connection failed. Check the bucket-restricted key and bucket name."); }); });
        }).show();
    }

    private void configureServer() {
        if (!serverSettings.available()) { Toast.makeText(this, "Android secure storage is unavailable. Restart the phone and try again.", Toast.LENGTH_LONG).show(); return; }
        android.widget.LinearLayout form = new android.widget.LinearLayout(this); form.setOrientation(android.widget.LinearLayout.VERTICAL); int pad = (int)(20 * getResources().getDisplayMetrics().density); form.setPadding(pad, 0, pad, 0);
        EditText url = new EditText(this); url.setHint("HTTPS server URL"); url.setSingleLine(); url.setText(serverSettings.url()); form.addView(url);
        EditText username = new EditText(this); username.setHint("Username"); username.setSingleLine(); username.setText(serverSettings.username()); form.addView(username);
        EditText password = new EditText(this); password.setHint("Password"); password.setSingleLine(); password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); form.addView(password);
        new AlertDialog.Builder(this).setTitle("Navidrome / OpenSubsonic").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Connect", (dialog, which) -> {
            String address = url.getText().toString().trim(); String user = username.getText().toString().trim(); String pass = password.getText().toString();
            if (!address.startsWith("https://") || user.isEmpty() || pass.isEmpty()) { Toast.makeText(this, "Use an HTTPS address and enter your username and password.", Toast.LENGTH_LONG).show(); return; }
            serverSettings.save(address, user, pass); status.setText("Connecting to Navidrome…");
            io.execute(() -> { boolean ok = new OpenSubsonicClient(serverSettings).ping(); main.post(() -> {
                if (ok) { remoteMode = true; b2Mode = false; permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE); ((TextView)findViewById(R.id.source)).setText("Navidrome  ▾"); setGroup(""); }
                else status.setText("Connection failed. Check the HTTPS address, login, and whether the server is running.");
            }); });
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

    @Override public void onDestroy() { jamTicker.removeCallbacksAndMessages(null); io.shutdown(); super.onDestroy(); }
}
