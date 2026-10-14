package com.abhayshah.localmusic;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class TrackTest {
    @Test public void missingMetadataUsesReadableFallbacks() {
        assertEquals("Unknown title", Track.clean(null, "Unknown title"));
        assertEquals("Unknown artist", Track.clean("", "Unknown artist"));
        assertEquals("Unknown album", Track.clean("   ", "Unknown album"));
    }

    @Test public void preservesProvidedMetadata() {
        assertEquals("Song", Track.clean("Song", "Unknown title"));
        assertEquals("Artist", Track.clean("Artist", "Unknown artist"));
        assertEquals("Album", Track.clean("Album", "Unknown album"));
    }
}
