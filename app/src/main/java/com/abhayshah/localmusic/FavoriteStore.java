package com.abhayshah.localmusic;

import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;
import java.util.*;

/** Keeps streaming favorites on this device alongside the original local favorites. */
final class FavoriteStore {
    static boolean contains(Context context, Track track) {
        String id=Track.originalId(track.stableId);
        for(String saved:values(context)) if(id.equals(id(saved))) return true;
        return false;
    }
    static synchronized boolean toggle(Context context, Track input) {
        Track track=input.original(); Set<String> saved=values(context);
        boolean removed=saved.removeIf(value->track.stableId.equals(id(value)));
        if(!removed) try {
            saved.add(new JSONObject().put("id",track.stableId).put("uri",track.playbackUri.toString())
                .put("title",track.title).put("artist",track.artist).put("album",track.album)
                .put("artwork",track.artworkUri==null?"":track.artworkUri.toString()).toString());
        } catch(Exception failure) { throw new IllegalStateException("Could not save favorite",failure); }
        context.getSharedPreferences("library",Context.MODE_PRIVATE).edit().putStringSet("favorites",saved).apply();
        return !removed;
    }
    static List<Track> remoteTracks(Context context) {
        List<Track> tracks=new ArrayList<>();
        for(String saved:values(context)) if(saved.startsWith("{")) try {
            JSONObject item=new JSONObject(saved);String id=item.getString("id"),art=item.optString("artwork");
            if(!id.startsWith("local:")) tracks.add(new Track(id,Uri.parse(item.optString("uri")),item.optString("title"),item.optString("artist"),item.optString("album"),art.isEmpty()?null:Uri.parse(art)));
        }catch(Exception ignored){}
        tracks.sort(Comparator.comparing(track->track.title,String.CASE_INSENSITIVE_ORDER));return tracks;
    }
    private static Set<String> values(Context context){return new HashSet<>(context.getSharedPreferences("library",Context.MODE_PRIVATE).getStringSet("favorites",Collections.emptySet()));}
    private static String id(String value){if(!value.startsWith("{"))return "local:"+value;try{return Track.originalId(new JSONObject(value).optString("id"));}catch(Exception ignored){return "";}}
    private FavoriteStore(){}
}
