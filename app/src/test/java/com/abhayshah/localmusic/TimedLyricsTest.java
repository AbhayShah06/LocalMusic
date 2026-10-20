package com.abhayshah.localmusic;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class TimedLyricsTest {
    @Test public void timestampsSortAndSeekBothDirections(){List<TimedLyrics> lines=TimedLyrics.parse("[00:10.50]Chorus\n[00:01.2]Verse\n[00:20.123]End");assertEquals(1200,lines.get(0).timeMs);assertEquals(-1,TimedLyrics.active(lines,100));assertEquals(1,TimedLyrics.active(lines,15000));assertEquals(0,TimedLyrics.active(lines,2000));assertEquals(2,TimedLyrics.active(lines,99999));}
    @Test public void multipleTimestampsOffsetAndMalformedInput(){List<TimedLyrics> lines=TimedLyrics.parse("[offset:200]\n[00:01][00:05.00]Repeat\n[00:99]Bad\n[ar:Artist]");assertEquals(2,lines.size());assertEquals(800,lines.get(0).timeMs);assertEquals(4800,lines.get(1).timeMs);assertTrue(TimedLyrics.parse("Just plain lyrics").isEmpty());assertTrue(TimedLyrics.parse(null).isEmpty());}
}
