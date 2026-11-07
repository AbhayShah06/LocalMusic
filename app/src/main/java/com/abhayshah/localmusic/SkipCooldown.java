package com.abhayshah.localmusic;

import java.util.List;

/** Repeated skips cool down a recording for two weeks; a completed listen resets it. */
final class SkipCooldown {
    static final long DAY=86_400_000L;
    static boolean suppresses(List<Signal> newestFirst,String song,long now){
        if(song.equals("|"))return false;
        int skips=0;
        for(Signal event:newestFirst){
            if(!event.song.equals(song)||event.at>now||now-event.at>14*DAY)continue;
            if(!event.skip)return false;
            if(++skips>=2)return true;
        }
        return false;
    }
    static final class Signal {
        final String artist,song;final boolean skip;final long at;
        Signal(String artist,String song,boolean skip,long at){this.artist=artist;this.song=song;this.skip=skip;this.at=at;}
    }
    private SkipCooldown(){}
}
