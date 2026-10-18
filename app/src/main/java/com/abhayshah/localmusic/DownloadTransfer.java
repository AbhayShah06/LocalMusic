package com.abhayshah.localmusic;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.*;

/** Resumable transfer. A partial file is never published to the offline library. */
final class DownloadTransfer {
    interface Progress { void update(long bytes,long total) throws IOException; }
    static final long MAX_BYTES=512L*1024*1024;
    static long copy(URL url,File temporary,Progress progress)throws IOException {
        if(!url.getProtocol().equals("https")&&!url.getProtocol().equals("http"))throw new IOException("Unsupported download address");
        Properties saved=new Properties();File metadata=new File(temporary.getPath()+".httpmeta");
        if(metadata.exists())try(InputStream in=new FileInputStream(metadata)){saved.load(in);}catch(IOException ignored){}
        String validator=saved.getProperty("etag","");if(validator.startsWith("W/"))validator="";if(validator.isEmpty())validator=saved.getProperty("modified","");
        long offset=temporary.exists()&&!validator.isEmpty()?temporary.length():0;
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(12000);connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent","LocalMusic/0.12.6");connection.setRequestProperty("Accept-Encoding","identity");
        if(offset>0){connection.setRequestProperty("Range","bytes="+offset+"-");connection.setRequestProperty("If-Range",validator);}
        long deadline=System.nanoTime()+180_000_000_000L;
        try {
            int status=connection.getResponseCode();
            if(status!=200&&status!=206)throw new IOException("Server returned HTTP "+status);
            String type=connection.getContentType();if(type!=null&&(type.toLowerCase(Locale.ROOT).contains("text/html")||type.toLowerCase(Locale.ROOT).contains("application/json")))throw new IOException("Server returned a page instead of audio");
            boolean append=status==206&&offset>0;long total=connection.getContentLengthLong();
            if(status==206){
                Matcher range=Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)").matcher(String.valueOf(connection.getHeaderField("Content-Range")));
                if(!range.matches()||Long.parseLong(range.group(1))!=(append?offset:0))throw new IOException("Invalid partial download response");
                total=Long.parseLong(range.group(3));
            }else offset=0;
            if(total>MAX_BYTES)throw new IOException("Audio file exceeds the 512 MB download limit");
            if(total>0&&temporary.getParentFile().getUsableSpace()<Math.max(0,total-offset)+8L*1024*1024)throw new IOException("Not enough free storage");
            saved=new Properties();saved.setProperty("etag",String.valueOf(Optional.ofNullable(connection.getHeaderField("ETag")).orElse("")));saved.setProperty("modified",String.valueOf(Optional.ofNullable(connection.getHeaderField("Last-Modified")).orElse("")));
            try(OutputStream out=new FileOutputStream(metadata)){saved.store(out,null);}
            long done=offset;progress.update(done,total);
            try(InputStream input=connection.getInputStream();FileOutputStream file=new FileOutputStream(temporary,append)){
                byte[] buffer=new byte[64*1024];int count;
                while((count=input.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Download paused");
                    if(System.nanoTime()>deadline)throw new IOException("Connection too slow; download will resume later");
                    done+=count;if(done>MAX_BYTES)throw new IOException("Audio file exceeds the 512 MB download limit");
                    file.write(buffer,0,count);progress.update(done,total);
                }
                if(done==0)throw new IOException("Server returned an empty file");
                if(total>=0&&done!=total)throw new IOException("Incomplete download: "+done+" of "+total+" bytes");
                file.getFD().sync();
            }
            return done;
        }finally{connection.disconnect();}
    }
    private DownloadTransfer(){}
}
