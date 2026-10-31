package com.abhayshah.localmusic;
import android.app.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;

/** A collection view uses the same artwork rows as the library, with optional selection. */
final class MixDialog {
    static Dialog show(Activity activity,String title,List<Track> input,BiConsumer<List<Track>,Integer> play,Consumer<List<Track>> add,Consumer<List<Track>> download,Consumer<Track> menu,Consumer<Consumer<List<Track>>> suggestions,Consumer<Track> addSuggestion){
        List<Track> tracks=Collections.unmodifiableList(new ArrayList<>(input));Set<Integer> selected=new TreeSet<>();boolean[] selecting={false};Dialog dialog=new Dialog(activity);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff121111);root.setPadding(dp(activity,16),dp(activity,12),dp(activity,16),dp(activity,12));
        LinearLayout header=new LinearLayout(activity);Button back=new Button(activity);back.setText("Close");back.setContentDescription("Close mix");back.setOnClickListener(v->dialog.dismiss());header.addView(back);
        TextView heading=new TextView(activity);heading.setText(title);heading.setTextSize(21);heading.setTextColor(0xfff6f1e8);heading.setPadding(dp(activity,12),0,0,0);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        LinearLayout playback=new LinearLayout(activity);Button start=new Button(activity),shuffle=new Button(activity);start.setText("Play");shuffle.setText("Shuffle play");style(start,0xff2c2825);style(shuffle,0xffb65e45);playback.addView(start,new LinearLayout.LayoutParams(0,-2,1));playback.addView(shuffle,new LinearLayout.LayoutParams(0,-2,1));root.addView(playback);
        LinearLayout controls=new LinearLayout(activity);Button select=new Button(activity),playlist=new Button(activity),save=new Button(activity);select.setText("Select");playlist.setText("Add to playlist");save.setText("Download all");style(select,0xff2c2825);style(playlist,0xff2c2825);style(save,0xff2c2825);controls.addView(select,new LinearLayout.LayoutParams(dp(activity,90),-2));controls.addView(playlist,new LinearLayout.LayoutParams(0,-2,1));root.addView(controls);root.addView(save,new LinearLayout.LayoutParams(-1,-2));
        ListView list=new ListView(activity);list.setDividerHeight(4);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(activity);footer.setOrientation(LinearLayout.VERTICAL);footer.setPadding(dp(activity,4),dp(activity,20),dp(activity,4),dp(activity,12));
        list.addFooterView(footer,null,false);
        java.util.function.Supplier<List<Track>> picked=()->{List<Track> result=new ArrayList<>();if(!selecting[0])result.addAll(tracks);else for(int i:selected)result.add(tracks.get(i));return result;};
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return tracks.size();}public Track getItem(int position){return tracks.get(position);}public long getItemId(int position){return position;}
            public View getView(int position,View convert,ViewGroup parent){
                View row=convert==null?activity.getLayoutInflater().inflate(R.layout.row_track,parent,false):convert;Track track=tracks.get(position);
                ((TextView)row.findViewById(R.id.title)).setText(track.title);((TextView)row.findViewById(R.id.subtitle)).setText(track.artist);ArtworkLoader.into(row.findViewById(R.id.artwork),track.artworkUri,android.R.drawable.ic_media_play);
                TextView more=row.findViewById(R.id.trackMenu);more.setText(selecting[0]?(selected.contains(position)?"✓":"○"):"⋮");more.setTextSize(selecting[0]?21:26);more.setContentDescription((selecting[0]?"Select ":"More options for ")+track.title);
                Runnable toggle=()->{if(!selected.remove(position))selected.add(position);playlist.setText(selected.isEmpty()?"Add to playlist":"Add "+selected.size());save.setText(selected.isEmpty()?"Download all":"Download "+selected.size());start.setEnabled(!selected.isEmpty());shuffle.setEnabled(!selected.isEmpty());playlist.setEnabled(!selected.isEmpty());save.setEnabled(!selected.isEmpty());notifyDataSetChanged();};
                more.setOnClickListener(v->{if(selecting[0])toggle.run();else menu.accept(track);});row.setOnClickListener(v->{if(selecting[0])toggle.run();else{dialog.dismiss();play.accept(tracks,position);}});
                row.setOnLongClickListener(v->{selecting[0]=true;select.setText("Done");toggle.run();return true;});row.setActivated(selected.contains(position));return row;
            }
        };list.setAdapter(adapter);
        select.setOnClickListener(v->{selecting[0]=!selecting[0];selected.clear();select.setText(selecting[0]?"Done":"Select");start.setEnabled(!selecting[0]);shuffle.setEnabled(!selecting[0]);playlist.setEnabled(!selecting[0]);save.setEnabled(!selecting[0]);playlist.setText("Add to playlist");save.setText("Download all");adapter.notifyDataSetChanged();});
        start.setOnClickListener(v->{List<Track> values=picked.get();if(!values.isEmpty()){dialog.dismiss();play.accept(values,0);}});playlist.setOnClickListener(v->{List<Track> values=picked.get();if(!values.isEmpty())add.accept(values);});
        shuffle.setOnClickListener(v->{List<Track> values=picked.get();if(!values.isEmpty()){Collections.shuffle(values);dialog.dismiss();play.accept(values,0);}});
        save.setOnClickListener(v->{List<Track> values=picked.get();if(!values.isEmpty())download.accept(values);});
        DismissSwipeLayout swipe=new DismissSwipeLayout(activity,dialog::dismiss);swipe.addView(root,new android.widget.FrameLayout.LayoutParams(-1,-1));dialog.setContentView(swipe);dialog.show();dialog.getWindow().setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.88f));
        final Runnable[] loadSuggestions=new Runnable[1];
        loadSuggestions[0]=()->{
            footer.removeAllViews();TextView waiting=new TextView(activity);waiting.setText("Finding songs for this playlist…");waiting.setTextColor(0xffaaa29b);footer.addView(waiting);
            suggestions.accept(found->activity.runOnUiThread(()->{
                if(!dialog.isShowing())return;footer.removeAllViews();
                TextView label=new TextView(activity);label.setText("SUGGESTED FOR THIS PLAYLIST");label.setTextColor(0xffff805f);label.setTextSize(12);label.setPadding(0,0,0,dp(activity,8));footer.addView(label);
                if(found.isEmpty()){TextView empty=new TextView(activity);empty.setText("No suggestions right now. Try again later.");empty.setTextColor(0xffaaa29b);footer.addView(empty);}
                for(Track track:found){
                    LinearLayout row=new LinearLayout(activity);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    TextView song=new TextView(activity);song.setText(track.title+"\n"+track.artist);song.setTextColor(0xfff6f1e8);song.setMaxLines(2);song.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(song,new LinearLayout.LayoutParams(0,dp(activity,54),1));
                    Button addButton=new Button(activity);addButton.setText("Add");addButton.setContentDescription("Add "+track.title+" to playlist");addButton.setOnClickListener(v->{addSuggestion.accept(track);addButton.setEnabled(false);addButton.setText("Added");});row.addView(addButton);footer.addView(row);
                }
                Button refresh=new Button(activity);refresh.setText("Refresh suggestions");refresh.setOnClickListener(v->loadSuggestions[0].run());footer.addView(refresh);
            }));
        };
        loadSuggestions[0].run();
        return dialog;
    }
    private static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private static void style(Button button,int color){button.setAllCaps(false);button.setTextSize(13);button.setTextColor(0xfff6f1e8);button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));}
    private MixDialog(){}
}
