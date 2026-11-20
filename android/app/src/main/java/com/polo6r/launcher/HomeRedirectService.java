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
    private static final String PHONE_PKG = "com.nwd.android.phone", ZLINK_PKG = "com.zjinnova.zlink";
    private long lastPhoneRedirect;

    /** The unit keeps the Bluetooth phone state in Settings.System bt_phone_status (0 = no call). */
    private boolean btCallIdle() {
        return android.provider.Settings.System.getInt(getContentResolver(), "bt_phone_status", 0) == 0;
    }
    private String lastPkg = "";

    private android.content.BroadcastReceiver screenRx;

    @Override
    protected void onServiceConnected() {
        instance = this;
        reload();
        // Car switched on = head unit wakes up: show the VW logo, then return to what was on screen.
        screenRx = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) SplashActivity.onScreenOff(c);
                else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) SplashActivity.maybeShow(c);

            }
        };
        android.content.IntentFilter f = new android.content.IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenRx, f);
        // the unit may close apps while the car is off; if we were restarted after the car came on, show the logo now
        SplashActivity.maybeShow(this);
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

    /**
     * Kept in device storage (readable right after a restart, before Android unlocks), because the unit starts this
     * service that early. Moved over once from the old location.
     */
    static SharedPreferences prefs(Context c) {
        Context dp = c.createDeviceProtectedStorageContext();
        SharedPreferences p = dp.getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.getBoolean("_moved", false) && MainActivity.isUnlocked(c)) {
            try { dp.moveSharedPreferencesFrom(c, PREFS); } catch (Exception ignored) { }
            p = dp.getSharedPreferences(PREFS, MODE_PRIVATE);
            p.edit().putBoolean("_moved", true).apply();
        }
        return p;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        String cls = e.getClassName() == null ? "" : e.getClassName().toString();
        if (!pkg.equals("com.android.systemui")) CanBridge.carInfoInFront = pkg.equals("com.nwd.can.setting");
        if (pkg.equals("com.nwd.can.help")) CanBridge.onCanWarning(); // its "Can device is abnormal" screen
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;

        if (!pkg.equals(lastPkg)) {
            lastPkg = pkg;
            remember(pkg);
        }
        boolean isOldHome = homeComponents.contains(pkg + "/" + cls) || extraPkgs.contains(pkg);
        long now = System.currentTimeMillis();
        // the unit's Phone button (stock phone app) -> CarPlay (ZLink), unless a Bluetooth call is ringing or active.
        // Pressing Phone again within 10 s opens the stock phone app (e.g. to pair a phone).
        if (pkg.equals(PHONE_PKG) && btCallIdle() && now - lastPhoneRedirect > 10000) {
            lastPhoneRedirect = now;
            Intent z = getPackageManager().getLaunchIntentForPackage(ZLINK_PKG);
            if (z != null) {
                z.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                try { startActivity(z); } catch (Exception ignored) { }
                return;
            }
        }
        // the unit's radio button (and the stock radio app) -> Polo Launcher's own radio screen
        if ((pkg.equals("com.nwd.radio") || pkg.equals("com.nwd.radiostation")) && now - lastRedirect > 700) {
            lastRedirect = now;
            Intent i = new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_SCREEN, "radio")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            try { startActivity(i); } catch (Exception ignored) { }
            return;
        }
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

    /** Switch the Home button fix back on if the unit turned it off (only if WRITE_SECURE_SETTINGS was granted). */
    static void ensureEnabled(Context c) {
        try {
            if (c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return;
            String me = c.getPackageName() + "/" + HomeRedirectService.class.getName();
            android.content.ContentResolver cr = c.getContentResolver();
            String list = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (list == null || list.equals("null")) list = "";
            boolean listed = false;
            for (String x : list.split(":")) if (x.equalsIgnoreCase(me)) listed = true;
            if (!listed) {
                android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        list.isEmpty() ? me : list + ":" + me);
            }
            if (!listed || android.provider.Settings.Secure.getInt(cr, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
                android.provider.Settings.Secure.putInt(cr, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            }
        } catch (Exception ignored) { }
    }

    private android.view.View logoView;

    /**
     * Draw the VW logo as an accessibility overlay on top of whatever is on screen. Unlike opening a screen,
     * this doesn't make the NWD firmware switch audio source (which caused a sound from the speakers).
     */
    void showLogoOverlay() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (logoView != null) return;
            try {
                final android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
                android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(
                        android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT,
                        android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                | android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                                | android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        android.graphics.PixelFormat.TRANSLUCENT);
                logoView = new SplashView(this,
                        () -> MainActivity.instance == null || MainActivity.webReadyAt > 0,
                        () -> { try { wm.removeView(logoView); } catch (Exception ignored) { } logoView = null; });
                wm.addView(logoView, lp);
            } catch (Exception e) {
                logoView = null;
                SplashActivity.startActivity(this); // fallback
            }
        });
    }

    static boolean global(String name) {
        HomeRedirectService s = instance;
        if (s == null) return false;
        switch (name) {
            case "back": return s.performGlobalAction(GLOBAL_ACTION_BACK);
            case "recents": return s.performGlobalAction(GLOBAL_ACTION_RECENTS);
            case "notifications": return s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
            case "power": return s.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); // Restart / Power off menu
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
