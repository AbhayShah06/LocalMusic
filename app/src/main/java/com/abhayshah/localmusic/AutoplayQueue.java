package com.abhayshah.localmusic;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** The service owns a rolling radio queue; its seed never drifts to the current recommendation. */
@androidx.media3.common.util.UnstableApi
final class AutoplayQueue {
    private final Context context;private final ExoPlayer player;private final Handler main;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<Track> pool=new ArrayList<>();private final LinkedHashSet<String> used=new LinkedHashSet<>();
    private final LinkedHashSet<String> usedSongs=new LinkedHashSet<>();
    private Track seed;private volatile int generation;private boolean busy,deferredTail;private volatile boolean closed;private final Runnable retry=this::refill;
    AutoplayQueue(Context context,ExoPlayer player,Handler main){this.context=context;this.player=player;this.main=main;
        try{String saved=context.getSharedPreferences("radio",0).getString("seed","");if(!saved.isEmpty()){seed=read(new JSONArray(saved)).get(0);used.add(seed.stableId);deferredTail=context.getSharedPreferences("radio",0).getBoolean("collection",false);}}catch(Exception ignored){}
    }
    void configure(String seedJson,String candidates){
        configure(seedJson,candidates,false);
    }
    void followCollection(Track original){try{configure(JamClient.hostCatalog(Collections.singletonList(original.original())),"[]",true);}catch(Exception invalid){stop();android.util.Log.w("AutoplayQueue","Could not start collection autoplay",invalid);}}
    void configure(String seedJson,String candidates,boolean collection){
        stop();try{seed=read(new JSONArray(seedJson)).get(0).original();pool.addAll(read(new JSONArray(candidates)));used.add(seed.stableId);deferredTail=collection;context.getSharedPreferences("radio",0).edit().putString("seed",seedJson).putBoolean("collection",collection).apply();refill();}catch(Exception ignored){stop();}
    }
    void stop(){generation++;seed=null;pool.clear();used.clear();usedSongs.clear();busy=false;deferredTail=false;main.removeCallbacks(retry);context.getSharedPreferences("radio",0).edit().remove("seed").remove("collection").apply();}
    private int upcoming(){int count=0;for(int i=player.getCurrentMediaItemIndex()+1;i<player.getMediaItemCount();i++)if(player.getMediaItemAt(i).mediaId.startsWith("auto:"))count++;return count;}
    void refill(){
        if(closed||busy||seed==null||player.getMediaItemCount()==0||!context.getSharedPreferences("settings",0).getBoolean("autoplay",true)||upcoming()>=5)return;
        if(deferredTail&&player.getMediaItemCount()-player.getCurrentMediaItemIndex()-1>=5)return;
        int request=generation,needed=5-upcoming();Track originalSeed=seed;List<Track> candidates=new ArrayList<>(pool);pool.clear();
        Set<String> known=new HashSet<>(used),knownSongs=new HashSet<>(usedSongs);knownSongs.add(RecordingIdentity.key(originalSeed.title,originalSeed.artist));
        for(int i=0;i<player.getMediaItemCount();i++){MediaItem item=player.getMediaItemAt(i);known.add(Track.originalId(item.mediaId));knownSongs.add(RecordingIdentity.key(item.mediaMetadata.title==null?"":item.mediaMetadata.title.toString(),item.mediaMetadata.artist==null?"":item.mediaMetadata.artist.toString()));}busy=true;
        io.execute(()->{
            List<Track> ready=new ArrayList<>(),remaining=new ArrayList<>();
            try{
                OnlineCatalogSource source=null;
                if(candidates.isEmpty()&&originalSeed.stableId.startsWith("online:")){source=new OnlineCatalogSource(context.getSharedPreferences("settings",0).getBoolean("avoid_clean",true));candidates.addAll(source.songMix(originalSeed,known));}
                Set<String> picked=new HashSet<>(known);
                for(Track track:candidates){
                    if(closed||request!=generation)return;
                    String id=Track.originalId(track.stableId),song=RecordingIdentity.key(track.title,track.artist);if(knownSongs.contains(song)||VersionPolicy.isMusicVideo(track.title)||context.getSharedPreferences("settings",0).getBoolean("avoid_clean",true)&&VersionPolicy.exclude(track.title,originalSeed.title)||!picked.add(id))continue;
                    if(ready.size()>=needed){remaining.add(track.original());continue;}
                    Track downloaded=DownloadStore.offline(context,track.original());ready.add(downloaded==null?track.original():downloaded);knownSongs.add(song);
                }
            }catch(RuntimeException unavailable){}
            main.post(()->{
                if(closed||request!=generation)return;pool.addAll(remaining);
                if(seed==null||!seed.stableId.equals(originalSeed.stableId)||!context.getSharedPreferences("settings",0).getBoolean("autoplay",true)){busy=false;return;}
                Set<String> queued=new HashSet<>();for(int i=0;i<player.getMediaItemCount();i++)queued.add(Track.originalId(player.getMediaItemAt(i).mediaId));
                for(Track track:ready){if(upcoming()>=5||!queued.add(track.stableId))continue;used.add(track.stableId);usedSongs.add(RecordingIdentity.key(track.title,track.artist));MediaItem item=StreamPlayback.item(context,track).buildUpon().setMediaId("auto:"+track.stableId).setCustomCacheKey(track.stableId).build();player.addMediaItem(item);}
                while(used.size()>500)used.remove(used.iterator().next());
                while(usedSongs.size()>500)usedSongs.remove(usedSongs.iterator().next());
                busy=false;main.removeCallbacks(retry);if(upcoming()<5)main.postDelayed(retry,15000);
            });
        });
    }
    void close(){closed=true;generation++;main.removeCallbacks(retry);io.shutdownNow();}
    private static List<Track> read(JSONArray values)throws JSONException{
        List<Track> result=new ArrayList<>();for(int i=0;i<values.length();i++){JSONObject t=values.getJSONObject(i);String art=t.optString("artwork");result.add(new Track(t.getString("id"),Uri.parse(t.optString("uri")),t.optString("title"),t.optString("artist"),t.optString("album"),art.isEmpty()?null:Uri.parse(art)));}return result;
    }
}
