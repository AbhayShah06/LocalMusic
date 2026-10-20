package com.abhayshah.localmusic;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import androidx.media3.common.MediaItem;
import java.util.*;

/** Qualified plays are timestamped separately from the small recommendation history. */
final class ListeningStats {
    private static Store store;
    static synchronized Store store(Context context){if(store==null)store=new Store(context,"listening-stats.db");return store;}
    static void record(Context context,MediaItem item){try{store(context).record(item,System.currentTimeMillis());}catch(SQLiteException unavailable){android.util.Log.w("ListeningStats","Could not save this play",unavailable);}}
    static final class Row{final String name,detail;final int count;Row(String name,String detail,int count){this.name=name;this.detail=detail;this.count=count;}}
    static final class Summary{int plays,tracks,artists;long since;List<Row> songs=new ArrayList<>(),performers=new ArrayList<>(),genres=new ArrayList<>();}
    static final class Store extends SQLiteOpenHelper {
        Store(Context context,String name){super(context.getApplicationContext(),name,null,1);}
        @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE plays (time INTEGER NOT NULL,song_key TEXT NOT NULL,title TEXT NOT NULL,artist_key TEXT NOT NULL,artist TEXT NOT NULL,genre TEXT NOT NULL)");db.execSQL("CREATE INDEX play_time ON plays(time)");db.execSQL("CREATE TABLE tracking (started INTEGER NOT NULL)");ContentValues start=new ContentValues();start.put("started",System.currentTimeMillis());db.insertOrThrow("tracking",null,start);}
        @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new SQLiteException("Unsupported stats schema upgrade");}
        void record(MediaItem item,long time){
            String title=text(item.mediaMetadata.title,"Unknown title"),artist=text(item.mediaMetadata.artist,"Unknown artist");String artistKey=RecordingIdentity.artist(artist),song=artistKey+"|"+RecordingIdentity.title(title,artist);
            ContentValues row=new ContentValues();row.put("time",time);row.put("song_key",song);row.put("title",title);row.put("artist_key",artistKey);row.put("artist",artist);row.put("genre",text(item.mediaMetadata.genre,"Not tagged"));getWritableDatabase().insertOrThrow("plays",null,row);
        }
        Summary summary(long start,long end){
            if(end<=start)throw new IllegalArgumentException("End must be after start");SQLiteDatabase db=getReadableDatabase();Summary result=new Summary();String[] bounds={Long.toString(start),Long.toString(end)};
            try(Cursor c=db.rawQuery("SELECT COUNT(*),COUNT(DISTINCT song_key),COUNT(DISTINCT artist_key) FROM plays WHERE time>=? AND time<?",bounds)){if(c.moveToFirst()){result.plays=c.getInt(0);result.tracks=c.getInt(1);result.artists=c.getInt(2);}}
            try(Cursor c=db.rawQuery("SELECT started FROM tracking LIMIT 1",null)){if(c.moveToFirst())result.since=c.getLong(0);}
            result.songs=rank(db,"song_key","title","artist",bounds);result.performers=rank(db,"artist_key","artist","''",bounds);result.genres=rank(db,"lower(genre)","genre","''",bounds);return result;
        }
        private List<Row> rank(SQLiteDatabase db,String group,String name,String detail,String[] bounds){List<Row> rows=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT "+name+","+detail+",COUNT(*) AS n FROM plays WHERE time>=? AND time<? GROUP BY "+group+" ORDER BY n DESC,"+name+" COLLATE NOCASE LIMIT 10",bounds)){while(c.moveToNext())rows.add(new Row(c.getString(0),c.getString(1),c.getInt(2)));}return rows;}
        private static String text(CharSequence value,String fallback){String text=value==null?"":value.toString().trim();return text.isEmpty()?fallback:text;}
    }
    private ListeningStats(){}
}
