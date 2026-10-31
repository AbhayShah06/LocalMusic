package com.abhayshah.localmusic;

import android.content.Context;
import androidx.media3.common.MediaItem;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Recent, device-only listening signals. Old habits fade instead of accumulating forever. */
final class TasteProfile {
    private static final String PREFS="taste_profile",KEY="events";
    private static final long DAY=86_400_000L;
    private final List<Event> events;
    private TasteProfile(List<Event> events){this.events=events;}
    static synchronized void record(Context context,MediaItem item,boolean skipped){
        if(item==null||item.mediaId==null||item.mediaId.isEmpty())return;
        String artist=item.mediaMetadata.artist==null?"":item.mediaMetadata.artist.toString();
        String title=item.mediaMetadata.title==null?"":item.mediaMetadata.title.toString();
        try{
            JSONArray old=new JSONArray(context.getSharedPreferences(PREFS,0).getString(KEY,"[]")),next=new JSONArray();
            next.put(new JSONObject().put("artist",artist).put("song",RecordingIdentity.key(title,artist)).put("skip",skipped).put("at",System.currentTimeMillis()));
            for(int i=0;i<old.length()&&next.length()<200;i++){
                JSONObject event=old.optJSONObject(i);
                if(event!=null&&System.currentTimeMillis()-event.optLong("at")<90*DAY)next.put(event);
            }
            context.getSharedPreferences(PREFS,0).edit().putString(KEY,next.toString()).apply();
        }catch(Exception ignored){}
    }
    static TasteProfile load(Context context){
        List<Event> events=new ArrayList<>();
        try{
            JSONArray saved=new JSONArray(context.getSharedPreferences(PREFS,0).getString(KEY,"[]"));
            for(int i=0;i<saved.length();i++){
                JSONObject value=saved.optJSONObject(i);if(value==null)continue;
                events.add(new Event(RecordingIdentity.artist(value.optString("artist")),value.optString("song"),value.optBoolean("skip"),value.optLong("at")));
            }
        }catch(Exception ignored){}
        return new TasteProfile(events);
    }
    double score(Track track){
        String artist=RecordingIdentity.artist(track.artist),song=RecordingIdentity.key(track.title,track.artist);
        long now=System.currentTimeMillis();double artistScore=0,songScore=0;
        for(Event event:events){
            long age=now-event.at;if(age<0||age>90*DAY)continue;
            double weight=Math.exp(-Math.log(2)*age/(event.skip?14d*DAY:21d*DAY));
            if(event.artist.equals(artist))artistScore+=(event.skip?-.7:.45)*weight;
            if(event.song.equals(song)&&event.skip)songScore-=5*weight;
        }
        return Math.max(-2,Math.min(2,artistScore))+songScore;
    }
    private static final class Event{
        final String artist,song;final boolean skip;final long at;
        Event(String artist,String song,boolean skip,long at){this.artist=artist;this.song=song;this.skip=skip;this.at=at;}
    }
}
