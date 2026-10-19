package com.abhayshah.localmusic;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

final class UpdateManager {
    private static final String MANIFEST = "https://raw.githubusercontent.com/AbhayShah06/LocalMusic/main/update.json";
    private static final String BANNER = "github-update-banner";
    private static final java.util.Map<Activity,Long> checks = new java.util.WeakHashMap<>();
    private UpdateManager() {}
    static void attach(Activity a, ViewGroup parent) {
        cleanup(a);
        // The public release cannot replace a preconfigured personal build because
        // doing so would remove its bundled, bucket-restricted credentials.
        if (BuildConfig.IS_PERSONAL) return;
        refresh(a,parent,false);
    }
    static void refresh(Activity a,ViewGroup parent,boolean manual) {
        if(BuildConfig.IS_PERSONAL){if(manual)Toast.makeText(a,"This personal build updates separately.",Toast.LENGTH_SHORT).show();return;}
        long now=android.os.SystemClock.elapsedRealtime();Long last=checks.get(a);if(!manual&&last!=null&&now-last<60000)return;checks.put(a,now);
        new Thread(() -> check(a,parent,manual),"update-check").start();
    }
    private static void check(Activity a, ViewGroup parent,boolean manual) {
        try { JSONObject j = new JSONObject(read(MANIFEST+"?t="+System.currentTimeMillis()/60000)); int current = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionCode;
            a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed())return;if(j.optInt("versionCode")>current){show(a,parent,j);if(manual)Toast.makeText(a,"Update "+j.optString("versionName")+" is ready",Toast.LENGTH_SHORT).show();}else if(manual)Toast.makeText(a,"You're up to date · "+BuildConfig.VERSION_NAME,Toast.LENGTH_SHORT).show();});
        } catch (Exception ignored) { if(manual)a.runOnUiThread(()->{if(!a.isFinishing())Toast.makeText(a,"Couldn't check. Try again or open GitHub releases.",Toast.LENGTH_LONG).show();}); }
    }
    static void show(Activity a, ViewGroup parent, JSONObject j) {
        if(a.isFinishing()||a.isDestroyed())return;
        android.view.View old=parent.findViewWithTag(BANNER);if(old!=null)parent.removeView(old);
        LinearLayout banner = new LinearLayout(a); banner.setOrientation(LinearLayout.VERTICAL); int p = (int)(14 * a.getResources().getDisplayMetrics().density); banner.setPadding(p,p,p,p); banner.setBackgroundColor(Color.rgb(32,58,49));
        banner.setTag(BANNER);banner.setContentDescription("GitHub update available");
        TextView text = new TextView(a); text.setTextColor(Color.WHITE); text.setText("Update " + j.optString("versionName") + " is ready\n" + j.optString("notes", "Bug fixes and improvements")); banner.addView(text);
        Button button = new Button(a); button.setText("Update now"); banner.addView(button); parent.addView(banner, 1); button.setOnClickListener(v -> install(a, button, j));
        Button later=new Button(a);later.setText("Later");later.setOnClickListener(v->parent.removeView(banner));banner.addView(later);
    }
    private static void install(Activity a, Button button, JSONObject j) {
        if (!a.getPackageManager().canRequestPackageInstalls()) { a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))); Toast.makeText(a,"Allow installs, then tap Update now again.",Toast.LENGTH_LONG).show(); return; }
        button.setEnabled(false); button.setText("Downloading…"); new Thread(() -> { try { File f = new File(a.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"pending-update.apk"); download(j.getString("apkUrl"),f); if (!hash(f).equalsIgnoreCase(j.getString("sha256"))) throw new SecurityException(); a.runOnUiThread(() -> launch(a,f)); }
            catch (Exception e) { a.runOnUiThread(() -> { button.setEnabled(true); button.setText("Retry update"); Toast.makeText(a,"Update download failed. Try again.",Toast.LENGTH_LONG).show(); }); } },"update-download").start();
    }
    private static void launch(Activity a, File f) { Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".updates",f); a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK)); }
    private static String read(String url) throws Exception { HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setUseCaches(false);c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestProperty("Cache-Control","no-cache");c.setRequestProperty("User-Agent","LocalMusic-Updater"); try(java.io.InputStream in=c.getInputStream();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[]b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toString("UTF-8");} finally{c.disconnect();} }
    private static void download(String url,File f)throws Exception{if(!url.startsWith("https://github.com/AbhayShah06/LocalMusic/"))throw new SecurityException(); HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setInstanceFollowRedirects(true); c.setConnectTimeout(10000); c.setReadTimeout(30000); try(java.io.InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(f)){byte[]b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}finally{c.disconnect();}}
    private static String hash(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");byte[]b=new byte[8192];int n;try(FileInputStream in=new FileInputStream(f)){while((n=in.read(b))>0)d.update(b,0,n);}StringBuilder h=new StringBuilder();for(byte x:d.digest())h.append(String.format("%02x",x));return h.toString();}
    private static void cleanup(Activity a){File dir=a.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);if(dir!=null){File f=new File(dir,"pending-update.apk");if(f.exists())f.delete();}}
}
