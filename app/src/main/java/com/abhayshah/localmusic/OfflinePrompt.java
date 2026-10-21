package com.abhayshah.localmusic;

import android.app.*;
import android.content.Context;
import android.net.*;
import android.os.*;
import java.util.function.BooleanSupplier;

/** One optional prompt per offline episode; never changes playback or source by itself. */
final class OfflinePrompt {
    private final Activity activity;
    private final BooleanSupplier streaming;
    private final Runnable useDownloads;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ConnectivityManager manager;
    private boolean started;
    private AlertDialog dialog;
    private final Runnable check=this::checkNow;
    private final ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback(){
        @Override public void onLost(Network network){schedule();}
        @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps){schedule();}
    };
    OfflinePrompt(Activity a,BooleanSupplier streaming,Runnable useDownloads){activity=a;this.streaming=streaming;this.useDownloads=useDownloads;manager=a.getSystemService(ConnectivityManager.class);}
    void start(){if(started)return;started=true;try{manager.registerDefaultNetworkCallback(callback);}catch(RuntimeException ignored){}schedule();}
    private void schedule(){main.post(()->{main.removeCallbacks(check);main.postDelayed(check,1500);});}
    void checkNow(){
        if(!started||activity.isDestroyed()||activity.isFinishing())return;
        NetworkCapabilities capabilities=manager.getNetworkCapabilities(manager.getActiveNetwork());boolean online=capabilities!=null&&capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        update(online);
    }
    void update(boolean online){
        android.content.SharedPreferences prefs=activity.getSharedPreferences("offline_prompt",0);
        if(online){prefs.edit().putBoolean("offered",false).apply();if(dialog!=null)dialog.dismiss();dialog=null;return;}
        if(!started||!streaming.getAsBoolean()||DownloadStore.all(activity).isEmpty()||prefs.getBoolean("offered",false)||!activity.getWindow().getDecorView().hasWindowFocus())return;
        prefs.edit().putBoolean("offered",true).apply();
        dialog=new AppDialogBuilder(activity).setTitle("You're offline").setMessage("Switch to downloaded music?").setPositiveButton("Use downloads",(d,w)->useDownloads.run()).setNegativeButton("Keep browsing",null).create();dialog.show();
    }
    void stop(){started=false;main.removeCallbacksAndMessages(null);try{manager.unregisterNetworkCallback(callback);}catch(RuntimeException ignored){}if(dialog!=null)dialog.dismiss();dialog=null;}
}
