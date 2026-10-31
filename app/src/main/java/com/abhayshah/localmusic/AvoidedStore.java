package com.abhayshah.localmusic;

import android.content.Context;
import java.util.HashSet;
import java.util.Set;

/** Local preference for tracks the listener does not want in automatic picks. */
final class AvoidedStore {
    private static final String PREFS="avoided_recommendations",KEY="songs";
    static boolean contains(Context context,Track track){
        Set<String> values=values(context);
        return values.contains("id:"+Track.originalId(track.stableId))||hasSongKey(track)&&values.contains("song:"+RecordingIdentity.key(track.title,track.artist));
    }
    static synchronized boolean toggle(Context context,Track track){
        Set<String> values=values(context);
        String id="id:"+Track.originalId(track.stableId),song="song:"+RecordingIdentity.key(track.title,track.artist);
        boolean avoided=values.contains(id)||hasSongKey(track)&&values.contains(song);
        if(avoided){values.remove(id);values.remove(song);}else if(hasSongKey(track))values.add(song);else values.add(id);
        context.getSharedPreferences(PREFS,0).edit().putStringSet(KEY,values).apply();
        return !avoided;
    }
    private static Set<String> values(Context context){return new HashSet<>(context.getSharedPreferences(PREFS,0).getStringSet(KEY,new HashSet<>()));}
    private static boolean hasSongKey(Track track){return !track.title.equals("Unknown title")&&!track.artist.equals("Unknown artist");}
    private AvoidedStore(){}
}
