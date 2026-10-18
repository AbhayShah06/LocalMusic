package com.abhayshah.localmusic;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
public class PlaylistTextTest {
    @Test public void spotifyCsvHandlesBomAndQuotedCommas() {
        List<PlaylistText.Song> songs=PlaylistText.parse("\uFEFFTrack URI,Track Name,Artist Name(s)\nspotify:track:1,\"Song, Part 2\",Artist");
        assertEquals("Song, Part 2",songs.get(0).title); assertEquals("Artist",songs.get(0).artist);
    }
    @Test public void appleTsvAndPastedLinesKeepOrder() {
        assertEquals("Artist",PlaylistText.parse("Name\tArtist\nSong\tArtist").get(0).artist);
        List<PlaylistText.Song> songs=PlaylistText.parse("1. First Artist - First Song\nSecond Artist - Second Song");
        assertEquals("First Song",songs.get(0).title);assertEquals("Second Artist",songs.get(1).artist);
    }
    @Test public void m3uUsesTrackLabels() {
        List<PlaylistText.Song> songs=PlaylistText.parse("#EXTM3U\n#EXTINF:230,Artist - Song\n/song.mp3");
        assertEquals("Song",songs.get(0).title);
    }
    @Test public void quotedNewlinesAndEscapedQuotesAreParsed() {
        assertEquals("A \"quoted\"\nsong",PlaylistText.parse("Title,Artist\n\"A \"\"quoted\"\"\nsong\",Singer").get(0).title);
    }
    @Test(expected=IllegalArgumentException.class) public void malformedCsvHasActionableError() { PlaylistText.parse("Title,Artist\n\"bad,Singer"); }
    @Test(expected=IllegalArgumentException.class) public void excessivePlaylistsAreRejectedRatherThanTruncated() { PlaylistText.parse("Artist - Song\n".repeat(501)); }
}
