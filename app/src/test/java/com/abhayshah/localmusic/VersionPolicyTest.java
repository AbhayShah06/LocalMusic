package com.abhayshah.localmusic;
import org.junit.Test;
import static org.junit.Assert.*;
public class VersionPolicyTest {
    @Test public void excludesLabelledEdits(){for(String title:new String[]{"Going Bad (Clean)","Going Bad [Censored]","How Deep Is Your Love (Radio Edit)","Song - Clean","Song Clean Version"})assertTrue(title,VersionPolicy.exclude(title,"Going Bad"));}
    @Test public void ordinaryWordsAndArtistNamesArePreserved(){for(String title:new String[]{"Clean Bandit - Rockabye","Clean","Keep It Clean","Radio","Cleanin' Out My Closet"})assertFalse(title,VersionPolicy.exclude(title,"music"));}
    @Test public void deliberateVersionSearchIsRespected(){assertFalse(VersionPolicy.exclude("Song (Clean)","Song clean version"));assertTrue(VersionPolicy.exclude("Rockabye (Clean)","Clean Bandit Rockabye"));}
    @Test public void unlabelledAudioIsNotFalselyCertified(){assertFalse(VersionPolicy.isEditedVersion("Song (Official Audio)"));assertFalse(VersionPolicy.isEditedVersion("Song (Explicit)"));}
}
