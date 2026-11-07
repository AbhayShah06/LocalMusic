package com.abhayshah.localmusic;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AvoidedStoreTest {
    @Test public void rejectingWrongVideoDoesNotHideAnotherUploadOfSameRecording(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Track video=new Track("online:0:video-avoid-test",Uri.EMPTY,"Unforgettable","French Montana","");
        Track audio=new Track("online:0:audio-avoid-test",Uri.EMPTY,"Unforgettable","French Montana","");
        if(AvoidedStore.contains(context,video))AvoidedStore.toggle(context,video);
        try{assertTrue(AvoidedStore.toggle(context,video));assertTrue(AvoidedStore.contains(context,video));assertFalse(AvoidedStore.contains(context,audio));}
        finally{if(AvoidedStore.contains(context,video))AvoidedStore.toggle(context,video);}
    }
    @Test public void avoidingSongHidesOtherVersionsUntilAllowed(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Track original=new Track("online:0:recording-avoid-test",Uri.EMPTY,"Test Recording (Official Audio)","Test Artist","");
        Track version=new Track("online:0:recording-version-test",Uri.EMPTY,"Test Recording (Live)","Test Artist","");
        if(BlacklistedStore.contains(context,original))BlacklistedStore.toggle(context,original);
        try{
            assertTrue(BlacklistedStore.toggle(context,original));
            assertTrue(BlacklistedStore.contains(context,version));
            assertFalse(AvoidedStore.contains(context,version));
            assertFalse(BlacklistedStore.toggle(context,version));
            assertFalse(BlacklistedStore.contains(context,original));
        }finally{if(BlacklistedStore.contains(context,original))BlacklistedStore.toggle(context,original);}
    }
}
