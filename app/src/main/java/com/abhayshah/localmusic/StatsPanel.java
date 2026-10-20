package com.abhayshah.localmusic;

import android.app.*;
import android.os.Handler;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;

final class StatsPanel {
    static Dialog show(Activity activity,Executor io,Handler main){
        Dialog dialog=new Dialog(activity);LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);root.setBackgroundColor(0xff121111);root.setContentDescription("Listening stats");
        Button close=new Button(activity);close.setText("Close");close.setContentDescription("Close stats");close.setOnClickListener(v->dialog.dismiss());root.addView(close);
        TextView title=new TextView(activity);title.setText("Your listening");title.setTextSize(26);title.setTextColor(0xfff6f1e8);root.addView(title);Button period=new Button(activity);period.setText("Last 30 days");root.addView(period);
        ScrollView scroll=new ScrollView(activity);LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        long[] start={System.currentTimeMillis()-30L*86400000},end={Long.MAX_VALUE};int[] request={0};
        Runnable refresh=()->{int token=++request[0];body.removeAllViews();body.addView(text(activity,"Loading…",16));long from=start[0],until=end[0];io.execute(()->{
            try{ListeningStats.Summary summary=ListeningStats.store(activity).summary(from,until);main.post(()->{if(!dialog.isShowing()||token!=request[0]||activity.isDestroyed())return;body.removeAllViews();body.addView(text(activity,summary.plays+" plays · "+summary.tracks+" songs · "+summary.artists+" artists",20));body.addView(text(activity,"Plays of 30+ seconds. Tracking since "+Instant.ofEpochMilli(summary.since).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy")),13));if(summary.plays==0)body.addView(text(activity,"No plays in this period yet.",16));section(activity,body,"Top songs",summary.songs);section(activity,body,"Top artists",summary.performers);section(activity,body,"Genres",summary.genres);body.addView(text(activity,"Genres use song tags; many streams aren't tagged. Earlier listening can't be reconstructed.",13));});}
            catch(RuntimeException unavailable){main.post(()->{if(dialog.isShowing()&&token==request[0]){body.removeAllViews();body.addView(text(activity,"Couldn't read stats. Change the period to retry.",16));}});}
        });};
        period.setOnClickListener(v->new AppDialogBuilder(activity).setTitle("Time period").setItems(new String[]{"Today","Last 7 days","Last 30 days","Last year","All time","Custom dates"},(d,which)->{
            if(which==5){LocalDate today=LocalDate.now();DatePickerDialog from=new DatePickerDialog(activity,(picker,y,m,day)->{LocalDate first=LocalDate.of(y,m+1,day);DatePickerDialog until=new DatePickerDialog(activity,(p,ey,em,ed)->{LocalDate last=LocalDate.of(ey,em+1,ed);if(last.isBefore(first)){Toast.makeText(activity,"End date must follow the start date.",Toast.LENGTH_SHORT).show();return;}start[0]=first.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();end[0]=last.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();period.setText(first+" – "+last);refresh.run();},today.getYear(),today.getMonthValue()-1,today.getDayOfMonth());until.setTitle("Through date");until.getDatePicker().setMinDate(first.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());until.getDatePicker().setMaxDate(System.currentTimeMillis());until.show();},today.getYear(),today.getMonthValue()-1,today.getDayOfMonth());from.setTitle("From date");from.getDatePicker().setMaxDate(System.currentTimeMillis());from.show();return;}
            long now=System.currentTimeMillis();start[0]=which==0?LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli():which==4?0:now-(which==1?7L:which==2?30L:365L)*86400000;end[0]=Long.MAX_VALUE;period.setText(new String[]{"Today","Last 7 days","Last 30 days","Last year","All time"}[which]);refresh.run();
        }).show());
        DismissSwipeLayout swipe=new DismissSwipeLayout(activity,dialog::dismiss);swipe.addView(root);dialog.setContentView(swipe);dialog.setOnDismissListener(v->request[0]++);dialog.show();dialog.getWindow().setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.9f));refresh.run();return dialog;
    }
    private static TextView text(Activity activity,String label,int size){TextView view=new TextView(activity);view.setText(label);view.setTextSize(size);view.setTextColor(0xfff6f1e8);view.setPadding(0,12,0,12);return view;}
    private static void section(Activity activity,LinearLayout body,String title,java.util.List<ListeningStats.Row> rows){if(rows.isEmpty())return;body.addView(text(activity,title,21));int n=0;for(ListeningStats.Row row:rows)body.addView(text(activity,(++n)+". "+row.name+(row.detail.isEmpty()?"":" · "+row.detail)+"  —  "+row.count,16));}
    private StatsPanel(){}
}
