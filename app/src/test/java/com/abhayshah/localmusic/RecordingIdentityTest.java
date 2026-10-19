package com.abhayshah.localmusic;
import org.junit.Test;import static org.junit.Assert.*;
public class RecordingIdentityTest {
    @Test public void alternateUploadOfSameRecordingMatches(){assertTrue(RecordingIdentity.same("Summer (Official Audio)","Calvin Harris - Topic","Calvin Harris - Summer","Calvin Harris"));}
    @Test public void differentVersionsDoNotReplaceOriginal(){for(String title:new String[]{"Summer (Live)","Summer (Remix)","Summer (Acoustic)","Summer (Clean)","Summer Censored Edition"})assertFalse(title,RecordingIdentity.same("Summer","Calvin Harris",title,"Calvin Harris"));}
    @Test public void differentArtistAndSimilarTitleDoNotMatch(){assertFalse(RecordingIdentity.same("Summer","Calvin Harris","Summer","Other Artist"));assertFalse(RecordingIdentity.same("Summer","Calvin Harris","Summer Nights","Calvin Harris"));}
    @Test public void videoLabelsAreFilteredButOrdinarySongNamesSurvive(){assertTrue(VersionPolicy.isMusicVideo("Summer (Official Video)"));assertTrue(VersionPolicy.isMusicVideo("Summer (Official Music Video)"));assertFalse(VersionPolicy.isMusicVideo("Video Games"));assertFalse(VersionPolicy.isEditedVersion("The Less I Know The Better"));assertTrue(VersionPolicy.isEditedVersion("Flashing Lights Censored Edition"));}
}
