package com.abhayshah.localmusic;

import android.net.Uri;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class B2CatalogClient implements CatalogSource {
    private static final String AUTHORIZE_URL = "https://api.backblazeb2.com/b2api/v4/b2_authorize_account";
    private static final int DOWNLOAD_TOKEN_SECONDS = 7 * 24 * 60 * 60;
    private final B2Settings settings;
    private Session session;

    B2CatalogClient(B2Settings settings) { this.settings = settings; }

    boolean ping() {
        try { authorize(); return true; } catch (Exception e) { return false; }
    }

    @Override public List<Track> page(String query, int offset, int limit) {
        try {
            Session s = authorize();
            List<JSONObject> files = listAllAudio(s);
            String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            List<Track> result = new ArrayList<>();
            int matched = 0;
            String token = downloadToken(s);
            for (JSONObject file : files) {
                String name = file.getString("fileName");
                SongName song = SongName.from(name);
                if (!needle.isEmpty() && !(song.title + " " + song.artist + " " + song.album).toLowerCase(Locale.ROOT).contains(needle)) continue;
                if (matched++ < offset) continue;
                result.add(new Track("b2:" + name, downloadUri(s, token, name), song.title, song.artist, song.album));
                if (result.size() >= limit) break;
            }
            return result;
        } catch (Exception e) { throw new IllegalStateException("Could not load Backblaze library", e); }
    }

    Uri playbackUri(String fileName) {
        try { Session s = authorize(); return downloadUri(s, downloadToken(s), fileName); }
        catch (Exception e) { throw new IllegalStateException("Could not authorize Backblaze playback", e); }
    }

    private Session authorize() throws Exception {
        if (session != null && session.expiresAt > System.currentTimeMillis()) return session;
        String credentials = settings.keyId() + ":" + settings.applicationKey();
        HttpURLConnection c = connection(AUTHORIZE_URL);
        c.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(credentials.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        JSONObject root = new JSONObject(read(c));
        JSONObject storage = root.getJSONObject("apiInfo").getJSONObject("storageApi");
        JSONObject allowed = root.getJSONObject("allowed");
        String bucketId = "";
        JSONArray buckets = allowed.optJSONArray("buckets");
        if (buckets != null) for (int i = 0; i < buckets.length(); i++) {
            JSONObject bucket = buckets.getJSONObject(i);
            if (settings.bucket().equals(bucket.optString("name"))) { bucketId = bucket.optString("id"); break; }
        }
        if (bucketId.isEmpty() && settings.bucket().equals(allowed.optString("bucketName"))) bucketId = allowed.optString("bucketId");
        if (bucketId.isEmpty()) throw new IllegalStateException("Key is not restricted to the " + settings.bucket() + " bucket");
        session = new Session(storage.getString("apiUrl"), storage.getString("downloadUrl"),
                root.getString("authorizationToken"), bucketId, System.currentTimeMillis() + 23L * 60 * 60 * 1000);
        return session;
    }

    private List<JSONObject> listAllAudio(Session s) throws Exception {
        List<JSONObject> files = new ArrayList<>();
        String next = null;
        do {
            JSONObject request = new JSONObject().put("bucketId", s.bucketId).put("maxFileCount", 1000);
            if (next != null && !next.isEmpty()) request.put("startFileName", next);
            JSONObject response = post(s.apiUrl + "/b2api/v4/b2_list_file_names", s.authorizationToken, request);
            JSONArray values = response.getJSONArray("files");
            for (int i = 0; i < values.length(); i++) {
                JSONObject file = values.getJSONObject(i);
                if (isAudio(file.optString("fileName"), file.optString("contentType"))) files.add(file);
            }
            next = response.optString("nextFileName", null);
        } while (next != null && !next.isEmpty() && files.size() < 10_000);
        return files;
    }

    private String downloadToken(Session s) throws Exception {
        JSONObject request = new JSONObject().put("bucketId", s.bucketId).put("fileNamePrefix", "")
                .put("validDurationInSeconds", DOWNLOAD_TOKEN_SECONDS);
        return post(s.apiUrl + "/b2api/v4/b2_get_download_authorization", s.authorizationToken, request)
                .getString("authorizationToken");
    }

    private Uri downloadUri(Session s, String token, String fileName) {
        return Uri.parse(s.downloadUrl + "/file/" + encodePath(settings.bucket()) + "/" + encodePath(fileName)
                + "?Authorization=" + encode(token));
    }

    private static JSONObject post(String url, String authorization, JSONObject body) throws Exception {
        HttpURLConnection c = connection(url); c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Authorization", authorization); c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        return new JSONObject(read(c));
    }

    private static HttpURLConnection connection(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(10_000); c.setReadTimeout(30_000); c.setRequestProperty("User-Agent", "LocalMusic/0.5"); return c;
    }

    private static String read(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode(); InputStream input = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in != null) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n); }
            String value = out.toString("UTF-8"); if (code < 200 || code >= 300) throw new IllegalStateException("Backblaze returned " + code + ": " + value); return value;
        } finally { c.disconnect(); }
    }

    private static boolean isAudio(String name, String type) {
        String n = name.toLowerCase(Locale.ROOT); return type.startsWith("audio/") || n.endsWith(".mp3") || n.endsWith(".m4a")
                || n.endsWith(".aac") || n.endsWith(".flac") || n.endsWith(".ogg") || n.endsWith(".opus") || n.endsWith(".wav");
    }
    private static String encode(String s) { try { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; } }
    private static String encodePath(String value) { StringBuilder out = new StringBuilder(); for (String part : value.split("/")) { if (out.length() > 0) out.append('/'); out.append(encode(part)); } return out.toString(); }

    private static final class Session {
        final String apiUrl, downloadUrl, authorizationToken, bucketId; final long expiresAt;
        Session(String apiUrl, String downloadUrl, String authorizationToken, String bucketId, long expiresAt) { this.apiUrl = apiUrl; this.downloadUrl = downloadUrl; this.authorizationToken = authorizationToken; this.bucketId = bucketId; this.expiresAt = expiresAt; }
    }
    private static final class SongName {
        final String title, artist, album;
        SongName(String title, String artist, String album) { this.title = title; this.artist = artist; this.album = album; }
        static SongName from(String key) {
            String[] path = key.split("/"); String file = path[path.length - 1].replaceFirst("\\.[^.]+$", "");
            String album = path.length > 1 ? path[path.length - 2] : "Backblaze"; String artist = path.length > 2 ? path[path.length - 3] : "Unknown artist"; String title = file;
            int dash = file.indexOf(" - "); if (dash > 0) { artist = file.substring(0, dash).trim(); title = file.substring(dash + 3).trim(); }
            return new SongName(title, artist, album);
        }
    }
}
