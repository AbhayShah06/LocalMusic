package com.abhayshah.localmusic;

import android.content.Context;
import org.json.JSONArray;
import java.util.HashSet;
import java.util.Set;

/** Recently queued recordings, used only to vary automatic picks across mixes. */
final class RadioHistory {
    private static final String PREFS="radio",KEY="recent_picks";
    static Set<String> recent(Context context){
        Set<String> songs=new HashSet<>();
        try{JSONArray saved=new JSONArray(context.getSharedPreferences(PREFS,0).getString(KEY,"[]"));
            for(int i=0;i<saved.length();i++)songs.add(saved.optString(i));
        }catch(Exception ignored){}
        return songs;
    }
    static synchronized void record(Context context,Track track){
        String song=RecordingIdentity.key(track.title,track.artist);if(song.equals("|"))return;
        JSONArray next=new JSONArray();next.put(song);
        try{JSONArray old=new JSONArray(context.getSharedPreferences(PREFS,0).getString(KEY,"[]"));
            for(int i=0;i<old.length()&&next.length()<40;i++){String previous=old.optString(i);if(!previous.equals(song))next.put(previous);}
        }catch(Exception ignored){}
        context.getSharedPreferences(PREFS,0).edit().putString(KEY,next.toString()).apply();
    }
    private RadioHistory(){}
}
