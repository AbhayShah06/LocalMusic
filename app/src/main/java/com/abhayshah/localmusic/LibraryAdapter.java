package com.abhayshah.localmusic;

import android.app.Activity;
import android.content.ContentUris;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

final class LibraryAdapter extends BaseAdapter {
    interface MenuListener { void onMenu(int position); }
    private final Activity activity; private final List<Track> tracks; private final List<String> labels = new ArrayList<>(); private final MenuListener menuListener;
    LibraryAdapter(Activity activity, List<Track> tracks, MenuListener menuListener) { this.activity = activity; this.tracks = tracks; this.menuListener = menuListener; }
    void add(String value) { labels.add(value); notifyDataSetChanged(); }
    void clear() { labels.clear(); notifyDataSetChanged(); }
    void remove(String value) { labels.remove(value); notifyDataSetChanged(); }
    @Override public int getCount() { return labels.size(); }
    @Override public String getItem(int position) { return labels.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public View getView(int position, View convert, ViewGroup parent) {
        View row = convert == null ? LayoutInflater.from(activity).inflate(R.layout.row_track, parent, false) : convert;
        TextView title = row.findViewById(R.id.title), subtitle = row.findViewById(R.id.subtitle); ImageView artwork = row.findViewById(R.id.artwork);
        String label = labels.get(position); int line = label.indexOf('\n'); title.setText(line >= 0 ? label.substring(0, line) : label); subtitle.setText(line >= 0 ? label.substring(line + 1) : "Browse collection");
        artwork.setImageResource(android.R.drawable.ic_media_play);
        if (position < tracks.size()) ArtworkLoader.into(artwork, tracks.get(position).artworkUri, android.R.drawable.ic_media_play);
        row.findViewById(R.id.trackMenu).setOnClickListener(v -> menuListener.onMenu(position));
        return row;
    }
}
