package com.abhayshah.localmusic;

import androidx.media3.session.MediaController;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
@androidx.media3.common.util.UnstableApi
public class BatchSelectionTest {
    @Test public void queueLongPressSelectsMultipleSongsForOneAction()throws Exception{
        FeatureRuntimeTest helper=new FeatureRuntimeTest();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            MediaController controller=helper.connect();Track first=helper.fixture("batch-one"),second=helper.fixture("batch-two"),third=helper.fixture("batch-three");
            try{
                FeatureRuntimeTest.main(()->{controller.setMediaItems(Arrays.asList(first.toMediaItem(),second.toMediaItem(),third.toMediaItem()));controller.prepare();});
                FeatureRuntimeTest.await("Queue was not ready",()->FeatureRuntimeTest.value(controller::getMediaItemCount)==3,10000);
                activity.onActivity(a->AudioExperienceTest.invoke(a,"showQueue"));
                onView(withContentDescription("Queue song "+second.title)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(longClick());
                onView(withContentDescription("Queue song "+third.title)).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());
                onView(withText("2 selected")).inRoot(FeatureRuntimeTest.focusedDialog()).check(matches(isDisplayed()));
                onView(withContentDescription("Selected queue actions")).inRoot(FeatureRuntimeTest.focusedDialog()).perform(click());
                onView(withText("Remove selected from queue")).perform(click());
                FeatureRuntimeTest.await("Batch removal missed a song",()->FeatureRuntimeTest.value(controller::getMediaItemCount)==1,5000);
                assertEquals(first.stableId,FeatureRuntimeTest.value(()->controller.getMediaItemAt(0).mediaId));
            }finally{FeatureRuntimeTest.main(controller::release);}
        }
    }

    @Test public void libraryLongPressSelectsWithoutStartingPlayback()throws Exception{
        FeatureRuntimeTest helper=new FeatureRuntimeTest();Track first=helper.fixture("library-batch-one"),second=helper.fixture("library-batch-two");
        ListeningHistory.record(helper.context,first.toMediaItem());ListeningHistory.record(helper.context,second.toMediaItem());
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withId(R.id.more)).perform(click());onView(withText("Recently played")).perform(click());
            onView(withContentDescription("Song "+first.title)).perform(longClick());
            onView(withContentDescription("Song "+second.title)).perform(click());
            onView(withText("2 selected")).check(matches(isDisplayed()));
            onView(withContentDescription("Selected song actions")).perform(click());
            onView(withText("Download selected")).perform(click());
            onView(withContentDescription("Done selecting songs")).check(matches(not(isDisplayed())));
        }
    }
}
