package com.abhayshah.localmusic;

import android.content.*;
import android.app.*;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import androidx.media3.session.MediaController;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

@RunWith(AndroidJUnit4.class)
@androidx.media3.common.util.UnstableApi
public class PlaybackExperienceTest {
    final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Test public void a_notificationTapOpensPlayerWarmAndColdWithoutDuplicates()throws Exception{
        FeatureRuntimeTest helper=new FeatureRuntimeTest();MediaController controller=helper.connect();Track song=helper.fixture("notification-player");
        FeatureRuntimeTest.main(()->{controller.setMediaItem(song.toMediaItem());controller.prepare();controller.play();});FeatureRuntimeTest.await("Playback failed",()->FeatureRuntimeTest.value(controller::isPlaying),15000);
        try{
            try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
                android.app.PendingIntent action=FeatureRuntimeTest.value(controller::getSessionActivity);assertNotNull("Notification has no player intent",action);action.send();
                onView(withId(R.id.fullTitle)).inRoot(FeatureRuntimeTest.focusedDialog()).check(matches(withText(song.title)));onView(withId(R.id.fullQueue)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());onView(withContentDescription("More options for "+song.title)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());action.send();FeatureRuntimeTest.awaitDismissed("Close queue");onView(withId(R.id.fullTitle)).inRoot(FeatureRuntimeTest.focusedDialog()).check(matches(withText(song.title)));onView(withId(R.id.closeNowPlaying)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());android.os.SystemClock.sleep(android.view.ViewConfiguration.getDoubleTapTimeout()+20);action.send();
                onView(withId(R.id.closeNowPlaying)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());FeatureRuntimeTest.awaitDismissed("Close player");activity.onActivity(Activity::finish);
            }
            Intent cold=new Intent(context,MainActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).putExtra("show_now_playing",true);
            try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(cold)){
                onView(withId(R.id.fullTitle)).inRoot(FeatureRuntimeTest.focusedDialog()).check(matches(withText(song.title)));onView(withId(R.id.closeNowPlaying)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());
            }
        }finally{FeatureRuntimeTest.main(()->{controller.pause();controller.release();});}
    }
    @Test public void b_liveLyricsHighlightSeekAndFollowSongChanges()throws Exception{
        FeatureRuntimeTest helper=new FeatureRuntimeTest();Track first=helper.fixture("lyric-first"),second=helper.fixture("lyric-second");AtomicReference<Track> current=new AtomicReference<>(first);AtomicLong position=new AtomicLong();AtomicReference<Dialog> dialog=new AtomicReference<>();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            activity.onActivity(a->dialog.set(LyricsPanel.show(a,first,current::get,position::get,position::set,Runnable::run,new Handler(Looper.getMainLooper()),track->track==first?"[00:00]First line\n[00:01]Second line\n[00:02]Third line":"[00:00]New song line")));
            FeatureRuntimeTest.await("First lyric did not highlight",()->FeatureRuntimeTest.value(()->{for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(FeatureRuntimeTest.hasDescription(root,"Current lyric: First line"))return true;return false;}),5000);
            position.set(1100);FeatureRuntimeTest.await("Lyrics did not follow position",()->FeatureRuntimeTest.value(()->{for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(FeatureRuntimeTest.hasDescription(root,"Current lyric: Second line"))return true;return false;}),5000);
            onView(withText("Third line")).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());assertEquals(2000,position.get());current.set(second);position.set(0);
            FeatureRuntimeTest.await("Lyrics did not follow next song",()->FeatureRuntimeTest.value(()->{for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(FeatureRuntimeTest.hasDescription(root,"Current lyric: New song line"))return true;return false;}),5000);
            onView(withContentDescription("Close lyrics")).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());FeatureRuntimeTest.awaitDismissed("Live lyrics");
        }finally{FeatureRuntimeTest.main(()->{if(dialog.get()!=null)dialog.get().dismiss();});}
    }
    @Test public void c_tenHomeSeedsPreferDifferentArtistsAndDeduplicate(){
        List<Track> tracks=new ArrayList<>();for(int i=0;i<5;i++)tracks.add(new Track("fixture:same-"+i,Uri.EMPTY,"Same Artist Song "+i,"Artist A",""));for(int i=0;i<12;i++)tracks.add(new Track("fixture:variety-"+i,Uri.EMPTY,"Different Song "+i,"Artist "+(char)('B'+i),""));
        tracks.add(tracks.get(0));List<Track> result=ForYouDialog.diverseSeeds(tracks,10);assertEquals(10,result.size());Set<String> artists=new HashSet<>(),ids=new HashSet<>();for(Track track:result){artists.add(track.artist);assertTrue(ids.add(track.stableId));}assertEquals(10,artists.size());
    }
    @Test public void d_songMixStartsPlayingWithoutPreviewOrRestartOnDoubleTap()throws Exception{
        FeatureRuntimeTest helper=new FeatureRuntimeTest();Track audio=helper.fixture("tap-mix");Track seed=new Track("online:0:mix-owned",audio.playbackUri,"Immediate mix seed","Different Artist","Fixture");java.io.File file=DownloadStore.destination(context,seed.stableId);java.nio.file.Files.copy(new java.io.File(audio.playbackUri.getPath()).toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);DownloadStore.put(context,seed,file);
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            MediaController controller=helper.connect();
            try{
                activity.onActivity(a->{try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("showSeedMix",Track.class,boolean.class);method.setAccessible(true);method.invoke(a,seed,false);method.invoke(a,seed,false);}catch(Exception failure){throw new AssertionError(failure);}});
                FeatureRuntimeTest.await("Mix tap did not start playback",()->FeatureRuntimeTest.value(()->controller.isPlaying()&&controller.getCurrentMediaItem()!=null&&Track.originalId(controller.getCurrentMediaItem().mediaId).equals(seed.stableId)),10000);
                onView(withContentDescription("Close mix")).check(doesNotExist());assertNull(FeatureRuntimeTest.value(controller::getPlayerError));
            }finally{FeatureRuntimeTest.main(()->{controller.pause();controller.release();});context.startService(new Intent(context,PlaybackService.class).setAction(PlaybackService.ACTION_STOP_RADIO));}
        }finally{DownloadStore.remove(context,seed.stableId);}
    }
    @Test public void e_nowPlayingMenuFollowsTheSongAndRepeatControlsPlayback()throws Exception{
        FeatureRuntimeTest helper=new FeatureRuntimeTest();Track first=helper.fixture("repeat-first"),second=helper.fixture("repeat-second");MediaController controller=helper.connect();
        context.startService(new Intent(context,PlaybackService.class).setAction(PlaybackService.ACTION_STOP_RADIO));
        FeatureRuntimeTest.main(()->{controller.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF);controller.setShuffleModeEnabled(false);controller.setMediaItems(Arrays.asList(first.toMediaItem(),second.toMediaItem()));controller.prepare();controller.play();});
        FeatureRuntimeTest.await("Playback failed",()->FeatureRuntimeTest.value(controller::isPlaying),15000);
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withId(R.id.miniPlayer)).perform(click());onView(withId(R.id.fullMore)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());onView(withText(first.title)).inRoot(FeatureRuntimeTest.focusedDialog()).check(matches(isDisplayed()));onView(withText("Add to playlist")).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
            onView(withId(R.id.fullNext)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());FeatureRuntimeTest.await("Next did not update",()->FeatureRuntimeTest.value(controller::getCurrentMediaItemIndex)==1,5000);
            onView(withId(R.id.fullMore)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());onView(withText(second.title)).inRoot(FeatureRuntimeTest.focusedDialog()).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
            onView(withId(R.id.fullRepeat)).inRoot(FeatureRuntimeTest.focusedDialog()).check(matches(withContentDescription("Repeat off"))).perform(click());assertEquals(androidx.media3.common.Player.REPEAT_MODE_ALL,(int)FeatureRuntimeTest.value(controller::getRepeatMode));
            onView(withId(R.id.fullRepeat)).inRoot(FeatureRuntimeTest.focusedDialog()).check(matches(withContentDescription("Repeat all"))).perform(click());assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE,(int)FeatureRuntimeTest.value(controller::getRepeatMode));
            FeatureRuntimeTest.main(()->controller.seekTo(0,299800));assertTrue(FeatureRuntimeTest.value(controller::getCurrentPosition)>299000);
            try{FeatureRuntimeTest.await("Repeat one did not replay the song",()->FeatureRuntimeTest.value(()->controller.getCurrentMediaItemIndex()==0&&controller.getCurrentPosition()<5000&&controller.isPlaying()),10000);}catch(AssertionError failure){throw new AssertionError(FeatureRuntimeTest.value(()->"Repeat state: mode="+controller.getRepeatMode()+" index="+controller.getCurrentMediaItemIndex()+" position="+controller.getCurrentPosition()+" duration="+controller.getDuration()+" playing="+controller.isPlaying()+" error="+controller.getPlayerError()),failure);}
            onView(withId(R.id.fullRepeat)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());onView(withId(R.id.fullRepeat)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());assertEquals(androidx.media3.common.Player.REPEAT_MODE_ALL,(int)FeatureRuntimeTest.value(controller::getRepeatMode));
            FeatureRuntimeTest.main(()->controller.seekTo(1,299800));FeatureRuntimeTest.await("Repeat all did not wrap the queue",()->FeatureRuntimeTest.value(controller::getCurrentMediaItemIndex)==0,10000);
            onView(withId(R.id.closeNowPlaying)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());onView(withId(R.id.miniPlayer)).perform(click());onView(withId(R.id.fullRepeat)).inRoot(FeatureRuntimeTest.focusedDialog()).check(matches(withContentDescription("Repeat all"))).perform(click());onView(withId(R.id.fullRepeat)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF,(int)FeatureRuntimeTest.value(controller::getRepeatMode));FeatureRuntimeTest.main(()->controller.seekTo(1,299800));FeatureRuntimeTest.await("Repeat off did not stop at the end",()->FeatureRuntimeTest.value(controller::getPlaybackState)==androidx.media3.common.Player.STATE_ENDED,10000);assertNull(FeatureRuntimeTest.value(controller::getPlayerError));
        }finally{FeatureRuntimeTest.main(()->{controller.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF);controller.pause();controller.release();});}
    }
    @Test public void f_repeatHoldsTheQueueAndTurningItOffResumesAutoplay()throws Exception{
        FeatureRuntimeTest helper=new FeatureRuntimeTest();Track seed=helper.fixture("repeat-radio");List<Track> candidates=new ArrayList<>();for(int i=0;i<7;i++)candidates.add(new Track("fixture:repeat-candidate-"+i,seed.playbackUri,"Related "+i,"Artist "+i,""));
        String seedJson=JamClient.hostCatalog(Collections.singletonList(seed)),candidatesJson=JamClient.hostCatalog(candidates);
        AtomicReference<androidx.media3.exoplayer.ExoPlayer> player=new AtomicReference<>();AtomicReference<AutoplayQueue> radio=new AtomicReference<>();boolean autoplay=context.getSharedPreferences("settings",0).getBoolean("autoplay",true);context.getSharedPreferences("settings",0).edit().putBoolean("autoplay",true).apply();
        try{
            FeatureRuntimeTest.main(()->{androidx.media3.exoplayer.ExoPlayer p=new androidx.media3.exoplayer.ExoPlayer.Builder(context).build();player.set(p);p.setMediaItem(seed.toMediaItem());p.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ALL);AutoplayQueue q=new AutoplayQueue(context,p,new Handler(Looper.getMainLooper()));radio.set(q);q.configure(seedJson,candidatesJson);});
            android.os.SystemClock.sleep(350);assertEquals(1,(int)FeatureRuntimeTest.value(()->player.get().getMediaItemCount()));
            FeatureRuntimeTest.main(()->{player.get().setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF);radio.get().refill();});FeatureRuntimeTest.await("Autoplay did not resume after repeat off",()->FeatureRuntimeTest.value(()->player.get().getMediaItemCount())==6,5000);
        }finally{FeatureRuntimeTest.main(()->{if(radio.get()!=null){radio.get().stop();radio.get().close();}if(player.get()!=null)player.get().release();});context.getSharedPreferences("settings",0).edit().putBoolean("autoplay",autoplay).apply();}
    }
}
