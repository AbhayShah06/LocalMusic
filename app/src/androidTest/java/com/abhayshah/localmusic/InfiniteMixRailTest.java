package com.abhayshah.localmusic;

import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class InfiniteMixRailTest {
    @Test public void scrollingRightLoadsMoreWithoutKeepingEveryCardInMemory()throws Exception{
        android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences history=context.getSharedPreferences("listening_history",0);String previous=history.getString("tracks","[]");
        JSONArray seeds=new JSONArray();for(int i=0;i<12;i++)seeds.put(new JSONObject().put("id","online:0:seed-"+i).put("title","Seed "+i).put("artist","Artist "+i).put("plays",1).put("last",System.currentTimeMillis()));
        history.edit().putString("tracks",seeds.toString()).commit();
        AtomicInteger loads=new AtomicInteger();AtomicReference<HorizontalScrollView> rail=new AtomicReference<>();
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            activity.onActivity(a->{View home=ForYouDialog.create(a,track->{},(track,artist)->{},track->{},()->{},()->{},()->{},(visible,seen,done)->{
                int page=loads.incrementAndGet();List<Track> next=new ArrayList<>();for(int i=0;i<8;i++)next.add(new Track("online:0:more-"+page+"-"+i,Uri.EMPTY,"More "+page+"-"+i,"New Artist "+page+"-"+i,""));done.accept(next);
            },1L);((ViewGroup)a.findViewById(android.R.id.content)).addView(home,new ViewGroup.LayoutParams(-1,-1));rail.set(lastRail(home));});
            FeatureRuntimeTest.await("Mix rail did not lay out",()->FeatureRuntimeTest.value(()->rail.get()!=null&&rail.get().getWidth()>0),4000);
            for(int page=1;page<=6;page++){int expected=page;FeatureRuntimeTest.main(()->rail.get().scrollTo(100000,0));FeatureRuntimeTest.await("Mix rail stopped loading at page "+page,()->loads.get()>=expected,4000);}
            int cards=FeatureRuntimeTest.value(()->((ViewGroup)rail.get().getChildAt(0)).getChildCount()-1);assertTrue("Too many artwork cards retained: "+cards,cards<=40);assertTrue(cards>=8);
        }finally{history.edit().putString("tracks",previous).commit();}
    }
    private static HorizontalScrollView lastRail(View root){
        HorizontalScrollView found=root instanceof HorizontalScrollView?(HorizontalScrollView)root:null;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){HorizontalScrollView child=lastRail(group.getChildAt(i));if(child!=null)found=child;}}
        return found;
    }
}
