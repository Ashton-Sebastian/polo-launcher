package com.polo6r.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/**
 * Shown when the car is switched on (the unit wakes up). Plays the VW logo and closes itself,
 * so whatever was on screen before (CarPlay, radio, the launcher) comes straight back.
 */
public class SplashActivity extends Activity {
    static final String KEY_ON = "splash_on_start";

    static boolean enabled(Context c) {
        return HomeRedirectService.prefs(c).getBoolean(KEY_ON, true);
    }

    static final String KEY_OFF_AT = "screen_off_at", KEY_SHOWN = "logo_after_off";
    static volatile long lastShown;

    /** Car switched off (screen off): the next start gets the logo. Saved, so it survives the app being closed. */
    static void onScreenOff(Context c) {
        HomeRedirectService.prefs(c).edit().putLong(KEY_OFF_AT, System.currentTimeMillis()).putBoolean(KEY_SHOWN, false).apply();
    }

    static void markShown(Context c) {
        lastShown = System.currentTimeMillis();
        HomeRedirectService.prefs(c).edit().putBoolean(KEY_SHOWN, true).apply();
    }

    /** Show the logo if the car has been switched on since the last logo. */
    static void maybeShow(Context c) {
        android.content.SharedPreferences p = HomeRedirectService.prefs(c);
        long off = p.getLong(KEY_OFF_AT, 0), now = System.currentTimeMillis();
        android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE);
        if (!enabled(c) || p.getBoolean(KEY_SHOWN, false) || off == 0 || now - off < 3000) return;
        if (pm != null && !pm.isInteractive()) return;
        if (now - lastShown < 10000) return;
        markShown(c);
        show(c);
    }

    static void show(Context c) {
        Intent i = new Intent(c, SplashActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try { c.startActivity(i); } catch (Exception ignored) { }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        lastShown = System.currentTimeMillis();
        // if the launcher is starting underneath, wait until its screens are drawn
        setContentView(new SplashView(this, () -> MainActivity.instance == null || MainActivity.webReadyAt > 0, this::finish));
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() { /* let it finish on its own */ }
}
