package com.abhayshah.localmusic;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.*;

/** App downloads need no permission; MediaStore access is optional. */
final class PhoneCatalogSource implements CatalogSource {
    private final Context context;
    PhoneCatalogSource(Context context){this.context=context.getApplicationContext();}
    @Override public List<Track> page(String query,int offset,int limit){
        String term=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        List<Track> downloads=new ArrayList<>();
        for(Track track:DownloadStore.all(context))if((track.title+" "+track.artist+" "+track.album).toLowerCase(Locale.ROOT).contains(term))downloads.add(track);
        downloads.sort(Comparator.comparing(track->track.title,String.CASE_INSENSITIVE_ORDER));
        List<Track> result=new ArrayList<>();int start=Math.max(0,offset),count=Math.max(0,limit);
        if(start<downloads.size())result.addAll(downloads.subList(start,Math.min(downloads.size(),start+count)));
        String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(result.size()<count&&context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED)
            result.addAll(new MediaStoreCatalogSource(context.getContentResolver()).page(query,Math.max(0,start-downloads.size()),count-result.size()));
        return result;
    }
}
