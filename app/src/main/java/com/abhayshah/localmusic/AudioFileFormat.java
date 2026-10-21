package com.abhayshah.localmusic;
import java.io.*;
import java.nio.charset.StandardCharsets;
final class AudioFileFormat {
    final String extension,mime;
    AudioFileFormat(String extension,String mime){this.extension=extension;this.mime=mime;}
    static AudioFileFormat read(File file)throws IOException{
        byte[] h=new byte[16];int n;try(InputStream in=new FileInputStream(file)){n=in.read(h);}if(n<4)throw new IOException("Audio file is incomplete");
        String magic=new String(h,0,Math.max(0,n),StandardCharsets.ISO_8859_1);
        if(n>=12&&magic.startsWith("RIFF")&&magic.substring(8).startsWith("WAVE"))return new AudioFileFormat("wav","audio/wav");
        if(n>=8&&magic.substring(4).startsWith("ftyp"))return new AudioFileFormat("m4a","audio/mp4");
        if(magic.startsWith("OggS"))return new AudioFileFormat("ogg","audio/ogg");
        if(magic.startsWith("fLaC"))return new AudioFileFormat("flac","audio/flac");
        if((h[0]&255)==0x1a&&(h[1]&255)==0x45&&(h[2]&255)==0xdf&&(h[3]&255)==0xa3)return new AudioFileFormat("webm","audio/webm");
        if(magic.startsWith("ID3"))return new AudioFileFormat("mp3","audio/mpeg");
        if((h[0]&255)==255&&(h[1]&0xf6)==0xf0)return new AudioFileFormat("aac","audio/aac");
        if((h[0]&255)==255&&(h[1]&0xe0)==0xe0)return new AudioFileFormat("mp3","audio/mpeg");
        throw new IOException("Couldn't identify the audio format; private download kept");
    }
    static String filename(String artist,String title,String id,String extension){
        String name=(artist+" - "+title).replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]","_").replaceFirst("^\\.+","").trim();
        if(name.length()>110)name=name.substring(0,110);if(name.isEmpty())name="LocalMusic";
        if(Character.isHighSurrogate(name.charAt(name.length()-1)))name=name.substring(0,name.length()-1);
        try{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));StringBuilder suffix=new StringBuilder();for(int i=0;i<8;i++)suffix.append(String.format(java.util.Locale.ROOT,"%02x",digest[i]));return name+" - "+suffix+"."+extension;}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
