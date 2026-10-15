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
    static final String SITE_BASE = "https://abhayshah06.github.io/LocalMusic/";

    Room create(List<Track> catalog) throws Exception {
        JSONArray values = new JSONArray();
        for (Track track : catalog) values.put(new JSONObject().put("id", track.stableId).put("title", track.title)
                .put("artist", track.artist).put("album", track.album));
        JSONObject body = post("/api/rooms", new JSONObject().put("catalog", values), null);
        return new Room(body.getString("roomCode"), body.getString("hostToken"));
    }

    List<Entry> queue(String roomCode) throws Exception {
        JSONObject body = get("/api/rooms/" + roomCode);
        JSONArray values = body.getJSONObject("room").getJSONArray("queue");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.getJSONObject(i);
            entries.add(new Entry(item.getString("id"), item.getString("trackId"), item.optString("guest", "Guest")));
        }
        return entries;
    }

    void close(Room room) throws Exception { post("/api/rooms/" + room.code + "/close", new JSONObject(), room.hostToken); }

    static Bitmap qr(String text, int size) throws Exception {
        com.google.zxing.common.BitMatrix matrix = new com.google.zxing.MultiFormatWriter().encode(text,
                com.google.zxing.BarcodeFormat.QR_CODE, size, size, java.util.Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN, 1));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
        return bitmap;
    }

    private static JSONObject get(String path) throws Exception {
        HttpURLConnection c = connection(path); return read(c);
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
    static final class Entry { final String id, trackId, guest; Entry(String id, String trackId, String guest) { this.id = id; this.trackId = trackId; this.guest = guest; } }
}
