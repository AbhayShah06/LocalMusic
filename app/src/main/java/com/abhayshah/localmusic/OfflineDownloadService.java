package com.abhayshah.localmusic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-initiated, service-owned downloads that continue when the app UI closes. */
@androidx.media3.common.util.UnstableApi
public final class OfflineDownloadService extends Service {
    private static final String CHANNEL = "offline_downloads";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    static void enqueue(Context context, Track track) {
        Intent intent = new Intent(context, OfflineDownloadService.class).putExtra("id", track.stableId).putExtra("title", track.title)
                .putExtra("artist", track.artist).putExtra("album", track.album).putExtra("artwork", track.artworkUri == null ? "" : track.artworkUri.toString());
        androidx.core.content.ContextCompat.startForegroundService(context, intent);
    }
    @Override public void onCreate() { super.onCreate(); if (android.os.Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "Music downloads", NotificationManager.IMPORTANCE_LOW)); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY; String title = intent.getStringExtra("title");
        startForeground(2401, notification("Preparing " + title, 0, true)); IO.execute(() -> download(intent, startId)); return START_NOT_STICKY;
    }
    private void download(Intent intent, int startId) {
        String id = intent.getStringExtra("id"), title = intent.getStringExtra("title"), artist = intent.getStringExtra("artist"), album = intent.getStringExtra("album"), artwork = intent.getStringExtra("artwork");
        File output = DownloadStore.destination(this, id), temporary = new File(output.getParentFile(), output.getName() + ".part"); HttpURLConnection connection = null;
        try {
            Track source = new Track(id, Uri.EMPTY, title, artist, album, artwork == null || artwork.isEmpty() ? null : Uri.parse(artwork));
            Track playable = id.startsWith("online:") ? new OnlineCatalogSource().resolve(source) : source;
            connection = (HttpURLConnection) new URL(playable.playbackUri.toString()).openConnection(); connection.setConnectTimeout(12_000); connection.setReadTimeout(20_000); connection.setRequestProperty("User-Agent", "LocalMusic/0.9");
            long total = connection.getContentLengthLong(), done = 0, lastUpdate = 0;
            try (InputStream input = connection.getInputStream(); FileOutputStream file = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024]; for (int count; (count = input.read(buffer)) >= 0; ) { file.write(buffer, 0, count); done += count; long now = android.os.SystemClock.elapsedRealtime(); if (now - lastUpdate > 600) { lastUpdate = now; int progress = total > 0 ? (int)Math.min(100, done * 100 / total) : 0; getSystemService(NotificationManager.class).notify(2401, notification("Downloading " + title, progress, total <= 0)); } }
            }
            if (output.exists()) output.delete(); if (!temporary.renameTo(output)) throw new IllegalStateException("Could not save download");
            DownloadStore.put(this, source, output); getSystemService(NotificationManager.class).notify(2402, notification("Downloaded " + title, 100, false));
        } catch (Exception error) { temporary.delete(); getSystemService(NotificationManager.class).notify(2402, notification("Download failed: " + title, 0, false)); }
        finally { if (connection != null) connection.disconnect(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId); }
    }
    private Notification notification(String text, int progress, boolean indeterminate) {
        Intent open = new Intent(this, MainActivity.class); PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Local Music").setContentText(text).setContentIntent(pending).setOnlyAlertOnce(true);
        if (indeterminate || progress > 0 && progress < 100) builder.setProgress(100, progress, indeterminate).setOngoing(true); return builder.build();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
