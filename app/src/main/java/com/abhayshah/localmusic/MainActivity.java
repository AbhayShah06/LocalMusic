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
    private final ExecutorService searchIo = Executors.newSingleThreadExecutor();
    private final ExecutorService streamIo = Executors.newFixedThreadPool(5);
    private final ExecutorService jamSyncIo = Executors.newSingleThreadExecutor();
    private final ExecutorService jamSearchIo = Executors.newFixedThreadPool(3);
    private final java.util.concurrent.atomic.AtomicInteger streamRequest = new java.util.concurrent.atomic.AtomicInteger();
    private long lastTrackTapAt;
    private String lastTrackTapId = "";
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
    private boolean onlineMode;
    private ServerSettings serverSettings;
    private B2Settings b2Settings;
    private String query = "";
    private String group = "";
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable seekTicker;
    private final Handler jamTicker = new Handler(Looper.getMainLooper());
    private JamClient.Room activeJam;
    private final java.util.Map<String, Track> jamCatalog = new java.util.HashMap<>();
    private final java.util.Set<String> seenJamEntries = new java.util.HashSet<>();
    private final java.util.Set<String> seenJamSearches = new java.util.HashSet<>();
    private final java.util.Set<String> seenJamControls = new java.util.HashSet<>();
    private volatile String lastJamPlayback = "";
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
        adapter = new LibraryAdapter(this, tracks, this::playFrom, this::showTrackActions);
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
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString(); searchHandler.removeCallbacksAndMessages(null);
                searchHandler.postDelayed(MainActivity.this::resetAndLoad, onlineMode ? 350 : 120);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        onlineMode = true; remoteMode = true; b2Mode = false;
        permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE);
        ((TextView)findViewById(R.id.source)).setText("Stream  ▾");
        status.setText("Search for any song, artist, or album");
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

    @Override public void onStart() { super.onStart(); if (controller == null) connect(); }
    @Override public void onStop() { ticker.removeCallbacksAndMessages(null); if (activeJam == null) { if (controller != null) controller.removeListener(uiPlayerListener); if (controllerFuture != null) MediaController.releaseFuture(controllerFuture); controller = null; controllerFuture = null; } super.onStop(); }

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
        searchIo.execute(() -> {
            List<Track> page;
            try { page = onlineMode ? onlineSource().page(pageQuery, pageOffset, PAGE_SIZE) : b2Mode ? new B2CatalogClient(b2Settings).page(pageQuery, pageOffset, PAGE_SIZE) : remoteMode ? new OpenSubsonicClient(serverSettings).page(pageQuery, pageOffset, PAGE_SIZE) : new MediaStoreCatalogSource(getContentResolver()).page(pageQuery, pageOffset, PAGE_SIZE); }
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
                if (onlineMode && pageQuery.trim().isEmpty()) status.setText("Search for any song, artist, or album");
                else status.setText(tracks.size() + (exhausted ? " songs" : "+ songs") + (onlineMode ? " found online" : b2Mode ? " in Backblaze" : remoteMode ? " from Navidrome" : " on this phone"));
            });
        });
    }

    private void showGroups(boolean artists) {
        if (remoteMode) { Toast.makeText(this, "Use search to find remote albums and artists for now.", Toast.LENGTH_SHORT).show(); return; }
        group = artists ? "artist" : "album";
        tracks.clear(); adapter.clear();
        streamIo.execute(() -> {
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
            if (tracks.get(position).stableId.startsWith("online:")) { resolveAndPlayOnline(new ArrayList<>(tracks), position); return; }
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

    private void resolveAndPlayOnline(List<Track> sourceTracks, int position) {
        Track selected = sourceTracks.get(position);
        long now = android.os.SystemClock.elapsedRealtime();
        if (selected.stableId.equals(lastTrackTapId) && now - lastTrackTapAt < 900) return;
        lastTrackTapId = selected.stableId; lastTrackTapAt = now;
        int request = streamRequest.incrementAndGet();
        status.setText("Starting " + selected.title + "…");
        if (controller != null) { controller.pause(); controller.clearMediaItems(); updateNowPlaying(); }
        Track offline = DownloadStore.offline(this, selected);
        if (offline != null && controller != null) {
            controller.setMediaItem(offline.toMediaItem()); controller.prepare(); controller.play(); status.setText("Playing downloaded copy"); updateNowPlaying();
        }
        streamIo.execute(() -> {
            OnlineCatalogSource source = onlineSource();
            try {
                OnlineCatalogSource.Resolution resolution = source.resolveWithRadio(selected);
                Track first = resolution.playable;
                main.post(() -> {
                    if (controller == null || streamRequest.get() != request) return;
                    if (offline == null) { controller.setMediaItem(first.toMediaItem()); controller.prepare(); controller.play(); status.setText("Streaming from " + first.album); updateNowPlaying(); }
                });
                appendRadio(source, sourceTracks, position, resolution.radio, request);
            } catch (RuntimeException error) {
                main.post(() -> { if (streamRequest.get() == request) { adapter.clearPending(); status.setText("Could not start this song"); Toast.makeText(this, "Try another result or search again.", Toast.LENGTH_LONG).show(); } });
            }
        });
    }

    private void appendRadio(OnlineCatalogSource source, List<Track> sourceTracks, int position, List<Track> radio, int request) {
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("autoplay", true)) return;
        List<Track> upcoming = radio.isEmpty() ? new ArrayList<>(sourceTracks.subList(Math.min(position + 1, sourceTracks.size()), Math.min(sourceTracks.size(), position + 6)))
                : new ArrayList<>(radio.subList(0, Math.min(5, radio.size())));
        for (Track item : upcoming) java.util.concurrent.CompletableFuture
                .supplyAsync(() -> { Track local = DownloadStore.offline(this, item); if (local != null) return local; try { return source.resolve(item); } catch (RuntimeException ignored) { return null; } }, streamIo)
                .thenAccept(ready -> main.post(() -> { if (ready != null && controller != null && streamRequest.get() == request) controller.addMediaItem(ready.toMediaItem()); }));
    }

    private void updateNowPlaying() {
        if (controller == null || controller.getMediaMetadata().title == null) {
            nowPlaying.setText("Nothing playing"); nowPlayingArtist.setText("Choose a song");
        } else {
            adapter.clearPending();
            nowPlaying.setText(controller.getMediaMetadata().title);
            nowPlayingArtist.setText(Track.clean(String.valueOf(controller.getMediaMetadata().artist), "Unknown artist"));
        }
        ArtworkLoader.into(findViewById(R.id.miniArtwork), controller == null ? null : controller.getMediaMetadata().artworkUri, android.R.drawable.ic_media_play);
        ((TextView)findViewById(R.id.playPause)).setText(controller != null && controller.isPlaying() ? "Ⅱ" : "▶");
        refreshProgress();
        publishJamPlayback();
    }

    private void showNowPlaying() {
        if (controller == null || controller.getCurrentMediaItem() == null) { Toast.makeText(this, "Choose a song first", Toast.LENGTH_SHORT).show(); return; }
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar);
        dialog.setContentView(R.layout.dialog_now_playing);
        if (dialog.getWindow() != null) { dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT); dialog.getWindow().setStatusBarColor(android.graphics.Color.rgb(11, 11, 12)); dialog.getWindow().setNavigationBarColor(android.graphics.Color.rgb(11, 11, 12)); }
        TextView title = dialog.findViewById(R.id.fullTitle), artist = dialog.findViewById(R.id.fullArtist), elapsed = dialog.findViewById(R.id.fullElapsed), durationLabel = dialog.findViewById(R.id.fullDuration);
        android.widget.ImageButton play = dialog.findViewById(R.id.fullPlayPause);
        android.widget.ImageView artwork = dialog.findViewById(R.id.fullArtwork), backgroundArtwork = dialog.findViewById(R.id.fullBackgroundArtwork); SeekBar fullSeek = dialog.findViewById(R.id.fullSeek);
        if (Build.VERSION.SDK_INT >= 31) backgroundArtwork.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(44f, 44f, android.graphics.Shader.TileMode.CLAMP));
        Runnable refresh = () -> {
            if (controller == null || controller.getCurrentMediaItem() == null) return;
            title.setText(Track.clean(String.valueOf(controller.getMediaMetadata().title), "Now playing"));
            artist.setText(Track.clean(String.valueOf(controller.getMediaMetadata().artist), "Unknown artist"));
            play.setImageResource(controller.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            ArtworkLoader.into(artwork, controller.getMediaMetadata().artworkUri, android.R.drawable.ic_media_play);
            ArtworkLoader.into(backgroundArtwork, controller.getMediaMetadata().artworkUri, android.R.drawable.ic_media_play);
            long duration = controller.getDuration(), position = controller.getCurrentPosition(); fullSeek.setProgress(duration > 0 ? (int)Math.min(1000, position * 1000 / duration) : 0);
            elapsed.setText(formatTime(position)); durationLabel.setText(formatTime(duration));
        };
        Runnable progress = () -> {
            if (controller == null || controller.getCurrentMediaItem() == null) return;
            long duration = controller.getDuration(), position = controller.getCurrentPosition();
            play.setImageResource(controller.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            fullSeek.setProgress(duration > 0 ? (int)Math.min(1000, position * 1000 / duration) : 0);
            elapsed.setText(formatTime(position)); durationLabel.setText(duration > 0 ? formatTime(duration) : "–:––");
        };
        final Runnable[] progressTicker = new Runnable[1];
        progressTicker[0] = () -> { progress.run(); if (dialog.isShowing()) main.postDelayed(progressTicker[0], 500); };
        refresh.run();
        dialog.findViewById(R.id.closeNowPlaying).setOnClickListener(v -> dialog.dismiss());
        play.setOnClickListener(v -> { if (controller.isPlaying()) controller.pause(); else controller.play(); refresh.run(); });
        dialog.findViewById(R.id.fullPrevious).setOnClickListener(v -> { controller.seekToPreviousMediaItem(); main.postDelayed(refresh, 150); });
        dialog.findViewById(R.id.fullNext).setOnClickListener(v -> { controller.seekToNextMediaItem(); main.postDelayed(refresh, 150); });
        dialog.findViewById(R.id.fullQueue).setOnClickListener(v -> showQueue());
        dialog.findViewById(R.id.fullLyrics).setOnClickListener(v -> { Track current = currentTrack(); if (current != null) showLyrics(current); });
        dialog.findViewById(R.id.fullPlaylist).setOnClickListener(v -> { Track current = currentTrack(); if (current != null) addTrackToPlaylist(current); });
        artist.setOnClickListener(v -> showArtistPage(artist.getText().toString()));
        fullSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { if (fromUser && controller.getDuration() > 0) controller.seekTo(controller.getDuration() * progress / 1000); }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        Player.Listener dialogListener = new Player.Listener() { @Override public void onEvents(Player player, Player.Events events) { refresh.run(); } };
        controller.addListener(dialogListener); dialog.setOnDismissListener(ignored -> { main.removeCallbacks(progressTicker[0]); if (controller != null) controller.removeListener(dialogListener); });
        dialog.show(); main.post(progressTicker[0]); if (dialog.getWindow() != null) { dialog.getWindow().setLayout(-1, -1); makeImmersive(dialog.getWindow()); }
    }

    private static void makeImmersive(android.view.Window window) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private static String formatTime(long milliseconds) {
        if (milliseconds < 0) milliseconds = 0; long seconds = milliseconds / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private Track currentTrack() {
        if (controller == null || controller.getCurrentMediaItem() == null) return null;
        androidx.media3.common.MediaItem item = controller.getCurrentMediaItem(); androidx.media3.common.MediaMetadata metadata = item.mediaMetadata;
        Uri uri = item.localConfiguration == null ? Uri.EMPTY : item.localConfiguration.uri;
        return new Track(item.mediaId, uri, String.valueOf(metadata.title), String.valueOf(metadata.artist), String.valueOf(metadata.albumTitle), metadata.artworkUri);
    }

    private void showLyrics(Track track) {
        Toast.makeText(this, "Finding lyrics…", Toast.LENGTH_SHORT).show();
        io.execute(() -> { String lyrics = LyricsClient.get(this, track); main.post(() -> {
            if (lyrics.isEmpty()) { Toast.makeText(this, "No lyrics found for this version", Toast.LENGTH_LONG).show(); return; }
            TextView text = new TextView(this); int pad = (int)(22 * getResources().getDisplayMetrics().density);
            text.setPadding(pad, pad, pad, pad); text.setText(lyrics); text.setTextColor(android.graphics.Color.rgb(246, 241, 232)); text.setTextSize(18); text.setLineSpacing(0, 1.25f); text.setTextIsSelectable(true);
            android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.setBackgroundColor(android.graphics.Color.rgb(11, 11, 12)); scroll.addView(text);
            android.app.Dialog lyricsDialog = new android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar); lyricsDialog.setContentView(scroll); lyricsDialog.show(); if (lyricsDialog.getWindow() != null) lyricsDialog.getWindow().setLayout(-1, -1);
        }); });
    }

    private void showArtistPage(String rawArtist) {
        String artistName = rawArtist.replaceAll("(?i)\\s*-\\s*topic$", "").trim();
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this); root.setOrientation(android.widget.LinearLayout.VERTICAL); root.setPadding(20, 26, 20, 20); root.setBackgroundColor(android.graphics.Color.rgb(11, 11, 12));
        TextView close = new TextView(this); close.setText("‹  Back"); close.setTextColor(android.graphics.Color.rgb(255, 128, 95)); close.setTextSize(17); close.setGravity(android.view.Gravity.CENTER_VERTICAL); close.setPadding(12, 8, 12, 8); root.addView(close, new android.widget.LinearLayout.LayoutParams(-1, 56));
        TextView heading = new TextView(this); heading.setText(artistName); heading.setTextColor(android.graphics.Color.rgb(246, 241, 232)); heading.setTextSize(32); heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); heading.setPadding(14, 22, 14, 10); root.addView(heading);
        TextView artistStatus = new TextView(this); artistStatus.setText("Loading top songs…"); artistStatus.setTextColor(android.graphics.Color.rgb(143, 138, 131)); artistStatus.setPadding(14, 8, 14, 14); root.addView(artistStatus);
        ListView list = new ListView(this); list.setDividerHeight(4); list.setDivider(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)); root.addView(list, new android.widget.LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(root); close.setOnClickListener(v -> dialog.dismiss()); dialog.show(); if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -1);
        List<Track> artistTracks = new ArrayList<>(); LibraryAdapter artistAdapter = new LibraryAdapter(this, artistTracks, position -> { dialog.dismiss(); resolveAndPlayOnline(artistTracks, position); }, position -> addTrackToPlaylist(artistTracks.get(position))); list.setAdapter(artistAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> { dialog.dismiss(); resolveAndPlayOnline(artistTracks, position); });
        searchIo.execute(() -> {
            List<Track> found = onlineSource().page(artistName, 0, 50);
            main.post(() -> { artistTracks.addAll(found); for (Track track : found) artistAdapter.add(track.title + "\n" + track.artist + " · " + track.album); artistStatus.setText(found.size() + " top songs"); });
        });
    }

    private void showMoreMenu() {
        String[] actions = {"Your mix", "Recently played", "Downloads", "Start a Jam", "Favorites", "Playlists", "Sleep timer", "Settings"};
        new AlertDialog.Builder(this).setTitle("Local Music").setItems(actions, (d, which) -> {
            if (which == 0) loadListeningList(true);
            else if (which == 1) loadListeningList(false);
            else if (which == 2) loadDownloads();
            else if (which == 3) startJam();
            else if (which == 4) { selectTab(R.id.playlists); loadSavedTracks("favorites", "Favorites"); }
            else if (which == 5) { selectTab(R.id.playlists); showPlaylists(); }
            else if (which == 6) chooseSleepTimer();
            else showSettings();
        }).show();
    }

    private OnlineCatalogSource onlineSource() {
        boolean avoidClean = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("avoid_clean", true);
        return new OnlineCatalogSource(avoidClean);
    }

    private void showSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setPadding(pad, pad / 2, pad, pad);

        TextView playback = settingsHeading("Playback"); content.addView(playback);
        android.widget.Switch haptics = settingsSwitch("Touch feedback", "Vibrate briefly when a tap is registered", prefs.getBoolean("haptics", true));
        haptics.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("haptics", checked).apply()); content.addView(haptics);
        android.widget.Switch autoplay = settingsSwitch("Autoplay recommendations", "Keep the queue moving with related music", prefs.getBoolean("autoplay", true));
        autoplay.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("autoplay", checked).apply()); content.addView(autoplay);
        android.widget.Switch explicit = settingsSwitch("Avoid clean and radio edits", "Prefer original explicit versions when available", prefs.getBoolean("avoid_clean", true));
        explicit.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("avoid_clean", checked).apply()); content.addView(explicit);

        content.addView(settingsHeading("Library"));
        content.addView(settingsAction("Music source", "Choose online streaming, phone files, Backblaze, or Navidrome", this::chooseSource));
        content.addView(settingsAction("Import playlists", "Import Spotify, Apple Music, Soundiiz, TuneMyMusic CSV, or M3U files", this::choosePlaylistFile));
        content.addView(settingsAction("Equalizer", "Open your phone's audio effects", this::openEqualizer));
        content.addView(settingsAction("Music permission", hasAudioPermission() ? "Allowed for on-device songs" : "Tap to allow on-device songs", () -> { if (hasAudioPermission()) openAppSettings(); else requestAudioPermission(); }));

        long downloads = DownloadStore.bytes(this), streams = PlaybackCache.bytes(), artwork = ArtworkLoader.bytes(this);
        content.addView(settingsHeading("Storage"));
        content.addView(settingsAction("Offline downloads", formatBytes(downloads) + " saved permanently", this::loadDownloads));
        content.addView(settingsAction("Clear temporary cache", formatBytes(streams + artwork) + " of streams and artwork. Downloads stay.", () -> {
            PlaybackCache.clear(); ArtworkLoader.clear(this); LyricsClient.clear(this);
            Toast.makeText(this, "Temporary cache cleared", Toast.LENGTH_SHORT).show();
        }));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("Settings").setView(scroll).setPositiveButton("Done", null).show();
    }

    private TextView settingsHeading(String label) {
        TextView view = new TextView(this); view.setText(label.toUpperCase(java.util.Locale.ROOT));
        view.setTextColor(android.graphics.Color.rgb(255, 128, 95)); view.setTextSize(13); view.setLetterSpacing(.12f);
        view.setPadding(0, 26, 0, 8); return view;
    }

    private android.widget.Switch settingsSwitch(String title, String detail, boolean checked) {
        android.widget.Switch view = new android.widget.Switch(this); view.setText(title + "\n" + detail); view.setChecked(checked);
        view.setTextColor(android.graphics.Color.rgb(246, 241, 232)); view.setTextSize(16); view.setPadding(0, 12, 0, 12);
        return view;
    }

    private View settingsAction(String title, String detail, Runnable action) {
        TextView view = new TextView(this); view.setText(title + "\n" + detail); view.setTextColor(android.graphics.Color.rgb(246, 241, 232));
        view.setTextSize(16); view.setPadding(0, 15, 0, 15); view.setBackgroundResource(android.R.drawable.list_selector_background);
        view.setOnClickListener(v -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); action.run(); }); return view;
    }

    private static String formatBytes(long value) {
        if (value < 1024 * 1024) return Math.max(0, value / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", value / 1048576d);
    }

    private void loadListeningList(boolean mix) {
        List<Track> found = mix ? ListeningHistory.dailyMix(this, 50) : ListeningHistory.recent(this, 50);
        group = ""; tracks.clear(); tracks.addAll(found); adapter.clear();
        for (Track track : found) adapter.add(track.title + "\n" + track.artist + " · " + track.album);
        status.setText(found.isEmpty() ? "Play a few songs first" : mix ? "Your daily mix · built on this phone" : "Recently played");
    }

    private void loadDownloads() {
        List<Track> found = DownloadStore.all(this); group = ""; tracks.clear(); tracks.addAll(found); adapter.clear();
        for (Track track : found) adapter.add(track.title + "\n" + track.artist + " · " + track.album);
        status.setText(found.isEmpty() ? "No offline downloads yet" : found.size() + " downloads · " + Math.max(1, DownloadStore.bytes(this) / (1024 * 1024)) + " MB");
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
                seenJamEntries.clear(); seenJamSearches.clear(); seenJamControls.clear(); lastJamPlayback = ""; activeJam = room;
                Intent jam = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_START_JAM)
                        .putExtra(PlaybackService.EXTRA_JAM_CODE, room.code).putExtra(PlaybackService.EXTRA_JAM_TOKEN, room.hostToken);
                startService(jam);
                main.post(() -> { status.setText(catalog.size() + " SONGS READY FOR JAM"); showJam(room); });
            } catch (Exception error) { main.post(() -> { status.setText("JAM COULD NOT START"); Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }); }
        });
    }

    private List<Track> loadJamCatalog() {
        if (onlineMode) {
            List<Track> visible = new ArrayList<>(tracks);
            if (visible.isEmpty()) throw new IllegalStateException("Search for music first, then start the Jam");
            return visible;
        }
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
        jamSyncIo.execute(() -> {
            try {
                JamClient.Room room = activeJam; if (room == null) return; JamClient.State state = new JamClient().state(room);
                List<JamClient.Entry> entries = state.entries;
                List<JamClient.Entry> fresh = new ArrayList<>();
                for (JamClient.Entry entry : entries) if (!seenJamEntries.contains(entry.id)) fresh.add(entry);
                List<JamClient.Control> controls = new ArrayList<>(); for (JamClient.Control control : state.controls) if (!seenJamControls.contains(control.id)) controls.add(control);
                main.post(() -> {
                    if (controller != null) for (JamClient.Entry entry : fresh) if (seenJamEntries.add(entry.id)) addJamEntry(entry);
                    if (controller != null) for (JamClient.Control control : controls) if (seenJamControls.add(control.id)) applyJamControl(control);
                    if (activeJam != null) jamTicker.postDelayed(this::pollJam, 650);
                });
                for (JamClient.Search search : state.searches) if (search.pending && seenJamSearches.add(search.id)) jamSearchIo.execute(() -> {
                    try { List<Track> results = onlineSource().page(search.query, 0, 35); for (Track track : results) jamCatalog.put(track.stableId, track); JamClient.Room active = activeJam; if (active != null) new JamClient().publish(active, search.id, results); }
                    catch (Exception error) { seenJamSearches.remove(search.id); }
                });
            } catch (Exception error) { main.post(() -> { if (activeJam != null) jamTicker.postDelayed(this::pollJam, 1500); }); }
        });
    }

    private void applyJamControl(JamClient.Control control) {
        if (controller == null) return;
        if ("previous".equals(control.action)) controller.seekToPreviousMediaItem();
        else if ("next".equals(control.action)) controller.seekToNextMediaItem();
        else if ("toggle".equals(control.action)) { if (controller.isPlaying()) controller.pause(); else controller.play(); }
        else if ("move".equals(control.action) && control.index >= 0 && Math.abs(control.direction) == 1) {
            int from = controller.getCurrentMediaItemIndex() + 1 + control.index, to = from + control.direction;
            if (from >= 0 && from < controller.getMediaItemCount() && to > controller.getCurrentMediaItemIndex() && to < controller.getMediaItemCount()) controller.moveMediaItem(from, to);
        }
        Toast.makeText(this, control.guest + " used " + control.action, Toast.LENGTH_SHORT).show();
        main.postDelayed(this::publishJamPlayback, 150);
    }

    private void publishJamPlayback() {
        JamClient.Room room = activeJam; if (room == null || controller == null || controller.getCurrentMediaItem() == null) return;
        androidx.media3.common.MediaMetadata current = controller.getMediaMetadata(); String title = Track.clean(String.valueOf(current.title), "Unknown title"), artist = Track.clean(String.valueOf(current.artist), "Unknown artist");
        List<String[]> upcoming = new ArrayList<>(); int start = Math.max(0, controller.getCurrentMediaItemIndex() + 1);
        for (int i = start; i < Math.min(controller.getMediaItemCount(), start + 20); i++) { androidx.media3.common.MediaMetadata item = controller.getMediaItemAt(i).mediaMetadata; upcoming.add(new String[]{Track.clean(String.valueOf(item.title), "Unknown title"), Track.clean(String.valueOf(item.artist), "Unknown artist")}); }
        StringBuilder key = new StringBuilder(title).append('|').append(artist).append('|').append(controller.isPlaying()); for (String[] item : upcoming) key.append('|').append(item[0]).append('/').append(item[1]);
        boolean playing = controller.isPlaying();
        if (key.toString().equals(lastJamPlayback)) return; lastJamPlayback = key.toString();
        jamSyncIo.execute(() -> { try { new JamClient().publishPlayback(room, title, artist, playing, upcoming); } catch (Exception ignored) { lastJamPlayback = ""; } });
    }

    private void addJamEntry(JamClient.Entry entry) {
        Track track = jamCatalog.get(entry.trackId);
        if (track != null) { if (track.stableId.startsWith("online:")) enqueueOnline(track, false); else { controller.addMediaItem(track.toMediaItem()); publishJamPlayback(); } Toast.makeText(this, entry.guest + " added " + track.title, Toast.LENGTH_SHORT).show(); return; }
        streamIo.execute(() -> {
            List<Track> matches = onlineSource().page(entry.title + " " + entry.artist, 0, 8);
            if (matches.isEmpty()) return; Track found = matches.get(0); jamCatalog.put(entry.trackId, found);
            main.post(() -> { enqueueOnline(found, false); Toast.makeText(this, entry.guest + " added " + found.title, Toast.LENGTH_SHORT).show(); });
        });
    }

    private void endJam() {
        JamClient.Room room = activeJam; activeJam = null; jamTicker.removeCallbacksAndMessages(null);
        startService(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_STOP_JAM));
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
        boolean online = track.stableId.startsWith("online:");
        String[] actions = online ? new String[]{"Play now", "Play next", "Add to queue", DownloadStore.contains(this, track.stableId) ? "Remove download" : "Download", "Add to playlist", "Open artist"} : new String[]{"Play now", "Play next", "Add to queue", "Add to favorites", "Add to playlist"};
        new AlertDialog.Builder(this).setTitle(track.title).setItems(actions, (d, which) -> {
            if (controller == null) return;
            String action = actions[which];
            if (action.equals("Play now")) playFrom(position);
            else if ((action.equals("Play next") || action.equals("Add to queue")) && online) {
                enqueueOnline(track, action.equals("Play next"));
            } else if (action.equals("Play next") || action.equals("Add to queue")) {
                androidx.media3.common.MediaItem item = track.toMediaItem();
                if (action.equals("Play next") && controller.getMediaItemCount() > 0) controller.addMediaItem(Math.min(controller.getCurrentMediaItemIndex() + 1, controller.getMediaItemCount()), item);
                else controller.addMediaItem(item);
                Toast.makeText(this, action.equals("Play next") ? "Queued next" : "Added to queue", Toast.LENGTH_SHORT).show();
            } else if (action.equals("Download")) { OfflineDownloadService.enqueue(this, track); Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show(); }
            else if (action.equals("Remove download")) new AlertDialog.Builder(this).setTitle("Remove download?").setMessage(track.title + " will remain available to stream.").setNegativeButton("Cancel", null).setPositiveButton("Remove", (dialog, button) -> { DownloadStore.remove(this, track.stableId); Toast.makeText(this, "Download removed", Toast.LENGTH_SHORT).show(); }).show();
            else if (action.equals("Add to playlist")) addTrackToPlaylist(track);
            else if (action.equals("Open artist")) showArtistPage(track.artist);
            else if (action.equals("Add to favorites")) toggleFavorite(track.id);
        }).show();
    }

    private void enqueueOnline(Track track, boolean next) {
        status.setText("Preparing " + track.title + "…");
        streamIo.execute(() -> {
            try {
                Track resolved = onlineSource().resolve(track);
                main.post(() -> {
                    if (controller == null) return;
                    int target = Math.min(controller.getCurrentMediaItemIndex() + 1, controller.getMediaItemCount());
                    if (next && controller.getMediaItemCount() > 0) controller.addMediaItem(target, resolved.toMediaItem());
                    else controller.addMediaItem(resolved.toMediaItem());
                    status.setText(next ? "Queued next" : "Added to queue");
                    publishJamPlayback();
                });
            } catch (RuntimeException error) { main.post(() -> status.setText("Could not prepare this song")); }
        });
    }

    private void showQueue() {
        if (controller == null || controller.getMediaItemCount() == 0) { Toast.makeText(this, "Queue is empty", Toast.LENGTH_SHORT).show(); return; }
        final android.app.Dialog dialog = new android.app.Dialog(this);
        android.widget.LinearLayout sheet = new android.widget.LinearLayout(this); sheet.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = queueDp(20); sheet.setPadding(pad, queueDp(14), pad, queueDp(22));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable(); background.setColor(android.graphics.Color.rgb(18, 17, 17)); background.setCornerRadii(new float[]{queueDp(26),queueDp(26),queueDp(26),queueDp(26),0,0,0,0}); sheet.setBackground(background);
        View grip = new View(this); android.graphics.drawable.GradientDrawable gripBg = new android.graphics.drawable.GradientDrawable(); gripBg.setColor(android.graphics.Color.rgb(92, 87, 82)); gripBg.setCornerRadius(queueDp(4)); grip.setBackground(gripBg);
        android.widget.LinearLayout.LayoutParams gripParams = new android.widget.LinearLayout.LayoutParams(queueDp(42), queueDp(4)); gripParams.gravity = android.view.Gravity.CENTER_HORIZONTAL; gripParams.bottomMargin = queueDp(18); sheet.addView(grip, gripParams);
        TextView eyebrow = new TextView(this); eyebrow.setText("PLAYBACK"); eyebrow.setTextColor(android.graphics.Color.rgb(255, 128, 95)); eyebrow.setTextSize(10); eyebrow.setLetterSpacing(.16f); eyebrow.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); sheet.addView(eyebrow);
        TextView title = new TextView(this); title.setText("Your queue"); title.setTextColor(android.graphics.Color.rgb(246, 241, 232)); title.setTextSize(28); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); title.setPadding(0, queueDp(4), 0, queueDp(2)); sheet.addView(title);
        TextView hint = new TextView(this); hint.setText("Tap a song to play. Drag the rail to reorder."); hint.setTextColor(android.graphics.Color.rgb(150, 144, 137)); hint.setTextSize(13); hint.setPadding(0, 0, 0, queueDp(12)); sheet.addView(hint);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.setFillViewport(true);
        android.widget.LinearLayout rows = new android.widget.LinearLayout(this); rows.setOrientation(android.widget.LinearLayout.VERTICAL); scroll.addView(rows);
        int current = controller.getCurrentMediaItemIndex();
        for (int i = 0; i < controller.getMediaItemCount(); i++) rows.addView(queueRow(dialog, i, current));
        sheet.addView(scroll, new android.widget.LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(sheet); android.view.Window window = dialog.getWindow();
        if (window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); window.setLayout(-1, -1); window.setGravity(android.view.Gravity.BOTTOM); window.setDimAmount(.62f); window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND); }
        dialog.setOnShowListener(ignored -> { android.view.Window w = dialog.getWindow(); if (w != null) { w.setLayout(-1, (int)(getResources().getDisplayMetrics().heightPixels * .82f)); w.setGravity(android.view.Gravity.BOTTOM); } });
        dialog.show();
    }

    private View queueRow(android.app.Dialog dialog, int index, int current) {
        androidx.media3.common.MediaMetadata metadata = controller.getMediaItemAt(index).mediaMetadata;
        android.widget.LinearLayout row = new android.widget.LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(queueDp(5), queueDp(8), 0, queueDp(8)); row.setMinimumHeight(queueDp(66));
        if (index == current) { android.graphics.drawable.GradientDrawable selected = new android.graphics.drawable.GradientDrawable(); selected.setColor(android.graphics.Color.rgb(39, 31, 28)); selected.setCornerRadius(queueDp(14)); row.setBackground(selected); }
        TextView rail = new TextView(this); rail.setText("≡"); rail.setGravity(android.view.Gravity.CENTER); rail.setTextColor(index == current ? android.graphics.Color.rgb(91, 86, 81) : android.graphics.Color.rgb(173, 164, 156)); rail.setTextSize(24); rail.setContentDescription("Drag to reorder");
        android.widget.LinearLayout.LayoutParams railParams = new android.widget.LinearLayout.LayoutParams(queueDp(42), queueDp(50)); row.addView(rail, railParams);
        android.widget.LinearLayout meta = new android.widget.LinearLayout(this); meta.setOrientation(android.widget.LinearLayout.VERTICAL); meta.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this); name.setText(Track.clean(String.valueOf(metadata.title), "Unknown title")); name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END); name.setTextColor(android.graphics.Color.rgb(246, 241, 232)); name.setTextSize(15); name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); meta.addView(name);
        TextView artist = new TextView(this); artist.setText(index == current ? "NOW PLAYING  ·  " + Track.clean(String.valueOf(metadata.artist), "Unknown artist") : Track.clean(String.valueOf(metadata.artist), "Unknown artist")); artist.setSingleLine(true); artist.setEllipsize(android.text.TextUtils.TruncateAt.END); artist.setTextColor(index == current ? android.graphics.Color.rgb(255, 128, 95) : android.graphics.Color.rgb(145, 139, 132)); artist.setTextSize(12); artist.setPadding(0, queueDp(4), 0, 0); meta.addView(artist);
        row.addView(meta, new android.widget.LinearLayout.LayoutParams(0, -1, 1));
        TextView remove = new TextView(this); remove.setText("×"); remove.setGravity(android.view.Gravity.CENTER); remove.setTextColor(android.graphics.Color.rgb(150, 144, 137)); remove.setTextSize(22); remove.setContentDescription("Remove from queue"); row.addView(remove, new android.widget.LinearLayout.LayoutParams(queueDp(44), queueDp(48)));
        row.setOnClickListener(v -> { controller.seekTo(index, 0); controller.play(); dialog.dismiss(); });
        remove.setOnClickListener(v -> { controller.removeMediaItem(index); dialog.dismiss(); main.postDelayed(this::showQueue, 100); });
        if (index != current) {
            final float[] down = new float[1]; final boolean[] dragging = new boolean[1];
            rail.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) { down[0] = event.getRawY(); dragging[0] = true; v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); row.animate().scaleX(1.02f).scaleY(1.02f).setDuration(80).start(); return true; }
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE && dragging[0]) { row.setTranslationY(event.getRawY() - down[0]); return true; }
                if ((event.getActionMasked() == android.view.MotionEvent.ACTION_UP || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) && dragging[0]) {
                    dragging[0] = false; int delta = Math.round((event.getRawY() - down[0]) / Math.max(1f, row.getHeight())); int target = Math.max(current + 1, Math.min(controller.getMediaItemCount() - 1, index + delta)); row.setTranslationY(0); row.setScaleX(1); row.setScaleY(1);
                    if (target != index) controller.moveMediaItem(index, target); dialog.dismiss(); main.postDelayed(this::showQueue, 100); return true;
                }
                return false;
            });
        }
        return row;
    }

    private int queueDp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

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
                if (id.startsWith("{")) {
                    try {
                        org.json.JSONObject saved = new org.json.JSONObject(id); String stableId = saved.getString("id");
                        if (!stableId.startsWith("local:")) {
                            String artwork = saved.optString("artwork");
                            found.add(new Track(stableId, Uri.parse(saved.optString("uri")), saved.optString("title"), saved.optString("artist"), saved.optString("album"), artwork.isEmpty() ? null : Uri.parse(artwork)));
                            continue;
                        }
                        id = stableId.substring(6);
                    } catch (Exception ignored) { continue; }
                }
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
        List<String> names = playlistNames(prefs); names.add("+ New playlist");
        new AlertDialog.Builder(this).setTitle("Add to playlist").setItems(names.toArray(new String[0]), (dialog, which) -> {
            String selected = names.get(which); if (selected.startsWith("+")) promptForPlaylist(track); else saveTrackToPlaylist(selected, track);
        }).show();
    }

    private List<String> playlistNames(android.content.SharedPreferences prefs) {
        List<String> names = new ArrayList<>(); for (String key : prefs.getAll().keySet()) if (key.startsWith("playlist:")) names.add(key.substring(9));
        java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER); return names;
    }

    private void promptForPlaylist(Track track) {
        EditText input = new EditText(this); input.setHint("Playlist name");
        new AlertDialog.Builder(this).setTitle("New playlist").setView(input).setPositiveButton("Create and add", (dialog, which) -> {
            String name = input.getText().toString().trim(); if (!name.isEmpty()) saveTrackToPlaylist(name, track);
        }).setNegativeButton("Cancel", null).show();
    }

    private void saveTrackToPlaylist(String playlist, Track track) {
        android.content.SharedPreferences prefs = getSharedPreferences("library", MODE_PRIVATE); String key = "playlist:" + playlist;
        java.util.Set<String> entries = new java.util.LinkedHashSet<>(prefs.getStringSet(key, new java.util.LinkedHashSet<>()));
        try {
            org.json.JSONObject value = new org.json.JSONObject(); value.put("id", track.stableId); value.put("uri", track.playbackUri.toString()); value.put("title", track.title);
            value.put("artist", track.artist); value.put("album", track.album); value.put("artwork", track.artworkUri == null ? "" : track.artworkUri.toString()); entries.add(value.toString());
        } catch (Exception ignored) { return; }
        prefs.edit().putStringSet(key, entries).putString("last_playlist", playlist).apply(); Toast.makeText(this, "Added to " + playlist, Toast.LENGTH_SHORT).show();
    }

    private void showPlaylists() {
        android.content.SharedPreferences prefs = getSharedPreferences("library", MODE_PRIVATE);
        List<String> names = playlistNames(prefs);
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
        if (BuildConfig.IS_PERSONAL && b2Settings.bundled()) {
            String[] options = new String[]{"Stream online", "Backblaze library", "This phone"};
            new AlertDialog.Builder(this).setTitle("Music source").setItems(options, (dialog, which) -> {
                if (which == 0) {
                    onlineMode = true; remoteMode = true; b2Mode = false; permissionPanel.setVisibility(View.GONE);
                    findViewById(R.id.tracks).setVisibility(View.VISIBLE);
                    ((TextView)findViewById(R.id.source)).setText("Stream  ▾"); setGroup("");
                } else if (which == 1) {
                    onlineMode = false; remoteMode = true; b2Mode = true; permissionPanel.setVisibility(View.GONE);
                    findViewById(R.id.tracks).setVisibility(View.VISIBLE);
                    ((TextView)findViewById(R.id.source)).setText("Backblaze  ▾"); setGroup("");
                } else {
                    onlineMode = false; remoteMode = false; b2Mode = false;
                    ((TextView)findViewById(R.id.source)).setText("This phone  ▾"); refreshPermissionState();
                }
            }).show();
            return;
        }
        String[] options = new String[]{"Stream online", "This phone", b2Settings.configured() ? "Backblaze library" : "Set up Backblaze", serverSettings.configured() ? "Navidrome" : "Set up Navidrome", "Backblaze settings", "Navidrome settings"};
        new AlertDialog.Builder(this).setTitle("Music library").setItems(options, (dialog, which) -> {
            if (which == 0) { onlineMode = true; remoteMode = true; b2Mode = false; permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE); ((TextView)findViewById(R.id.source)).setText("Stream  ▾"); setGroup(""); }
            else if (which == 1) { onlineMode = false; remoteMode = false; b2Mode = false; ((TextView)findViewById(R.id.source)).setText("This phone  ▾"); refreshPermissionState(); setGroup(""); }
            else if (which == 2 && b2Settings.configured()) { onlineMode = false; remoteMode = true; b2Mode = true; permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE); ((TextView)findViewById(R.id.source)).setText("Backblaze  ▾"); setGroup(""); }
            else if (which == 2) configureBackblaze();
            else if (which == 3 && serverSettings.configured()) { onlineMode = false; remoteMode = true; b2Mode = false; permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE); ((TextView)findViewById(R.id.source)).setText("Navidrome  ▾"); setGroup(""); }
            else if (which == 3 || which == 5) configureServer();
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
            b2Settings.save(id, secret, bucketName); status.setText("Connecting to Backblaze…"); io.execute(() -> { boolean ok = new B2CatalogClient(b2Settings).ping(); main.post(() -> { if (ok) { reconnectPlaybackService(); onlineMode = false; remoteMode = true; b2Mode = true; permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE); ((TextView)findViewById(R.id.source)).setText("Backblaze  ▾"); setGroup(""); } else status.setText("Backblaze connection failed. Check the bucket-restricted key and bucket name."); }); });
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
                if (ok) { onlineMode = false; remoteMode = true; b2Mode = false; permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE); ((TextView)findViewById(R.id.source)).setText("Navidrome  ▾"); setGroup(""); }
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

    @Override public void onDestroy() { jamTicker.removeCallbacksAndMessages(null); io.shutdown(); jamSyncIo.shutdown(); jamSearchIo.shutdown(); super.onDestroy(); }
}
