package com.abhayshah.localmusic;

import android.app.Dialog;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GestureRegressionTest {
    @Test public void repeatedMixSwipesDismissWithoutBlockingLists()throws Exception{
        Track track=new FeatureRuntimeTest().fixture("gesture-regression");AtomicReference<Dialog> panel=new AtomicReference<>();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            for(int i=0;i<20;i++){
                int iteration=i;activity.onActivity(a->panel.set(MixDialog.show(a,"Swipe "+iteration,Collections.singletonList(track),(songs,index)->fail("Swipe played a song"),songs->fail("Swipe added songs"),song->fail("Swipe opened song menu"))));
                onView(withContentDescription("Dismiss panel")).inRoot(FeatureRuntimeTest.focusedDialog()).perform(FeatureRuntimeTest.swipeFromMiddle(i%2==0));
                try{FeatureRuntimeTest.await("Mix swipe "+i+" did not dismiss",()->!FeatureRuntimeTest.value(()->panel.get().isShowing()),3000);}
                catch(AssertionError failure){throw new AssertionError("Iteration "+i+": "+FeatureRuntimeTest.value(()->state(panel.get().getWindow().getDecorView())),failure);}
            }
        }finally{FeatureRuntimeTest.main(()->{if(panel.get()!=null)panel.get().dismiss();});}
    }
    static String state(View view){
        StringBuilder out=new StringBuilder();if(view instanceof DismissSwipeLayout){out.append("size=").append(view.getWidth()).append('x').append(view.getHeight()).append(" rootX=").append(view.getRootView().getTranslationX());for(java.lang.reflect.Field field:DismissSwipeLayout.class.getDeclaredFields()){try{field.setAccessible(true);out.append(' ').append(field.getName()).append('=').append(field.get(view));}catch(Exception ignored){}}}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)out.append(state(((ViewGroup)view).getChildAt(i)));return out.toString();
    }
}
