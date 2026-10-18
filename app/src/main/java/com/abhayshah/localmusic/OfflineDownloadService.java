package com.abhayshah.localmusic;
import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Compatibility entry point. WorkManager owns the persistent background queue. */
@androidx.media3.common.util.UnstableApi
final class OfflineDownloadService {
    static String workName(String id){return "offline:"+Track.originalId(id);}
    static synchronized void enqueue(Context context,Track input){
        Track track=input.original();
        if(DownloadStore.contains(context,track.stableId)||DownloadTasks.active(context,track.stableId))return;
        context.getSharedPreferences("download_notifications",Context.MODE_PRIVATE).edit().remove(track.stableId).apply();
        DownloadTasks.put(context,track,"queued",0,-1,"Waiting for a connection; safe to close the app");
        Data data=new Data.Builder().putString("id",track.stableId).putString("title",track.title).putString("artist",track.artist).putString("album",track.album).putString("uri",track.playbackUri.toString()).putString("artwork",track.artworkUri==null?"":track.artworkUri.toString()).build();
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(DownloadWorker.class).setInputData(data).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag("offline-download").build();
        WorkManager.getInstance(context).enqueueUniqueWork(workName(track.stableId),ExistingWorkPolicy.KEEP,request);
    }
    static void cancel(Context context,Track track){DownloadTasks.put(context,track.original(),"cancelled",0,-1,"Cancelled; tap Retry to resume");WorkManager.getInstance(context).cancelUniqueWork(workName(track.stableId));}
}
