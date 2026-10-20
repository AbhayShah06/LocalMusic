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
    @org.junit.Before public void stopPreviousRadio(){main(()->context.startService(new Intent(context,PlaybackService.class).setAction(PlaybackService.ACTION_STOP_RADIO)));InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    static void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    static <T>T value(Supplier<T> read){AtomicReference<T> result=new AtomicReference<>();main(()->result.set(read.get()));return result.get();}
    static void await(String message,Supplier<Boolean> condition,long timeout)throws Exception {long until=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<until){if(condition.get())return;Thread.sleep(150);}fail(message);}
    static org.hamcrest.Matcher<androidx.test.espresso.Root> focusedDialog(){
        return new org.hamcrest.TypeSafeMatcher<androidx.test.espresso.Root>(){
            @Override protected boolean matchesSafely(androidx.test.espresso.Root root){return androidx.test.espresso.matcher.RootMatchers.isDialog().matches(root)&&root.getDecorView().hasWindowFocus();}
            @Override public void describeTo(org.hamcrest.Description description){description.appendText("the focused dialog");}
        };
    }
    static boolean hasDescription(android.view.View view,String description){
        if(!view.isShown()||!view.isAttachedToWindow())return false;
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return true;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(hasDescription(group.getChildAt(i),description))return true;}
        return false;
    }
    static void awaitDismissed(String description)throws Exception{
        await(description+" remained open",()->!value(()->{for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(hasDescription(root,description))return true;return false;}),5000);
    }
    static androidx.test.espresso.ViewAction swipeFromMiddle(boolean right){
        return new androidx.test.espresso.action.GeneralSwipeAction(androidx.test.espresso.action.Swipe.SLOW,
                androidx.test.espresso.action.GeneralLocation.CENTER,right?androidx.test.espresso.action.GeneralLocation.CENTER_RIGHT:androidx.test.espresso.action.GeneralLocation.CENTER_LEFT,
                androidx.test.espresso.action.Press.FINGER);
    }
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
            onView(withId(R.id.forYou)).perform(click());onView(withText("Recently played")).check(matches(isDisplayed()));onView(withId(R.id.songs)).perform(click());
            onView(withId(R.id.playlists)).perform(click());onView(withText("+ Import playlist")).perform(click());
            onView(withText("Paste song names")).perform(click());onView(withText("Import playlist")).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
            onView(withId(R.id.more)).perform(click());onView(withText("Settings")).perform(click());AudioExperienceTest.waitPanel("Track normalization");onView(withText(org.hamcrest.Matchers.startsWith("Import playlists"))).perform(scrollTo()).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
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
    @Test public void f_queueRefreshesDuringAutomaticTransitionAndIncomingAddition()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            Track one=fixture("transition-one"),two=fixture("transition-two"),incoming=fixture("incoming");MediaController controller=connect();
            main(()->{controller.setMediaItems(Arrays.asList(one.toMediaItem(),two.toMediaItem()));controller.prepare();controller.play();});
            await("Playback not ready",()->value(controller::isPlaying),10000);
            onView(withId(R.id.miniArtwork)).perform(click());onView(withId(R.id.fullQueue)).inRoot(focusedDialog()).perform(click());
            main(()->controller.seekTo(299750));
            await("Automatic transition did not happen",()->value(()->controller.getCurrentMediaItem().mediaId).equals(two.stableId),10000);
            activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            main(()->controller.addMediaItem(incoming.toMediaItem().buildUpon().setMediaId("auto:"+incoming.stableId).build()));
            activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            onView(withText("Test Song incoming")).inRoot(focusedDialog()).check(matches(isDisplayed()));
            onView(withContentDescription("More options for Test Song incoming")).perform(click());onView(withText("Download")).check(matches(isDisplayed()));
            androidx.test.espresso.Espresso.pressBack();androidx.test.espresso.Espresso.pressBack();
            onView(withId(R.id.fullTitle)).check(matches(withText("Test Song transition-two")));
            androidx.test.espresso.Espresso.pressBack();onView(withId(R.id.nowPlaying)).check(matches(withText("Test Song transition-two")));main(()->{controller.pause();controller.release();});
        }
    }
    @Test public void g_phonePlaylistsSongsAlbumsAndSourceSwitchKeepDownloadsVisible()throws Exception{
        Track saved=fixture("navigation");File destination=DownloadStore.destination(context,saved.stableId);Files.copy(new File(saved.playbackUri.getPath()).toPath(),destination.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);DownloadStore.put(context,saved,destination);
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withId(R.id.source)).perform(click());onView(withText("This phone")).perform(click());
            waitForText("Test Song navigation");
            for(int round=0;round<2;round++){
                onView(withId(R.id.playlists)).perform(click());androidx.test.espresso.Espresso.pressBack();onView(withId(R.id.songs)).perform(click());waitForText("Test Song navigation");
                onView(withId(R.id.albums)).perform(click());waitForText("Fixtures \u00b7 Runtime Test");onView(withId(R.id.songs)).perform(click());waitForText("Test Song navigation");
                onView(withId(R.id.forYou)).perform(click());onView(withId(R.id.songs)).perform(click());waitForText("Test Song navigation");
                onView(withId(R.id.source)).perform(click());onView(withText("Stream online")).perform(click());
                onView(withId(R.id.source)).perform(click());onView(withText("This phone")).perform(click());waitForText("Test Song navigation");
            }
        }finally{DownloadStore.remove(context,saved.stableId);}
    }
    static void waitForPlayerTitle(String text)throws Exception{
        long until=System.currentTimeMillis()+10000;
        while(System.currentTimeMillis()<until){try{onView(withId(R.id.nowPlaying)).check(matches(withText(text)));return;}catch(AssertionError|androidx.test.espresso.NoMatchingViewException waiting){Thread.sleep(150);}}
        onView(withId(R.id.nowPlaying)).check(matches(withText(text)));
    }
    static void waitForText(String text)throws Exception{
        long until=System.currentTimeMillis()+10000;AssertionError last=null;
        while(System.currentTimeMillis()<until){try{onView(withText(text)).check(matches(isDisplayed()));return;}catch(androidx.test.espresso.NoMatchingViewException|AssertionError waiting){if(waiting instanceof AssertionError)last=(AssertionError)waiting;Thread.sleep(150);}}
        if(last!=null)throw last;onView(withText(text)).check(matches(isDisplayed()));
    }
    @Test public void h_feedbackAndNotificationActionsDoNotTrapNavigation()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withId(R.id.more)).perform(click());onView(withText("Settings")).perform(click());AudioExperienceTest.waitPanel("Track normalization");
            new AudioExperienceTest().settingsAction("Feedback");AudioExperienceTest.waitPanel("Copy email address");android.os.SystemClock.sleep(320);onView(withText("Copy email address")).perform(click());
            assertEquals(FeedbackDraft.EMAIL,value(()->context.getSystemService(android.content.ClipboardManager.class).getPrimaryClip().getItemAt(0).getText().toString()));
            onView(withText("Settings")).check(doesNotExist());onView(withId(R.id.forYou)).perform(click());onView(withText("Recently played")).check(matches(isDisplayed()));
            Track track=fixture("notification-cancel");DownloadTasks.put(context,track,"queued",0,-1,"");
            new DownloadNotificationReceiver().onReceive(context,new Intent(context,DownloadNotificationReceiver.class).setAction("hide").putExtra("track_id",track.stableId));
            assertTrue(context.getSharedPreferences("download_notifications",Context.MODE_PRIVATE).getBoolean(track.stableId,false));assertEquals("queued",DownloadTasks.get(context,track.stableId).optString("state"));
            new DownloadNotificationReceiver().onReceive(context,new Intent(context,DownloadNotificationReceiver.class).setAction("cancel").putExtra("track_id",track.stableId));
            assertEquals("cancelled",DownloadTasks.get(context,track.stableId).optString("state"));
            android.app.NotificationChannel channel=context.getSystemService(android.app.NotificationManager.class).getNotificationChannel("offline_downloads_quiet");
            if(channel!=null){assertEquals(android.app.NotificationManager.IMPORTANCE_LOW,channel.getImportance());assertNull(channel.getSound());assertFalse(channel.shouldVibrate());}
        }
    }
    @Test public void i_autoplayRefillsFiveKeepsOriginalSeedAndManualTracks()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            MediaController controller=connect();Track seed=fixture("radio-seed"),manual=fixture("radio-manual");List<Track> candidates=new ArrayList<>();
            for(int i=0;i<14;i++)candidates.add(new Track("fixture:radio-"+i,seed.playbackUri,"Radio Song "+i,"Related Artist","Radio"));
            main(()->{controller.setRepeatMode(Player.REPEAT_MODE_OFF);controller.setMediaItem(seed.toMediaItem());controller.prepare();controller.play();});
            context.startService(new Intent(context,PlaybackService.class).setAction(PlaybackService.ACTION_SET_RADIO).putExtra("radio_seed",JamClient.hostCatalog(Collections.singletonList(seed))).putExtra("radio_candidates",JamClient.hostCatalog(candidates)));
            try{
                await("Initial five recommendations missing",()->value(()->autoplayAhead(controller))==5,20000);
                main(()->controller.addMediaItem(1,manual.toMediaItem()));
                for(int pass=0;pass<4;pass++){
                    main(controller::seekToNextMediaItem);await("Autoplay did not replenish to five",()->value(()->autoplayAhead(controller))==5,15000);
                    assertTrue(value(()->{for(int n=0;n<controller.getMediaItemCount();n++)if(controller.getMediaItemAt(n).mediaId.equals(manual.stableId))return true;return false;}));
                    assertEquals(seed.stableId,new org.json.JSONArray(context.getSharedPreferences("radio",0).getString("seed","[]")).getJSONObject(0).getString("id"));
                }
                assertNull(value(controller::getPlayerError));
            }finally{context.startService(new Intent(context,PlaybackService.class).setAction(PlaybackService.ACTION_STOP_RADIO));main(()->{controller.pause();controller.release();});}
        }
    }
    static int autoplayAhead(MediaController controller){int count=0;for(int i=controller.getCurrentMediaItemIndex()+1;i<controller.getMediaItemCount();i++)if(controller.getMediaItemAt(i).mediaId.startsWith("auto:"))count++;return count;}
    @Test public void j_mixPlaysExactlyItsPreviewAndSelectedSongsCanBeAdded()throws Exception{
        Track base=fixture("collection");List<Track> songs=new ArrayList<>();for(int n=0;n<6;n++)songs.add(new Track("fixture:collection-"+n,base.playbackUri,"Collection Song "+n,"Runtime Artist","Collection"));
        String name="QA Selection "+System.nanoTime();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            MediaController controller=connect();main(()->{controller.setShuffleModeEnabled(true);controller.setRepeatMode(Player.REPEAT_MODE_ALL);});
            activity.onActivity(a->a.previewPersonalMix("Collection Mix",songs));android.os.SystemClock.sleep(320);onView(withText("Save playlist")).inRoot(focusedDialog()).check(doesNotExist());onView(withText("Play")).inRoot(focusedDialog()).perform(click());
            await("Mix did not start",()->value(()->controller.getMediaItemCount()==6&&controller.getCurrentMediaItem().mediaId.equals(songs.get(0).stableId)),15000);
            for(int n=0;n<6;n++){int index=n;assertEquals(songs.get(n).stableId,value(()->controller.getMediaItemAt(index).mediaId));}
            assertFalse(value(controller::getShuffleModeEnabled));assertEquals(Integer.valueOf(Player.REPEAT_MODE_OFF),value(controller::getRepeatMode));
            activity.onActivity(a->a.previewPersonalMix("Collection Mix",songs));android.os.SystemClock.sleep(320);onView(withText("Select")).inRoot(focusedDialog()).perform(click());
            for(int index:new int[]{1,3,4})onView(withContentDescription("Select Collection Song "+index)).inRoot(focusedDialog()).perform(click());
            onView(withText("Add 3 to playlist")).inRoot(focusedDialog()).perform(click());onView(withText("+ New playlist")).inRoot(focusedDialog()).perform(click());onView(isAssignableFrom(android.widget.EditText.class)).inRoot(focusedDialog()).perform(typeText(name),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());onView(withText("Add")).inRoot(focusedDialog()).perform(click());
            org.json.JSONArray saved=new org.json.JSONArray(context.getSharedPreferences("library",0).getString("playlist_order:"+name,"[]"));assertEquals(3,saved.length());
            for(int n=0;n<3;n++)assertEquals(songs.get(new int[]{1,3,4}[n]).stableId,new org.json.JSONObject(saved.getString(n)).getString("id"));
            onView(withText("Play")).inRoot(focusedDialog()).perform(click());await("Selected songs did not become exact queue",()->value(()->controller.getMediaItemCount()==3&&controller.getCurrentMediaItem().mediaId.equals(songs.get(1).stableId)),10000);
            for(int n=0;n<3;n++){int index=n;assertEquals(songs.get(new int[]{1,3,4}[n]).stableId,value(()->controller.getMediaItemAt(index).mediaId));}
            main(()->{controller.pause();controller.release();});
        }finally{context.getSharedPreferences("library",0).edit().remove("playlist:"+name).remove("playlist_order:"+name).apply();}
    }
    @Test public void k_homeRefreshAndBothDirectionsDismissSheets()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withId(R.id.forYou)).perform(click());Track fresh=fixture("fresh-home");ListeningHistory.record(context,fresh.toMediaItem());
            onView(withContentDescription("Refresh home")).perform(swipeDown());
            onView(org.hamcrest.Matchers.allOf(withText("Test Song fresh-home"),
                    isDescendantOfA(withContentDescription("Refresh home")))).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("Artist mixes")).check(doesNotExist());
            activity.onActivity(a->a.previewPersonalMix("Gesture Mix",Arrays.asList(fresh)));onView(withContentDescription("Dismiss panel")).inRoot(androidx.test.espresso.matcher.RootMatchers.withDecorView(hasDescendant(withText("Gesture Mix")))).perform(swipeFromMiddle(false));awaitDismissed("Close mix");
            MediaController controller=connect();main(()->{controller.setMediaItem(fresh.toMediaItem());controller.prepare();controller.play();});await("Playback did not start",()->value(controller::isPlaying),10000);waitForPlayerTitle("Test Song fresh-home");
            onView(withId(R.id.miniArtwork)).perform(click());onView(withId(R.id.fullQueue)).inRoot(focusedDialog()).perform(click());
            onView(withContentDescription("Dismiss panel")).inRoot(androidx.test.espresso.matcher.RootMatchers.withDecorView(hasDescendant(withContentDescription("Close queue")))).perform(swipeFromMiddle(false));awaitDismissed("Close queue");
            onView(withId(R.id.fullArtwork)).inRoot(focusedDialog()).perform(swipeFromMiddle(true));onView(withId(R.id.miniPlayer)).check(matches(isDisplayed()));main(()->{controller.pause();controller.release();});
        }
    }
    @Test public void l_downloadedRecordingIsPreferredAcrossSourceIdsAndFormats()throws Exception{
        Track audio=fixture("offline-original");Track stored=new Track("online:0:owned-upload",audio.playbackUri,"Summer","Calvin Harris","Motion");File file=DownloadStore.destination(context,stored.stableId);Files.copy(new File(audio.playbackUri.getPath()).toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);DownloadStore.put(context,stored,file);
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            Track recommendation=new Track("online:0:other-upload",Uri.EMPTY,"Summer (Official Audio)","Calvin Harris - Topic","Motion");assertNotNull(DownloadStore.offline(context,recommendation));assertEquals("file",StreamPlayback.item(context,recommendation).localConfiguration.uri.getScheme());
            MediaController controller=connect();PlaybackCollection.request(context,Collections.singletonList(recommendation),0);await("Downloaded alternate upload did not play",()->value(controller::isPlaying),10000);assertNull(value(controller::getPlayerError));
            List<Track> candidates=new ArrayList<>();candidates.add(recommendation);candidates.add(new Track("online:0:duplicate-upload",Uri.EMPTY,"Summer","Calvin Harris","Motion"));
            for(int n=0;n<4;n++)candidates.add(new Track("fixture:other-"+n,audio.playbackUri,"Other Song "+n,"Related Artist","Fixtures"));
            main(()->{controller.setMediaItem(audio.toMediaItem());controller.prepare();controller.play();});
            context.startService(new Intent(context,PlaybackService.class).setAction(PlaybackService.ACTION_SET_RADIO).putExtra("radio_seed",JamClient.hostCatalog(Collections.singletonList(audio))).putExtra("radio_candidates",JamClient.hostCatalog(candidates)));
            await("Autoplay did not fill five distinct downloaded-first songs",()->value(()->autoplayAhead(controller))==5,15000);
            assertEquals("auto:"+stored.stableId,value(()->controller.getMediaItemAt(1).mediaId));assertEquals("file",value(()->controller.getMediaItemAt(1).localConfiguration.uri.getScheme()));
            context.startService(new Intent(context,PlaybackService.class).setAction(PlaybackService.ACTION_STOP_RADIO));main(()->{controller.pause();controller.release();});
        }finally{DownloadStore.remove(context,stored.stableId);}
    }
    @Test public void x_livePromisesPromisesKeepsFixedGainAcrossSeek()throws Exception{
        android.content.SharedPreferences prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE);boolean oldEq=prefs.getBoolean("eq_enabled",false),oldLevel=prefs.getBoolean("volume_leveling",true);
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            List<Track> found=new OnlineCatalogSource().page("Naked Eyes Promises Promises",0,10);Track song=null;
            for(Track track:found)if(track.title.toLowerCase(java.util.Locale.ROOT).contains("promises")&&(track.artist+" "+track.title).toLowerCase(java.util.Locale.ROOT).contains("naked")){song=track;break;}
            assertNotNull("Could not find the requested Naked Eyes recording",song);System.out.println("Quiet-source test: "+song.title+" / "+song.artist);
            MediaController controller=connect();Track selected=song;
            try{
                main(()->{prefs.edit().putBoolean("eq_enabled",false).putBoolean("volume_leveling",true).commit();controller.setMediaItem(StreamPlayback.item(context,selected));controller.prepare();controller.play();});
                await("Promises, Promises did not stream",()->value(controller::isPlaying),45000);float gain=value(controller::getVolume);
                Thread.sleep(1500);assertEquals(gain,value(controller::getVolume),0f);
                main(()->controller.seekTo(60000));await("Song did not resume after seek",()->value(controller::isPlaying),15000);Thread.sleep(1500);assertEquals(gain,value(controller::getVolume),0f);assertNull(value(controller::getPlayerError));
            }finally{main(()->{controller.pause();controller.release();});}
        }finally{prefs.edit().putBoolean("eq_enabled",oldEq).putBoolean("volume_leveling",oldLevel).commit();}
    }
    @Test public void m_liveSongMixAndLazyOnlinePlayback()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            OnlineCatalogSource source=new OnlineCatalogSource();List<Track> matches=source.page("Calvin Harris Summer",0,5);assertFalse(matches.isEmpty());Track seed=matches.get(0);
            List<Track> mix=source.songMix(seed);assertTrue("Song mix returned fewer than three songs",mix.size()>=3);Set<String> artists=new HashSet<>();
            for(Track track:mix){assertFalse("Video returned in mix",VersionPolicy.isMusicVideo(track.title));assertFalse("Edited version returned in mix",VersionPolicy.isEditedVersion(track.title));artists.add(RecordingIdentity.artist(track.artist));}
            assertTrue("Song mix collapsed to one artist",artists.size()>1);
            MediaController controller=connect();PlaybackCollection.request(context,mix,0);await("Lazy online mix did not start playback",()->value(controller::isPlaying),45000);
            assertEquals(Integer.valueOf(mix.size()),value(controller::getMediaItemCount));for(int n=0;n<mix.size();n++){int index=n;assertEquals(mix.get(n).stableId,value(()->controller.getMediaItemAt(index).mediaId));}
            assertNull(value(controller::getPlayerError));main(()->{controller.pause();controller.release();});
        }
    }
    @Test public void n_githubUpdateBannerIsVisibleDismissibleAndNotDuplicated()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            org.json.JSONObject update=new org.json.JSONObject().put("versionCode",9999).put("versionName","QA").put("notes","Update available");
            activity.onActivity(a->{UpdateManager.show(a,a.findViewById(R.id.root),update);UpdateManager.show(a,a.findViewById(R.id.root),update);});
            onView(withContentDescription("GitHub update available")).check(matches(isDisplayed()));
            onView(withText("Update now")).check(matches(isDisplayed()));onView(withText("Later")).perform(click());
            onView(withContentDescription("GitHub update available")).check(doesNotExist());
            onView(withId(R.id.songs)).perform(click());onView(withId(R.id.more)).perform(click());onView(withText("Settings")).perform(click());AudioExperienceTest.waitPanel("Track normalization");
            onView(withText(org.hamcrest.Matchers.startsWith("Check for updates"))).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }
    @Test public void o_autoplayQueuesUnresolvedSongsAndContinuesAfterCollection()throws Exception{
        Track seed=fixture("collection-radio");List<Track> promised=new ArrayList<>(),recommendations=new ArrayList<>();
        for(int n=0;n<7;n++)promised.add(new Track("fixture:promised-"+n,seed.playbackUri,"Promised "+n,"Collection Artist","Album"));
        for(int n=0;n<8;n++)recommendations.add(new Track("online:0:unreachable-"+n,Uri.EMPTY,"Recommendation "+n,"Other Artist "+n,""));
        String seedJson=JamClient.hostCatalog(Collections.singletonList(seed)),candidatesJson=JamClient.hostCatalog(recommendations);
        AtomicReference<androidx.media3.exoplayer.ExoPlayer> player=new AtomicReference<>();AtomicReference<AutoplayQueue> radio=new AtomicReference<>();
        boolean autoplay=context.getSharedPreferences("settings",0).getBoolean("autoplay",true);context.getSharedPreferences("settings",0).edit().putBoolean("autoplay",true).apply();
        try{
            main(()->{androidx.media3.exoplayer.ExoPlayer value=new androidx.media3.exoplayer.ExoPlayer.Builder(context).build();player.set(value);List<MediaItem> items=new ArrayList<>();for(Track track:promised)items.add(track.toMediaItem());value.setMediaItems(items);AutoplayQueue queue=new AutoplayQueue(context,value,new android.os.Handler(android.os.Looper.getMainLooper()));radio.set(queue);queue.configure(seedJson,candidatesJson,true);});
            Thread.sleep(250);assertEquals(Integer.valueOf(7),value(()->player.get().getMediaItemCount()));
            main(()->{player.get().seekTo(3,0);radio.get().refill();});
            await("Recommendations waited on unavailable audio URLs",()->value(()->player.get().getMediaItemCount()==12),3000);
            for(int n=0;n<7;n++){int index=n;assertEquals(promised.get(n).stableId,value(()->player.get().getMediaItemAt(index).mediaId));}
            for(int n=7;n<12;n++){int index=n;assertTrue(value(()->player.get().getMediaItemAt(index).mediaId.startsWith("auto:")));assertEquals("localmusic",value(()->player.get().getMediaItemAt(index).localConfiguration.uri.getScheme()));}
            main(()->{player.get().seekTo(9,0);radio.get().refill();});await("Rolling queue did not replenish after collection",()->value(()->autoplayAhead(player.get())==5),3000);
            assertEquals(seed.stableId,new org.json.JSONArray(context.getSharedPreferences("radio",0).getString("seed","[]")).getJSONObject(0).getString("id"));
        }finally{main(()->{if(radio.get()!=null){radio.get().stop();radio.get().close();}if(player.get()!=null)player.get().release();});context.getSharedPreferences("settings",0).edit().putBoolean("autoplay",autoplay).apply();}
    }
    static int autoplayAhead(androidx.media3.exoplayer.ExoPlayer player){int count=0;for(int i=player.getCurrentMediaItemIndex()+1;i<player.getMediaItemCount();i++)if(player.getMediaItemAt(i).mediaId.startsWith("auto:"))count++;return count;}
    @Test public void p_secondTapDoesNotTurnOffAutoplay()throws Exception{
        Track base=fixture("double-tap");Track selected=new Track("online:0:aW52YWxpZA",base.playbackUri,"Double Tap Song","Runtime Artist","");File saved=DownloadStore.destination(context,selected.stableId);Files.copy(new File(base.playbackUri.getPath()).toPath(),saved.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);DownloadStore.put(context,selected,saved);
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            MediaController controller=connect();activity.onActivity(a->a.resolveAndPlayOnline(Collections.singletonList(selected),0));
            await("First tap did not enable autoplay",()->context.getSharedPreferences("radio",0).getString("seed","").contains(selected.stableId),5000);
            activity.onActivity(a->a.resolveAndPlayOnline(Collections.singletonList(selected),0));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertTrue(context.getSharedPreferences("radio",0).getString("seed","").contains(selected.stableId));
            await("Downloaded song did not play",()->value(controller::isPlaying),10000);main(()->{controller.pause();controller.release();});
        }finally{context.startService(new Intent(context,PlaybackService.class).setAction(PlaybackService.ACTION_STOP_RADIO));DownloadStore.remove(context,selected.stableId);}
    }
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
                if(!manual)guest(room.code,"control","{\"action\":\"toggle\",\"guest\":\"Automated Friend\"}");
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
