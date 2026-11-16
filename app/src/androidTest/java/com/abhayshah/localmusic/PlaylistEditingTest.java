package com.abhayshah.localmusic;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Collections;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static com.abhayshah.localmusic.FeatureRuntimeTest.focusedDialog;

@RunWith(AndroidJUnit4.class)
public class PlaylistEditingTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static Track track(String id,String title,String artist){return new Track(id,Uri.EMPTY,title,artist,"Album");}
    private static void save(MainActivity activity,String playlist,Track track){
        try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("saveTrackToPlaylist",String.class,Track.class);method.setAccessible(true);method.invoke(activity,playlist,track);}
        catch(Exception error){throw new AssertionError(error);}
    }
    @Test public void playlistRowCanBeRemovedFromSetAndOrder(){
        String name="QA Remove "+System.nanoTime();Track song=track("fixture:remove","Rather Be","Clean Bandit");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{save(activity,name,song);activity.previewPersonalMix(name,new ArrayList<>(Collections.singletonList(song)));});
            onView(withContentDescription("More options for Rather Be")).inRoot(focusedDialog()).perform(click());
            onView(withText("Remove from playlist")).inRoot(focusedDialog()).perform(click());
            assertTrue(context.getSharedPreferences("library",0).getStringSet("playlist:"+name,Collections.emptySet()).isEmpty());
            assertEquals("[]",context.getSharedPreferences("library",0).getString("playlist_order:"+name,""));
        }finally{context.getSharedPreferences("library",0).edit().remove("playlist:"+name).remove("playlist_order:"+name).apply();}
    }
    @Test public void similarTitleRequiresConfirmationButExactIdCannotDuplicate(){
        String name="QA Similar "+System.nanoTime();Track drake=track("fixture:drake","Lord Knows","Drake"),ross=track("fixture:ross","Lord Knows","Rick Ross");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->save(activity,name,drake));
            scenario.onActivity(activity->save(activity,name,ross));
            onView(withText("Similar song in playlist")).inRoot(focusedDialog()).check(matches(isDisplayed()));
            onView(withText("No")).inRoot(focusedDialog()).perform(click());
            assertEquals(1,context.getSharedPreferences("library",0).getStringSet("playlist:"+name,Collections.emptySet()).size());
            scenario.onActivity(activity->save(activity,name,ross));
            onView(withText("Add anyway")).inRoot(focusedDialog()).perform(click());
            scenario.onActivity(activity->save(activity,name,ross));
            assertEquals(2,context.getSharedPreferences("library",0).getStringSet("playlist:"+name,Collections.emptySet()).size());
            assertEquals(2,new org.json.JSONArray(context.getSharedPreferences("library",0).getString("playlist_order:"+name,"[]")).length());
        }catch(org.json.JSONException error){throw new AssertionError(error);}
        finally{context.getSharedPreferences("library",0).edit().remove("playlist:"+name).remove("playlist_order:"+name).apply();}
    }
    @Test public void genreMixStartsWithFiveSongsAndKeepsGenreContext()throws Exception{
        Track file=new FeatureRuntimeTest().fixture("genre-start");
        java.util.List<Track> songs=new ArrayList<>();
        for(int i=0;i<8;i++)songs.add(new Track("fixture:genre-"+i,file.playbackUri,"Genre Song "+i,"Genre Artist","Genre"));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            androidx.media3.session.MediaController controller=new FeatureRuntimeTest().connect();
            try{
                scenario.onActivity(activity->{try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("playGenreMix",String.class,java.util.List.class);method.setAccessible(true);method.invoke(activity,"Rock",songs);}catch(Exception error){throw new AssertionError(error);}});
                FeatureRuntimeTest.await("Genre mix did not start with five songs",()->FeatureRuntimeTest.value(()->controller.getMediaItemCount())==5,12000);
                assertEquals("fixture:genre-0",FeatureRuntimeTest.value(()->controller.getCurrentMediaItem().mediaId));
                assertEquals("Rock",context.getSharedPreferences("radio",0).getString("genre",null));
            }finally{FeatureRuntimeTest.main(()->{controller.pause();controller.release();context.startService(new android.content.Intent(context,PlaybackService.class).setAction(PlaybackService.ACTION_STOP_RADIO));});}
        }finally{new java.io.File(file.playbackUri.getPath()).delete();}
    }
}
