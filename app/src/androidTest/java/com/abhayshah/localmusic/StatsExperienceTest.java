package com.abhayshah.localmusic;

import android.content.Context;
import android.widget.DatePicker;
import androidx.media3.common.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.time.LocalDate;
import org.junit.Test;import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

@RunWith(AndroidJUnit4.class)
@androidx.media3.common.util.UnstableApi
public class StatsExperienceTest {
    final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Test public void a_dateBoundsCountsArtistsAndGenresAreAccurate(){
        String database="listening-stats-qa.db";context.deleteDatabase(database);ListeningStats.Store store=new ListeningStats.Store(context,database);
        try{
            MediaMetadata rock=new MediaMetadata.Builder().setTitle("Same song").setArtist("Artist A").setGenre("Rock").build();
            store.record(new MediaItem.Builder().setMediaId("online:1").setMediaMetadata(rock).build(),1000);store.record(new MediaItem.Builder().setMediaId("download:2").setMediaMetadata(rock).build(),1999);
            store.record(new MediaItem.Builder().setMediaId("online:3").setMediaMetadata(new MediaMetadata.Builder().setTitle("Other song").setArtist("Artist B").setGenre("Pop").build()).build(),2000);
            ListeningStats.Summary first=store.summary(1000,2000);assertEquals(2,first.plays);assertEquals(1,first.tracks);assertEquals(1,first.artists);assertEquals("Rock",first.genres.get(0).name);assertEquals(2,first.genres.get(0).count);assertEquals(2,first.songs.get(0).count);
            assertEquals(1,store.summary(2000,3000).plays);assertEquals(0,store.summary(3000,4000).plays);
            try{store.summary(2000,1000);fail("Invalid range accepted");}catch(IllegalArgumentException expected){}
        }finally{store.close();context.deleteDatabase(database);}
    }
    static androidx.test.espresso.ViewAction date(LocalDate value){return new androidx.test.espresso.ViewAction(){public org.hamcrest.Matcher<android.view.View> getConstraints(){return isAssignableFrom(DatePicker.class);}public String getDescription(){return "select date "+value;}public void perform(androidx.test.espresso.UiController ui,android.view.View view){((DatePicker)view).updateDate(value.getYear(),value.getMonthValue()-1,value.getDayOfMonth());ui.loopMainThreadUntilIdle();}};}
    @Test public void b_settingsStatsPresetsAndCustomDatesActuallyWork()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            AudioExperienceTest helper=new AudioExperienceTest();helper.settings();helper.settingsAction("Listening stats");AudioExperienceTest.waitPanel("Your listening");
            onView(withText("Last 30 days")).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());onView(withText("All time")).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());AudioExperienceTest.waitPanel("Your listening");
            onView(withText("All time")).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());onView(withText("Custom dates")).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());android.os.SystemClock.sleep(350);
            LocalDate end=LocalDate.now(),start=end.minusDays(1);onView(isAssignableFrom(DatePicker.class)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(date(start));onView(withId(android.R.id.button1)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());android.os.SystemClock.sleep(350);
            onView(isAssignableFrom(DatePicker.class)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(date(end));onView(withId(android.R.id.button1)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());AudioExperienceTest.waitPanel("Your listening");
            onView(withText(start+" – "+end)).inRoot(FeatureRuntimeTest.focusedDialog()).check(matches(isDisplayed()));onView(withContentDescription("Close stats")).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());FeatureRuntimeTest.awaitDismissed("Listening stats");
        }
    }
}
