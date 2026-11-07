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
    @Test public void radioInterleavesArtistsInsteadOfRepeatingTheSeedArtist(){
        java.util.List<Track> input=new java.util.ArrayList<>();
        input.add(new Track("a",null,"A","Rick Ross",""));input.add(new Track("b",null,"B","Rick Ross",""));
        input.add(new Track("c",null,"C","Meek Mill",""));input.add(new Track("d",null,"D","Drake",""));
        java.util.List<Track> output=OnlineCatalogSource.diverseMix(input,4,"rick ross");
        assertEquals("Meek Mill",output.get(0).artist);
        assertEquals("Drake",output.get(1).artist);
        assertEquals("Rick Ross",output.get(2).artist);
    }
    @Test public void musicVideoAndLyricUploadsAreNotAutomaticPicks(){
        assertTrue(VersionPolicy.isMusicVideo("Rather Be (Official Music Video)"));
        assertTrue(VersionPolicy.isMusicVideo("Rather Be (Lyrics)"));
        assertTrue(VersionPolicy.isMusicVideo("Rather Be Lyric Video"));
        assertFalse(VersionPolicy.isMusicVideo("Rather Be (Official Audio)"));
    }
    @Test public void varietyMovesRecentlyQueuedRecordingsBehindFreshMatches(){
        java.util.List<Track> input=new java.util.ArrayList<>();
        input.add(new Track("old",null,"Old Song","Artist One",""));
        input.add(new Track("fresh",null,"Fresh Song","Artist Two",""));
        input.add(new Track("another",null,"Another Song","Artist Three",""));
        java.util.Set<String> recent=java.util.Collections.singleton(RecordingIdentity.key("Old Song","Artist One"));
        java.util.List<Track> output=OnlineCatalogSource.diverseMix(input,3,"",recent,123);
        assertFalse("old".equals(output.get(0).stableId));
        assertEquals(3,output.size());
    }
    @Test public void exactRequestedRecordingBeatsUnrelatedTitles(){
        assertTrue(SearchPolicy.rank("Rather Be (feat. Jess Glynne)","Clean Bandit","Rather Be")
                >SearchPolicy.rank("Rather Be Rich","Lily","Rather Be"));
        assertTrue(SearchPolicy.rank("SHAQ & KOBE","Rick Ross, Meek Mill","Shaq and Kobe")
                >SearchPolicy.rank("Kobe and Shaq Freestyle","Unknown","Shaq and Kobe"));
    }
}
