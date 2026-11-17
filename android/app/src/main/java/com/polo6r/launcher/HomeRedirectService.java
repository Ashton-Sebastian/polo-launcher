package com.polo6r.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * "Home button fix". Many head units send the Home key straight to their own launcher, ignoring the
 * default-home setting. When this accessibility service sees the old home screen appear, it brings
 * Polo Launcher to the front instead. It also lets the side bar perform Back / Recent apps.
 */
public class HomeRedirectService extends AccessibilityService {
    static final String PREFS = "polo_prefs";
    static final String KEY_EXTRA = "extra_redirect";   // packages the user marked as "old home screen"
    static final String KEY_RECENT = "recent_pkgs";     // last screens seen (to help pick the old launcher)

    static volatile HomeRedirectService instance;

    private final Set<String> homeComponents = new HashSet<>();
    private final Set<String> extraPkgs = new HashSet<>();
    private long lastRedirect;
    private String lastPkg = "";

    private android.content.BroadcastReceiver screenRx;
    private long lastScreenOff;

    @Override
    protected void onServiceConnected() {
        instance = this;
        reload();
        // Car switched on = head unit wakes up: show the VW logo, then return to what was on screen.
        screenRx = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                long now = System.currentTimeMillis();
                if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) { lastScreenOff = now; return; }
                if (Intent.ACTION_SCREEN_ON.equals(i.getAction()) && lastScreenOff > 0
                        && now - lastScreenOff > 3000 && SplashActivity.enabled(c)) {
                    SplashActivity.show(c);
                }
            }
        };
        android.content.IntentFilter f = new android.content.IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenRx, f);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    /** Other apps' HOME activities ("pkg/class"), excluding ours and the Settings fallback. */
    static List<String[]> otherHomeActivities(Context c) {
        List<String[]> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || pkg.equals("com.android.settings")) continue;
            out.add(new String[]{pkg, ri.activityInfo.name, String.valueOf(ri.loadLabel(pm))});
            if (ri.activityInfo.targetActivity != null) {
                out.add(new String[]{pkg, ri.activityInfo.targetActivity, String.valueOf(ri.loadLabel(pm))});
            }
        }
        return out;
    }

    void reload() {
        homeComponents.clear();
        for (String[] a : otherHomeActivities(this)) homeComponents.add(a[0] + "/" + a[1]);
        extraPkgs.clear();
        extraPkgs.addAll(prefs(this).getStringSet(KEY_EXTRA, new HashSet<>()));
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        String cls = e.getClassName() == null ? "" : e.getClassName().toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;

        if (!pkg.equals(lastPkg)) {
            lastPkg = pkg;
            remember(pkg);
        }
        boolean isOldHome = homeComponents.contains(pkg + "/" + cls) || extraPkgs.contains(pkg);
        long now = System.currentTimeMillis();
        if (isOldHome && now - lastRedirect > 700) {
            lastRedirect = now;
            Intent i = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            try { startActivity(i); } catch (Exception ignored) { }
        }
    }

    private void remember(String pkg) {
        SharedPreferences p = prefs(this);
        LinkedList<String> list = new LinkedList<>(Arrays.asList(p.getString(KEY_RECENT, "").split(",")));
        list.remove("");
        list.remove(pkg);
        list.addFirst(pkg);
        while (list.size() > 10) list.removeLast();
        p.edit().putString(KEY_RECENT, String.join(",", list)).apply();
    }

    /**
     * Primary path: catch the Home key itself before Android acts on it, so the old launcher is
     * never started and only Polo Launcher appears (no flash).
     */
    @Override
    protected boolean onKeyEvent(android.view.KeyEvent e) {
        if (e.getKeyCode() != android.view.KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == android.view.KeyEvent.ACTION_UP && !e.isCanceled()) {
            lastRedirect = System.currentTimeMillis();
            Intent i = new Intent(this, MainActivity.class)
                    .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            try { startActivity(i); } catch (Exception ignored) { }
        }
        return true; // consumed: Android will not start the old home screen
    }

    static boolean global(String name) {
        HomeRedirectService s = instance;
        if (s == null) return false;
        switch (name) {
            case "back": return s.performGlobalAction(GLOBAL_ACTION_BACK);
            case "recents": return s.performGlobalAction(GLOBAL_ACTION_RECENTS);
            case "notifications": return s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
            default: return false;
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        if (screenRx != null) { try { unregisterReceiver(screenRx); } catch (Exception ignored) { } screenRx = null; }
        instance = null;
        super.onDestroy();
    }
}
