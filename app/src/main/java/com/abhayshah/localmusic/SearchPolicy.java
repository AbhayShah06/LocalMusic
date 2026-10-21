package com.abhayshah.localmusic;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Rank recordings without treating substrings inside song or artist names as version labels. */
final class SearchPolicy {
    private static final String[] VARIANTS={"cover","karaoke","instrumental","remix","nightcore","slowed","sped up","8d audio","reaction","interview","podcast","documentary","trailer","review","explained"};
    static boolean unwanted(String title,String query,boolean avoidClean){
        for(String term:VARIANTS)if(has(title,term)&&!has(query,term)){
            if(term.equals("cover")||term.equals("remix")||term.equals("instrumental")){
                if(Pattern.compile("(?i)(?:[\\[(].*\\b"+term+"\\b|\\b"+term+"\\s+(?:version|mix|edit|by)\\b|\\b"+term+"\\s*$)").matcher(title).find())return true;
            }else return true;
        }
        if(Pattern.compile("(?i)[\\[(]\\s*live(?:\\s|[\\])])|\\blive (?:at|on|from|in)\\b").matcher(title).find()&&!has(query,"live"))return true;
        return VersionPolicy.isMusicVideo(title)||avoidClean&&VersionPolicy.exclude(title,query);
    }
    private static boolean has(String text,String term){return (" "+words(text)+" ").contains(" "+term+" ");}
    static int rank(String title,String artist,String query){
        Set<String> wanted=new HashSet<>(Arrays.asList(words(query).split(" ")));
        Set<String> candidate=new HashSet<>(Arrays.asList(words(title+" "+artist).split(" ")));
        int matches=0;for(String word:wanted)if(candidate.contains(word))matches++;
        int score=100*matches/Math.max(1,wanted.size());
        String song=RecordingIdentity.title(title,artist),q=words(query),a=RecordingIdentity.artist(artist);
        if(song.equals(q)||(!a.isEmpty()&&(song+" "+a).equals(q))||(!a.isEmpty()&&(a+" "+song).equals(q)))score+=45;
        if(!a.isEmpty()&&(" "+q+" ").contains(" "+a+" "))score+=25;
        if(title.toLowerCase(Locale.ROOT).contains("official audio")||artist.toLowerCase(Locale.ROOT).endsWith(" - topic"))score+=12;
        return score;
    }
    private static String words(String text){return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFKD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll("\\s+"," ");}
    private SearchPolicy(){}
}
