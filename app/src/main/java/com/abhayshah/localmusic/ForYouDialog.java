package com.abhayshah.localmusic;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.function.*;

/** Personal listening shortcuts; opening a section never changes playback. */
final class ForYouDialog {
    static void show(Activity activity,Consumer<Track> play,BiConsumer<Track,Boolean> mix,Runnable recent,Runnable favorites){
        Dialog dialog=new Dialog(activity);LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff0b0b0c);root.setPadding(dp(activity,20),dp(activity,20),dp(activity,20),dp(activity,12));
        TextView title=label(activity,"For You",26);root.addView(title);
        ScrollView scroll=new ScrollView(activity);LinearLayout sections=new LinearLayout(activity);sections.setOrientation(LinearLayout.VERTICAL);scroll.addView(sections);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        sections.addView(action(activity,"Favorites",()->{dialog.dismiss();favorites.run();}));
        List<Track> history=ListeningHistory.recent(activity,50);
        sections.addView(action(activity,"Recently played",()->{dialog.dismiss();recent.run();}));
        if(history.isEmpty())sections.addView(label(activity,"Your listening history will appear here.",15));
        for(Track track:history.subList(0,Math.min(5,history.size())))sections.addView(action(activity,track.title+"\n"+track.artist,()->{dialog.dismiss();play.accept(track);}));
        List<Track> seeds=new ArrayList<>(FavoriteStore.remoteTracks(activity));
        seeds.addAll(ListeningHistory.dailyMix(activity,50));Set<String> ids=new HashSet<>(),artists=new HashSet<>();
        List<Track> unique=new ArrayList<>();for(Track seed:seeds)if(ids.add(Track.originalId(seed.stableId)))unique.add(seed.original());
        if(!unique.isEmpty()){
            sections.addView(label(activity,"Song mixes",20));
            for(Track seed:unique.subList(0,Math.min(4,unique.size())))sections.addView(action(activity,seed.title+" Mix\n"+seed.artist,()->{dialog.dismiss();mix.accept(seed,false);}));
            sections.addView(label(activity,"Artist mixes",20));
            int count=0;for(Track seed:unique){String artist=seed.artist.replaceAll("(?i)\\s*-\\s*topic$","").trim();if(artist.equalsIgnoreCase("Unknown artist")||!artists.add(artist.toLowerCase(Locale.ROOT)))continue;
                sections.addView(action(activity,artist+" Mix",()->{dialog.dismiss();mix.accept(seed,true);}));if(++count==4)break;
            }
        }
        Button close=new Button(activity);close.setText("Close");close.setOnClickListener(v->dialog.dismiss());root.addView(close);dialog.setContentView(root);dialog.show();dialog.getWindow().setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.86f));
    }
    private static TextView label(Activity activity,String text,int size){TextView view=new TextView(activity);view.setText(text);view.setTextSize(size);view.setTextColor(0xfff6f1e8);view.setPadding(0,dp(activity,14),0,dp(activity,10));return view;}
    private static View action(Activity activity,String text,Runnable action){TextView view=label(activity,text,16);view.setBackgroundResource(android.R.drawable.list_selector_background);view.setOnClickListener(v->action.run());return view;}
    private static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private ForYouDialog(){}
}
