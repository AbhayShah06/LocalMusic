package com.abhayshah.localmusic;
import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.*;
import java.io.IOException;

/** Queue metadata is immediate; only the track being opened needs a fresh stream address. */
@androidx.media3.common.util.UnstableApi
final class StreamPlayback {
    static MediaItem item(Context context,Track track){
        Track local=DownloadStore.offline(context,track);
        if(local!=null)return new Track(track.stableId,local.playbackUri,track.title,track.artist,track.album,track.artworkUri).toMediaItem();
        if(!Track.originalId(track.stableId).startsWith("online:"))return track.toMediaItem();
        Uri uri=new Uri.Builder().scheme("localmusic").authority("stream").appendQueryParameter("id",Track.originalId(track.stableId)).appendQueryParameter("title",track.title).appendQueryParameter("artist",track.artist).appendQueryParameter("album",track.album).build();
        return new Track(track.stableId,uri,track.title,track.artist,track.album,track.artworkUri).toMediaItem().buildUpon().setCustomCacheKey(Track.originalId(track.stableId)).build();
    }
    static DataSource.Factory resolving(Context context,DataSource.Factory upstream){
        return new ResolvingDataSource.Factory(upstream,spec->{
            if(!"localmusic".equals(spec.uri.getScheme()))return spec;
            Track track=new Track(spec.uri.getQueryParameter("id"),Uri.EMPTY,spec.uri.getQueryParameter("title"),spec.uri.getQueryParameter("artist"),spec.uri.getQueryParameter("album"));
            Track local=DownloadStore.offline(context,track);
            try{return spec.withUri(local!=null?local.playbackUri:new OnlineCatalogSource().resolve(track).playbackUri);}catch(RuntimeException unavailable){throw new IOException("This song could not be reached. Try again when connected.",unavailable);}
        });
    }
    private StreamPlayback(){}
}
