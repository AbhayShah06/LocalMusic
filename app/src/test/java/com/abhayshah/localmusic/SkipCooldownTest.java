package com.abhayshah.localmusic;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class SkipCooldownTest {
    private static final String SONG="drake|passionfruit";
    private static SkipCooldown.Signal signal(String song,boolean skip,long at){return new SkipCooldown.Signal("drake",song,skip,at);}
    @Test public void repeatedSkipsTemporarilySuppressOnlyThatRecording(){
        long now=100*SkipCooldown.DAY;
        List<SkipCooldown.Signal> events=Arrays.asList(signal(SONG,true,now-1_000),signal("drake|other",true,now-2_000),signal(SONG,true,now-SkipCooldown.DAY));
        assertTrue(SkipCooldown.suppresses(events,SONG,now));
        assertFalse(SkipCooldown.suppresses(events,"drake|other",now));
        assertFalse(SkipCooldown.suppresses(events,"drake|passionfruit live",now));
        assertFalse(SkipCooldown.suppresses(events,SONG,now+15*SkipCooldown.DAY));
    }
    @Test public void oneSkipOrACompletedListenDoesNotSuppress(){
        long now=100*SkipCooldown.DAY;
        assertFalse(SkipCooldown.suppresses(Arrays.asList(signal(SONG,true,now-1_000)),SONG,now));
        assertFalse(SkipCooldown.suppresses(Arrays.asList(signal(SONG,false,now-1_000),signal(SONG,true,now-2_000),signal(SONG,true,now-3_000)),SONG,now));
        assertFalse(SkipCooldown.suppresses(Arrays.asList(signal(SONG,true,now-1_000),signal(SONG,false,now-2_000),signal(SONG,true,now-3_000)),SONG,now));
    }
}
