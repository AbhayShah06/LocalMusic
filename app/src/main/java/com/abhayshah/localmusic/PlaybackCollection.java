package com.abhayshah.localmusic;
import android.content.*;
import android.net.Uri;
import androidx.media3.common.*;
import org.json.*;
import java.util.*;

@androidx.media3.common.util.UnstableApi
final class PlaybackCollection {
    static final String ACTION="com.abhayshah.localmusic.PLAY_COLLECTION";
    static void request(Context context,List<Track> tracks,int position){
        try{context.startService(new Intent(context,PlaybackService.class).setAction(ACTION).putExtra("tracks",JamClient.hostCatalog(new ArrayList<>(tracks))).putExtra("position",position));}
        catch(Exception failure){throw new IllegalStateException("Could not open this collection",failure);}
    }
    static List<MediaItem> items(Context context,String json)throws JSONException{
        JSONArray values=new JSONArray(json);List<MediaItem> result=new ArrayList<>();
        for(int i=0;i<values.length()&&i<500;i++){JSONObject value=values.getJSONObject(i);String art=value.optString("artwork");Track track=new Track(value.getString("id"),Uri.parse(value.optString("uri")),value.optString("title"),value.optString("artist"),value.optString("album"),art.isEmpty()?null:Uri.parse(art));result.add(StreamPlayback.item(context,track));}return result;
    }
    private PlaybackCollection(){}
}
