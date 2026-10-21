package com.abhayshah.localmusic;
import org.junit.Test;
import static org.junit.Assert.*;
public class SearchPolicyTest {
    @Test public void originalsBeatWrongArtistsAndUnrelatedSongs(){
        int original=SearchPolicy.rank("One Dance","Drake, Wizkid, Kyla","One Dance Drake");
        assertTrue(original>SearchPolicy.rank("One Dance","Dan Bern","One Dance Drake"));
        assertTrue(original>SearchPolicy.rank("Diamonds Dancing","Drake","One Dance Drake"));
    }
    @Test public void versionsAreFilteredAcrossEverySource(){
        assertTrue(SearchPolicy.unwanted("One Dance (Cover Version)","One Dance Drake",true));
        assertTrue(SearchPolicy.unwanted("Summer (Karaoke Version)","Calvin Harris Summer",true));
        assertFalse(SearchPolicy.unwanted("One Dance","Drake One Dance",true));
        assertFalse(SearchPolicy.unwanted("Live Forever","Live Forever",true));
        assertFalse(SearchPolicy.unwanted("Oliver's Army","Oliver's Army",true));
        assertFalse(SearchPolicy.unwanted("Summer (Remix)","Summer Remix",true));
        assertTrue(SearchPolicy.unwanted("Flashing Lights (Clean)","Flashing Lights",true));
        assertFalse(SearchPolicy.unwanted("Cover Me In Sunshine","P!nk",true));
        assertFalse(SearchPolicy.unwanted("तुम ही हो","अरिजीत सिंह",true));
        assertFalse(SearchPolicy.unwanted("ਮੇਰੇ ਵਾਲਾ ਸਰਦਾਰ","Jugraj Sandhu",true));
    }
}
