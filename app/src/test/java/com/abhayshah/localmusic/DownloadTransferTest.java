package com.abhayshah.localmusic;
import org.junit.Test;
import okhttp3.mockwebserver.*;
import okio.Buffer;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
public class DownloadTransferTest {
    interface TestBody { void run(MockWebServer server, File directory) throws Exception; }
    void server(TestBody test) throws Exception {
        MockWebServer server=new MockWebServer(); server.start();
        File folder=Files.createTempDirectory("localmusic-transfer-test").toFile();
        try { test.run(server,folder); }
        finally { server.shutdown(); for(File file:folder.listFiles())file.delete(); folder.delete(); }
    }
    MockResponse audio(byte[] bytes) { return new MockResponse().setHeader("Content-Type","audio/wav").setBody(new Buffer().write(bytes)); }
    @Test public void completeTransferIsVerified() throws Exception { server((s,d)->{
        byte[] bytes=new byte[130000];new Random(1).nextBytes(bytes);s.enqueue(audio(bytes));File file=new File(d,"audio.part");
        assertEquals(bytes.length,DownloadTransfer.copy(s.url("/audio").url(),file,(n,t)->{}));assertArrayEquals(bytes,Files.readAllBytes(file.toPath()));
    }); }
    @Test public void rejectsHtmlAndHttpErrors() throws Exception { server((s,d)->{
        s.enqueue(new MockResponse().setHeader("Content-Type","text/html").setBody("oops"));
        try {DownloadTransfer.copy(s.url("/audio").url(),new File(d,"audio.part"),(n,t)->{});fail();}
        catch(IOException expected){assertTrue(expected.getMessage().contains("instead of audio"));}
        s.enqueue(new MockResponse().setResponseCode(503));
        try {DownloadTransfer.copy(s.url("/audio").url(),new File(d,"error.part"),(n,t)->{});fail();}
        catch(IOException expected){assertTrue(expected.getMessage().contains("HTTP 503"));}
    }); }
    @Test public void interruptedTransferResumesWithValidator() throws Exception { server((s,d)->{
        byte[] bytes=new byte[80000];new Random(2).nextBytes(bytes);s.enqueue(audio(bytes).setHeader("ETag","test-audio"));File file=new File(d,"audio.part");
        try {DownloadTransfer.copy(s.url("/audio").url(),file,(n,t)->{if(n>16000)throw new InterruptedIOException("test connection drop");});fail();}
        catch(InterruptedIOException expected){} long offset=file.length();assertTrue(offset>0);s.takeRequest();
        s.enqueue(audio(Arrays.copyOfRange(bytes,(int)offset,bytes.length)).setResponseCode(206).setHeader("ETag","test-audio").setHeader("Content-Range","bytes "+offset+"-"+(bytes.length-1)+"/"+bytes.length));
        DownloadTransfer.copy(s.url("/audio").url(),file,(n,t)->{});RecordedRequest resumed=s.takeRequest();
        assertEquals("bytes="+offset+"-",resumed.getHeader("Range"));assertEquals("test-audio",resumed.getHeader("If-Range"));assertArrayEquals(bytes,Files.readAllBytes(file.toPath()));
    }); }
    @Test public void concurrentTracksDoNotOverwriteEachOther() throws Exception { server((s,d)->{
        byte[] one=new byte[64000],two=new byte[48000];Arrays.fill(one,(byte)9);Arrays.fill(two,(byte)4);
        s.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest request){return audio(request.getPath().equals("/one")?one:two);}});
        ExecutorService workers=Executors.newFixedThreadPool(2);try{
            Future<Long> a=workers.submit(()->DownloadTransfer.copy(s.url("/one").url(),new File(d,"one.part"),(n,t)->{}));
            Future<Long> b=workers.submit(()->DownloadTransfer.copy(s.url("/two").url(),new File(d,"two.part"),(n,t)->{}));
            assertEquals(Long.valueOf(one.length),a.get());assertEquals(Long.valueOf(two.length),b.get());
            assertArrayEquals(one,Files.readAllBytes(new File(d,"one.part").toPath()));assertArrayEquals(two,Files.readAllBytes(new File(d,"two.part").toPath()));
        }finally{workers.shutdownNow();}
    }); }
    @Test public void refusesWrongResumeOffset() throws Exception { server((s,d)->{
        File file=new File(d,"audio.part");Files.write(file.toPath(),new byte[]{1,2});Properties p=new Properties();p.setProperty("etag","test");try(OutputStream out=new FileOutputStream(file.getPath()+".httpmeta")){p.store(out,null);}
        s.enqueue(audio(new byte[]{3,4}).setResponseCode(206).setHeader("Content-Range","bytes 1-2/4"));
        try{DownloadTransfer.copy(s.url("/audio").url(),file,(n,t)->{});fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("Invalid partial"));}
        assertArrayEquals(new byte[]{1,2},Files.readAllBytes(file.toPath()));
    }); }
}
