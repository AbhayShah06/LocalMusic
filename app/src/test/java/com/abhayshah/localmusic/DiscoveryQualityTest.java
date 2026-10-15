package com.abhayshah.localmusic;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DiscoveryQualityTest {
    @Test public void alternateUploadsCollapseToOneIdentity() {
        String seed = OnlineCatalogSource.identity("Shree Hanuman Chalisa with Lyrics and English Translation");
        String duplicate = OnlineCatalogSource.identity("Hanuman Chalisa (Official Video) [HD]");
        assertEquals("shree hanuman chalisa", seed);
        assertEquals("hanuman chalisa", duplicate);
        assertTrue(OnlineCatalogSource.tooSimilar(seed, duplicate));
    }

    @Test public void genuinelyDifferentSongsRemainEligible() {
        String seed = OnlineCatalogSource.identity("Going Bad - Meek Mill ft. Drake (Official Audio)");
        String next = OnlineCatalogSource.identity("Nonstop - Drake (Official Audio)");
        assertFalse(OnlineCatalogSource.tooSimilar(seed, next));
    }

    @Test public void videoDecorationIsRemovedBeforeLyricsLookup() {
        assertEquals("Shree hanuman chalisa", LyricsClient.cleanTitle("Shree hanuman chalisa with lyrics and english translation"));
    }
}
