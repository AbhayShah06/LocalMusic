package com.abhayshah.localmusic;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Explicit, temporary negative taste feedback. Avoid remains the permanent blacklist. */
final class DislikedStore {
    private static final String PREFS="disliked_songs",KEY="entries";
    private static final long LIFE=30L*86_400_000L;
    static synchronized void add(Context context,Track track){
        String song=BlacklistedStore.key(track.title,track.artist);
        if(song.isEmpty())return;
        String seedArtist="";
        try{
            String saved=context.getSharedPreferences("radio",0).getString("seed","");
            if(!saved.isEmpty())seedArtist=RecordingIdentity.artist(new JSONArray(saved).getJSONObject(0).optString("artist"));
        }catch(Exception ignored){}
        JSONArray next=new JSONArray();long now=System.currentTimeMillis();
        try{next.put(new JSONObject().put("song",song).put("artist",RecordingIdentity.artist(track.artist)).put("seedArtist",seedArtist).put("at",now));
            JSONArray old=new JSONArray(context.getSharedPreferences(PREFS,0).getString(KEY,"[]"));
            for(int i=0;i<old.length()&&next.length()<100;i++){
                JSONObject entry=old.optJSONObject(i);if(entry!=null&&now-entry.optLong("at")<LIFE&&!song.equals(entry.optString("song")))next.put(entry);
            }
            context.getSharedPreferences(PREFS,0).edit().putString(KEY,next.toString()).apply();
        }catch(Exception ignored){}
    }
    static boolean contains(Context context,Track track){
        return songs(context).contains(BlacklistedStore.key(track.title,track.artist));
    }
    static Set<String> songs(Context context){
        Set<String> songs=new HashSet<>();long now=System.currentTimeMillis();
        try{JSONArray entries=new JSONArray(context.getSharedPreferences(PREFS,0).getString(KEY,"[]"));
            for(int i=0;i<entries.length();i++){JSONObject entry=entries.optJSONObject(i);
                if(entry!=null&&now-entry.optLong("at")<LIFE)songs.add(entry.optString("song"));
            }
        }catch(Exception ignored){}
        return songs;
    }
    static Map<String,Double> seedPenalties(Context context,Track seed){
        String seedArtist=RecordingIdentity.artist(seed.artist);
        long now=System.currentTimeMillis();Map<String,Double> penalties=new HashMap<>();
        try{JSONArray entries=new JSONArray(context.getSharedPreferences(PREFS,0).getString(KEY,"[]"));
            for(int i=0;i<entries.length();i++){JSONObject entry=entries.optJSONObject(i);if(entry==null)continue;
                long age=now-entry.optLong("at");
                if(age>=0&&age<LIFE&&seedArtist.equals(entry.optString("seedArtist"))){
                    String artist=entry.optString("artist");double weight=-2*Math.exp(-Math.log(2)*age/(14d*86_400_000L));
                    penalties.put(artist,Math.max(-4,penalties.getOrDefault(artist,0d)+weight));
                }
            }
        }catch(Exception ignored){}
        return penalties;
    }
    private DislikedStore(){}
}
