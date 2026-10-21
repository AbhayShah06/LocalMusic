package com.abhayshah.localmusic;
import java.text.Normalizer;
import java.util.Locale;

/** Conservative recording matching: decoration may differ, musical versions may not. */
final class RecordingIdentity {
    static String artist(String artist){return words(artist.replaceAll("(?i)\\s*-\\s*topic$|vevo$","").split("(?i), | feat\\.? | ft\\.? ",2)[0]);}
    static String title(String title,String artist){
        String value=title.replaceAll("(?i)[\\[(](?:official\\s+)?(?:audio|music video|video|lyrics?|explicit|visuali[sz]er)(?:\\s+version)?[\\])]","")
                .replaceAll("(?i)\\b(?:official audio|official music video|official video|lyrics video)\\b","").trim();
        String[] split=value.split("\\s[-–—]\\s",2);if(split.length==2&&artist(split[0]).equals(artist(artist)))value=split[1];
        value=value.replaceAll("(?i)\\s+[\\[(]?(?:feat\\.?|ft\\.?)\\s+.*$","").trim();
        return words(value);
    }
    static boolean same(String title,String artist,String otherTitle,String otherArtist){
        return !VersionPolicy.isEditedVersion(otherTitle)&&!VersionPolicy.isMusicVideo(otherTitle)
            &&!artist(artist).isEmpty()&&artist(artist).equals(artist(otherArtist))&&title(title,artist).equals(title(otherTitle,otherArtist));
    }
    static String key(String title,String artist){return artist(artist)+"|"+title(title,artist);}
    private static String words(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll("\\s+"," ");}
    private RecordingIdentity(){}
}
