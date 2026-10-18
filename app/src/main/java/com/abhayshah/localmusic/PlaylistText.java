package com.abhayshah.localmusic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure playlist parsing: CSV/TSV exports, M3U and pasted Artist - Title lines. */
final class PlaylistText {
    static final int MAX_TRACKS = 500;
    static final class Song {
        final String title, artist;
        Song(String title, String artist) { this.title = title.trim(); this.artist = artist.trim(); }
    }
    static List<Song> parse(String input) {
        String text = input.replace("\uFEFF", "").trim();
        if (text.length() > 2_000_000) throw new IllegalArgumentException("Playlist is too large. Import up to 500 songs at a time.");
        List<Song> songs = new ArrayList<>();
        if (text.startsWith("#EXTM3U") || text.contains("#EXTINF:")) {
            String pending = "";
            for (String raw : text.split("\\R")) {
                String line = raw.trim();
                if (line.startsWith("#EXTINF:")) { int comma = line.indexOf(','); pending = comma < 0 ? "" : line.substring(comma + 1); }
                else if (!line.isEmpty() && !line.startsWith("#")) {
                    String name = pending.isEmpty() ? line.replace('\\','/').replaceAll(".*/", "").replaceAll("\\.[^.]+$", "") : pending;
                    addLine(songs, name); pending = "";
                }
            }
        } else {
            String first = text.split("\\R", 2)[0];
            char delimiter = first.indexOf('\t') >= 0 ? '\t' : first.indexOf(';') >= 0 && first.indexOf(',') < 0 ? ';' : ',';
            List<List<String>> rows = rows(text, delimiter);
            int title = rows.isEmpty() ? -1 : column(rows.get(0), "trackname", "songname", "track", "title", "name");
            int artist = rows.isEmpty() ? -1 : column(rows.get(0), "artistnames", "artistname", "artists", "artist", "albumartist");
            if (title >= 0) {
                for (int i=1; i<rows.size(); i++) {
                    List<String> row=rows.get(i);
                    if (title<row.size() && !row.get(title).trim().isEmpty()) songs.add(new Song(row.get(title), artist>=0 && artist<row.size()?row.get(artist):""));
                }
            } else {
                if (first.contains(",") && first.toLowerCase(Locale.ROOT).contains("uri")) throw new IllegalArgumentException("This export has no song titles. Include Track Name and Artist columns.");
                for(String line:text.split("\\R")) if(!line.trim().isEmpty()) addLine(songs,line);
            }
        }
        if(songs.isEmpty()) throw new IllegalArgumentException("No tracks found. Paste Artist - Song lines or choose a CSV/M3U export.");
        if(songs.size()>MAX_TRACKS) throw new IllegalArgumentException("Import up to 500 songs at a time; this list has "+songs.size()+".");
        return songs;
    }
    private static void addLine(List<Song> songs,String line) {
        String value=line.trim().replaceFirst("^\\d+[.)]\\s+", "");
        int split=value.indexOf(" - ");
        songs.add(split>0 ? new Song(value.substring(split+3),value.substring(0,split)) : new Song(value,""));
    }
    private static int column(List<String> headers,String... names) {
        for(int i=0;i<headers.size();i++) {
            String header=headers.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            for(String name:names) if(header.equals(name)) return i;
        }
        return -1;
    }
    private static List<List<String>> rows(String text,char delimiter) {
        List<List<String>> rows=new ArrayList<>(); List<String> row=new ArrayList<>(); StringBuilder value=new StringBuilder(); boolean quoted=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c=='"') { if(quoted && i+1<text.length() && text.charAt(i+1)=='"') {value.append('"');i++;} else quoted=!quoted; }
            else if(!quoted && c==delimiter) {row.add(value.toString().trim());value.setLength(0);}
            else if(!quoted && (c=='\r'||c=='\n')) { if(c=='\r' && i+1<text.length() && text.charAt(i+1)=='\n')i++; row.add(value.toString().trim());value.setLength(0);rows.add(row);row=new ArrayList<>(); }
            else value.append(c);
        }
        if(quoted)throw new IllegalArgumentException("This CSV has an unfinished quoted field.");
        row.add(value.toString().trim()); rows.add(row);return rows;
    }
}
