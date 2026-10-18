package com.abhayshah.localmusic;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@androidx.media3.common.util.UnstableApi
public class FeatureRuntimeTest {
    final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    static void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    static <T>T value(Supplier<T> read){AtomicReference<T> result=new AtomicReference<>();main(()->result.set(read.get()));return result.get();}
    static void await(String message,Supplier<Boolean> condition,long timeout)throws Exception {long until=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<until){if(condition.get())return;Thread.sleep(150);}fail(message);}
    MediaController connect()throws Exception{
        AtomicReference<com.google.common.util.concurrent.ListenableFuture<MediaController>> future=new AtomicReference<>();
        main(()->future.set(new MediaController.Builder(context,new SessionToken(context,new android.content.ComponentName(context,PlaybackService.class))).buildAsync()));
        return future.get().get(20,TimeUnit.SECONDS);
    }
    Track fixture(String id)throws Exception{
        File file=new File(context.getFilesDir(),"runtime-test-"+id+".wav");int samples=8000*300;ByteBuffer bytes=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("RIFF".getBytes()).putInt(36+samples*2).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(samples*2);
        for(int i=0;i<samples;i++)bytes.putShort((short)(Math.sin(i*2*Math.PI*440/8000)*100));Files.write(file.toPath(),bytes.array());
        return new Track("fixture:"+id,Uri.fromFile(file),"Test Song "+id,"Runtime Test","Fixtures");
    }
    @Test public void a_realPlaybackQueueSeekShuffleRepeatAndRestore()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            MediaController controller=connect();Track one=fixture("one"),two=fixture("two"),three=fixture("three");
            main(()->{controller.setMediaItems(Arrays.asList(one.toMediaItem(),two.toMediaItem(),three.toMediaItem()));controller.prepare();controller.play();});
            await("Playback failed to start",()->value(controller::isPlaying),20000);
            main(()->{controller.seekTo(10000);controller.setShuffleModeEnabled(true);controller.setRepeatMode(Player.REPEAT_MODE_ALL);});
            await("Seek did not take effect",()->value(controller::getCurrentPosition)>=9000,5000);
            assertTrue(value(controller::getShuffleModeEnabled));assertEquals(Integer.valueOf(Player.REPEAT_MODE_ALL),value(controller::getRepeatMode));
            main(()->{controller.setShuffleModeEnabled(false);controller.moveMediaItem(2,1);});
            assertEquals("fixture:three",value(()->controller.getMediaItemAt(1).mediaId));
            main(()->{controller.removeMediaItem(1);controller.seekToNextMediaItem();});
            await("Next track did not become current",()->value(()->controller.getCurrentMediaItem().mediaId).equals("fixture:two"),5000);
            onView(withId(R.id.nowPlaying)).check(matches(withText("Test Song two")));
            main(()->{controller.seekToPreviousMediaItem();controller.pause();});
            assertEquals(Integer.valueOf(2),value(controller::getMediaItemCount));assertNull(value(controller::getPlayerError));
            main(controller::release);context.stopService(new Intent(context,PlaybackService.class));Thread.sleep(1000);
            MediaController restored=connect();await("Saved queue was not restored",()->value(restored::getMediaItemCount)==2,15000);
            assertFalse(value(restored::getPlayWhenReady));assertEquals("fixture:one",value(()->restored.getMediaItemAt(0).mediaId));main(restored::release);
        }
    }
    @Test public void b_downloadIndexHistoryAndDailyMixPersist()throws Exception{
        Track track=fixture("download");File downloaded=DownloadStore.destination(context,track.stableId);Files.copy(new File(track.playbackUri.getPath()).toPath(),downloaded.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        DownloadStore.put(context,track,downloaded);assertTrue(DownloadStore.contains(context,track.stableId));assertNotNull(DownloadStore.offline(context,track));assertTrue(DownloadStore.bytes(context)>0);
        ListeningHistory.record(context,track.toMediaItem());assertFalse(ListeningHistory.recent(context,10).isEmpty());assertFalse(ListeningHistory.dailyMix(context,10).isEmpty());
        assertTrue(FavoriteStore.toggle(context,track));assertTrue(FavoriteStore.contains(context,track));
        assertFalse(FavoriteStore.toggle(context,track));assertFalse(FavoriteStore.contains(context,track));
        assertTrue(DownloadStore.remove(context,track.stableId));assertFalse(DownloadStore.contains(context,track.stableId));
    }
    @Test public void c_settingsAndPlaylistImportScreensActuallyOpen()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withId(R.id.forYou)).perform(click());onView(withText("Recently played")).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
            onView(withId(R.id.playlists)).perform(click());onView(withText("+ Import playlist")).perform(click());
            onView(withText("Paste song names")).perform(click());onView(withText("Import playlist")).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
            onView(withId(R.id.more)).perform(click());onView(withText("Settings")).perform(click());onView(withText(org.hamcrest.Matchers.startsWith("Import playlists"))).perform(scrollTo()).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
        }
    }
    @Test public void d_liveCatalogueSearchResolveAndAudioBytes()throws Exception{
        OnlineCatalogSource source=new OnlineCatalogSource();List<Track> tracks=source.page("Meek Mill Going Bad",0,8);assertFalse("Live search returned no songs",tracks.isEmpty());
        Track resolved=source.resolve(tracks.get(0));java.net.HttpURLConnection connection=(java.net.HttpURLConnection)new java.net.URL(resolved.playbackUri.toString()).openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setRequestProperty("Range","bytes=0-1023");int status=connection.getResponseCode();assertTrue("Audio URL returned "+status,status==200||status==206);assertTrue(connection.getInputStream().read()!=-1);connection.disconnect();
    }
    @Test public void e_backgroundDownloadWaitsOfflineDeduplicatesAndCompletes()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            Track track=new Track("fixture:network-download",Uri.parse("https://raw.githubusercontent.com/anars/blank-audio/master/1-second-of-silence.mp3"),"Silence download test","Runtime Test","Fixtures");
            androidx.work.WorkManager manager=androidx.work.WorkManager.getInstance(context);
            manager.cancelUniqueWork(OfflineDownloadService.workName(track.stableId)).getResult().get();DownloadStore.remove(context,track.stableId);
            shell("svc wifi disable");shell("svc data disable");
            try{
                Thread.sleep(1500);OfflineDownloadService.enqueue(context,track);OfflineDownloadService.enqueue(context,track);
                await("Download request was not persisted",()->DownloadTasks.active(context,track.stableId),5000);
                java.util.List<androidx.work.WorkInfo> jobs=manager.getWorkInfosForUniqueWork(OfflineDownloadService.workName(track.stableId)).get();
                assertEquals(1,jobs.stream().filter(job->!job.getState().isFinished()).count());
                assertFalse("Download unexpectedly completed with no network",DownloadStore.contains(context,track.stableId));
            }finally{shell("svc wifi enable");shell("svc data enable");}
            await("Background download did not complete: "+DownloadTasks.get(context,track.stableId),()->DownloadStore.contains(context,track.stableId),90000);
            assertEquals("completed",DownloadTasks.get(context,track.stableId).optString("state"));
            assertTrue(DownloadStore.destination(context,track.stableId).length()>0);DownloadStore.remove(context,track.stableId);
        }
    }
    static void shell(String command)throws Exception{try(android.os.ParcelFileDescriptor result=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(result)){byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}}}
    @Test public void z_actualAndroidJamHostAndBrowserFriend()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            Track one=fixture("host"),two=fixture("friend");MediaController controller=connect();
            main(()->{controller.setMediaItem(one.toMediaItem());controller.prepare();controller.play();});await("Host playback did not start",()->value(controller::isPlaying),20000);
            List<Track> catalog=Arrays.asList(one,two);JamClient.Room room=new JamClient().create(catalog);
            Files.write(new File(context.getFilesDir(),"runtime-jam-code.txt").toPath(),room.code.getBytes());
            Intent start=new Intent(context,PlaybackService.class).setAction(PlaybackService.ACTION_START_JAM).putExtra(PlaybackService.EXTRA_JAM_CODE,room.code).putExtra(PlaybackService.EXTRA_JAM_TOKEN,room.hostToken).putExtra(PlaybackService.EXTRA_JAM_CATALOG,JamClient.hostCatalog(catalog));context.startService(start);
            android.util.Log.i("FeatureRuntimeTest","JAM_READY "+room.code);
            try{
                boolean manual="true".equals(InstrumentationRegistry.getArguments().getString("manualJam"));
                if(!manual)guest(room.code,"queue","{\"trackId\":\"fixture:friend\",\"guest\":\"Automated Friend\"}");
                await("Friend song never reached actual Android queue",()->value(controller::getMediaItemCount)>=2,180000);
                assertTrue(value(()->controller.getMediaItemAt(1).mediaId.startsWith("jam:")));assertEquals("Test Song friend",value(()->controller.getMediaItemAt(1).mediaMetadata.title.toString()));
                android.util.Log.i("FeatureRuntimeTest","JAM_QUEUE_PASS waiting for guest pause");
                if(!manual)guest(room.code,"control","{\"action\":\"pause\",\"guest\":\"Automated Friend\"}");
                await("Guest pause never reached Android player",()->!value(controller::getPlayWhenReady),120000);
                assertNull(value(controller::getPlayerError));
            }finally{new JamClient().close(room);context.startService(new Intent(context,PlaybackService.class).setAction(PlaybackService.ACTION_STOP_JAM));main(controller::release);}
        }
    }
    static void guest(String code,String action,String body)throws Exception{
        java.net.HttpURLConnection connection=(java.net.HttpURLConnection)new java.net.URL(JamClient.API_BASE+"/api/rooms/"+code+"/"+action).openConnection();
        connection.setRequestMethod("POST");connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
        try{try(java.io.OutputStream out=connection.getOutputStream()){out.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}assertTrue("Guest request rejected",connection.getResponseCode()<300);}finally{connection.disconnect();}
    }
}
