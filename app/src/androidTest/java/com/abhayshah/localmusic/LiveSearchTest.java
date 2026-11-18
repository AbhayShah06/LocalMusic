package com.abhayshah.localmusic;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import androidx.test.core.app.ActivityScenario;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class LiveSearchTest {
    private Track find(String query,String song,String artist){
        List<Track> result=new OnlineCatalogSource().page(query,0,40);
        for(Track track:result)if(RecordingIdentity.title(track.title,track.artist).contains(song)&&RecordingIdentity.artist(track.artist).contains(artist)){android.util.Log.i("SearchQA",query+" → "+track.title+" / "+track.artist);return track;}
        StringBuilder diagnostic=new StringBuilder(query+" missing: ");for(Track t:result)diagnostic.append(t.title).append(" / ").append(t.artist).append("; ");throw new AssertionError(diagnostic.toString());
    }
    @Test public void drakeOriginalRecordingIsDiscoverable()throws Exception{
        Track track=find("One Dance Drake","one dance","drake"),playable=new OnlineCatalogSource().resolve(track);
        java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(playable.playbackUri.toString()).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("Range","bytes=0-1023");try{assertTrue(c.getResponseCode()==200||c.getResponseCode()==206);try(java.io.InputStream in=c.getInputStream()){assertTrue(in.read()!=-1);}}finally{c.disconnect();}
    }
    @Test public void khaiDreamsIsDiscoverable(){find("khai dreams ultimately","ultimately","khai dreams");}
    @Test public void hindiAndPunjabiCataloguesAreDiscoverable(){find("Arijit Singh Tum Hi Ho","tum hi ho","arijit");find("Diljit Dosanjh GOAT","g o a t","diljit");}
    @Test public void streamSearchFromHomeShowsResults()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            onView(withId(R.id.search)).perform(replaceText("I got a feeling black eyed peas"));
            FeatureRuntimeTest.await("Stream search did not show songs",()->{
                java.util.concurrent.atomic.AtomicBoolean found=new java.util.concurrent.atomic.AtomicBoolean();
                activity.onActivity(a->{android.widget.ListView list=a.findViewById(R.id.tracks);found.set(list.getVisibility()==android.view.View.VISIBLE&&list.getAdapter().getCount()>0);});
                return found.get();
            },20000);
        }
    }
}
