package com.abhayshah.localmusic;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
final class FeedbackDraft {
    static final String EMAIL="abhay.shah270@gmail.com";
    static String subject(String version){return "LocalMusic feedback — "+version;}
    static String body(String version,Instant time,ZoneId zone){
        String when=DateTimeFormatter.ofPattern("yyyy-MM-dd 'at' HH:mm z",Locale.getDefault()).withZone(zone).format(time);
        return "I was using LocalMusic on "+when+".\n\nWhat went wrong:\n[Describe the error]\n\nWhat I was doing:\n[Steps to reproduce]\n\nWhat I expected:\n[Expected behavior]\n\nApp version: "+version+"\n";
    }
    private FeedbackDraft(){}
}
