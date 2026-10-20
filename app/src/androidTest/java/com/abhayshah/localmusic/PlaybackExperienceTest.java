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
                onView(withId(R.id.fullTitle)).inRoot(FeatureRuntimeTest.focusedDialog()).check(matches(withText(song.title)));onView(withId(R.id.closeNowPlaying)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());android.os.SystemClock.sleep(android.view.ViewConfiguration.getDoubleTapTimeout()+20);action.send();
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
}
