package com.abhayshah.localmusic;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.net.Uri;
import android.os.*;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.*;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.concurrent.Semaphore;

@androidx.media3.common.util.UnstableApi
public final class DownloadWorker extends Worker {
    private static final Semaphore SLOTS=new Semaphore(2,true);
    private static final String CHANNEL="offline_downloads";
    private final Track source;
    public DownloadWorker(@NonNull Context c,@NonNull WorkerParameters params){
        super(c,params);Data data=getInputData();String art=data.getString("artwork");
        source=new Track(data.getString("id"),Uri.parse(data.getString("uri")==null?"":data.getString("uri")),data.getString("title"),data.getString("artist"),data.getString("album"),art==null||art.isEmpty()?null:Uri.parse(art));
    }
    @NonNull @Override public Result doWork(){
        Context context=getApplicationContext();
        if(DownloadStore.contains(context,source.stableId)){DownloadTasks.put(context,source,"completed",0,0,"");return Result.success();}
        File output=DownloadStore.destination(context,source.stableId),temporary=new File(output.getParentFile(),output.getName()+"."+getId()+".part");boolean acquired=false;
        try{
            context.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Music downloads",NotificationManager.IMPORTANCE_LOW));
            setForegroundAsync(foreground("Queued: "+source.title,0,true)).get();
            SLOTS.acquire();acquired=true;if(isStopped())throw new InterruptedIOException("Waiting for connection");
            DownloadTasks.put(context,source,"downloading",0,-1,"");Track playable=resolve();final long[] last={0};
                DownloadTransfer.copy(new URL(playable.playbackUri.toString()),temporary,(done,total)->{
                if(isStopped())throw new InterruptedIOException("Waiting for connection");long now=android.os.SystemClock.elapsedRealtime();
                if(now-last[0]>700){last[0]=now;DownloadTasks.put(context,source,"downloading",done,total,"");context.getSystemService(NotificationManager.class).notify(notificationId(),notification("Downloading "+source.title,total>0?(int)(done*100/total):0,total<=0));}
                // Streaming has priority while its player is buffering.
                if(PlaybackService.isBufferingForDownloads())try{Thread.sleep(350);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new InterruptedIOException("Download paused");}
            });
            if(isStopped())throw new InterruptedIOException("Download paused");validateAudio(temporary);
            DownloadTasks.put(context,source,"saving",temporary.length(),temporary.length(),"");
            try{Files.move(temporary.toPath(),output.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            DownloadStore.put(context,source,output);if(!DownloadStore.contains(context,source.stableId))throw new IOException("Could not save download index");
            new File(temporary.getPath()+".httpmeta").delete();
            DownloadTasks.put(context,source,"completed",output.length(),output.length(),"");return Result.success();
        }catch(Exception failure){
            org.json.JSONObject existing=DownloadTasks.get(context,source.stableId);
            if(existing!=null&&"cancelled".equals(existing.optString("state"))){temporary.delete();new File(temporary.getPath()+".httpmeta").delete();return Result.failure();}
            String error=failure.getMessage()==null?"Connection unavailable":failure.getMessage();
            String normalized=error.toLowerCase(java.util.Locale.ROOT);
            boolean invalid=normalized.contains("no playable audio")||normalized.contains("instead of audio")||normalized.contains("empty file");
            boolean permanent=error.contains("512 MB")||error.contains("Not enough free storage")||invalid&&getRunAttemptCount()>=3;
            if(permanent){temporary.delete();new File(temporary.getPath()+".httpmeta").delete();}
            DownloadTasks.put(context,source,permanent?"failed":"waiting",0,-1,permanent?error+". Tap Retry.":"Will retry automatically when the connection recovers. "+error);
            return permanent?Result.failure():Result.retry();
        }finally{if(acquired)SLOTS.release();}
    }
    private Track resolve()throws Exception{
        if(source.stableId.startsWith("online:")){OnlineCatalogSource.invalidate(source.stableId);return new OnlineCatalogSource().resolve(source);}
        if(source.stableId.startsWith("b2:"))return new Track(source.stableId,new B2CatalogClient(new B2Settings(getApplicationContext())).playbackUri(source.stableId.substring(3)),source.title,source.artist,source.album,source.artworkUri);
        return source;
    }
    private static void validateAudio(File file)throws IOException{
        MediaExtractor extractor=new MediaExtractor();try{extractor.setDataSource(file.getAbsolutePath());for(int i=0;i<extractor.getTrackCount();i++){String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/"))return;}throw new IOException("Downloaded file contains no playable audio");}finally{extractor.release();}
    }
    private int notificationId(){return 10000+(source.stableId.hashCode()&0x3fffffff);}
    private ForegroundInfo foreground(String text,int progress,boolean indeterminate){return Build.VERSION.SDK_INT>=29?new ForegroundInfo(notificationId(),notification(text,progress,indeterminate),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC):new ForegroundInfo(notificationId(),notification(text,progress,indeterminate));}
    private Notification notification(String text,int progress,boolean indeterminate){
        Context context=getApplicationContext();PendingIntent open=PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class).putExtra("show_download_activity",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b=new NotificationCompat.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Local Music").setContentText(text).setStyle(new NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true);
        if(indeterminate||progress<100)b.setProgress(100,progress,indeterminate);return b.build();
    }
    @Override public void onStopped(){org.json.JSONObject existing=DownloadTasks.get(getApplicationContext(),source.stableId);if(existing!=null&&!"cancelled".equals(existing.optString("state"))&&!"completed".equals(existing.optString("state")))DownloadTasks.put(getApplicationContext(),source,"waiting",0,-1,"Paused; will resume when Android can run the download.");}
}
