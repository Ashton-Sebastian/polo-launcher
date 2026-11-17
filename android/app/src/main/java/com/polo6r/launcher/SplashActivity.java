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
        setContentView(new SplashView(this, this::finish));
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
