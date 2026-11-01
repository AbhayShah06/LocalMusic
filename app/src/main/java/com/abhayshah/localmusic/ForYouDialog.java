package com.abhayshah.localmusic;

import android.app.Activity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.function.*;

/** Personal listening shortcuts; opening a section never changes playback. */
final class ForYouDialog {
    interface MixLoader { void load(List<Track> visible,Set<String> seen,Consumer<List<Track>> done); }
    static View create(Activity activity,Consumer<Track> play,BiConsumer<Track,Boolean> mix,Consumer<Track> menu,Runnable recent,Runnable favorites,Runnable refresh,MixLoader moreMixes,long refreshSeed){
        android.content.SharedPreferences appearance=activity.getSharedPreferences("settings",0);
        boolean compact=appearance.getBoolean("home_compact",false);
        int accent=appearance.getInt("home_accent",0xffff805f);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff0b0b0c);root.setPadding(dp(activity,18),dp(activity,12),dp(activity,18),dp(activity,8));
        TextView title=label(activity,"For You",28);title.setTextColor(accent);root.addView(title);
        ScrollView scroll=new ScrollView(activity);LinearLayout sections=new LinearLayout(activity);sections.setOrientation(LinearLayout.VERTICAL);scroll.addView(sections);androidx.swiperefreshlayout.widget.SwipeRefreshLayout pull=new androidx.swiperefreshlayout.widget.SwipeRefreshLayout(activity);pull.setContentDescription("Refresh home");pull.setColorSchemeColors(0xffff5c35);pull.setProgressBackgroundColorSchemeColor(0xff24201e);pull.addView(scroll);pull.setOnChildScrollUpCallback((parent,child)->scroll.canScrollVertically(-1));pull.setOnRefreshListener(refresh::run);root.addView(pull,new LinearLayout.LayoutParams(-1,0,1));
        sections.addView(action(activity,"Favorites",()->{favorites.run();}));
        List<Track> history=ListeningHistory.recent(activity,50);
        sections.addView(action(activity,"Recently played",()->{recent.run();}));
        if(history.isEmpty())sections.addView(label(activity,"Your listening history will appear here.",15));
        if(!history.isEmpty())sections.addView(rail(activity,history.subList(0,Math.min(8,history.size())),track->track.title,play,menu,compact,accent,null));
        List<Track> seeds=new ArrayList<>(FavoriteStore.remoteTracks(activity));
        seeds.addAll(ListeningHistory.dailyMix(activity,50));Set<String> ids=new HashSet<>(),artists=new HashSet<>();
        List<Track> unique=new ArrayList<>();for(Track seed:seeds)if(ids.add(Track.originalId(seed.stableId)))unique.add(seed.original());
        Collections.shuffle(unique,new Random(refreshSeed));
        if(!unique.isEmpty()){
            sections.addView(label(activity,"Song mixes",20));
            sections.addView(rail(activity,diverseSeeds(unique,10),track->track.title+" Mix",track->mix.accept(track,false),menu,compact,accent,moreMixes));

        }
        return root;
    }
    static List<Track> diverseSeeds(List<Track> input,int limit){
        List<Track> result=new ArrayList<>();Set<String> artists=new HashSet<>(),ids=new HashSet<>();
        for(Track track:input)if(artists.add(RecordingIdentity.artist(track.artist))&&ids.add(Track.originalId(track.stableId))){result.add(track);if(result.size()>=limit)return result;}
        for(Track track:input)if(ids.add(Track.originalId(track.stableId))){result.add(track);if(result.size()>=limit)break;}
        return result;
    }
    private static TextView label(Activity activity,String text,int size){TextView view=new TextView(activity);view.setText(text);view.setTextSize(size);view.setTextColor(0xfff6f1e8);view.setPadding(0,dp(activity,14),0,dp(activity,10));return view;}
    private static View action(Activity activity,String text,Runnable action){TextView view=label(activity,text,16);view.setBackgroundResource(android.R.drawable.list_selector_background);view.setOnClickListener(v->action.run());return view;}
    private static View rail(Activity activity,List<Track> tracks,Function<Track,String> title,Consumer<Track> action,Consumer<Track> menu,boolean compact,int accent,MixLoader loader){
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);LinearLayout row=new LinearLayout(activity);row.setGravity(android.view.Gravity.CENTER_HORIZONTAL);scroll.addView(row);
        int width=dp(activity,compact?132:164),span=width+dp(activity,10);
        List<Track> visible=new ArrayList<>();Set<String> seen=new HashSet<>(),seenSongs=new HashSet<>();
        Consumer<Track> append=track->{String id=Track.originalId(track.stableId),song=RecordingIdentity.key(track.title,track.artist);if(!seen.add(id))return;if(!song.isEmpty()&&!seenSongs.add(song))return;visible.add(track);
            LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(android.view.Gravity.CENTER_HORIZONTAL);card.setPadding(dp(activity,8),dp(activity,8),dp(activity,8),dp(activity,8));
            android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(0xff1b1919);background.setCornerRadius(dp(activity,16));background.setStroke(dp(activity,1),0xff302a28);card.setBackground(background);
            ImageView artwork=new ImageView(activity);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.setBackgroundColor(0xff24201e);card.addView(artwork,new LinearLayout.LayoutParams(-1,dp(activity,compact?104:138)));
            ArtworkLoader.into(artwork,track.artworkUri,R.mipmap.ic_brand);
            TextView name=label(activity,title.apply(track),15);name.setGravity(android.view.Gravity.CENTER);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setMinHeight(dp(activity,50));card.addView(name,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout footer=new LinearLayout(activity);footer.setGravity(android.view.Gravity.CENTER_VERTICAL);TextView artist=label(activity,track.artist,12);artist.setTextColor(0xffaaa29b);artist.setSingleLine(true);artist.setEllipsize(android.text.TextUtils.TruncateAt.END);footer.addView(artist,new LinearLayout.LayoutParams(0,-2,1));
            TextView more=label(activity,"⋮",25);more.setTextColor(accent);more.setGravity(android.view.Gravity.CENTER);more.setPadding(0,0,0,0);more.setContentDescription("More options for "+track.title);more.setOnClickListener(v->menu.accept(track));footer.addView(more,new LinearLayout.LayoutParams(dp(activity,40),dp(activity,40)));card.addView(footer,new LinearLayout.LayoutParams(-1,-2));
            card.setContentDescription(title.apply(track)+", "+track.artist+". Long press for options");card.setFocusable(true);card.setOnClickListener(v->action.accept(track));card.setOnLongClickListener(v->{menu.accept(track);return true;});
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(width,-2);params.setMargins(0,0,dp(activity,10),dp(activity,8));row.addView(card,params);
        };for(Track track:tracks)append.accept(track);
        if(loader!=null){boolean[] loading={false};long[] retryAt={0};Runnable[] request={null};
            TextView edge=label(activity,"More mixes  →",15);edge.setTextColor(accent);edge.setGravity(android.view.Gravity.CENTER);edge.setContentDescription("Load more song mixes");
            row.addView(edge,new LinearLayout.LayoutParams(width,dp(activity,190)));
            request[0]=()->{if(loading[0]||android.os.SystemClock.elapsedRealtime()<retryAt[0]||!scroll.isAttachedToWindow())return;loading[0]=true;
                edge.setText("Finding more mixes…");
                loader.load(new ArrayList<>(visible),new HashSet<>(seen),found->activity.runOnUiThread(()->{
                    loading[0]=false;if(!scroll.isAttachedToWindow())return;int before=visible.size();row.removeView(edge);for(Track track:found)append.accept(track);row.addView(edge,new LinearLayout.LayoutParams(width,dp(activity,190)));
                    if(visible.size()==before){edge.setText("No new mixes. Tap to retry");retryAt[0]=android.os.SystemClock.elapsedRealtime()+5000;return;}
                    edge.setText("More mixes  →");
                    int removed=Math.max(0,visible.size()-40),oldX=scroll.getScrollX();for(int i=0;i<removed;i++){visible.remove(0);row.removeViewAt(0);}
                    if(removed>0)scroll.post(()->scroll.scrollTo(Math.max(0,oldX-removed*span),0));
                }));
            };
            edge.setOnClickListener(v->{retryAt[0]=0;request[0].run();});
            scroll.setOnScrollChangeListener((View v,int x,int y,int oldX,int oldY)->{if(x>oldX&&row.getWidth()-scroll.getWidth()-x<2*span)request[0].run();});
        }
        return scroll;
    }
    private static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private ForYouDialog(){}
}
