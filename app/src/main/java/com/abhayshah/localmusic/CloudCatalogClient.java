package com.abhayshah.localmusic;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

final class CloudCatalogClient implements CatalogSource {
    private final CloudSettings settings;
    CloudCatalogClient(CloudSettings settings) { this.settings = settings; }
    boolean ping() { try { return new JSONObject(get("/v1/health")).optBoolean("ok"); } catch (Exception e) { return false; } }
    @Override public List<Track> page(String query, int offset, int limit) {
        try {
            String path = "/v1/catalog?q=" + encode(query == null ? "" : query) + "&offset=" + offset + "&limit=" + limit;
            JSONArray values = new JSONObject(get(path)).getJSONArray("tracks"); List<Track> tracks = new ArrayList<>();
            for (int i = 0; i < values.length(); i++) { JSONObject item = values.getJSONObject(i); String key = item.getString("key");
                tracks.add(new Track("cloud:" + key, Uri.parse(settings.url() + "/v1/stream/" + encodePath(key)), item.optString("title"), item.optString("artist"), item.optString("album"))); }
            return tracks;
        } catch (Exception e) { throw new IllegalStateException("Could not load cloud library", e); }
    }
    private String get(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(settings.url() + path).openConnection(); c.setConnectTimeout(7000); c.setReadTimeout(20000); c.setRequestProperty("Authorization", "Bearer " + settings.token()); c.setRequestProperty("User-Agent", "LocalMusic/0.4");
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n); return out.toString("UTF-8"); } finally { c.disconnect(); }
    }
    private static String encode(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }
    private static String encodePath(String value) { StringBuilder out = new StringBuilder(); for (String part : value.split("/")) { if (out.length() > 0) out.append('/'); out.append(encode(part).replace("+", "%20")); } return out.toString(); }
}
