package com.abhayshah.localmusic;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

final class PlaylistImporter {
    interface Progress { boolean update(int done,int total); }
    static final class Result {
        final String name; final List<Track> tracks; final int missed;
        Result(String name,List<Track> tracks,int missed) {this.name=name;this.tracks=tracks;this.missed=missed;}
    }
    private final ContentResolver resolver;
    PlaylistImporter(ContentResolver resolver) {this.resolver=resolver;}
    Result importFile(Uri uri,Progress progress) throws Exception {
        String name="Imported playlist";
        try(Cursor c=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null && c.moveToFirst())name=c.getString(0).replaceFirst("\\.[^.]+$", "");
        }
        try(InputStream input=resolver.openInputStream(uri);ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            if(input==null)throw new IllegalArgumentException("Could not open this file.");
            byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1) { if(output.size()+count>2_000_000)throw new IllegalArgumentException("Playlist file is too large.");output.write(buffer,0,count); }
            return importText(name,output.toString(StandardCharsets.UTF_8.name()),progress);
        }
    }
    Result importText(String name,String text,Progress progress) throws Exception {
        List<PlaylistText.Song> wanted=PlaylistText.parse(text); Map<String,List<Track>> library=localLibrary();
        List<Track> found=new ArrayList<>();Set<String> seen=new HashSet<>();int missed=0,done=0;
        OnlineCatalogSource online=new OnlineCatalogSource();
        for(PlaylistText.Song song:wanted) {
            if(!progress.update(done,wanted.size()))throw new java.util.concurrent.CancellationException();
            Track best=match(song,library.getOrDefault(normalize(song.title),Collections.emptyList()));
            if(best==null) try {best=match(song,online.page(song.artist+" "+song.title,0,8));}catch(RuntimeException ignored){}
            if(best==null)missed++;else if(seen.add(best.stableId))found.add(best);done++;
        }
        progress.update(done,wanted.size());return new Result(name,found,missed);
    }
    private static Track match(PlaylistText.Song song,List<Track> candidates) {
        String title=normalize(song.title),artist=normalize(song.artist);
        for(Track candidate:candidates) {
            String actualTitle=normalize(candidate.title),actualArtist=normalize(candidate.artist);
            boolean titleMatches=actualTitle.equals(title)||actualTitle.equals(artist+" "+title);
            boolean artistMatches=artist.isEmpty()||actualArtist.equals(artist)||actualArtist.contains(artist)||artist.contains(actualArtist);
            if(titleMatches && artistMatches)return candidate;
        }
        return null;
    }
    private Map<String,List<Track>> localLibrary() {
        Map<String,List<Track>> result=new HashMap<>();
        String[] cols={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.ALBUM_ID};
        try(Cursor c=resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cols,MediaStore.Audio.Media.IS_MUSIC+" != 0",null,null)) {
            if(c!=null)while(c.moveToNext()) {Track track=new Track(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4));result.computeIfAbsent(normalize(track.title),k->new ArrayList<>()).add(track);}
        }catch(SecurityException ignored) { /* Online matching works without library permission. */ }
        return result;
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
            .replaceAll("\\([^)]*\\)|\\[[^]]*]", " ").replaceAll("(?i)\\b(official|audio|video|lyrics)\\b", " ").replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
}
