package com.abhayshah.localmusic;

import java.util.*;
import java.util.regex.*;

final class TimedLyrics {
    final long timeMs;final String text;
    TimedLyrics(long timeMs,String text){this.timeMs=timeMs;this.text=text;}
    private static final Pattern TIME=Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    static List<TimedLyrics> parse(String raw){
        List<TimedLyrics> result=new ArrayList<>();if(raw==null)return result;
        long offset=0;Matcher adjustment=Pattern.compile("(?im)\\[offset:([+-]?\\d{1,7})]").matcher(raw);if(adjustment.find())offset=Long.parseLong(adjustment.group(1));
        for(String line:raw.split("\\r?\\n")){
            Matcher matcher=TIME.matcher(line);List<Long> times=new ArrayList<>();int end=0;
            while(matcher.find()){int second=Integer.parseInt(matcher.group(2));if(second>=60)continue;String fraction=matcher.group(3);long ms=fraction==null?0:Long.parseLong((fraction+"00").substring(0,3));times.add(Math.max(0,Integer.parseInt(matcher.group(1))*60000L+second*1000L+ms-offset));end=matcher.end();}
            if(times.isEmpty())continue;String text=line.substring(end).trim();for(long time:times)result.add(new TimedLyrics(time,text));
        }
        result.sort(Comparator.comparingLong(line->line.timeMs));return result;
    }
    static int active(List<TimedLyrics> lines,long position){
        int low=0,high=lines.size()-1,result=-1;while(low<=high){int mid=(low+high)>>>1;if(lines.get(mid).timeMs<=position){result=mid;low=mid+1;}else high=mid-1;}return result;
    }
}
