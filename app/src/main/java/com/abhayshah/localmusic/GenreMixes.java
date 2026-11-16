package com.abhayshah.localmusic;

import java.util.List;

/** Shared genre anchors for home artwork, starting queues, and radio refills. */
final class GenreMixes {
    static String[] queries(String genre) {
        switch (genre) {
            case "High energy": return new String[]{"Dua Lipa dance official audio", "The Weeknd dance official audio", "Calvin Harris dance official audio"};
            case "Rock": return new String[]{"Foo Fighters rock official audio", "The Killers rock official audio", "The Strokes rock official audio"};
            case "Workout": return new String[]{"Eminem workout official audio", "Kanye West workout official audio", "Travis Scott workout official audio"};
            case "Psychedelic rock": return new String[]{"Tame Impala psychedelic rock official audio", "MGMT psychedelic rock official audio", "Pond psychedelic rock official audio"};
            case "Hip-hop": return new String[]{"Kendrick Lamar hip hop official audio", "Drake hip hop official audio", "J Cole hip hop official audio"};
            default: return new String[]{"Frank Ocean chill official audio", "SZA chill official audio", "Daniel Caesar chill official audio"};
        }
    }
    static Track cover(String genre, List<Track> history) {
        String[] artists;
        switch (genre) {
            case "High energy": artists=new String[]{"dua lipa","the weeknd","calvin harris"};break;
            case "Rock": artists=new String[]{"foo fighters","the killers","the strokes"};break;
            case "Workout": artists=new String[]{"eminem","kanye west","travis scott"};break;
            case "Psychedelic rock": artists=new String[]{"tame impala","mgmt","pond"};break;
            case "Hip-hop": artists=new String[]{"kendrick lamar","drake","j cole"};break;
            default: artists=new String[]{"frank ocean","sza","daniel caesar"};
        }
        for (Track track:history) {
            String artist=RecordingIdentity.artist(track.artist);
            for (String anchor:artists) if (artist.equals(anchor) && track.artworkUri!=null) return track;
        }
        return null;
    }
    private GenreMixes() {}
}
