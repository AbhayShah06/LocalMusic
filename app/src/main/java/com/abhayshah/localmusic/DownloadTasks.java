package com.abhayshah.localmusic;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.util.*;

/** Persistent progress/error records; process death never leaves an endless spinner. */
final class DownloadTasks {
    private static final String SESSION=UUID.randomUUID().toString();
    static final String PREFS="download_jobs";
    static synchronized void put(Context c,Track t,String state,long bytes,long total,String error){
        try{
            JSONObject v=new JSONObject().put("id",t.stableId).put("title",t.title).put("artist",t.artist).put("album",t.album).put("uri",t.playbackUri.toString()).put("artwork",t.artworkUri==null?"":t.artworkUri.toString())
                .put("state",state).put("bytes",bytes).put("total",total).put("error",error==null?"":error).put("session",SESSION).put("updated",System.currentTimeMillis());
            c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(t.stableId,v.toString()).apply();
        }catch(JSONException errorIgnored){}
    }
    static synchronized List<JSONObject> all(Context c){
        List<JSONObject> out=new ArrayList<>();
        for(Object raw:c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getAll().values())try{
            JSONObject v=new JSONObject(String.valueOf(raw));
            out.add(v);
        }catch(Exception ignored){}
        out.sort((a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));return out;
    }
    static JSONObject get(Context c,String id){for(JSONObject v:all(c))if(id.equals(v.optString("id")))return v;return null;}
    static boolean active(JSONObject v){String s=v.optString("state");return s.equals("queued")||s.equals("waiting")||s.equals("downloading")||s.equals("retrying")||s.equals("saving");}
    static boolean active(Context c,String id){JSONObject v=get(c,id);return v!=null&&active(v);}
    static Track track(JSONObject v){String art=v.optString("artwork");return new Track(v.optString("id"),Uri.parse(v.optString("uri")),v.optString("title"),v.optString("artist"),v.optString("album"),art.isEmpty()?null:Uri.parse(art));}
    private DownloadTasks(){}
}
