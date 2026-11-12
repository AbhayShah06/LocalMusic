package com.abhayshah.localmusic;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
public class HomeUiTest {
    @Test public void recentSearchesAppearOnlyWhileSearching() {
        android.content.Context context=ApplicationProvider.getApplicationContext();
        android.content.SharedPreferences history=context.getSharedPreferences("search_history",0);
        String previous=history.getString("queries",null);
        history.edit().putString("queries","[\"Design smoke\"]").commit();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withId(R.id.recentSearchScroll)).check(matches(not(isDisplayed())));
            onView(withId(R.id.search)).perform(click());
            onView(withId(R.id.recentSearchScroll)).check(matches(isDisplayed()));
            onView(withId(R.id.forYou)).perform(click());
            onView(withId(R.id.recentSearchScroll)).check(matches(not(isDisplayed())));
        }finally{
            if(previous==null)history.edit().remove("queries").commit();
            else history.edit().putString("queries",previous).commit();
        }
    }
}
