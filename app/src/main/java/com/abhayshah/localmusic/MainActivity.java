package com.abhayshah.localmusic;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private final List<Track> tracks = new ArrayList<>();
    private MediaPlayer player;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        findViewById(R.id.stop).setOnClickListener(v -> stopPlayback());
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{permission}, 12);
        else loadTracks();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 12 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadTracks();
        else ((TextView)findViewById(R.id.status)).setText("Music permission denied. Nothing was scanned.");
    }
    private void loadTracks() {
        tracks.clear();
        String[] columns = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST};
        try (Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
            if (cursor != null) while (cursor.moveToNext()) tracks.add(new Track(cursor.getLong(0), cursor.getString(1), cursor.getString(2)));
        }
        List<String> labels = new ArrayList<>();
        for (Track track : tracks) labels.add(track.title + "\n" + (track.artist == null ? "Unknown artist" : track.artist));
        ListView list = findViewById(R.id.tracks);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        list.setOnItemClickListener((parent, view, position, id) -> play(tracks.get(position)));
        ((TextView)findViewById(R.id.status)).setText(tracks.size() + " tracks found on this phone.");
    }
    private void play(Track track) {
        stopPlayback();
        player = MediaPlayer.create(this, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id));
        if (player == null) { ((TextView)findViewById(R.id.status)).setText("This track could not be opened."); return; }
        player.setOnCompletionListener(done -> stopPlayback()); player.start();
        ((TextView)findViewById(R.id.nowPlaying)).setText(track.title + " — " + track.artist);
    }
    private void stopPlayback() {
        if (player != null) { player.stop(); player.release(); player = null; }
        ((TextView)findViewById(R.id.nowPlaying)).setText("Nothing playing");
    }
    @Override public void onDestroy() { stopPlayback(); super.onDestroy(); }
    private static final class Track {
        final long id; final String title; final String artist;
        Track(long id, String title, String artist) { this.id = id; this.title = title; this.artist = artist; }
    }
}

