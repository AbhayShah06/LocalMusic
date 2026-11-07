package com.abhayshah.localmusic;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BlacklistedStoreTest {
    @Test public void versionsShareRecordingKeyButOtherSongsDoNot(){
        String original=BlacklistedStore.key("Post Malone - Circles (Official Audio)","Post Malone");
        assertEquals(original,BlacklistedStore.key("Circles (Live at Reading)","Post Malone - Topic"));
        assertEquals(original,BlacklistedStore.key("Circles Remix","Post Malone"));
        assertEquals(original,BlacklistedStore.key("Circles (Lyric Video)","Post Malone"));
        assertNotEquals(original,BlacklistedStore.key("Better Now","Post Malone"));
        assertNotEquals(original,BlacklistedStore.key("Circles","Other Artist"));
    }
}
