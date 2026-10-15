package com.abhayshah.localmusic;

import android.graphics.Bitmap;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class JamClient {
    static final String API_BASE = "https://localmusic-jam.localmusic-jam-worker.workers.dev";
    static final String SITE_BASE = "https://localmusic-jam.vercel.app/";

    Room create(List<Track> catalog) throws Exception {
        JSONArray values = new JSONArray();
        for (Track track : catalog) values.put(new JSONObject().put("id", track.stableId).put("title", track.title)
                .put("artist", track.artist).put("album", track.album));
        JSONObject body = post("/api/rooms", new JSONObject().put("catalog", values), null);
        return new Room(body.getString("roomCode"), body.getString("hostToken"));
    }

    State state(Room room) throws Exception {
        JSONObject body = get("/api/rooms/" + room.code, room.hostToken);
        JSONArray values = body.getJSONObject("room").getJSONArray("queue");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.getJSONObject(i);
            entries.add(new Entry(item.getString("id"), item.getString("trackId"), item.optString("guest", "Guest"), item.optString("title"), item.optString("artist")));
        }
        List<Search> searches = new ArrayList<>(); JSONArray searchValues = body.getJSONObject("room").optJSONArray("searches");
        if (searchValues != null) for (int i = 0; i < searchValues.length(); i++) { JSONObject item = searchValues.getJSONObject(i); searches.add(new Search(item.getString("id"), item.optString("query"), "pending".equals(item.optString("status")))); }
        List<Control> controls = new ArrayList<>(); JSONArray controlValues = body.getJSONObject("room").optJSONArray("controls");
        if (controlValues != null) for (int i = 0; i < controlValues.length(); i++) { JSONObject item = controlValues.getJSONObject(i); controls.add(new Control(item.getString("id"), item.optString("action"), item.optString("guest", "Guest"), item.optInt("index", -1), item.optInt("direction", 0))); }
        return new State(entries, searches, controls);
    }

    void publish(Room room, String searchId, List<Track> tracks) throws Exception {
        JSONArray values = new JSONArray(); for (Track track : tracks) values.put(new JSONObject().put("id", track.stableId).put("title", track.title).put("artist", track.artist).put("album", track.album));
        post("/api/rooms/" + room.code + "/search/" + searchId + "/results", new JSONObject().put("catalog", values), room.hostToken);
    }

    void publishPlayback(Room room, String title, String artist, boolean playing, List<String[]> upcoming) throws Exception {
        JSONArray queue = new JSONArray(); for (String[] item : upcoming) queue.put(new JSONObject().put("title", item[0]).put("artist", item[1]));
        post("/api/rooms/" + room.code + "/playback", new JSONObject().put("title", title).put("artist", artist).put("playing", playing).put("upcoming", queue), room.hostToken);
    }

    void close(Room room) throws Exception { post("/api/rooms/" + room.code + "/close", new JSONObject(), room.hostToken); }

    static Bitmap qr(String text, int size) throws Exception {
        com.google.zxing.common.BitMatrix matrix = new com.google.zxing.MultiFormatWriter().encode(text,
                com.google.zxing.BarcodeFormat.QR_CODE, size, size, java.util.Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN, 1));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
        return bitmap;
    }

    private static JSONObject get(String path, String bearer) throws Exception {
        HttpURLConnection c = connection(path); if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer); return read(c);
    }
    private static JSONObject post(String path, JSONObject body, String bearer) throws Exception {
        HttpURLConnection c = connection(path); c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        return read(c);
    }
    private static HttpURLConnection connection(String path) throws Exception {
        if (API_BASE.contains("REPLACE_ME")) throw new IllegalStateException("Jam service is not deployed");
        HttpURLConnection c = (HttpURLConnection)new URL(API_BASE + path).openConnection();
        c.setConnectTimeout(10_000); c.setReadTimeout(20_000); c.setRequestProperty("User-Agent", "LocalMusic/0.6"); return c;
    }
    private static JSONObject read(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode(); InputStream input = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in != null) { byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n); }
            String text = out.toString("UTF-8"); JSONObject result = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (code < 200 || code >= 300) throw new IllegalStateException(result.optString("error", "Jam request failed")); return result;
        } finally { c.disconnect(); }
    }

    static final class Room { final String code, hostToken; Room(String code, String hostToken) { this.code = code; this.hostToken = hostToken; } String joinUrl() { return SITE_BASE + "?room=" + code; } }
    static final class State { final List<Entry> entries; final List<Search> searches; final List<Control> controls; State(List<Entry> entries, List<Search> searches, List<Control> controls) { this.entries = entries; this.searches = searches; this.controls = controls; } }
    static final class Search { final String id, query; final boolean pending; Search(String id, String query, boolean pending) { this.id = id; this.query = query; this.pending = pending; } }
    static final class Control { final String id, action, guest; final int index, direction; Control(String id, String action, String guest, int index, int direction) { this.id = id; this.action = action; this.guest = guest; this.index = index; this.direction = direction; } }
    static final class Entry { final String id, trackId, guest, title, artist; Entry(String id, String trackId, String guest, String title, String artist) { this.id = id; this.trackId = trackId; this.guest = guest; this.title = title; this.artist = artist; } }
}
