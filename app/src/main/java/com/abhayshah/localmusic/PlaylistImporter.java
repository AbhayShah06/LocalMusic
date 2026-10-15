package com.abhayshah.localmusic;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Imports common playlist exports locally. It never sends library data to a server. */
final class PlaylistImporter {
    static final class Result {
        final String name; final Set<String> localIds; final int matched; final int missed;
        Result(String name, Set<String> ids, int matched, int missed) { this.name = name; this.localIds = ids; this.matched = matched; this.missed = missed; }
    }
    private static final class Wanted { final String title, artist; Wanted(String title, String artist) { this.title = title; this.artist = artist; } }
    private final ContentResolver resolver;
    PlaylistImporter(ContentResolver resolver) { this.resolver = resolver; }

    Result importFile(Uri uri) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resolver.openInputStream(uri)))) {
            String line; while ((line = reader.readLine()) != null && lines.size() < 20_000) lines.add(line);
        }
        List<Wanted> wanted = !lines.isEmpty() && (lines.get(0).startsWith("#EXTM3U") || lines.stream().anyMatch(v -> v.startsWith("#EXTINF:"))) ? parseM3u(lines) : parseCsv(lines);
        if (wanted.isEmpty()) throw new IllegalArgumentException("No tracks found");
        Map<String, List<Track>> byTitle = localLibrary();
        Set<String> ids = new LinkedHashSet<>(); int missed = 0;
        for (Wanted item : wanted) {
            List<Track> candidates = byTitle.get(normalize(item.title)); Track best = null; int bestScore = -1;
            if (candidates != null) for (Track track : candidates) {
                int score = normalize(track.title).equals(normalize(item.title)) ? 70 : 0;
                String wantedArtist = normalize(item.artist), trackArtist = normalize(track.artist);
                if (!wantedArtist.isEmpty() && (trackArtist.equals(wantedArtist) || trackArtist.contains(wantedArtist) || wantedArtist.contains(trackArtist))) score += 30;
                if (score > bestScore) { best = track; bestScore = score; }
            }
            if (best != null && bestScore >= 70) ids.add(Long.toString(best.id)); else missed++;
        }
        return new Result(fileName(uri), ids, ids.size(), missed);
    }

    private Map<String, List<Track>> localLibrary() {
        Map<String, List<Track>> result = new HashMap<>();
        String[] cols = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID};
        try (Cursor c = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null)) {
            if (c != null) while (c.moveToNext()) { Track track = new Track(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)); result.computeIfAbsent(normalize(track.title), k -> new ArrayList<>()).add(track); }
        }
        return result;
    }

    private static List<Wanted> parseM3u(List<String> lines) {
        List<Wanted> result = new ArrayList<>(); String pending = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF:")) { int comma = line.indexOf(','); pending = comma >= 0 ? line.substring(comma + 1).trim() : ""; }
            else if (!line.isEmpty() && !line.startsWith("#")) {
                String label = pending == null || pending.isEmpty() ? fileStem(line) : pending; pending = null;
                int split = label.indexOf(" - "); result.add(split > 0 ? new Wanted(label.substring(split + 3), label.substring(0, split)) : new Wanted(label, ""));
            }
        }
        return result;
    }

    private static List<Wanted> parseCsv(List<String> lines) {
        List<Wanted> result = new ArrayList<>(); if (lines.isEmpty()) return result;
        List<String> headers = csv(lines.get(0)); int title = find(headers, "track name", "track", "title", "name"); int artist = find(headers, "artist name", "artist", "artists", "album artist");
        if (title < 0) throw new IllegalArgumentException("No title column");
        for (int i = 1; i < lines.size(); i++) { List<String> row = csv(lines.get(i)); if (title < row.size() && !row.get(title).trim().isEmpty()) result.add(new Wanted(row.get(title), artist >= 0 && artist < row.size() ? row.get(artist) : "")); }
        return result;
    }

    private static List<String> csv(String line) {
        List<String> out = new ArrayList<>(); StringBuilder value = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) { char ch = line.charAt(i); if (ch == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { value.append('"'); i++; } else quoted = !quoted; } else if (ch == ',' && !quoted) { out.add(value.toString().trim()); value.setLength(0); } else value.append(ch); }
        out.add(value.toString().trim()); return out;
    }

    private static int find(List<String> values, String... names) { for (int i = 0; i < values.size(); i++) for (String name : names) if (normalize(values.get(i)).equals(name)) return i; return -1; }
    private String fileName(Uri uri) { try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (c != null && c.moveToFirst()) return fileStem(c.getString(0)); } return "Imported playlist"; }
    private static String fileStem(String value) { String name = value.replace('\\', '/'); name = name.substring(name.lastIndexOf('/') + 1); int dot = name.lastIndexOf('.'); return dot > 0 ? name.substring(0, dot) : name; }
    private static String normalize(String value) { if (value == null) return ""; return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("\\([^)]*(remaster|version|edit)[^)]*\\)", "").replaceAll("[^a-z0-9]+", " ").trim(); }
}
