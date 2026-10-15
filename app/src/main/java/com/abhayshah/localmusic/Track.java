package com.abhayshah.localmusic;

import android.net.Uri;
import android.provider.MediaStore;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

final class Track {
    final long id;
    final String title;
    final String artist;
    final String album;
    final long albumId;
    final String stableId;
    final Uri playbackUri;
    final Uri artworkUri;

    Track(long id, String title, String artist, String album, long albumId) {
        this.id = id;
        this.title = clean(title, "Unknown title");
        this.artist = clean(artist, "Unknown artist");
        this.album = clean(album, "Unknown album");
        this.albumId = albumId;
        this.stableId = "local:" + id;
        this.playbackUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.toString(id));
        this.artworkUri = albumId >= 0 ? android.content.ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId) : null;
    }

    /** Constructor for a future authorized catalog adapter with opaque, source-stable IDs. */
    Track(String stableId, Uri playbackUri, String title, String artist, String album) {
        this(stableId, playbackUri, title, artist, album, null);
    }

    Track(String stableId, Uri playbackUri, String title, String artist, String album, Uri artworkUri) {
        this.id = -1;
        this.title = clean(title, "Unknown title");
        this.artist = clean(artist, "Unknown artist");
        this.album = clean(album, "Unknown album");
        this.albumId = -1;
        this.stableId = stableId;
        this.playbackUri = playbackUri;
        this.artworkUri = artworkUri;
    }

    MediaItem toMediaItem() {
        MediaMetadata.Builder metadata = new MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album);
        if (artworkUri != null) metadata.setArtworkUri(artworkUri);
        return new MediaItem.Builder().setMediaId(stableId).setUri(playbackUri).setMediaMetadata(metadata.build()).build();
    }

    static String clean(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }
}
