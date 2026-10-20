package com.abhayshah.localmusic;

import android.app.*;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.*;

/** Follows playback using provider timestamps; plain lyrics remain readable without fake timing. */
final class LyricsPanel {
    static Dialog show(Activity activity,Track initial,Supplier<Track> current,LongSupplier position,LongConsumer seek,Executor io,Handler main){
        return show(activity,initial,current,position,seek,io,main,track->LyricsClient.get(activity,track));
    }
    static Dialog show(Activity activity,Track initial,Supplier<Track> current,LongSupplier position,LongConsumer seek,Executor io,Handler main,Function<Track,String> load){
        Dialog dialog=new Dialog(activity);LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,20,24,20);root.setBackgroundColor(0xff121111);root.setContentDescription("Live lyrics");
        Button close=new Button(activity);close.setText("Close");close.setContentDescription("Close lyrics");close.setOnClickListener(v->dialog.dismiss());root.addView(close);
        TextView heading=new TextView(activity);heading.setTextSize(22);heading.setTextColor(0xfff6f1e8);root.addView(heading);
        ScrollView scroll=new ScrollView(activity);LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        DismissSwipeLayout swipe=new DismissSwipeLayout(activity,dialog::dismiss);swipe.addView(root);dialog.setContentView(swipe);
        List<TimedLyrics> lines=new ArrayList<>();List<TextView> views=new ArrayList<>();String[] id={""};int[] generation={0},active={-2};long[] manualScrollUntil={0};
        scroll.setOnTouchListener((v,event)->{manualScrollUntil[0]=android.os.SystemClock.uptimeMillis()+5000;return false;});
        Consumer<Track> display=track->{
            id[0]=Track.originalId(track.stableId);int request=++generation[0];heading.setText(track.title+"\n"+track.artist);lines.clear();views.clear();body.removeAllViews();active[0]=-2;
            TextView loading=new TextView(activity);loading.setText("Finding lyrics…");body.addView(loading);
            io.execute(()->{String result;try{result=load.apply(track);}catch(RuntimeException unavailable){result="";}String text=result;main.post(()->{
                if(!dialog.isShowing()||request!=generation[0]||activity.isDestroyed())return;
                body.removeAllViews();lines.addAll(TimedLyrics.parse(text));
                if(lines.isEmpty()){TextView plain=new TextView(activity);plain.setText(text.isEmpty()?"No lyrics available for this recording.":text);plain.setTextColor(0xfff6f1e8);plain.setTextSize(20);plain.setTextIsSelectable(true);body.addView(plain);return;}
                for(TimedLyrics line:lines){TextView view=new TextView(activity);view.setText(line.text.isEmpty()?"♪":line.text);view.setTextSize(24);view.setTextColor(0xff8f8a83);view.setPadding(0,20,0,20);view.setOnClickListener(v->{Track playing=current.get();if(playing!=null&&id[0].equals(Track.originalId(playing.stableId)))seek.accept(line.timeMs);});views.add(view);body.addView(view);}
            });});
        };
        Runnable[] tick={null};tick[0]=()->{
            if(!dialog.isShowing())return;Track playing=current.get();
            if(playing!=null&&!id[0].equals(Track.originalId(playing.stableId)))display.accept(playing);
            if(!lines.isEmpty()){int next=TimedLyrics.active(lines,position.getAsLong());if(next!=active[0]){if(active[0]>=0&&active[0]<views.size())views.get(active[0]).setTextColor(0xff8f8a83);active[0]=next;if(next>=0&&next<views.size()){TextView selected=views.get(next);selected.setTextColor(0xffff805f);selected.setContentDescription("Current lyric: "+selected.getText());for(int i=0;i<views.size();i++)if(i!=next)views.get(i).setContentDescription(null);if(android.os.SystemClock.uptimeMillis()>manualScrollUntil[0])scroll.smoothScrollTo(0,Math.max(0,selected.getTop()-scroll.getHeight()/3));}}}
            main.postDelayed(tick[0],250);
        };
        dialog.setOnDismissListener(v->{generation[0]++;main.removeCallbacks(tick[0]);});dialog.show();dialog.getWindow().setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.9f));display.accept(initial);main.post(tick[0]);return dialog;
    }
    private LyricsPanel(){}
}
