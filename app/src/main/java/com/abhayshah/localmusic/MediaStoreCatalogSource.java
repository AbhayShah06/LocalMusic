package com.abhayshah.localmusic;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

final class MediaStoreCatalogSource implements CatalogSource {
    private static final String[] COLUMNS = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID};
    private final ContentResolver resolver;

    MediaStoreCatalogSource(ContentResolver resolver) { this.resolver = resolver; }

    @Override public List<Track> page(String query, int offset, int limit) {
        List<Track> result = new ArrayList<>();
        Bundle args = new Bundle();
        args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
        args.putInt(ContentResolver.QUERY_ARG_LIMIT, Math.max(1, Math.min(limit, 200)));
        args.putInt(ContentResolver.QUERY_ARG_OFFSET, Math.max(0, offset));
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String[] selectionArgs = null;
        String term = query == null ? "" : query.trim();
        if (!term.isEmpty()) {
            selection += " AND (" + MediaStore.Audio.Media.TITLE + " LIKE ? OR " + MediaStore.Audio.Media.ARTIST + " LIKE ? OR " + MediaStore.Audio.Media.ALBUM + " LIKE ? )";
            String pattern = "%" + term.replace("%", "\\%").replace("_", "\\_") + "%";
            selectionArgs = new String[]{pattern, pattern, pattern};
        }
        args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
        if (selectionArgs != null) args.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs);
        try (Cursor c = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, COLUMNS, args, null)) {
            if (c != null) while (c.moveToNext()) result.add(new Track(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)));
        } catch (RuntimeException ignored) { }
        return result;
    }
}
