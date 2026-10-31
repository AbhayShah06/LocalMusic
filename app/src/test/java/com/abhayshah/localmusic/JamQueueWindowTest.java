package com.abhayshah.localmusic;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class JamQueueWindowTest {
    @Test public void keepsCurrentSongVisibleWithTwoHundredEntryLimit() {
        assertEquals(0, JamQueueWindow.offset(0, 200));
        assertEquals(0, JamQueueWindow.offset(199, 200));
        assertEquals(0, JamQueueWindow.offset(20, 240));
        assertEquals(30, JamQueueWindow.offset(50, 240));
        assertEquals(40, JamQueueWindow.offset(239, 240));
    }
}
