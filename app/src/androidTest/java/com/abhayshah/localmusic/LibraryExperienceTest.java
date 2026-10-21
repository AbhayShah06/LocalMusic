package com.abhayshah.localmusic;

import android.Manifest;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.MediaStore;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static com.abhayshah.localmusic.FeatureRuntimeTest.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

@RunWith(AndroidJUnit4.class)
@androidx.media3.common.util.UnstableApi
public class LibraryExperienceTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<Track> fixtures=new ArrayList<>();
    private final List<Uri> copies=new ArrayList<>();
    private String shell(String command)throws Exception{try(ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private boolean autoExports;
    @Before public void grant()throws Exception{autoExports=context.getSharedPreferences("settings",0).getBoolean("export_music",true);context.getSharedPreferences("settings",0).edit().putBoolean("export_music",false).commit();shell("pm grant "+context.getPackageName()+" "+(Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE));}
    private Track fixture(String suffix)throws Exception{Track track=new FeatureRuntimeTest().fixture("library-"+suffix+"-"+System.nanoTime());fixtures.add(track);DownloadStore.put(context,track,new File(track.playbackUri.getPath()));return track;}
    private Uri publish(Track track)throws Exception{Uri uri=MusicExportWorker.publish(context,track);copies.add(uri);return uri;}
    @After public void clean()throws Exception{
        shell("svc wifi enable");shell("svc data enable");
        Set<Uri> cleanup=new HashSet<>(copies);
        for(Track track:fixtures){androidx.work.WorkManager.getInstance(context).cancelUniqueWork("export-music-folder:"+track.stableId).getResult().get();String shared=context.getSharedPreferences("music_exports",0).getString("uri:"+track.stableId,"");if(!shared.isEmpty())cleanup.add(Uri.parse(shared));DownloadStore.remove(context,track.stableId);context.getSharedPreferences("music_exports",0).edit().remove("uri:"+track.stableId).apply();}
        for(Uri uri:cleanup)context.getContentResolver().delete(uri,null,null);
        context.getSharedPreferences("settings",0).edit().putBoolean("export_music",autoExports).commit();
    }
    private void button(String text)throws Exception{SystemClock.sleep(350);onView(withText(text)).inRoot(focusedDialog()).perform(click());}
    @Test public void sharedAudioKeepsItsFormatAndDoesNotDuplicateLibrary()throws Exception{
        Track track=fixture("export");Uri uri=publish(track);assertEquals(uri,MusicExportWorker.publish(context,track));
        try(android.database.Cursor c=context.getContentResolver().query(uri,new String[]{MediaStore.Audio.Media.DISPLAY_NAME,MediaStore.Audio.Media.RELATIVE_PATH,MediaStore.Audio.Media.IS_PENDING},null,null,null)){
            assertTrue(c.moveToFirst());assertTrue(c.getString(0).endsWith(".wav"));assertEquals("Music/LocalMusic/",c.getString(1));assertEquals(0,c.getInt(2));
        }
        try(InputStream shared=context.getContentResolver().openInputStream(uri);InputStream original=new FileInputStream(new File(track.playbackUri.getPath()))){assertNotNull(shared);assertArrayEquals(read(original),read(shared));}
        List<Track> phone=new PhoneCatalogSource(context).page(track.title,0,100);assertEquals(1,phone.size());assertEquals(track.stableId,phone.get(0).stableId);
    }
    @Test public void spotifyCsvNeedsOneImportAndConfirmsSavedCount()throws Exception{
        Track one=fixture("one"),two=fixture("two");publish(one);publish(two);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            try{
                scenario.onActivity(a->{try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("showImportOptions");method.setAccessible(true);method.invoke(a);}catch(Exception e){throw new AssertionError(e);}});
                onView(withClassName(org.hamcrest.Matchers.equalTo(android.widget.EditText.class.getName()))).inRoot(focusedDialog()).perform(replaceText("Track Name,Artist Name(s)\n"+one.title+","+one.artist+"\n"+two.title+","+two.artist));
                String previous=context.getSharedPreferences("library",0).getString("last_playlist","");button("Import");await("Playlist was not saved",()->!context.getSharedPreferences("library",0).getString("last_playlist","").equals(previous),15000);
                String name=context.getSharedPreferences("library",0).getString("last_playlist","");assertEquals(2,context.getSharedPreferences("library",0).getStringSet("playlist:"+name,Collections.emptySet()).size());
                onView(withText(org.hamcrest.Matchers.startsWith("2 songs from"))).inRoot(focusedDialog()).check(matches(isDisplayed()));button("Done");
                context.getSharedPreferences("library",0).edit().remove("playlist:"+name).remove("playlist_order:"+name).remove("last_playlist").apply();
            }finally{scenario.onActivity(AppDialogBuilder::dismissAll);}
        }
    }
    @Test public void backgroundExportCompletesWithoutAnOpenActivity()throws Exception{
        Track track=fixture("background");MusicExportWorker.enqueue(context,track.stableId);MusicExportWorker.enqueue(context,track.stableId);
        await("Background export did not complete",()->!context.getSharedPreferences("music_exports",0).getString("uri:"+track.stableId,"").isEmpty(),20000);
        Uri uri=Uri.parse(context.getSharedPreferences("music_exports",0).getString("uri:"+track.stableId,""));copies.add(uri);
        try(InputStream in=context.getContentResolver().openInputStream(uri)){assertNotNull(in);assertEquals('R',in.read());}
        assertTrue(DownloadStore.contains(context,track.stableId));
    }
    @Test public void actualConnectionLossOffersOneOptionalSwitch()throws Exception{
        fixture("offline");context.getSharedPreferences("offline_prompt",0).edit().clear().commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            try{
                shell("svc wifi disable");shell("svc data disable");
                await("Internet did not disconnect",()->context.getSystemService(ConnectivityManager.class).getActiveNetwork()==null,12000);
                SystemClock.sleep(2000);onView(withText("You're offline")).inRoot(focusedDialog()).check(matches(isDisplayed()));button("Keep browsing");
                SystemClock.sleep(2000);assertTrue(context.getSharedPreferences("offline_prompt",0).getBoolean("offered",false));
                scenario.onActivity(a->assertEquals("Stream  ▾",((android.widget.TextView)a.findViewById(R.id.source)).getText().toString()));
                // A fresh connectivity episode permits one new prompt; accepting is explicit.
                context.getSharedPreferences("offline_prompt",0).edit().putBoolean("offered",false).commit();scenario.onActivity(a->{try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("offlinePrompt");f.setAccessible(true);((OfflinePrompt)f.get(a)).checkNow();}catch(Exception e){throw new AssertionError(e);}});
                button("Use downloads");scenario.onActivity(a->assertEquals("This phone  ▾",((android.widget.TextView)a.findViewById(R.id.source)).getText().toString()));
                onView(withId(R.id.albums)).perform(click());onView(withId(R.id.songs)).perform(click());
                await("Downloaded songs disappeared",()->{java.util.concurrent.atomic.AtomicInteger count=new java.util.concurrent.atomic.AtomicInteger();scenario.onActivity(a->count.set(((android.widget.ListView)a.findViewById(R.id.tracks)).getAdapter().getCount()));return count.get()>0;},5000);
            }finally{scenario.onActivity(AppDialogBuilder::dismissAll);shell("svc wifi enable");shell("svc data enable");}
        }
    }
    private byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
}
