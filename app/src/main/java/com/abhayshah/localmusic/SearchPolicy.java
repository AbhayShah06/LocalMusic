package com.abhayshah.localmusic;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Rank recordings without treating substrings inside song or artist names as version labels. */
final class SearchPolicy {
    private static final String[] VARIANTS={"cover","karaoke","instrumental","remix","nightcore","slowed","sped up","8d audio","reaction","interview","podcast","documentary","trailer","review","explained"};
    static boolean unwanted(String title,String query,boolean avoidClean){
        if(Pattern.compile("(?i)(?:#shorts\\b|\\byoutube shorts\\b)").matcher(title).find())return true;
        for(String term:VARIANTS)if(has(title,term)&&!has(query,term)){
            if(term.equals("cover")||term.equals("remix")||term.equals("instrumental")){
                if(Pattern.compile("(?i)(?:[\\[(].*\\b"+term+"\\b|\\b"+term+"\\s+(?:version|mix|edit|by)\\b|\\b"+term+"\\s*$)").matcher(title).find())return true;
            }else return true;
        }
        if(Pattern.compile("(?i)[\\[(]\\s*live(?:\\s|[\\])])|\\blive (?:at|on|from|in)\\b").matcher(title).find()&&!has(query,"live"))return true;
        // An official video may be the only available original recording. Rank audio
        // ahead of it, but keep the exact song searchable and playable as audio.
        return avoidClean&&VersionPolicy.exclude(title,query);
    }
    private static boolean has(String text,String term){return (" "+words(text)+" ").contains(" "+term+" ");}
    static int rank(String title,String artist,String query){
        String q=words(query).replaceAll("\\band\\b","").trim().replaceAll("\\s+"," ");
        Set<String> wanted=new HashSet<>(Arrays.asList(q.split(" ")));
        Set<String> candidate=new HashSet<>(Arrays.asList(words(title+" "+artist).split(" ")));
        int matches=0;for(String word:wanted)if(candidate.contains(word))matches++;
        int score=100*matches/Math.max(1,wanted.size());
        String song=RecordingIdentity.title(title,artist).replaceAll("\\band\\b","").trim().replaceAll("\\s+"," "),a=RecordingIdentity.artist(artist);
        if(song.equals(q)||(!a.isEmpty()&&(song+" "+a).equals(q))||(!a.isEmpty()&&(a+" "+song).equals(q)))score+=80;
        else if(song.endsWith(" "+q))score+=50;
        if(!a.isEmpty()&&(" "+q+" ").contains(" "+a+" "))score+=25;
        if(title.toLowerCase(Locale.ROOT).contains("official audio")||artist.toLowerCase(Locale.ROOT).endsWith(" - topic"))score+=20;
        if(VersionPolicy.isMusicVideo(title))score-=15;
        return score;
    }
    private static String words(String text){return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFKD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("\\b(i['’]?m|ima)\\b","ima").replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll("\\s+"," ").replaceAll("\\bima a\\b","ima");}
    private SearchPolicy(){}
}
