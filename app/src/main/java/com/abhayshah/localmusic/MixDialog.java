package com.abhayshah.localmusic;
import android.app.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;

/** A collection view uses the same artwork rows as the library, with optional selection. */
final class MixDialog {
    static void show(Activity activity,String title,List<Track> input,BiConsumer<List<Track>,Integer> play,Consumer<List<Track>> add,Consumer<Track> menu){
        List<Track> tracks=Collections.unmodifiableList(new ArrayList<>(input));Set<Integer> selected=new TreeSet<>();boolean[] selecting={false};Dialog dialog=new Dialog(activity);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff121111);root.setPadding(dp(activity,16),dp(activity,12),dp(activity,16),dp(activity,12));
        LinearLayout header=new LinearLayout(activity);Button back=new Button(activity);back.setText("Close");back.setContentDescription("Close mix");back.setOnClickListener(v->dialog.dismiss());header.addView(back);
        TextView heading=new TextView(activity);heading.setText(title);heading.setTextSize(21);heading.setTextColor(0xfff6f1e8);heading.setPadding(dp(activity,12),0,0,0);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        LinearLayout controls=new LinearLayout(activity);Button start=new Button(activity),select=new Button(activity),playlist=new Button(activity);start.setText("Play");select.setText("Select");playlist.setText("Add to playlist");controls.addView(start);controls.addView(select);controls.addView(playlist,new LinearLayout.LayoutParams(0,-2,1));root.addView(controls);
        ListView list=new ListView(activity);list.setDividerHeight(4);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        java.util.function.Supplier<List<Track>> picked=()->{List<Track> result=new ArrayList<>();if(!selecting[0])result.addAll(tracks);else for(int i:selected)result.add(tracks.get(i));return result;};
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return tracks.size();}public Track getItem(int position){return tracks.get(position);}public long getItemId(int position){return position;}
            public View getView(int position,View convert,ViewGroup parent){
                View row=convert==null?activity.getLayoutInflater().inflate(R.layout.row_track,parent,false):convert;Track track=tracks.get(position);
                ((TextView)row.findViewById(R.id.title)).setText(track.title);((TextView)row.findViewById(R.id.subtitle)).setText(track.artist);ArtworkLoader.into(row.findViewById(R.id.artwork),track.artworkUri,android.R.drawable.ic_media_play);
                TextView more=row.findViewById(R.id.trackMenu);more.setText(selecting[0]?(selected.contains(position)?"✓":"○"):"⋮");more.setTextSize(selecting[0]?21:26);more.setContentDescription((selecting[0]?"Select ":"More options for ")+track.title);
                Runnable toggle=()->{if(!selected.remove(position))selected.add(position);playlist.setText(selected.isEmpty()?"Add to playlist":"Add "+selected.size()+" to playlist");start.setEnabled(!selected.isEmpty());playlist.setEnabled(!selected.isEmpty());notifyDataSetChanged();};
                more.setOnClickListener(v->{if(selecting[0])toggle.run();else menu.accept(track);});row.setOnClickListener(v->{if(selecting[0])toggle.run();else{dialog.dismiss();play.accept(tracks,position);}});
                row.setOnLongClickListener(v->{selecting[0]=true;select.setText("Done");toggle.run();return true;});row.setActivated(selected.contains(position));return row;
            }
        };list.setAdapter(adapter);
        select.setOnClickListener(v->{selecting[0]=!selecting[0];selected.clear();select.setText(selecting[0]?"Done":"Select");start.setEnabled(!selecting[0]);playlist.setEnabled(!selecting[0]);playlist.setText("Add to playlist");adapter.notifyDataSetChanged();});
        start.setOnClickListener(v->{List<Track> values=picked.get();if(!values.isEmpty()){dialog.dismiss();play.accept(values,0);}});playlist.setOnClickListener(v->{List<Track> values=picked.get();if(!values.isEmpty())add.accept(values);});
        DismissSwipeLayout swipe=new DismissSwipeLayout(activity,dialog::dismiss);swipe.addView(root,new android.widget.FrameLayout.LayoutParams(-1,-1));dialog.setContentView(swipe);dialog.show();dialog.getWindow().setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.88f));
    }
    private static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private MixDialog(){}
}
