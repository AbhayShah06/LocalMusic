package com.abhayshah.localmusic;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
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
}
