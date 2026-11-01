package com.abhayshah.localmusic;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class TrackSelectionTest {
    @Test public void selectionDoesNotActOnAReplacedQueueSlot(){
        TrackSelection selection=new TrackSelection();List<String> queue=Arrays.asList("first","second","third");
        selection.toggle(1,queue.get(1));selection.toggle(2,queue.get(2));
        assertEquals(Arrays.asList(1,2),selection.valid(queue.size(),queue::get));
        List<String> changed=Arrays.asList("first","replacement","third");
        assertEquals(Arrays.asList(2),selection.valid(changed.size(),changed::get));
        selection.toggle(2,"third");assertTrue(selection.active());assertEquals(0,selection.size());
        selection.clear();assertFalse(selection.active());
    }
}
