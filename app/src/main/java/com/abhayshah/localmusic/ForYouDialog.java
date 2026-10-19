package com.abhayshah.localmusic;

import android.app.Activity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.function.*;

/** Personal listening shortcuts; opening a section never changes playback. */
final class ForYouDialog {
    static View create(Activity activity,Consumer<Track> play,BiConsumer<Track,Boolean> mix,Runnable recent,Runnable favorites,Runnable refresh,long refreshSeed){
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff0b0b0c);root.setPadding(dp(activity,20),dp(activity,20),dp(activity,20),dp(activity,12));
        TextView title=label(activity,"For You",26);root.addView(title);
        ScrollView scroll=new ScrollView(activity);LinearLayout sections=new LinearLayout(activity);sections.setOrientation(LinearLayout.VERTICAL);scroll.addView(sections);androidx.swiperefreshlayout.widget.SwipeRefreshLayout pull=new androidx.swiperefreshlayout.widget.SwipeRefreshLayout(activity);pull.setContentDescription("Refresh home");pull.setColorSchemeColors(0xffff5c35);pull.setProgressBackgroundColorSchemeColor(0xff24201e);pull.addView(scroll);pull.setOnChildScrollUpCallback((parent,child)->scroll.canScrollVertically(-1));pull.setOnRefreshListener(refresh::run);root.addView(pull,new LinearLayout.LayoutParams(-1,0,1));
        sections.addView(action(activity,"Favorites",()->{favorites.run();}));
        List<Track> history=ListeningHistory.recent(activity,50);
        sections.addView(action(activity,"Recently played",()->{recent.run();}));
        if(history.isEmpty())sections.addView(label(activity,"Your listening history will appear here.",15));
        if(!history.isEmpty())sections.addView(rail(activity,history.subList(0,Math.min(8,history.size())),track->track.title,track->{play.accept(track);}));
        List<Track> seeds=new ArrayList<>(FavoriteStore.remoteTracks(activity));
        seeds.addAll(ListeningHistory.dailyMix(activity,50));Set<String> ids=new HashSet<>(),artists=new HashSet<>();
        List<Track> unique=new ArrayList<>();for(Track seed:seeds)if(ids.add(Track.originalId(seed.stableId)))unique.add(seed.original());
        Collections.shuffle(unique,new Random(refreshSeed));
        if(!unique.isEmpty()){
            sections.addView(label(activity,"Song mixes",20));
            sections.addView(rail(activity,unique.subList(0,Math.min(4,unique.size())),track->track.title+" Mix",track->{mix.accept(track,false);}));

        }
        return root;
    }
    private static TextView label(Activity activity,String text,int size){TextView view=new TextView(activity);view.setText(text);view.setTextSize(size);view.setTextColor(0xfff6f1e8);view.setPadding(0,dp(activity,14),0,dp(activity,10));return view;}
    private static View action(Activity activity,String text,Runnable action){TextView view=label(activity,text,16);view.setBackgroundResource(android.R.drawable.list_selector_background);view.setOnClickListener(v->action.run());return view;}
    private static View rail(Activity activity,List<Track> tracks,Function<Track,String> title,Consumer<Track> action){
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(activity);scroll.addView(row);
        for(Track track:tracks){
            LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(0,0,dp(activity,12),dp(activity,8));
            ImageView artwork=new ImageView(activity);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.setBackgroundColor(0xff24201e);card.addView(artwork,new LinearLayout.LayoutParams(-1,dp(activity,132)));
            ArtworkLoader.into(artwork,track.artworkUri,R.mipmap.ic_brand);
            TextView name=label(activity,title.apply(track),15);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setMinHeight(dp(activity,56));card.addView(name);
            card.setContentDescription(title.apply(track)+", "+track.artist);card.setFocusable(true);card.setBackgroundResource(android.R.drawable.list_selector_background);card.setOnClickListener(v->action.accept(track));row.addView(card,new LinearLayout.LayoutParams(dp(activity,156),-2));
        }
        return scroll;
    }
    private static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private ForYouDialog(){}
}
