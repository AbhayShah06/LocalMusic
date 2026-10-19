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
    private final java.util.Set<Runnable> livePlayerViews = new java.util.HashSet<>();
    private void refreshLivePlayerViews() { for(Runnable refresh:new ArrayList<>(livePlayerViews))refresh.run(); }
    private final Player.Listener uiPlayerListener = new Player.Listener() {
        @Override public void onEvents(Player player, Player.Events events) {
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_TIMELINE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) { updateNowPlaying(); refreshLivePlayerViews(); }
        }
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
        findViewById(R.id.playPause).setOnClickListener(v -> togglePlayback());
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
        findViewById(R.id.forYou).setOnClickListener(v -> showForYou());
        findViewById(R.id.albums).setOnClickListener(v -> { selectTab(R.id.albums); showGroups(false); });
        findViewById(R.id.artists).setOnClickListener(v -> { selectTab(R.id.artists); showGroups(true); });
        findViewById(R.id.playlists).setOnClickListener(v -> { selectTab(R.id.playlists); showPlaylists(); });
        selectTab(R.id.songs);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString(); if(showingForYou && !query.trim().isEmpty())selectTab(R.id.songs); searchHandler.removeCallbacksAndMessages(null);
                searchHandler.postDelayed(MainActivity.this::resetAndLoad, onlineMode ? 350 : 120);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        onlineMode = true; remoteMode = true; b2Mode = false;
        permissionPanel.setVisibility(View.GONE); findViewById(R.id.tracks).setVisibility(View.VISIBLE);
        ((TextView)findViewById(R.id.source)).setText("Stream  ▾");
        status.setText("Search for any song, artist, or album");
        showForYou();
        handlePlaylistShare(getIntent());
        if (getIntent().getBooleanExtra("show_download_activity",false)) showDownloadActivity();
    }

    private void connect() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try { controller = controllerFuture.get(); controller.addListener(uiPlayerListener); updateNowPlaying(); refreshLivePlayerViews(); }
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

    @Override protected void onResume() { super.onResume(); refreshPermissionState(); UpdateManager.refresh(this,findViewById(R.id.root),false); }

    private String audioPermission() { return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE; }

    private boolean hasAudioPermission() { return checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED; }

    private void requestAudioPermission() { requestPermissions(new String[]{audioPermission()}, AUDIO_PERMISSION_REQUEST); }

    private void refreshPermissionState() {
        if (permissionPanel == null || remoteMode || showingForYou) return;
        if (hasAudioPermission() || !DownloadStore.all(this).isEmpty()) {
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

    private View forYouContent;
    private boolean showingForYou;
    private void selectTab(int selectedId) {
        if (selectedId != R.id.forYou && showingForYou) {
            showingForYou=false;
            if(forYouContent!=null){((android.widget.FrameLayout)findViewById(R.id.libraryContent)).removeView(forYouContent);forYouContent=null;}
            findViewById(R.id.tracks).setVisibility(View.VISIBLE);status.setVisibility(View.VISIBLE);
        }
        int[] ids = {R.id.forYou, R.id.songs, R.id.albums, R.id.artists, R.id.playlists};
        for (int id : ids) findViewById(id).setSelected(id == selectedId);
    }

    private void setGroup(String value) { selectTab(R.id.songs); group = value; query = ""; search.setText(""); resetAndLoad(); }

    private boolean libraryPaged = true;
    private int libraryGeneration;
    private void resetAndLoad() {
        libraryGeneration++; libraryPaged=true;
        offset = 0; exhausted = false; loading = false; tracks.clear(); adapter.clear();
        loadNextPage();
    }

    private void loadNextPage() {
        if (showingForYou || !libraryPaged || loading || exhausted || !group.isEmpty()) return;
        loading = true;
        int generation=libraryGeneration; boolean pageOnline=onlineMode,pageB2=b2Mode,pageRemote=remoteMode;
        int pageOffset = offset;
        String pageQuery = query;
        searchIo.execute(() -> {
            List<Track> page;
            try { page = pageOnline ? onlineSource().page(pageQuery, pageOffset, PAGE_SIZE) : pageB2 ? new B2CatalogClient(b2Settings).page(pageQuery, pageOffset, PAGE_SIZE) : pageRemote ? new OpenSubsonicClient(serverSettings).page(pageQuery, pageOffset, PAGE_SIZE) : new PhoneCatalogSource(this).page(pageQuery, pageOffset, PAGE_SIZE); }
            catch (RuntimeException error) { main.post(() -> { if(generation!=libraryGeneration||isDestroyed())return; loading = false; status.setText("Could not reach this music source. Check its connection settings."); }); return; }
            main.post(() -> {
                if(generation!=libraryGeneration||isDestroyed())return;
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
        libraryGeneration++;libraryPaged=false;loading=false;searchHandler.removeCallbacksAndMessages(null);int generation=libraryGeneration;
        group = artists ? "artist" : "album"; tracks.clear(); adapter.clear();
        streamIo.execute(() -> {
            java.util.Set<String> labels = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for(Track track:DownloadStore.all(this))labels.add(artists?track.artist:track.album+" · "+track.artist);
            if(hasAudioPermission()){
                String[] columns = artists ? new String[]{android.provider.MediaStore.Audio.Artists._ID, android.provider.MediaStore.Audio.Artists.ARTIST}
                        : new String[]{android.provider.MediaStore.Audio.Albums._ID, android.provider.MediaStore.Audio.Albums.ALBUM, android.provider.MediaStore.Audio.Albums.ARTIST};
                Uri uri = artists ? android.provider.MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI : android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
                try (android.database.Cursor c = getContentResolver().query(uri, columns, null, null, columns[1] + " COLLATE NOCASE ASC")) {
                    if (c != null) while (c.moveToNext() && labels.size() < 200) labels.add(artists ? Track.clean(c.getString(1), "Unknown artist") : Track.clean(c.getString(1), "Unknown album") + " · " + Track.clean(c.getString(2), "Unknown artist"));
                }catch(SecurityException permissionChanged){}
            }
            main.post(() -> {if(generation!=libraryGeneration||isDestroyed())return;adapter.clear();for(String label:labels)adapter.add(label);status.setText(labels.size()+(artists?" artists":" albums"));});
        });
    }

    private void playFrom(int position) {
        if (group.isEmpty()) {
            if (controller == null || tracks.isEmpty()) return;
            if (remoteMode && tracks.get(position).stableId.startsWith("online:")) { resolveAndPlayOnline(new ArrayList<>(tracks), position); return; }
            stopRadio();
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
        stopRadio();
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
        main.post(() -> {
            if(controller==null||streamRequest.get()!=request||!getSharedPreferences("settings",MODE_PRIVATE).getBoolean("autoplay",true))return;
            try{startService(new Intent(this,PlaybackService.class).setAction(PlaybackService.ACTION_SET_RADIO)
                .putExtra("radio_seed",JamClient.hostCatalog(java.util.Collections.singletonList(sourceTracks.get(position).original())))
                .putExtra("radio_candidates",JamClient.hostCatalog(radio)));}catch(Exception ignored){}
        });
    }

    private void stopRadio(){startService(new Intent(this,PlaybackService.class).setAction(PlaybackService.ACTION_STOP_RADIO));}

    private androidx.media3.common.MediaMetadata displayMetadata() {
        if (controller == null) return androidx.media3.common.MediaMetadata.EMPTY;
        androidx.media3.common.MediaItem item = controller.getCurrentMediaItem();
        return item == null ? androidx.media3.common.MediaMetadata.EMPTY : item.mediaMetadata;
    }

    private void updateNowPlaying() {
        if (controller == null || displayMetadata().title == null) {
            nowPlaying.setText("Nothing playing"); nowPlayingArtist.setText("Choose a song");
        } else {
            adapter.clearPending();
            nowPlaying.setText(displayMetadata().title);
            nowPlayingArtist.setText(Track.clean(displayMetadata().artist==null?null:displayMetadata().artist.toString(), "Unknown artist"));
        }
        ArtworkLoader.into(findViewById(R.id.miniArtwork), controller == null ? null : displayMetadata().artworkUri, android.R.drawable.ic_media_play);
        ((android.widget.ImageButton)findViewById(R.id.playPause)).setImageResource(controller != null && controller.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        refreshProgress();
        publishJamPlayback();
    }

    private void showNowPlaying() {
        if (controller == null || controller.getCurrentMediaItem() == null) { Toast.makeText(this, "Choose a song first", Toast.LENGTH_SHORT).show(); return; }
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar);
        dialog.setContentView(R.layout.dialog_now_playing);
        installSwipeBack(dialog.findViewById(android.R.id.content), dialog::dismiss);
        if (dialog.getWindow() != null) { dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT); dialog.getWindow().setStatusBarColor(android.graphics.Color.rgb(11, 11, 12)); dialog.getWindow().setNavigationBarColor(android.graphics.Color.rgb(11, 11, 12)); }
        TextView title = dialog.findViewById(R.id.fullTitle), artist = dialog.findViewById(R.id.fullArtist), elapsed = dialog.findViewById(R.id.fullElapsed), durationLabel = dialog.findViewById(R.id.fullDuration);
        android.widget.ImageButton play = dialog.findViewById(R.id.fullPlayPause);
        android.widget.ImageView artwork = dialog.findViewById(R.id.fullArtwork), backgroundArtwork = dialog.findViewById(R.id.fullBackgroundArtwork); SeekBar fullSeek = dialog.findViewById(R.id.fullSeek);
        if (Build.VERSION.SDK_INT >= 31) backgroundArtwork.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(44f, 44f, android.graphics.Shader.TileMode.CLAMP));
        Runnable refresh = () -> {
            if (controller == null) return;
            if(controller.getCurrentMediaItem()==null){title.setText("Nothing playing");artist.setText("");play.setImageResource(R.drawable.ic_play);artwork.setImageResource(android.R.drawable.ic_media_play);backgroundArtwork.setImageDrawable(null);elapsed.setText("0:00");durationLabel.setText("0:00");fullSeek.setProgress(0);return;}
            title.setText(Track.clean(displayMetadata().title==null?null:displayMetadata().title.toString(), "Now playing"));
            artist.setText(Track.clean(displayMetadata().artist==null?null:displayMetadata().artist.toString(), "Unknown artist"));
            play.setImageResource(controller.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            ArtworkLoader.into(artwork, displayMetadata().artworkUri, android.R.drawable.ic_media_play);
            ArtworkLoader.into(backgroundArtwork, displayMetadata().artworkUri, android.R.drawable.ic_media_play);
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
        play.setOnClickListener(v -> { togglePlayback(); refresh.run(); });
        dialog.findViewById(R.id.fullPrevious).setOnClickListener(v -> { if(controller!=null)controller.seekToPreviousMediaItem(); });
        dialog.findViewById(R.id.fullNext).setOnClickListener(v -> { if(controller!=null)controller.seekToNextMediaItem(); });
        dialog.findViewById(R.id.fullQueue).setOnClickListener(v -> showQueue());
        dialog.findViewById(R.id.fullLyrics).setOnClickListener(v -> { Track current = currentTrack(); if (current != null) showLyrics(current); });
        dialog.findViewById(R.id.fullPlaylist).setOnClickListener(v -> { Track current = currentTrack(); if (current != null) addTrackToPlaylist(current); });
        artist.setOnClickListener(v -> showArtistPage(artist.getText().toString()));
        fullSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { if (fromUser && controller!=null && controller.getDuration() > 0) controller.seekTo(controller.getDuration() * progress / 1000); }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        livePlayerViews.add(refresh); dialog.setOnDismissListener(ignored -> { main.removeCallbacks(progressTicker[0]); livePlayerViews.remove(refresh); });
        dialog.show(); main.post(progressTicker[0]); if (dialog.getWindow() != null) { dialog.getWindow().setLayout(-1, -1); }
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
            installSwipeBack(scroll, lyricsDialog::dismiss);
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
        dialog.setContentView(root); close.setOnClickListener(v -> dialog.dismiss()); installSwipeBack(root, dialog::dismiss); installSwipeBack(list, dialog::dismiss); dialog.show(); if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -1);
        List<Track> artistTracks = new ArrayList<>(); LibraryAdapter artistAdapter = new LibraryAdapter(this, artistTracks, position -> { dialog.dismiss(); resolveAndPlayOnline(artistTracks, position); }, position -> addTrackToPlaylist(artistTracks.get(position))); list.setAdapter(artistAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> { dialog.dismiss(); resolveAndPlayOnline(artistTracks, position); });
        searchIo.execute(() -> {
            List<Track> found = onlineSource().page(artistName, 0, 50);
            main.post(() -> { artistTracks.addAll(found); for (Track track : found) artistAdapter.add(track.title + "\n" + track.artist + " · " + track.album); artistStatus.setText(found.size() + " top songs"); });
        });
    }

    private void showFeedback() {
        final String email = FeedbackDraft.EMAIL;
        new AlertDialog.Builder(this).setTitle("Feedback").setItems(new String[]{"Email Abhay", "Copy email address"}, (dialog, which) -> {
            if (which == 1) { copySupportEmail(email); return; }
            String body = FeedbackDraft.body(BuildConfig.VERSION_NAME, java.time.Instant.now(), java.time.ZoneId.systemDefault());
            Intent draft = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email + "?subject=" + Uri.encode(FeedbackDraft.subject(BuildConfig.VERSION_NAME)) + "&body=" + Uri.encode(body)));
            try { startActivity(draft); }
            catch (android.content.ActivityNotFoundException unavailable) { copySupportEmail(email); Toast.makeText(this, "No email app installed. Address copied.", Toast.LENGTH_LONG).show(); }
        }).setNegativeButton("Close", null).show();
    }

    private void copySupportEmail(String email) {
        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("Feedback email", email));
        Toast.makeText(this, "Email address copied", Toast.LENGTH_SHORT).show();
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
        android.widget.Switch leveling=settingsSwitch("Volume leveling","Reduce loudness jumps between songs",prefs.getBoolean("volume_leveling",true));
        leveling.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("volume_leveling",checked).apply());content.addView(leveling);
        android.widget.Switch haptics = settingsSwitch("Touch feedback", "Vibrate briefly when a tap is registered", prefs.getBoolean("haptics", true));
        haptics.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("haptics", checked).apply()); content.addView(haptics);
        android.widget.Switch autoplay = settingsSwitch("Autoplay recommendations", "Keep the queue moving with related music", prefs.getBoolean("autoplay", true));
        autoplay.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("autoplay", checked).apply()); content.addView(autoplay);
        android.widget.Switch explicit = settingsSwitch("Avoid clean and radio edits", "Prefer original explicit versions when available", prefs.getBoolean("avoid_clean", true));
        explicit.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("avoid_clean", checked).apply()); content.addView(explicit);

        content.addView(settingsHeading("Library"));
        content.addView(settingsAction("Music source", "Choose online streaming, phone files, Backblaze, or Navidrome", this::chooseSource));
        content.addView(settingsAction("Import playlists", "Paste YouTube links, Spotify / Apple Music exports, or a song list", this::showImportOptions));
        content.addView(settingsAction("Equalizer", "Open your phone's audio effects", this::openEqualizer));
        content.addView(settingsAction("Music permission", hasAudioPermission() ? "Allowed for on-device songs" : "Tap to allow on-device songs", () -> { if (hasAudioPermission()) openAppSettings(); else requestAudioPermission(); }));

        long downloads = DownloadStore.bytes(this), streams = PlaybackCache.bytes(), artwork = ArtworkLoader.bytes(this);
        content.addView(settingsHeading("Storage"));
        content.addView(settingsAction("Download activity", "", this::showDownloadActivity));
        content.addView(settingsAction("Feedback", "", this::showFeedback));
        content.addView(settingsAction("Check for updates", "LocalMusic " + BuildConfig.VERSION_NAME, () -> UpdateManager.refresh(this,findViewById(R.id.root),true)));
        content.addView(settingsAction("GitHub releases", "", () -> startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/AbhayShah06/LocalMusic/releases/latest")))));
        content.addView(settingsAction("Offline downloads", formatBytes(downloads) + " saved permanently", this::loadDownloads));
        content.addView(settingsAction("Clear temporary cache", formatBytes(streams + artwork) + " of streams and artwork. Downloads stay.", () -> {
            PlaybackCache.clear(); ArtworkLoader.clear(this); LyricsClient.clear(this);
            Toast.makeText(this, "Temporary cache cleared", Toast.LENGTH_SHORT).show();
        }));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(content);
        AlertDialog settingsDialog=new AlertDialog.Builder(this).setTitle("Settings").setView(scroll).setPositiveButton("Done", null).show();
        installSwipeBack(settingsDialog.findViewById(android.R.id.content),settingsDialog::dismiss);
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
        libraryGeneration++;libraryPaged=false;loading=false;searchHandler.removeCallbacksAndMessages(null);
        selectTab(R.id.songs);
        List<Track> found = mix ? ListeningHistory.dailyMix(this, 50) : ListeningHistory.recent(this, 50);
        group = ""; tracks.clear(); tracks.addAll(found); adapter.clear();
        for (Track track : found) adapter.add(track.title + "\n" + track.artist + " · " + track.album);
        status.setText(found.isEmpty() ? "Play a few songs first" : mix ? "Your daily mix · built on this phone" : "Recently played");
    }

    private long homeRefreshSeed=System.currentTimeMillis()/86_400_000L;
    private void showForYou() {
        libraryGeneration++;libraryPaged=false;loading=false;
        searchHandler.removeCallbacksAndMessages(null);
        android.widget.FrameLayout frame=findViewById(R.id.libraryContent);
        if(forYouContent!=null)frame.removeView(forYouContent);
        showingForYou=true;findViewById(R.id.tracks).setVisibility(View.GONE);permissionPanel.setVisibility(View.GONE);status.setVisibility(View.GONE);
        forYouContent=ForYouDialog.create(this, track -> playMixTracks(java.util.Collections.singletonList(track), 0), this::showSeedMix,
                () -> loadListeningList(false), () -> loadSavedTracks("favorites", "Favorites"), ()->{personalMixes.clear();homeRefreshSeed=System.nanoTime();showForYou();forYouContent.announceForAccessibility("Home refreshed");}, homeRefreshSeed);
        frame.addView(forYouContent,new android.widget.FrameLayout.LayoutParams(-1,-1));selectTab(R.id.forYou);
    }

    private final java.util.Map<String,List<Track>> personalMixes = new java.util.HashMap<>();
    private void showSeedMix(Track seed, boolean artistMix) {
        String name = (artistMix ? seed.artist.replaceAll("(?i)\\s*-\\s*topic$", "").trim() : seed.title) + " Mix";
        String key = seed.stableId + ":" + artistMix;
        if (personalMixes.containsKey(key)) { previewPersonalMix(name, personalMixes.get(key)); return; }
        TextView progress = new TextView(this); progress.setText("Finding related songs…"); progress.setPadding(queueDp(20),queueDp(20),queueDp(20),queueDp(20));
        AlertDialog loadingMix = new AlertDialog.Builder(this).setTitle(name).setView(progress).setNegativeButton("Cancel", null).show();
        streamIo.execute(() -> {
            List<Track> candidates = new ArrayList<>(); candidates.add(seed);
            // Known music remains available if discovery cannot reach its source.
            for (Track track : ListeningHistory.recent(this, 100)) if (artistMix && track.artist.equalsIgnoreCase(seed.artist)) candidates.add(track.original());
            boolean discoveryAvailable = true;
            try {
                OnlineCatalogSource source = onlineSource();
                Track onlineSeed = seed;
                if (!seed.stableId.startsWith("online:")) {
                    List<Track> matches = source.page(seed.artist + " " + seed.title, 0, 5);
                    if (matches.isEmpty()) throw new IllegalStateException();
                    onlineSeed = matches.get(0);
                }
                candidates.addAll(source.songMix(onlineSeed));
                if (artistMix) candidates.addAll(source.page(seed.artist, 0, 15));
            } catch (RuntimeException unavailable) { discoveryAvailable = false; }
            java.util.Set<String> known = new java.util.HashSet<>(); List<Track> mix = new ArrayList<>();
            for (Track candidate : candidates) {
                String identity = RecordingIdentity.title(candidate.title,candidate.artist) + "|" + RecordingIdentity.artist(candidate.artist);
                if (known.add(identity)) mix.add(candidate.original());
                if (mix.size() >= 20) break;
            }
            boolean available = discoveryAvailable;
            main.post(() -> {
                if (isDestroyed() || !loadingMix.isShowing()) return;
                loadingMix.dismiss();
                if (!available) Toast.makeText(this,"Discovery is unavailable. Showing known songs.",Toast.LENGTH_LONG).show();
                else personalMixes.put(key, mix);
                previewPersonalMix(name, mix);
            });
        });
    }

    void previewPersonalMix(String name, List<Track> mix) {
        MixDialog.show(this,name,mix,this::playMixTracks,this::addTracksToPlaylist,this::showCollectionTrackActions);
    }

    private void showCollectionTrackActions(Track track) {
        new AlertDialog.Builder(this).setTitle(track.title).setItems(new String[]{"Play next","Download","Add to playlist"},(dialog,index)->{
            if(index==0){if(track.stableId.startsWith("online:"))enqueueOnline(track,true);else if(controller!=null)controller.addMediaItem(Math.min(controller.getCurrentMediaItemIndex()+1,controller.getMediaItemCount()),track.toMediaItem());}
            else if(index==1)requestDownload(track);else addTracksToPlaylist(java.util.Collections.singletonList(track));
        }).show();
    }

    private boolean savingBatch;
    private void saveTracksToPlaylist(String name,List<Track> tracks){savingBatch=true;try{for(Track track:tracks)saveTrackToPlaylist(name,track);}finally{savingBatch=false;}Toast.makeText(this,"Added "+tracks.size()+" songs to "+name,Toast.LENGTH_SHORT).show();}

    private void addTracksToPlaylist(List<Track> selected) {
        android.content.SharedPreferences prefs=getSharedPreferences("library",MODE_PRIVATE);List<String> names=playlistNames(prefs);names.add("+ New playlist");
        new AlertDialog.Builder(this).setTitle("Add to playlist").setItems(names.toArray(new String[0]),(dialog,index)->{
            if(index==names.size()-1){EditText input=new EditText(this);input.setHint("Playlist name");new AlertDialog.Builder(this).setTitle("New playlist").setView(input).setPositiveButton("Add",(d,w)->{String name=input.getText().toString().trim();if(!name.isEmpty())saveTracksToPlaylist(name,selected);}).setNegativeButton("Cancel",null).show();}
            else saveTracksToPlaylist(names.get(index),selected);
        }).show();
    }

    private void playMixTracks(List<Track> mix, int position) {
        if(controller==null||position<0||position>=mix.size())return;
        streamRequest.incrementAndGet();PlaybackCollection.request(this,mix,position);
        status.setText("Playing mix");
    }

    private void loadDownloads() {
        selectTab(R.id.songs);libraryGeneration++;libraryPaged=false;loading=false;searchHandler.removeCallbacksAndMessages(null);
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
                        .putExtra(PlaybackService.EXTRA_JAM_CODE, room.code).putExtra(PlaybackService.EXTRA_JAM_TOKEN, room.hostToken).putExtra(PlaybackService.EXTRA_JAM_CATALOG, JamClient.hostCatalog(catalog));
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

    // PlaybackService is the only writer of playback snapshots. Activity-only
    // snapshots used to overwrite its full queue with an incomplete list.
    private void publishJamPlayback() {
        if (activeJam != null) startService(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_PUBLISH_JAM));
    }
    private void endJam() {
        JamClient.Room room = activeJam; activeJam = null; jamTicker.removeCallbacksAndMessages(null);
        startService(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_STOP_JAM));
        if (room != null) io.execute(() -> { try { new JamClient().close(room); } catch (Exception ignored) { } });
        Toast.makeText(this, "Jam ended", Toast.LENGTH_SHORT).show();
    }

    private final ExecutorService importIo = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean importCancelled = new java.util.concurrent.atomic.AtomicBoolean();
    private boolean importingPlaylist;

    private void showImportOptions() {
        new AlertDialog.Builder(this).setTitle("Import a playlist").setItems(new String[]{"Paste YouTube / SoundCloud playlist link", "Choose Spotify / Apple Music CSV or M3U", "Paste song names"}, (dialog, which) -> {
            if (which == 1) choosePlaylistFile(); else showPlaylistPaste("");
        }).setNegativeButton("Cancel", null).show();
    }

    private void showPlaylistPaste(String initial) {
        EditText input = new EditText(this); input.setHint("Public playlist link, or one Artist - Song per line"); input.setMinLines(4); input.setText(initial);
        new AlertDialog.Builder(this).setTitle("Import playlist").setMessage("YouTube / SoundCloud link, or Spotify / Apple Music track list.").setView(input)
            .setPositiveButton("Import", (dialog, which) -> {
                String text = input.getText().toString().trim();
                if (text.isEmpty()) return;
                java.util.regex.Matcher link = java.util.regex.Pattern.compile("https?://[^\\s<>]+", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
                if (link.find()) { String url = link.group(); runPlaylistImport(progress -> new OnlineCatalogSource().importPlaylist(url, progress)); }
                else runPlaylistImport(progress -> new PlaylistImporter(getContentResolver()).importText("Imported playlist", text, progress));
            }).setNeutralButton("Choose file", (dialog, which) -> choosePlaylistFile()).setNegativeButton("Cancel", null).show();
    }

    private interface PlaylistImportWork { PlaylistImporter.Result run(PlaylistImporter.Progress progress) throws Exception; }

    private void runPlaylistImport(PlaylistImportWork work) {
        if (importingPlaylist) { Toast.makeText(this, "An import is already running", Toast.LENGTH_SHORT).show(); return; }
        importingPlaylist = true; importCancelled.set(false);
        TextView progressLabel = new TextView(this); progressLabel.setPadding(40, 30, 40, 30); progressLabel.setText("Reading playlist…");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Importing playlist").setView(progressLabel).setCancelable(false).setNegativeButton("Cancel", (d, w) -> importCancelled.set(true)).show();
        importIo.execute(() -> {
            try {
                PlaylistImporter.Result result = work.run((done, total) -> {
                    main.post(() -> { if (dialog.isShowing()) progressLabel.setText("Matched / read " + done + " of " + total + " tracks…"); });
                    return !importCancelled.get() && !isDestroyed();
                });
                main.post(() -> {
                    importingPlaylist = false; dialog.dismiss();
                    if (importCancelled.get() || isDestroyed()) return;
                    if (result.tracks.isEmpty()) { new AlertDialog.Builder(this).setTitle("No matching tracks").setMessage("No songs could be matched. Check the artist/title columns or try a public playlist link.").setPositiveButton("OK", null).show(); return; }
                    EditText name = new EditText(this); name.setText(result.name);
                    new AlertDialog.Builder(this).setTitle("Save imported playlist").setMessage(result.tracks.size() + " songs ready" + (result.missed > 0 ? " · " + result.missed + " could not be matched" : "") + ". Order is preserved; existing playlists will not be replaced.").setView(name)
                        .setPositiveButton("Save", (d, w) -> saveImportedPlaylist(name.getText().toString(), result.tracks)).setNegativeButton("Cancel", null).show();
                });
            } catch (Exception error) {
                main.post(() -> { importingPlaylist = false; dialog.dismiss(); if (!importCancelled.get() && !isDestroyed()) new AlertDialog.Builder(this).setTitle("Could not import playlist").setMessage(error.getMessage() == null ? "Try a public playlist link, a CSV/M3U export, or pasted Artist - Song lines." : error.getMessage()).setPositiveButton("OK", null).show(); });
            }
        });
    }

    private void saveImportedPlaylist(String requested, List<Track> imported) {
        android.content.SharedPreferences library = getSharedPreferences("library", MODE_PRIVATE);
        String base = requested.trim().isEmpty() ? "Imported playlist" : requested.trim(); String name = base; int suffix = 2;
        while (library.contains("playlist:" + name)) name = base + " (" + suffix++ + ")";
        java.util.Set<String> entries = new java.util.LinkedHashSet<>(); org.json.JSONArray order = new org.json.JSONArray();
        for (Track track : imported) { String item = savedMediaItem(track.toMediaItem()); if (!item.isEmpty() && entries.add(item)) order.put(item); }
        library.edit().putStringSet("playlist:" + name, entries).putString("playlist_order:" + name, order.toString()).putString("last_playlist", name).apply();
        loadPlaylist(name); Toast.makeText(this, "Imported " + entries.size() + " songs into " + name, Toast.LENGTH_LONG).show();
    }

    private void handlePlaylistShare(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction())) return;
        Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream != null && "content".equals(stream.getScheme())) { runPlaylistImport(progress -> new PlaylistImporter(getContentResolver()).importFile(stream, progress)); return; }
        String text = intent.getStringExtra(Intent.EXTRA_TEXT); if (text != null && !text.trim().isEmpty()) showPlaylistPaste(text);
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handlePlaylistShare(intent); if (intent.getBooleanExtra("show_download_activity", false)) showDownloadActivity(); }

    private void choosePlaylistFile() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "text/comma-separated-values", "audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain"});
        startActivityForResult(pick, IMPORT_PLAYLIST_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_PLAYLIST_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri file = data.getData();
        runPlaylistImport(progress -> new PlaylistImporter(getContentResolver()).importFile(file, progress));
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
        String[] actions = online ? new String[]{"Play now", "Play next", "Add to queue", DownloadStore.contains(this, track.stableId) ? "Remove download" : "Download", "Add to playlist", "Open artist", FavoriteStore.contains(this,track) ? "Remove favorite" : "Add to favorites", "Start song mix"} : new String[]{"Play now", "Play next", "Add to queue", FavoriteStore.contains(this,track) ? "Remove favorite" : "Add to favorites", "Add to playlist", "Start song mix"};
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
            } else if (action.equals("Download")) { requestDownload(track); }
            else if (action.equals("Remove download")) new AlertDialog.Builder(this).setTitle("Remove download?").setMessage(track.title + " will remain available to stream.").setNegativeButton("Cancel", null).setPositiveButton("Remove", (dialog, button) -> { DownloadStore.remove(this, track.stableId); Toast.makeText(this, "Download removed", Toast.LENGTH_SHORT).show(); }).show();
            else if (action.equals("Add to playlist")) addTrackToPlaylist(track);
            else if (action.equals("Open artist")) showArtistPage(track.artist);
            else if (action.equals("Add to favorites") || action.equals("Remove favorite")) toggleTrackFavorite(track);
            else if (action.equals("Start song mix")) showSeedMix(track,false);
        }).show();
    }

    private void enqueueOnline(Track track, boolean next) {
        status.setText("Preparing " + track.title + "…");
        streamIo.execute(() -> {
            try {
                Track offline = DownloadStore.offline(this,track); Track resolved=offline!=null?offline:onlineSource().resolve(track);
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
        if (controller == null) return;
        final android.app.Dialog dialog = new android.app.Dialog(this);
        android.widget.LinearLayout sheet = new android.widget.LinearLayout(this); sheet.setOrientation(android.widget.LinearLayout.VERTICAL); sheet.setPadding(queueDp(18), queueDp(16), queueDp(18), queueDp(18)); sheet.setBackgroundColor(0xff121111);
        android.widget.LinearLayout header = new android.widget.LinearLayout(this); header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this); title.setText("Queue"); title.setTextColor(0xfff6f1e8); title.setTextSize(28); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); header.addView(title, new android.widget.LinearLayout.LayoutParams(0, -2, 1));
        android.widget.Button close=new android.widget.Button(this);close.setText("Close");close.setContentDescription("Close queue");close.setOnClickListener(v->dialog.dismiss());header.addView(close);
        android.widget.Button save = new android.widget.Button(this); save.setText("Save"); save.setContentDescription("Save queue as playlist"); save.setOnClickListener(v -> saveQueueAsPlaylist(dialog)); header.addView(save); sheet.addView(header);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); android.widget.LinearLayout rows = new android.widget.LinearLayout(this); rows.setOrientation(android.widget.LinearLayout.VERTICAL); scroll.addView(rows); sheet.addView(scroll, new android.widget.LinearLayout.LayoutParams(-1,0,1));
        boolean[] dragging = {false};
        Runnable render = () -> {
            if (dragging[0] || controller == null) return;
            MediaController observed = controller;
            int y = scroll.getScrollY(); rows.removeAllViews(); int current = observed.getCurrentMediaItemIndex(); String last = "";
            for (int i=0;i<observed.getMediaItemCount();i++) {
                String section=queueSection(i,current,observed.getMediaItemAt(i).mediaId);
                if (!section.equals(last)) { rows.addView(queueSectionHeader(section)); last=section; }
                rows.addView(queueRow(dialog,i,current,dragging));
            }
            if(observed.getMediaItemCount()==0) {TextView empty=new TextView(this);empty.setText("Queue is empty");empty.setTextColor(0xffaaa6a0);rows.addView(empty);}
            scroll.post(() -> scroll.scrollTo(0,y));
        };
        livePlayerViews.add(render); dialog.setOnDismissListener(v -> livePlayerViews.remove(render));
        dialog.setContentView(sheet); installSwipeBack(sheet,dialog::dismiss); dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.84f));dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);}
        render.run();
    }

    private int queueIndex(androidx.media3.common.MediaItem item,int hint) {
        if(controller==null)return -1;
        if(hint>=0&&hint<controller.getMediaItemCount()&&controller.getMediaItemAt(hint).mediaId.equals(item.mediaId))return hint;
        for(int i=0;i<controller.getMediaItemCount();i++)if(controller.getMediaItemAt(i).mediaId.equals(item.mediaId))return i;
        return -1;
    }
    private Track queueTrack(androidx.media3.common.MediaItem item) {
        androidx.media3.common.MediaMetadata m=item.mediaMetadata;
        return new Track(Track.originalId(item.mediaId),item.localConfiguration==null?Uri.EMPTY:item.localConfiguration.uri,Track.clean(m.title==null?null:m.title.toString(),"Unknown title"),Track.clean(m.artist==null?null:m.artist.toString(),"Unknown artist"),m.albumTitle==null?"":m.albumTitle.toString(),m.artworkUri);
    }
    private View queueRow(android.app.Dialog dialog,int index,int current,boolean[] draggingQueue) {
        androidx.media3.common.MediaItem item=controller.getMediaItemAt(index); Track track=queueTrack(item);
        android.widget.LinearLayout row=new android.widget.LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(0,queueDp(8),0,queueDp(8));
        if(index==current)row.setBackgroundColor(0xff271f1c);
        TextView rail=new TextView(this);rail.setText(index>current?"\u2630":index==current?"\u25b6":"");rail.setTextColor(0xff969089);rail.setGravity(android.view.Gravity.CENTER);rail.setTextSize(20);rail.setContentDescription("Reorder "+track.title);row.addView(rail,new android.widget.LinearLayout.LayoutParams(queueDp(40),queueDp(52)));
        android.widget.LinearLayout labels=new android.widget.LinearLayout(this);labels.setOrientation(android.widget.LinearLayout.VERTICAL);
        TextView title=new TextView(this);title.setText(track.title);title.setTextColor(0xfff6f1e8);title.setTextSize(15);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(title);
        TextView artist=new TextView(this);artist.setText(track.artist);artist.setTextColor(index==current?0xffff805f:0xff969089);artist.setTextSize(12);artist.setSingleLine(true);artist.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(artist);row.addView(labels,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        TextView more=new TextView(this);more.setText("\u22ee");more.setTextSize(26);more.setTextColor(0xffc7c0b9);more.setGravity(android.view.Gravity.CENTER);more.setContentDescription("More options for "+track.title);more.setClickable(true);row.addView(more,new android.widget.LinearLayout.LayoutParams(queueDp(48),queueDp(52)));
        more.setOnClickListener(v -> showQueueActions(item,index));
        row.setOnClickListener(v -> {int at=queueIndex(item,index);if(at>=0){controller.seekTo(at,0);controller.play();}});
        if(index>current){float[] down={0};rail.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){down[0]=event.getRawY();draggingQueue[0]=true;v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);return true;}
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_MOVE&&draggingQueue[0]){row.setTranslationY(event.getRawY()-down[0]);return true;}
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP||event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL){boolean commit=event.getActionMasked()==android.view.MotionEvent.ACTION_UP;draggingQueue[0]=false;row.setTranslationY(0);int from=queueIndex(item,index);if(commit&&from>=0){int target=Math.max(controller.getCurrentMediaItemIndex()+1,Math.min(controller.getMediaItemCount()-1,from+Math.round((event.getRawY()-down[0])/Math.max(1f,row.getHeight()))));if(target!=from)controller.moveMediaItem(from,target);}return true;}return false;
        });}
        return row;
    }
    private void showQueueActions(androidx.media3.common.MediaItem item,int hint) {
        Track track=queueTrack(item);
        String download=DownloadStore.contains(this,track.stableId)?"Remove download":DownloadTasks.active(this,track.stableId)?"Download activity":"Download";
        new AlertDialog.Builder(this).setTitle(track.title).setItems(new String[]{"Play", "Play next", download, "Add to playlist", "Artist", "Remove from queue"},(d,which)->{
            int at=queueIndex(item,hint);
            if(which==0&&at>=0){controller.seekTo(at,0);controller.play();}
            else if(which==1&&at>=0){int next=Math.min(controller.getCurrentMediaItemIndex()+1,controller.getMediaItemCount()-1);controller.moveMediaItem(at,next);}
            else if(which==2){if(download.equals("Remove download")){DownloadStore.remove(this,track.stableId);Toast.makeText(this,"Download removed",Toast.LENGTH_SHORT).show();}else requestDownload(track);}
            else if(which==3)addTrackToPlaylist(track);
            else if(which==4)showArtistPage(track.artist);
            else if(which==5&&at>=0)controller.removeMediaItem(at);
        }).show();
    }

    private void requestDownload(Track input) {
        Track track=input.original();String scheme=track.playbackUri.getScheme();
        if(DownloadStore.contains(this,track.stableId)||"content".equals(scheme)||"file".equals(scheme)){Toast.makeText(this,"Already on this device",Toast.LENGTH_SHORT).show();return;}
        if(DownloadTasks.active(this,track.stableId)){showDownloadActivity();return;}
        OfflineDownloadService.enqueue(this,track);Toast.makeText(this,"Saved for offline download",Toast.LENGTH_SHORT).show();
    }

    private void showDownloadActivity() {
        android.app.Dialog dialog=new android.app.Dialog(this);android.widget.LinearLayout root=new android.widget.LinearLayout(this);root.setOrientation(android.widget.LinearLayout.VERTICAL);root.setPadding(queueDp(16),queueDp(16),queueDp(16),queueDp(16));root.setBackgroundColor(0xff121111);
        TextView heading=new TextView(this);heading.setText("Downloads");heading.setTextSize(26);heading.setTextColor(0xfff6f1e8);root.addView(heading);
        android.widget.Button saved=new android.widget.Button(this);saved.setText("Offline library");saved.setOnClickListener(v->{dialog.dismiss();loadDownloads();});root.addView(saved);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);android.widget.LinearLayout rows=new android.widget.LinearLayout(this);rows.setOrientation(android.widget.LinearLayout.VERTICAL);scroll.addView(rows);root.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));
        java.util.Map<String,TextView> texts=new java.util.HashMap<>();java.util.Map<String,android.widget.Button> actions=new java.util.HashMap<>();final Runnable[] refresh=new Runnable[1];
        refresh[0]=()->{
            List<org.json.JSONObject> records=DownloadTasks.all(this);
            for(org.json.JSONObject record:records){String id=record.optString("id");Track track=DownloadTasks.track(record);TextView text=texts.get(id);android.widget.Button action=actions.get(id);
                if(text==null){android.widget.LinearLayout row=new android.widget.LinearLayout(this);row.setPadding(0,queueDp(12),0,queueDp(12));text=new TextView(this);text.setTextColor(0xfff6f1e8);text.setTextSize(14);row.addView(text,new android.widget.LinearLayout.LayoutParams(0,-2,1));action=new android.widget.Button(this);row.addView(action);rows.addView(row);texts.put(id,text);actions.put(id,action);}
                long bytes=record.optLong("bytes"),total=record.optLong("total",-1);String state=record.optString("state");String progress=total>0?" · "+Math.min(100,bytes*100/total)+"%":bytes>0?" · "+formatBytes(bytes):"";
                String label=state.equals("waiting")||state.equals("queued")?"Waiting for connection / background retry":state.equals("completed")?"Downloaded":state.equals("failed")?"Failed":state.equals("cancelled")?"Cancelled":"Downloading";
                text.setText(track.title+"\n"+label+progress+(record.optString("error").isEmpty()?"":"\n"+record.optString("error")));
                boolean active=DownloadTasks.active(record);action.setText(active?"Cancel":state.equals("completed")?"Saved":"Retry");action.setEnabled(!state.equals("completed"));action.setContentDescription((active?"Cancel download of ":"Retry download of ")+track.title);
                action.setOnClickListener(v->{if(active)OfflineDownloadService.cancel(this,track);else OfflineDownloadService.enqueue(this,track);});
            }
            if(records.isEmpty()&&rows.getChildCount()==0){TextView empty=new TextView(this);empty.setText("No downloads");empty.setTextColor(0xffaaa6a0);rows.addView(empty);}
            if(dialog.isShowing())main.postDelayed(refresh[0],1000);
        };
        dialog.setContentView(root);installSwipeBack(root,dialog::dismiss);dialog.setOnDismissListener(v->main.removeCallbacks(refresh[0]));dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.85f));refresh[0].run();
    }

    private String queueSection(int index, int current, String mediaId) {
        if (index < current) return "Played";
        if (index == current) return "Now playing";
        if (mediaId != null && mediaId.startsWith("jam:")) return "Jam requests";
        if (mediaId != null && mediaId.startsWith("auto:")) return "Autoplay";
        return "Your queue";
    }

    private View queueSectionHeader(String label) {
        TextView header = new TextView(this); header.setText(label.toUpperCase(java.util.Locale.ROOT));
        header.setTextColor(android.graphics.Color.rgb(255, 128, 95)); header.setTextSize(10); header.setLetterSpacing(.14f);
        header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); header.setPadding(queueDp(5), queueDp(16), 0, queueDp(5));
        return header;
    }

    private void saveQueueAsPlaylist(android.app.Dialog queueDialog) {
        EditText input = new EditText(this); input.setHint("Playlist name");
        new AlertDialog.Builder(this).setTitle("Save queue as playlist").setView(input).setPositiveButton("Save", (dialog, which) -> {
            String name = input.getText().toString().trim(); if (name.isEmpty() || controller == null) return;
            org.json.JSONArray order = new org.json.JSONArray(); java.util.Set<String> entries = new java.util.LinkedHashSet<>();
            for (int i = 0; i < controller.getMediaItemCount(); i++) { String value = savedMediaItem(controller.getMediaItemAt(i)); if (!value.isEmpty() && entries.add(value)) order.put(value); }
            getSharedPreferences("library", MODE_PRIVATE).edit().putStringSet("playlist:" + name, entries).putString("playlist_order:" + name, order.toString()).putString("last_playlist", name).apply();
            Toast.makeText(this, "Saved " + order.length() + " songs to " + name, Toast.LENGTH_SHORT).show(); queueDialog.dismiss();
        }).setNegativeButton("Cancel", null).show();
    }

    private String savedMediaItem(androidx.media3.common.MediaItem item) {
        try {
            String stableId = Track.originalId(item.mediaId);
            org.json.JSONObject value = new org.json.JSONObject(); value.put("id", stableId); value.put("uri", item.localConfiguration == null ? "" : item.localConfiguration.uri.toString());
            value.put("title", String.valueOf(item.mediaMetadata.title)); value.put("artist", String.valueOf(item.mediaMetadata.artist)); value.put("album", String.valueOf(item.mediaMetadata.albumTitle)); value.put("artwork", item.mediaMetadata.artworkUri == null ? "" : item.mediaMetadata.artworkUri.toString()); return value.toString();
        } catch (Exception ignored) { return ""; }
    }

    private int queueDp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private void installSwipeBack(View view, Runnable back) {
        if(view==null||view instanceof DismissSwipeLayout||!(view.getParent() instanceof android.view.ViewGroup))return;
        android.view.ViewGroup parent=(android.view.ViewGroup)view.getParent();int index=parent.indexOfChild(view);android.view.ViewGroup.LayoutParams params=view.getLayoutParams();
        parent.removeView(view);DismissSwipeLayout swipe=new DismissSwipeLayout(this,back);swipe.addView(view,new android.widget.FrameLayout.LayoutParams(-1,-1));parent.addView(swipe,index,params);
    }

    private void togglePlayback() {
        if (controller == null) return;
        if (controller.isPlaying()) { controller.pause(); return; }
        if (controller.getMediaItemCount() == 0) return;
        if (controller.getPlaybackState() == Player.STATE_ENDED) controller.seekTo(Math.max(0, controller.getCurrentMediaItemIndex()), 0);
        if (controller.getPlaybackState() == Player.STATE_IDLE) controller.prepare();
        controller.play();
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
        selectTab(R.id.songs);libraryGeneration++;libraryPaged=false;loading=false;searchHandler.removeCallbacksAndMessages(null);
        int generation=libraryGeneration;
        android.content.SharedPreferences library = getSharedPreferences("library", MODE_PRIVATE);
        java.util.List<String> ids = new ArrayList<>(); String order = key.startsWith("playlist:") ? library.getString("playlist_order:" + key.substring(9), "") : "";
        if (!order.isEmpty()) try { org.json.JSONArray values = new org.json.JSONArray(order); for (int i = 0; i < values.length(); i++) ids.add(values.getString(i)); } catch (Exception ignored) { }
        if (ids.isEmpty()) ids.addAll(library.getStringSet(key, new java.util.HashSet<>()));
        io.execute(() -> {
            List<Track> found = new ArrayList<>();
            int loaded = 0;
            for (String id : ids) {
                if (loaded++ >= 500) break;
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
            main.post(() -> { if(generation!=libraryGeneration||isDestroyed())return; group = ""; tracks.clear(); tracks.addAll(found); adapter.clear(); for (Track t : found) adapter.add(t.title + "\n" + t.artist + " · " + t.album); status.setText(found.size() + " " + label.toLowerCase()); if(key.startsWith("playlist:"))previewPersonalMix(key.substring(9),found); });
        });
    }

    private void toggleCurrentFavorite() {
        if (controller == null || controller.getCurrentMediaItem() == null) return;
        toggleTrackFavorite(queueTrack(controller.getCurrentMediaItem()));
    }

    private void toggleTrackFavorite(Track track) {
        boolean saved = FavoriteStore.toggle(this,track);
        Toast.makeText(this,saved ? "Added to favorites" : "Removed from favorites",Toast.LENGTH_SHORT).show();
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
        org.json.JSONArray order; try { order = new org.json.JSONArray(prefs.getString("playlist_order:" + playlist, "[]")); } catch (Exception ignored) { order = new org.json.JSONArray(); }
        String added = null; for (String entry : entries) if (entry.contains("\"id\":\"" + track.stableId.replace("\"", "") + "\"")) added = entry;
        if (added != null) { boolean present = false; for (int i = 0; i < order.length(); i++) if (added.equals(order.optString(i))) present = true; if (!present) order.put(added); }
        prefs.edit().putStringSet(key, entries).putString("playlist_order:" + playlist, order.toString()).putString("last_playlist", playlist).apply(); if(!savingBatch)Toast.makeText(this, "Added to " + playlist, Toast.LENGTH_SHORT).show();
    }

    private void showPlaylists() {
        android.content.SharedPreferences prefs = getSharedPreferences("library", MODE_PRIVATE);
        List<String> names = playlistNames(prefs);
        names.add("+ Import playlist");
        names.add("+ Create playlist");
        new AlertDialog.Builder(this).setTitle("Playlists").setItems(names.toArray(new String[0]), (d, which) -> {
            String name = names.get(which);
            if (name.equals("+ Import playlist")) showImportOptions(); else if (name.equals("+ Create playlist")) createPlaylist(); else loadPlaylist(name);
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
