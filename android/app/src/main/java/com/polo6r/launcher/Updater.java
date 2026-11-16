package com.polo6r.launcher;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Over-the-air updates from an "update source" (a web address holding update.json).
 *
 *  update.json = { "versionCode": 3, "versionName": "1.2", "apk": "PoloLauncher.apk",
 *                  "webVersion": 7, "web": "web.zip", "webMinApp": 2, "notes": "..." }
 *
 *  - Screen/design updates (web.zip) are downloaded and applied automatically: no install needed.
 *  - App updates (apk) show "Update available"; one tap downloads it and Android asks to confirm.
 */
class Updater {
    static final String PREFS = "polo_update";
    private static final long CHECK_EVERY_MS = 6 * 60 * 60 * 1000L;

    private final MainActivity act;
    private volatile boolean busy;
    private JSONObject lastFeed;
    volatile String pendingApp;

    Updater(MainActivity act) { this.act = act; }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static File webDir(Context c) { return new File(c.getFilesDir(), "www"); }

    static int bundledWebVersion(Context c) {
        try (InputStream in = c.getAssets().open("www/version.txt")) {
            return Integer.parseInt(new BufferedReader(new InputStreamReader(in)).readLine().trim());
        } catch (Exception e) { return 0; }
    }

    static int installedWebVersion(Context c) { return prefs(c).getInt("web_version", 0); }

    static int appVersion(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) { return 0; }
    }

    static String appVersionName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    /** Page to load: a downloaded screen update if it is newer than the one built into the app. */
    static String startUrl(Context c) {
        File idx = new File(webDir(c), "index.html");
        if (idx.exists() && installedWebVersion(c) > bundledWebVersion(c)
                && appVersion(c) >= prefs(c).getInt("web_min_app", 0)) {
            return "file://" + idx.getAbsolutePath();
        }
        return "file:///android_asset/www/index.html";
    }

    static int activeWebVersion(Context c) {
        return startUrl(c).startsWith("file:///android_asset") ? bundledWebVersion(c) : installedWebVersion(c);
    }

    String source() {
        String u = prefs(act).getString("url", null);
        if (u == null) {
            try (InputStream in = act.getAssets().open("update_url.txt")) {
                u = new BufferedReader(new InputStreamReader(in)).readLine();
            } catch (Exception e) { u = ""; }
        }
        return u == null ? "" : u.trim();
    }

    void setSource(String url) {
        prefs(act).edit().putString("url", url == null ? "" : url.trim()).apply();
    }

    /** Called on start and every few hours. */
    void schedule() {
        act.getWindow().getDecorView().postDelayed(new Runnable() {
            @Override public void run() {
                check(false);
                act.getWindow().getDecorView().postDelayed(this, CHECK_EVERY_MS);
            }
        }, 15000);
    }

    private void status(String state, String msg) {
        try {
            JSONObject o = new JSONObject().put("state", state).put("message", msg);
            if (lastFeed != null) o.put("versionName", lastFeed.optString("versionName")).put("notes", lastFeed.optString("notes"));
            act.runJs("window.onUpdate && window.onUpdate(" + o + ")");
        } catch (Exception ignored) { }
    }

    private String base() {
        String s = source();
        if (s.endsWith("update.json")) s = s.substring(0, s.length() - "update.json".length());
        if (!s.isEmpty() && !s.endsWith("/")) s += "/";
        return s;
    }

    void check(boolean userAsked) {
        if (busy) return;
        if (source().isEmpty()) { if (userAsked) status("error", "Set an update source first"); return; }
        busy = true;
        new Thread(() -> {
            try {
                if (userAsked) status("checking", "Checking for updates…");
                JSONObject f = new JSONObject(new String(get(base() + "update.json"), StandardCharsets.UTF_8));
                lastFeed = f;
                boolean webDone = false;
                int webV = f.optInt("webVersion", 0);
                if (webV > activeWebVersion(act) && appVersion(act) >= f.optInt("webMinApp", 0)) {
                    applyWeb(get(base() + f.optString("web", "web.zip")), webV, f.optInt("webMinApp", 0));
                    webDone = true;
                }
                boolean appNew = f.optInt("versionCode", 0) > appVersion(act);
                pendingApp = appNew ? f.optString("versionName") : null;
                if (webDone) {
                    // reload with the new screens; the page asks for any pending app update when it starts
                    act.runOnUiThread(act::reloadWeb);
                } else if (appNew) {
                    status("app-available", "Version " + f.optString("versionName") + " is available");
                } else if (userAsked) {
                    status("none", "You have the latest version");
                }
            } catch (Exception e) {
                if (userAsked) status("error", "Could not check: " + e.getMessage());
            }
            busy = false;
        }, "updater").start();
    }

    private void applyWeb(byte[] zip, int version, int minApp) throws Exception {
        File tmp = new File(act.getFilesDir(), "www_new");
        deleteAll(tmp);
        tmp.mkdirs();
        String root = tmp.getCanonicalPath() + File.separator;
        try (ZipInputStream z = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = z.getNextEntry()) != null) {
                File out = new File(tmp, e.getName());
                if (!out.getCanonicalPath().startsWith(root)) throw new SecurityException("bad zip entry");
                if (e.isDirectory()) { out.mkdirs(); continue; }
                out.getParentFile().mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = z.read(buf)) > 0) os.write(buf, 0, n);
                }
            }
        }
        if (!new File(tmp, "index.html").exists()) throw new IllegalStateException("update has no index.html");
        File dir = webDir(act);
        deleteAll(dir);
        if (!tmp.renameTo(dir)) throw new IllegalStateException("could not apply update");
        prefs(act).edit().putInt("web_version", version).putInt("web_min_app", minApp).apply();
    }

    /** Downloads the new APK and hands it to Android's installer (you confirm on screen). */
    void installApp() {
        if (busy) return;
        if (lastFeed == null) { check(true); return; }
        busy = true;
        new Thread(() -> {
            try {
                status("downloading", "Downloading update…");
                byte[] apk = get(base() + lastFeed.optString("apk", "PoloLauncher.apk"));
                PackageInstaller pi = act.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                p.setAppPackageName(act.getPackageName());
                int id = pi.createSession(p);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    try (OutputStream os = s.openWrite("base.apk", 0, apk.length)) {
                        os.write(apk);
                        s.fsync(os);
                    }
                    Intent cb = new Intent(act, InstallResult.class);
                    int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT;
                    s.commit(PendingIntent.getBroadcast(act, id, cb, flags).getIntentSender());
                }
                status("installing", "Confirm the install on screen");
            } catch (Exception e) {
                status("error", "Update failed: " + e.getMessage());
            }
            busy = false;
        }, "installer").start();
    }

    /** Receives the installer's answer; shows Android's confirmation screen when needed. */
    public static class InstallResult extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { c.startActivity(confirm); } catch (Exception ignored) { }
                }
            } else if (st != PackageInstaller.STATUS_SUCCESS) {
                android.widget.Toast.makeText(c, "Update not installed: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        int code = c.getResponseCode();
        if (code != 200) throw new IllegalStateException("HTTP " + code + " for " + url);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally { c.disconnect(); }
    }

    private static void deleteAll(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        f.delete();
    }
}
