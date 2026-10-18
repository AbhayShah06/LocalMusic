package com.abhayshah.localmusic;
import android.content.*;

@androidx.media3.common.util.UnstableApi
public final class DownloadNotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String id=intent.getStringExtra("track_id");if(id==null)return;
        if("hide".equals(intent.getAction()))context.getSharedPreferences("download_notifications",Context.MODE_PRIVATE).edit().putBoolean(id,true).apply();
        else {org.json.JSONObject job=DownloadTasks.get(context,id);if(job!=null)OfflineDownloadService.cancel(context,DownloadTasks.track(job));}
    }
}
