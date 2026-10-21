package com.abhayshah.localmusic;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.io.*;
import java.util.*;

/** Publish a complete, correctly named audio copy to shared Music/LocalMusic storage. */
public final class MusicExportWorker extends Worker {
    private static final String PREFS="music_exports";
    public MusicExportWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    static void enqueue(Context context){
        for(Track track:DownloadStore.all(context))enqueue(context,track.stableId);
    }
    static void enqueue(Context context,String id){WorkManager.getInstance(context).enqueueUniqueWork("export-music-folder:"+id,ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(MusicExportWorker.class).setInputData(new Data.Builder().putString("id",id).build()).addTag("music-export").build());}
    static Set<String> exportedUris(Context c){Set<String> uris=new HashSet<>();for(Track track:DownloadStore.all(c)){String uri=c.getSharedPreferences(PREFS,0).getString("uri:"+track.stableId,"");if(!uri.isEmpty())uris.add(uri);}return uris;}
    static String status(Context c){int errors=0;for(String key:c.getSharedPreferences(PREFS,0).getAll().keySet())if(key.startsWith("error:"))errors++;return errors>0?errors+" files couldn't be exported. Tap to retry.":c.getSharedPreferences(PREFS,0).getString("status","Copy existing downloads to Music/LocalMusic");}
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();int saved=0;String error=null;
        for(Track track:DownloadStore.all(c)){
            String requested=getInputData().getString("id");if(requested!=null&&!requested.equals(track.stableId))continue;
            if(isStopped())return Result.retry();
            try{publish(c,track);saved++;c.getSharedPreferences(PREFS,0).edit().remove("error:"+track.stableId).apply();}catch(Exception failure){error=failure.getMessage()==null?"Could not export audio":failure.getMessage();c.getSharedPreferences(PREFS,0).edit().putString("error:"+track.stableId,error).apply();}
        }
        c.getSharedPreferences(PREFS,0).edit().putString("status",error==null?"Saved in Music/LocalMusic":error+". Tap to retry.").apply();
        return error==null?Result.success():getRunAttemptCount()<2?Result.retry():Result.failure();
    }
    static synchronized Uri publish(Context c,Track track)throws IOException{
        if(!"file".equals(track.playbackUri.getScheme()))throw new IOException("Download file is unavailable");
        File source=new File(track.playbackUri.getPath());if(!source.isFile())throw new IOException("Download file is missing");
        String key="uri:"+track.stableId;String known=c.getSharedPreferences(PREFS,0).getString(key,"");
        if(!known.isEmpty())try(android.content.res.AssetFileDescriptor fd=c.getContentResolver().openAssetFileDescriptor(Uri.parse(known),"r")){if(fd!=null)return Uri.parse(known);}catch(Exception missing){}
        if(Build.VERSION.SDK_INT<29&&c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)throw new IOException("Allow storage access using Export downloads in Settings");
        AudioFileFormat format=AudioFileFormat.read(source);String filename=AudioFileFormat.filename(track.artist,track.title,track.stableId,format.extension);
        ContentValues metadata=new ContentValues();metadata.put(MediaStore.Audio.Media.DISPLAY_NAME,filename);metadata.put(MediaStore.Audio.Media.MIME_TYPE,format.mime);metadata.put(MediaStore.Audio.Media.TITLE,track.title);metadata.put(MediaStore.Audio.Media.ARTIST,track.artist);metadata.put(MediaStore.Audio.Media.ALBUM,track.album);metadata.put(MediaStore.Audio.Media.IS_MUSIC,1);
        Uri collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        if(Build.VERSION.SDK_INT>=29){
            metadata.put(MediaStore.Audio.Media.RELATIVE_PATH,Environment.DIRECTORY_MUSIC+"/LocalMusic/");metadata.put(MediaStore.Audio.Media.IS_PENDING,1);
            // Recover a completed copy after process death between publication and indexing.
            try(Cursor existing=c.getContentResolver().query(collection,new String[]{"_id","_size","is_pending"},"_display_name=? AND relative_path=?",new String[]{filename,"Music/LocalMusic/"},null)){
                if(existing!=null)while(existing.moveToNext()){
                    Uri uri=ContentUris.withAppendedId(collection,existing.getLong(0));
                    if(existing.getInt(2)==0&&existing.getLong(1)==source.length()){record(c,key,uri);return uri;}
                }
            }catch(RuntimeException ignored){}
        }else{
            File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),"LocalMusic");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Could not create Music/LocalMusic");metadata.put(MediaStore.Audio.Media.DATA,new File(folder,filename).getAbsolutePath());
        }
        Uri uri=c.getContentResolver().insert(collection,metadata);if(uri==null)throw new IOException("Could not create shared music file");
        try{
            try(InputStream in=new FileInputStream(source);OutputStream out=c.getContentResolver().openOutputStream(uri,"w")){
                if(out==null)throw new IOException("Could not open shared music file");byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Export paused");out.write(buffer,0,n);}
            }
            if(Build.VERSION.SDK_INT>=29){ContentValues ready=new ContentValues();ready.put(MediaStore.Audio.Media.IS_PENDING,0);if(c.getContentResolver().update(uri,ready,null,null)!=1)throw new IOException("Could not publish shared audio");}
            record(c,key,uri);return uri;
        }catch(Exception error){try{c.getContentResolver().delete(uri,null,null);}catch(RuntimeException ignored){}if(error instanceof IOException)throw(IOException)error;throw new IOException("Could not save shared audio",error);}
    }
    private static void record(Context c,String key,Uri uri)throws IOException{if(!c.getSharedPreferences(PREFS,0).edit().putString(key,uri.toString()).commit())throw new IOException("Could not save export index");}
}
