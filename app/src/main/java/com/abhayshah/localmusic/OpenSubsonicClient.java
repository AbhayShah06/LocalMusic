package com.abhayshah.localmusic;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

final class OpenSubsonicClient implements CatalogSource {
    private final ServerSettings settings;
    OpenSubsonicClient(ServerSettings settings) { this.settings = settings; }

    boolean ping() {
        try { return "ok".equals(request("ping", "").getString("status")); } catch (Exception error) { return false; }
    }

    @Override public List<Track> page(String query, int offset, int limit) {
        List<Track> tracks = new ArrayList<>();
        try {
            String term = query == null ? "" : query.trim();
            JSONObject response;
            JSONArray songs;
            if (term.isEmpty()) {
                if (offset > 0) return tracks;
                response = request("getRandomSongs", "size=" + Math.min(500, Math.max(50, limit * 4)));
                songs = response.optJSONObject("randomSongs") == null ? null : response.optJSONObject("randomSongs").optJSONArray("song");
            } else {
                response = request("search3", "query=" + encode(term) + "&songCount=" + limit + "&songOffset=" + offset + "&albumCount=0&artistCount=0");
                songs = response.optJSONObject("searchResult3") == null ? null : response.optJSONObject("searchResult3").optJSONArray("song");
            }
            if (songs == null) return tracks;
            for (int i = 0; i < songs.length(); i++) {
                JSONObject song = songs.getJSONObject(i); String id = song.getString("id");
                tracks.add(new Track("remote:" + id, Uri.parse(endpoint("stream", "id=" + encode(id))),
                        song.optString("title"), song.optString("artist"), song.optString("album")));
            }
        } catch (Exception error) { throw new IllegalStateException("Could not load the Navidrome library", error); }
        return tracks;
    }

    private JSONObject request(String method, String args) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(endpoint(method, args)).openConnection();
        connection.setConnectTimeout(7000); connection.setReadTimeout(15000); connection.setRequestProperty("User-Agent", "LocalMusic/0.3");
        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int read; while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            JSONObject root = new JSONObject(out.toString("UTF-8")).getJSONObject("subsonic-response");
            if (!"ok".equals(root.optString("status"))) throw new IllegalStateException("Server rejected the request");
            return root;
        } finally { connection.disconnect(); }
    }

    private String endpoint(String method, String args) throws Exception {
        String salt = Long.toHexString(new SecureRandom().nextLong());
        String token = hex(MessageDigest.getInstance("MD5").digest((settings.password() + salt).getBytes(StandardCharsets.UTF_8)));
        return settings.url() + "/rest/" + method + ".view?u=" + encode(settings.username()) + "&t=" + token + "&s=" + salt
                + "&v=1.16.1&c=LocalMusic&f=json" + (args.isEmpty() ? "" : "&" + args);
    }

    private static String encode(String value) { try { return URLEncoder.encode(value, "UTF-8"); } catch (Exception impossible) { return value; } }
    private static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b)); return out.toString(); }
}
