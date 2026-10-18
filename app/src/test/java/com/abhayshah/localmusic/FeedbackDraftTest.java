package com.abhayshah.localmusic;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;
public class FeedbackDraftTest {
    @Test public void draftUsesDeviceDateAndVersionWithoutSendingAnything(){
        String draft=FeedbackDraft.body("0.12.6",Instant.parse("2026-10-18T04:15:00Z"),ZoneId.of("America/Toronto"));
        assertTrue(draft.contains("2026-10-18 at 00:15"));assertTrue(draft.contains("App version: 0.12.6"));assertTrue(draft.contains("[Describe the error]"));assertTrue(draft.contains("[Steps to reproduce]"));
        assertEquals("abhay.shah270@gmail.com",FeedbackDraft.EMAIL);
    }
}
