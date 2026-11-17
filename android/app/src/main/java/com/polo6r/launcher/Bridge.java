package com.polo6r.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Methods the web UI can call as window.PoloBridge.xxx(). */
public class Bridge {
    private final Activity act;
    private final VehicleHub hub;
    private final PackageManager pm;

    private final CarDataScanner scanner;
    private final MainActivity main;

    Bridge(MainActivity act, VehicleHub hub) {
        this.act = act;
        this.hub = hub;
        this.pm = act.getPackageManager();
        this.scanner = new CarDataScanner(act, act::runJs);
        this.main = act;
    }

    /* ---------- Home button fix (accessibility service) ---------- */

    @JavascriptInterface
    public String homeFixStatus() {
        try {
            JSONObject o = new JSONObject().put("on", HomeRedirectService.instance != null);
            JSONArray targets = new JSONArray();
            Set<String> seen = new HashSet<>();
            for (String[] a : HomeRedirectService.otherHomeActivities(act)) {
                if (seen.add(a[0])) targets.put(new JSONObject().put("pkg", a[0]).put("label", a[2]));
            }
            o.put("targets", targets);
            android.content.SharedPreferences p = HomeRedirectService.prefs(act);
            o.put("extra", new JSONArray(p.getStringSet(HomeRedirectService.KEY_EXTRA, new HashSet<>())));
            JSONArray recent = new JSONArray();
            for (String pkg : p.getString(HomeRedirectService.KEY_RECENT, "").split(",")) {
                if (pkg.isEmpty()) continue;
                String label = pkg;
                try { label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))); } catch (Exception ignored) { }
                recent.put(new JSONObject().put("pkg", pkg).put("label", label));
            }
            o.put("recent", recent);
            return o.toString();
        } catch (Exception e) {
            return "{\"on\":false}";
        }
    }

    @JavascriptInterface
    public void openAccessibilitySettings() {
        act.runOnUiThread(() -> {
            Intent i = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { act.startActivity(i); } catch (Exception e) { openHomeSettings(act); }
        });
    }

    /** Mark / unmark an app as "the old home screen" so it is always replaced by Polo Launcher. */
    @JavascriptInterface
    public void setHomeRedirect(String pkg, boolean on) {
        android.content.SharedPreferences p = HomeRedirectService.prefs(act);
        Set<String> s = new HashSet<>(p.getStringSet(HomeRedirectService.KEY_EXTRA, new HashSet<>()));
        if (on) s.add(pkg); else s.remove(pkg);
        p.edit().putStringSet(HomeRedirectService.KEY_EXTRA, s).apply();
        HomeRedirectService svc = HomeRedirectService.instance;
        if (svc != null) svc.reload();
    }

    /** "back" / "recents" for the side bar (needs the Home button fix switched on). */
    @JavascriptInterface
    public boolean globalAction(String name) {
        return HomeRedirectService.global(name);
    }

    /* ---------- Updates ---------- */

    @JavascriptInterface
    public String getUpdateInfo() {
        try {
            return new JSONObject()
                    .put("source", main.updater.source())
                    .put("appVersion", Updater.appVersionName(act) + " (" + Updater.appVersion(act) + ")")
                    .put("webVersion", Updater.activeWebVersion(act))
                    .toString();
        } catch (Exception e) { return "{}"; }
    }

    @JavascriptInterface
    public void setUpdateSource(String url) { main.updater.setSource(url); }

    @JavascriptInterface
    public void checkUpdates() { main.updater.check(true); }

    @JavascriptInterface
    public void installUpdate() { main.updater.installApp(); }

    /** Version name of an app update waiting to be installed, or "" (also tells the page if screens were just updated). */
    @JavascriptInterface
    public String pendingUpdate() { String p = main.updater.pendingApp; return p == null ? "" : p; }

    /* ---------- Find car data ---------- */

    @JavascriptInterface
    public void startCarScan(String webViewUa) {
        scanner.start(webViewUa);
    }

    @JavascriptInterface
    public String saveCarReport() {
        return scanner.save();
    }

    /* ---------- apps ---------- */

    @JavascriptInterface
    public String getApps() {
        JSONArray out = new JSONArray();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(act.getPackageName()) || !seen.add(pkg)) continue;
            try {
                out.put(new JSONObject().put("pkg", pkg).put("label", String.valueOf(ri.loadLabel(pm))));
            } catch (Exception ignored) { }
        }
        return out.toString();
    }

    @JavascriptInterface
    public String getIcon(String pkg) {
        try {
            Drawable d = pm.getApplicationIcon(pkg);
            int size = 96;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, size, size);
            d.draw(c);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, bos);
            return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public boolean launch(String pkg) {
        Intent i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        act.runOnUiThread(() -> {
            try { act.startActivity(i); } catch (Exception ignored) { }
        });
        return true;
    }

    @JavascriptInterface
    public boolean openAction(String action) {
        Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (i.resolveActivity(pm) == null) return false;
        act.runOnUiThread(() -> {
            try { act.startActivity(i); } catch (ActivityNotFoundException ignored) { }
        });
        return true;
    }

    /* ---------- default home screen ---------- */

    @JavascriptInterface
    public boolean isDefaultLauncher() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && act.getPackageName().equals(ri.activityInfo.packageName);
    }

    /** Ask Android to make us the Home app: system dialog on Android 10+, otherwise the Home settings page. */
    @JavascriptInterface
    public void requestDefaultLauncher() {
        act.runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    android.app.role.RoleManager rm = act.getSystemService(android.app.role.RoleManager.class);
                    if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)
                            && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                        act.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),
                                MainActivity.REQ_DEFAULT_HOME);
                        return;
                    }
                } catch (Exception ignored) { }
            }
            openHomeSettings(act);
        });
    }

    static void openHomeSettings(Activity act) {
        String[] actions = {android.provider.Settings.ACTION_HOME_SETTINGS,
                "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", android.provider.Settings.ACTION_SETTINGS};
        for (String a : actions) {
            Intent i = new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(act.getPackageManager()) == null) continue;
            try { act.startActivity(i); return; } catch (Exception ignored) { }
        }
    }

    /* ---------- volume ---------- */

    @JavascriptInterface
    public void volume(int dir) {
        AudioManager am = (AudioManager) act.getSystemService(Activity.AUDIO_SERVICE);
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                dir > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
    }

    @JavascriptInterface
    public boolean toggleMute() {
        AudioManager am = (AudioManager) act.getSystemService(Activity.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= 23) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
            return am.isStreamMute(AudioManager.STREAM_MUSIC);
        }
        return false;
    }

    /* ---------- vehicle ---------- */

    /** Everything Car Info sends (all CarInfo fields with a value + raw CAN frames), for mapping / diagnostics. */
    @JavascriptInterface
    public void resetLongTerm() { hub.resetLongTerm(); }

    @JavascriptInterface
    public String getRawCarData() { return hub.rawCarData(); }

    @JavascriptInterface
    public String getVehicle() {
        return hub.snapshot();
    }

    /** The VW logo on start-up is now drawn natively (SplashView), so the page never shows its own. */
    @JavascriptInterface
    public boolean isFirstStart() { return false; }

    @JavascriptInterface
    public String getNetworkStatus() { return NetStatus.json(act); }

    @JavascriptInterface
    public boolean getSplashOnStart() { return SplashActivity.enabled(act); }

    @JavascriptInterface
    public void setSplashOnStart(boolean on) {
        HomeRedirectService.prefs(act).edit().putBoolean(SplashActivity.KEY_ON, on).apply();
    }

    @JavascriptInterface
    public void showSplash() { SplashActivity.show(act); }

    /* ---------- diagnostics (helps wire up the real CAN data) ---------- */

    @JavascriptInterface
    public String getDeviceInfo() {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            act.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            return new JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("device", Build.DEVICE)
                    .put("brand", Build.BRAND)
                    .put("android", Build.VERSION.RELEASE)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("screen", dm.widthPixels + "x" + dm.heightPixels + " @" + dm.densityDpi + "dpi")
                    .put("vehicleStatus", hub.status())
                    .toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Installed packages whose name hints at car / CAN / MCU / radio services (incl. hidden system apps). */
    @JavascriptInterface
    public String getCarPackages() {
        String[] hints = {"can", "car", "vehicle", "desay", "mcu", "radio", "bt", "blue", "link", "auto",
                "obd", "trip", "drive", "media", "music", "video", "launcher", "setting", "factory", "logo", "hct", "fyt", "syu", "ts"};
        List<String> out = new ArrayList<>();
        for (PackageInfo p : pm.getInstalledPackages(0)) {
            String n = p.packageName.toLowerCase(Locale.ROOT);
            if (n.startsWith("com.google.") || n.startsWith("com.android.internal")) continue;
            for (String h : hints) {
                if (n.contains(h)) { out.add(p.packageName); break; }
            }
        }
        Collections.sort(out);
        return new JSONArray(out).toString();
    }
}
