package com.abhayshah.localmusic;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.media3.common.*;

import androidx.media3.extractor.metadata.flac.VorbisComment;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
@androidx.media3.common.util.UnstableApi
public class AudioExperienceTest {
    final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    static Player.Events events(int event){return new Player.Events(new FlagSet.Builder().add(event).build());}
    static Tracks tracks(String gain){
        Format format=new Format.Builder().setSampleMimeType("audio/mpeg").setMetadata(gain==null?null:new Metadata(new VorbisComment("REPLAYGAIN_TRACK_GAIN",gain))).build();
        return new Tracks(Collections.singletonList(new Tracks.Group(new TrackGroup(format),false,new int[]{C.FORMAT_HANDLED},new boolean[]{true})));
    }
    @Test public void a_readsOnlyValidTrackGainTags(){
        assertEquals(.501187f,TrackVolume.fromMetadata(new Metadata(new VorbisComment("REPLAYGAIN_TRACK_GAIN","-6 dB"))),.00001f);
        assertEquals(.251189f,TrackVolume.fromMetadata(new Metadata(new TextInformationFrame("TXXX","replaygain_track_gain","-12 dB"))),.00001f);
        assertEquals(1f,TrackVolume.fromMetadata(new Metadata(new VorbisComment("REPLAYGAIN_ALBUM_GAIN","-9 dB"))),0f);
        assertEquals(1f,TrackVolume.fromMetadata(new Metadata(new VorbisComment("REPLAYGAIN_TRACK_GAIN","NaN"))),0f);
        assertEquals(1f,TrackVolume.fromMetadata(null),0f);
    }
    @Test public void b_gainIsFrozenThroughSeeksAndLateMetadata(){
        FeatureRuntimeTest.main(()->{
            SharedPreferences prefs=context.getSharedPreferences("audio-gain-qa",Context.MODE_PRIVATE);prefs.edit().clear().commit();
            float[] volume={1};Tracks[] selected={tracks("-6 dB")};
            Player fake=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->{
                if(method.getName().equals("getCurrentTracks"))return selected[0];
                if(method.getName().equals("getVolume"))return volume[0];
                if(method.getName().equals("setVolume")){volume[0]=(float)args[0];return null;}
                return null;
            });
            TrackVolume level=new TrackVolume(fake,prefs);
            try{
                level.onEvents(fake,events(Player.EVENT_MEDIA_ITEM_TRANSITION));float original=volume[0];assertEquals(.501187f,original,.00001f);
                selected[0]=tracks("-18 dB");
                for(int event:new int[]{Player.EVENT_TRACKS_CHANGED,Player.EVENT_POSITION_DISCONTINUITY,Player.EVENT_PLAYBACK_STATE_CHANGED,Player.EVENT_MEDIA_METADATA_CHANGED}){level.onEvents(fake,events(event));assertEquals(original,volume[0],0f);}
                selected[0]=tracks(null);level.onEvents(fake,events(Player.EVENT_MEDIA_ITEM_TRANSITION));assertEquals(1f,volume[0],0f);
                prefs.edit().putBoolean("eq_active",true).putInt("eq_band_0",6).commit();assertEquals(.501187f,volume[0],.00001f);
                prefs.edit().putBoolean("eq_active",false).commit();assertEquals(1f,volume[0],0f);
            }finally{level.close();prefs.edit().clear().commit();}
        });
    }
    void settings(){onView(withId(R.id.more)).perform(click());onView(withText("Settings")).perform(click());}
    void settingsAction(String title){onView(withText(org.hamcrest.Matchers.startsWith(title+"\n"))).perform(scrollTo(),click());}
    @Test public void c_equalizerPresetsResetAndPersistenceActuallyWork()throws Exception{
        SharedPreferences prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE);
        boolean previous=prefs.getBoolean("eq_enabled",false);int[] bands=PlaybackEqualizer.levels(prefs);
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            settings();settingsAction("Equalizer");onView(withText("Presets")).check(matches(isDisplayed()));
            onView(withText("Presets")).perform(click());onView(withText("Bass")).perform(click());assertEquals(4,prefs.getInt("eq_band_0",0));
            onView(withContentDescription("Enable equalizer")).perform(click());
            onView(withText("Done")).perform(click());settings();settingsAction("Equalizer");assertEquals(4,prefs.getInt("eq_band_0",0));
            onView(withText("Reset")).perform(click());assertFalse(prefs.getBoolean("eq_enabled",true));assertArrayEquals(new int[5],PlaybackEqualizer.levels(prefs));
            onView(withText("Presets")).perform(click());onView(withText("Vocal")).perform(click());assertEquals(3,prefs.getInt("eq_band_3",0));onView(withText("Done")).perform(click());
        }finally{SharedPreferences.Editor restore=prefs.edit().putBoolean("eq_enabled",previous);for(int i=0;i<5;i++)restore.putInt("eq_band_"+i,bands[i]);restore.commit();}
    }
    @Test public void d_settingsButtonsRevealTheirDestinations()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            settings();settingsAction("Music source");onView(withText("Music library")).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
            settings();settingsAction("Import playlists");onView(withText("Paste song names")).perform(click());onView(withText("Import playlist")).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
            settings();settingsAction("Feedback");onView(withText("Copy email address")).perform(click());
            settings();settingsAction("Download activity");onView(withText("Offline library")).perform(click());onView(withText("Settings")).check(doesNotExist());
            settings();settingsAction("Offline downloads");onView(withText("Settings")).check(doesNotExist());
            settings();settingsAction("Clear temporary cache");onView(withText("Settings")).check(doesNotExist());
            onView(withId(R.id.more)).perform(click());onView(withText("Sleep timer")).perform(click());onView(withText("Off")).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
        }
    }
    @Test public void e_realPlaybackAndNativeEqDoNotBreakQueueOrSeek()throws Exception{
        FeatureRuntimeTest helper=new FeatureRuntimeTest();SharedPreferences prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE);boolean old=prefs.getBoolean("eq_enabled",false);
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            androidx.media3.session.MediaController controller=helper.connect();Track one=helper.fixture("gain-one"),two=helper.fixture("gain-two");
            try{
                FeatureRuntimeTest.main(()->{prefs.edit().putBoolean("eq_enabled",false).commit();controller.setMediaItems(Arrays.asList(one.toMediaItem(),two.toMediaItem()));controller.prepare();controller.play();});
                FeatureRuntimeTest.await("Audio never started",()->FeatureRuntimeTest.value(controller::isPlaying),15000);
                assertEquals(1f,FeatureRuntimeTest.value(controller::getVolume),0f);
                FeatureRuntimeTest.main(()->controller.seekTo(120000));Thread.sleep(500);assertEquals(1f,FeatureRuntimeTest.value(controller::getVolume),0f);
                FeatureRuntimeTest.main(()->prefs.edit().putBoolean("eq_enabled",true).commit());Thread.sleep(500);
                assertNull(FeatureRuntimeTest.value(controller::getPlayerError));assertEquals(2,(int)FeatureRuntimeTest.value(controller::getMediaItemCount));
                assertFalse(prefs.getString("eq_status","").isEmpty());
                FeatureRuntimeTest.main(()->{prefs.edit().putBoolean("eq_enabled",false).commit();controller.seekToNextMediaItem();});
                FeatureRuntimeTest.await("Next song failed",()->FeatureRuntimeTest.value(()->controller.getCurrentMediaItemIndex()==1&&controller.isPlaying()),10000);
                assertEquals(1f,FeatureRuntimeTest.value(controller::getVolume),0f);assertNull(FeatureRuntimeTest.value(controller::getPlayerError));
            }finally{FeatureRuntimeTest.main(()->{controller.pause();controller.release();});}
        }finally{prefs.edit().putBoolean("eq_enabled",old).commit();}
    }
    @Test public void f_dragFollowsFingerCancelsAndProtectsSeekbar()throws Exception{
        java.util.concurrent.atomic.AtomicReference<android.app.Dialog> dialog=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<DismissSwipeLayout> swipe=new java.util.concurrent.atomic.AtomicReference<>();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            activity.onActivity(a->{android.app.Dialog panel=new android.app.Dialog(a);dialog.set(panel);DismissSwipeLayout layout=new DismissSwipeLayout(a,panel::dismiss);swipe.set(layout);android.widget.LinearLayout content=new android.widget.LinearLayout(a);content.setOrientation(1);android.widget.TextView title=new android.widget.TextView(a);title.setText("Gesture fixture");title.setHeight(180);content.addView(title);android.widget.SeekBar seek=new android.widget.SeekBar(a);seek.setId(123456);seek.setMax(100);content.addView(seek,new android.widget.LinearLayout.LayoutParams(-1,100));layout.addView(content);panel.setContentView(layout);panel.show();panel.getWindow().setLayout(480,600);});
            FeatureRuntimeTest.await("Gesture panel did not lay out",()->FeatureRuntimeTest.value(()->swipe.get().getWidth()>0),3000);
            FeatureRuntimeTest.main(()->{
                DismissSwipeLayout layout=swipe.get();float x=layout.getWidth()/2f;touch(layout,android.view.MotionEvent.ACTION_DOWN,x,40);touch(layout,android.view.MotionEvent.ACTION_MOVE,x+50*context.getResources().getDisplayMetrics().density,40);
                assertTrue("Panel did not follow finger",layout.getRootView().getTranslationX()>0);touch(layout,android.view.MotionEvent.ACTION_CANCEL,x+50,40);
            });
            Thread.sleep(250);assertTrue(FeatureRuntimeTest.value(()->dialog.get().isShowing()));assertEquals(0,FeatureRuntimeTest.value(()->swipe.get().getRootView().getTranslationX()),.01f);
            FeatureRuntimeTest.main(()->{
                DismissSwipeLayout layout=swipe.get();android.view.View seek=dialog.get().findViewById(123456);int[] parent=new int[2],child=new int[2];layout.getLocationOnScreen(parent);seek.getLocationOnScreen(child);float y=child[1]-parent[1]+seek.getHeight()/2f;
                touch(layout,android.view.MotionEvent.ACTION_DOWN,20,y);touch(layout,android.view.MotionEvent.ACTION_MOVE,layout.getWidth()-20,y);assertEquals(0,layout.getRootView().getTranslationX(),0f);touch(layout,android.view.MotionEvent.ACTION_UP,layout.getWidth()-20,y);
                float x=layout.getWidth()/2f;touch(layout,android.view.MotionEvent.ACTION_DOWN,x,40);touch(layout,android.view.MotionEvent.ACTION_MOVE,10,40);touch(layout,android.view.MotionEvent.ACTION_UP,10,40);
            });
            FeatureRuntimeTest.await("Completed swipe did not dismiss",()->!FeatureRuntimeTest.value(()->dialog.get().isShowing()),3000);
        }finally{FeatureRuntimeTest.main(()->{if(dialog.get()!=null)dialog.get().dismiss();});}
    }
    static void touch(android.view.View view,int action,float x,float y){long now=android.os.SystemClock.uptimeMillis();android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now,action,x,y,0);view.dispatchTouchEvent(event);event.recycle();}
    static void invoke(MainActivity activity,String method){try{java.lang.reflect.Method call=MainActivity.class.getDeclaredMethod(method);call.setAccessible(true);call.invoke(activity);}catch(Exception failure){throw new AssertionError(failure);}}
    @Test public void g_rapidNavigationDoesNotStackDuplicatePanels()throws Exception{
        FeatureRuntimeTest helper=new FeatureRuntimeTest();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            androidx.media3.session.MediaController controller=helper.connect();Track one=helper.fixture("rapid-navigation");FeatureRuntimeTest.main(()->{controller.setMediaItem(one.toMediaItem());controller.prepare();controller.play();});
            FeatureRuntimeTest.await("Player not ready",()->FeatureRuntimeTest.value(controller::isPlaying),15000);
            try{
                activity.onActivity(a->{invoke(a,"showNowPlaying");invoke(a,"showNowPlaying");invoke(a,"showQueue");invoke(a,"showQueue");});
                onView(withContentDescription("Close queue")).check(matches(isDisplayed()));onView(withContentDescription("Close queue")).perform(click());
                onView(withId(R.id.closeNowPlaying)).check(matches(isDisplayed()));onView(withId(R.id.closeNowPlaying)).perform(click());onView(withId(R.id.closeNowPlaying)).check(doesNotExist());
                activity.onActivity(a->{invoke(a,"showSettings");invoke(a,"showSettings");});onView(withText("Done")).perform(click());onView(withText("Settings")).check(doesNotExist());
                activity.onActivity(a->{invoke(a,"openEqualizer");invoke(a,"openEqualizer");});onView(withText("Done")).perform(click());onView(withText("Presets")).check(doesNotExist());
                onView(withId(R.id.playPause)).perform(click());FeatureRuntimeTest.await("Pause button stopped responding",()->!FeatureRuntimeTest.value(controller::isPlaying),5000);onView(withId(R.id.playPause)).perform(click());FeatureRuntimeTest.await("Play button stopped responding",()->FeatureRuntimeTest.value(controller::isPlaying),5000);
                assertEquals(1,(int)FeatureRuntimeTest.value(controller::getMediaItemCount));assertNull(FeatureRuntimeTest.value(controller::getPlayerError));
            }finally{FeatureRuntimeTest.main(()->{controller.pause();controller.release();});}
        }
    }
}
