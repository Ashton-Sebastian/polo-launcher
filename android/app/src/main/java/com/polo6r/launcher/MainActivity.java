package com.polo6r.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Home-screen activity: a full-screen WebView showing assets/www/index.html. */
public class MainActivity extends Activity {

    /** True only for the first launcher start in this process (i.e. right after boot) -> show VW splash. */
    static boolean firstStart = true;
    static volatile MainActivity instance;
    /** When the web screens last finished drawing (0 = not yet). */
    static volatile long webReadyAt;

    private WebView web;
    Updater updater;
    private VehicleHub hub;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HomeRedirectService.ensureEnabled(this);
        instance = this;
        webReadyAt = 0;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        WebView.setWebContentsDebuggingEnabled(true); // lets us inspect it over adb (chrome://inspect)
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(0, 6, 16));
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100); // ignore system font scaling so the layout matches the design
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                // the unit ran low on memory and closed the web view's renderer: rebuild instead of crashing
                webReadyAt = 0;
                recreate();
                return true;
            }
        });
        if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);

        hub = new VehicleHub(this, json -> runJs("window.onVehicle && window.onVehicle(" + json + ")"));
        web.addJavascriptInterface(new Bridge(this, hub), "PoloBridge");
        updater = new Updater(this);
        web.loadUrl(Updater.startUrl(this));
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(web);
        if (firstStart && SplashActivity.enabled(this) && System.currentTimeMillis() - SplashActivity.lastShown > 10000) {
            // launcher (re)started, e.g. the unit closed it while the car was off: VW logo until the screens are drawn
            SplashActivity.markShown(this);
            final SplashView[] sv = new SplashView[1];
            sv[0] = new SplashView(this, () -> webReadyAt > 0, () -> root.removeView(sv[0]));
            root.addView(sv[0]);
        }
        firstStart = false;
        setContentView(root);
        hub.start();
        updater.schedule();
        hideSystemBars();
    }

    /** Reload the screens (after a screen update was downloaded). */
    void reloadWeb() {
        if (web != null) { firstStart = false; webReadyAt = 0; web.loadUrl(Updater.startUrl(this)); }
    }

    void runJs(String js) {
        if (web != null) web.post(() -> web.evaluateJavascript(js, null));
    }

    static final int REQ_DEFAULT_HOME = 42;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DEFAULT_HOME) {
            // Dialog dismissed or unavailable on this unit -> fall back to the Home settings page
            if (resultCode != RESULT_OK) Bridge.openHomeSettings(this);
            runJs("window.refreshDefault && window.refreshDefault()");
        }
    }

    private int lastW = -1, lastH = -1, lastDpi = -1;

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        // If the screen size or density changes at runtime, rebuild the page so no stale
        // tiles from the old size stay on screen.
        if (lastW != -1 && (c.screenWidthDp != lastW || c.screenHeightDp != lastH || c.densityDpi != lastDpi)) {
            firstStart = false; // no splash on a rebuild
            if (web != null) web.reload();
        }
        lastW = c.screenWidthDp; lastH = c.screenHeightDp; lastDpi = c.densityDpi;
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (lastW == -1) {
            android.content.res.Configuration c = getResources().getConfiguration();
            lastW = c.screenWidthDp; lastH = c.screenHeightDp; lastDpi = c.densityDpi;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while we are already in front -> back to the first home page
        runJs("window.goHome && window.goHome()");
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeRedirectService.ensureEnabled(this);
        hideSystemBars();
        runJs("window.onResumeLauncher && window.onResumeLauncher()");
        if (updater != null) updater.checkSoon();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // A launcher never closes on Back; let the UI close sheets / go back to the home grid.
        runJs("window.onBack && window.onBack()");
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        if (hub != null) hub.stop();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
