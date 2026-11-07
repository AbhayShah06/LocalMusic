package com.abhayshah.localmusic;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Song-wide Avoid preference. Keeps the old storage key so existing choices survive updates. */
final class BlacklistedStore {
    private static final String PREFS="blacklisted_recordings",KEY="songs";
    static String key(String title,String artist){
        String singer=RecordingIdentity.artist(artist),song=RecordingIdentity.title(title,artist);
        if(!singer.isEmpty()&&song.endsWith(" "+singer))song=song.substring(0,song.length()-singer.length()-1);
        song=song.replaceAll("(?i)\\s+(?:live(?:\\s+(?:at|in|from)\\s+.*)?|remix(?:\\s+.*)?|acoustic(?:\\s+version)?|instrumental|sped\\s+up|slowed(?:\\s+reverb)?|clean(?:\\s+version)?|radio\\s+edit|explicit(?:\\s+version)?|lyric(?:s|al)?(?:\\s+video)?|visualizer)$","").trim();
        return singer+"|"+song;
    }
    static boolean contains(Context context,Track track){return contains(context,key(track.title,track.artist));}
    private static boolean contains(Context context,String key){for(Entry entry:list(context))if(entry.key.equals(key))return true;return false;}
    static synchronized boolean toggle(Context context,Track track){
        String key=key(track.title,track.artist);if(key.equals("|"))return false;
        List<Entry> entries=list(context);boolean removed=entries.removeIf(entry->entry.key.equals(key));
        if(!removed)entries.add(0,new Entry(key,track.title,track.artist));save(context,entries);return !removed;
    }
    static synchronized void remove(Context context,String key){List<Entry> entries=list(context);entries.removeIf(entry->entry.key.equals(key));save(context,entries);}
    static List<Entry> list(Context context){
        List<Entry> result=new ArrayList<>();
        try{JSONArray saved=new JSONArray(context.getSharedPreferences(PREFS,0).getString(KEY,"[]"));
            for(int i=0;i<saved.length();i++){JSONObject item=saved.optJSONObject(i);if(item!=null&&!item.optString("key").isEmpty())result.add(new Entry(item.optString("key"),item.optString("title"),item.optString("artist")));}
        }catch(Exception ignored){}
        return result;
    }
    private static void save(Context context,List<Entry> entries){
        JSONArray saved=new JSONArray();for(Entry entry:entries)try{saved.put(new JSONObject().put("key",entry.key).put("title",entry.title).put("artist",entry.artist));}catch(Exception ignored){}
        context.getSharedPreferences(PREFS,0).edit().putString(KEY,saved.toString()).apply();
    }
    static final class Entry {final String key,title,artist;Entry(String key,String title,String artist){this.key=key;this.title=title;this.artist=artist;}}
    private BlacklistedStore(){}
}
