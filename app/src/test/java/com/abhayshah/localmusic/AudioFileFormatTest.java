package com.abhayshah.localmusic;
import org.junit.Test;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;
public class AudioFileFormatTest {
    private String detect(byte[] bytes)throws Exception{File f=File.createTempFile("audio-format",".bin");try{Files.write(f.toPath(),bytes);return AudioFileFormat.read(f).extension;}finally{f.delete();}}
    @Test public void realContainersGetRealExtensions()throws Exception{
        assertEquals("wav",detect("RIFF0000WAVEfmt ".getBytes()));assertEquals("m4a",detect("0000ftypM4A 0000".getBytes()));assertEquals("ogg",detect("OggS000000000000".getBytes()));assertEquals("mp3",detect("ID30000000000000".getBytes()));assertEquals("webm",detect(new byte[]{0x1a,0x45,(byte)0xdf,(byte)0xa3,0,0,0,0}));
    }
    @Test public void namesCannotEscapeMusicFolder(){String name=AudioFileFormat.filename("../Artist","a/b:c?","one","m4a");assertFalse(name.contains("/"));assertFalse(name.contains(":"));assertTrue(name.endsWith(".m4a"));assertNotEquals(name,AudioFileFormat.filename("../Artist","a/b:c?","two","m4a"));}
    @Test(expected=IOException.class) public void truncatedFileIsRejected()throws Exception{detect("RIFF".getBytes());}
    @Test public void featuredCreditsDoNotHideARecording(){assertEquals("one dance",RecordingIdentity.title("Drake - One Dance (Lyrics) ft. Wizkid & Kyla","Drake"));}
}
