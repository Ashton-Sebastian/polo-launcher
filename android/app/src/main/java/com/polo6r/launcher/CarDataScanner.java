package com.polo6r.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * "Find car data": works out how this head unit publishes CAN-bus data (fuel, range, trip computer).
 * 1. Looks through settings, system properties and the car/vendor apps for broadcast actions,
 *    content providers and data keys.
 * 2. Listens to every candidate broadcast and logs what arrives while the car is running.
 * The report can be saved to the Download folder and used to wire up the real data.
 */
class CarDataScanner {
    interface Js { void run(String code); }

    private static final Pattern PKG_HINT = Pattern.compile(
            "(?i)(can|car|fuel|vehicle|mcu|desay|syu|fyt|hct|mtc|microntek|autochips|ecar|cbus|obd|trip|radio|setting|launcher)");
    private static final Pattern DATA_HINT = Pattern.compile(
            "(?i)(fuel|\\boil|_oil|oil_|mileage|odometer|\\bodo|\\btrip|_trip|trip_|\\brange|_range|range_|vehicle|canbus|\\bcan_|_can_|\\.can\\.|carinfo|car_info|carinf|\\bmcu|_mcu|consum|\\btank|_tank|carspeed|car_speed|vehicle_speed|outtemp|out_temp|outside_temp|endurance|remain_|restmil)");
    private static final Pattern DOTTED = Pattern.compile("^[a-zA-Z][\\w]*(\\.[\\w]+){2,}$");
    private static final Pattern KEYISH = Pattern.compile("^[A-Za-z_][\\w]{3,60}$");

    private final Activity act;
    private final Js js;
    private final StringBuilder report = new StringBuilder();
    private final List<String> sniff = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Long> lastLogged = new HashMap<>();
    private BroadcastReceiver receiver;
    private volatile boolean running;

    CarDataScanner(Activity act, Js js) {
        this.act = act;
        this.js = js;
    }

    void start(String webViewUa) {
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                scan(webViewUa);
            } catch (Throwable t) {
                line("\nScan error: " + t);
                js.run("window.onScanDone && window.onScanDone(" + JSONObject.quote(report.toString()) + ",0)");
            }
            running = false;
        }, "car-scan").start();
    }

    private void line(String s) { synchronized (report) { report.append(s).append('\n'); } }
    private void section(String s) { line("\n== " + s + " =="); }

    private void scan(String ua) {
        stopSniff();
        synchronized (report) { report.setLength(0); }
        line("== Polo Launcher car data report " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()) + " ==");
        line("Unit: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + "), board " + Build.BOARD + ", hw " + Build.HARDWARE);
        line("Android " + Build.VERSION.RELEASE + " / SDK " + Build.VERSION.SDK_INT + ", build " + Build.DISPLAY);
        line("WebView: " + ua);

        section("Settings that look car-related");
        for (String t : new String[]{"system", "global", "secure"}) dumpSettings(t);

        section("System properties that look car-related");
        getprop();

        section("Car / vendor apps");
        PackageManager pm = act.getPackageManager();
        Set<String> actions = new TreeSet<>();
        for (PackageInfo p : pm.getInstalledPackages(0)) {
            String n = p.packageName;
            if (n.equals(act.getPackageName()) || !PKG_HINT.matcher(n).find()) continue;
            if (n.startsWith("com.google.android.") && !n.contains("car")) continue;
            try {
                int flags = PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS;
                PackageInfo f = pm.getPackageInfo(n, flags);
                boolean sys = (f.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                line("\n# " + n + "  v" + f.versionName + (sys ? "  [system]" : "") + "  " + f.applicationInfo.sourceDir);
                if (f.providers != null) for (ProviderInfo pi : f.providers)
                    line("  provider  content://" + pi.authority + (pi.exported ? "" : "  (private)"));
                if (f.services != null) for (ServiceInfo si : f.services)
                    if (si.exported) line("  service   " + si.name);
                if (f.receivers != null) for (ActivityInfo ri : f.receivers)
                    if (ri.exported) line("  receiver  " + ri.name);
                for (String s : dexStrings(f.applicationInfo.sourceDir)) {
                    line("  string    " + s);
                    if (looksLikeAction(s)) actions.add(s);
                }
            } catch (Throwable t) {
                line("  (could not read: " + t.getClass().getSimpleName() + ")");
            }
        }

        List<String> list = new ArrayList<>(actions);
        if (list.size() > 400) list = list.subList(0, 400);
        section("Listening for live car messages");
        line(list.size() + " candidate broadcast actions");
        startSniff(list);
        js.run("window.onScanDone && window.onScanDone(" + JSONObject.quote(report.toString()) + "," + list.size() + ")");
    }

    private void dumpSettings(String table) {
        try (Cursor c = act.getContentResolver().query(Uri.parse("content://settings/" + table),
                new String[]{"name", "value"}, null, null, null)) {
            if (c == null) return;
            int n = 0;
            while (c.moveToNext() && n < 200) {
                String k = c.getString(0), v = c.getString(1);
                if (k != null && DATA_HINT.matcher(k).find()) { line(table + ": " + k + " = " + v); n++; }
            }
        } catch (Throwable t) {
            line(table + ": (not readable)");
        }
    }

    private void getprop() {
        try {
            Process p = Runtime.getRuntime().exec("getprop");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l;
                int n = 0;
                while ((l = r.readLine()) != null && n < 200) {
                    if (DATA_HINT.matcher(l).find() || l.contains("canbus") || l.contains("desay")) { line(l); n++; }
                }
            }
        } catch (Throwable t) {
            line("(getprop not available)");
        }
    }

    /** Printable strings inside the app's code that look like broadcast actions, content URIs or data keys. */
    private static Set<String> dexStrings(String apk) {
        Set<String> res = new LinkedHashSet<>();
        try (ZipFile z = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.getName().matches("classes\\d*\\.dex")) continue;
                byte[] b = readAll(z.getInputStream(e));
                int start = -1;
                for (int i = 0; i <= b.length; i++) {
                    int c = i < b.length ? b[i] & 0xff : 0;
                    if (c >= 0x21 && c < 0x7f) {
                        if (start < 0) start = i;
                    } else if (start >= 0) {
                        int len = i - start;
                        if (len >= 8 && len < 160) {
                            String s = new String(b, start, len, StandardCharsets.US_ASCII);
                            if (interesting(s)) {
                                res.add(s);
                                if (res.size() >= 150) return res;
                            }
                        }
                        start = -1;
                    }
                }
            }
        } catch (Throwable ignored) { }
        return res;
    }

    private static boolean interesting(String s) {
        if (!DATA_HINT.matcher(s).find()) return false;
        if (s.startsWith("content://")) return true;
        if (DOTTED.matcher(s).matches()) return true;
        return KEYISH.matcher(s).matches() && (s.contains("_") || Character.isUpperCase(s.charAt(0)));
    }

    private static boolean looksLikeAction(String s) {
        if (!DOTTED.matcher(s).matches()) return false;
        String last = s.substring(s.lastIndexOf('.') + 1);
        return s.contains("ACTION") || s.contains(".action.") || last.matches("[A-Z0-9_]{3,}");
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }

    private void startSniff(List<String> actions) {
        if (actions.isEmpty()) return;
        IntentFilter f = new IntentFilter();
        for (String a : actions) f.addAction(a);
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { log(i); }
        };
        act.runOnUiThread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= 33) act.getApplicationContext().registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
                else act.getApplicationContext().registerReceiver(receiver, f);
            } catch (Throwable t) { line("(could not listen: " + t + ")"); }
        });
    }

    private void log(Intent i) {
        String a = String.valueOf(i.getAction());
        long now = System.currentTimeMillis();
        Long prev = lastLogged.get(a);
        if (prev != null && now - prev < 1000) return; // at most once a second per message type
        lastLogged.put(a, now);
        StringBuilder sb = new StringBuilder(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()))
                .append("  ").append(a);
        Bundle x = i.getExtras();
        if (x != null) {
            for (String k : x.keySet()) {
                Object v;
                try { v = x.get(k); } catch (Throwable t) { v = "?"; }
                if (v instanceof int[]) v = java.util.Arrays.toString((int[]) v);
                else if (v instanceof byte[]) v = java.util.Arrays.toString((byte[]) v);
                sb.append("  ").append(k).append('=').append(v);
                if (sb.length() > 500) break;
            }
        }
        String l = sb.toString();
        sniff.add(l);
        if (sniff.size() > 2000) sniff.remove(0);
        js.run("window.onSniff && window.onSniff(" + JSONObject.quote(l) + ")");
    }

    void stopSniff() {
        if (receiver != null) {
            try { act.getApplicationContext().unregisterReceiver(receiver); } catch (Throwable ignored) { }
            receiver = null;
        }
    }

    /** Saves report + live log to the Download folder. Returns a message for the user. */
    String save() {
        StringBuilder all = new StringBuilder();
        synchronized (report) { all.append(report); }
        all.append("\n== Live messages (").append(sniff.size()).append(") ==\n");
        synchronized (sniff) { for (String s : sniff) all.append(s).append('\n'); }
        if (report.length() == 0) return "Tap Start scan first";
        String name = "polo-car-report-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
        byte[] data = all.toString().getBytes(StandardCharsets.UTF_8);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = act.getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) return "Could not save the report";
                try (OutputStream os = cr.openOutputStream(u)) { os.write(data); }
            } else {
                if (Build.VERSION.SDK_INT >= 23 && act.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    act.runOnUiThread(() -> act.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7));
                    return "Allow storage access, then tap Save report again";
                }
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                dir.mkdirs();
                try (FileOutputStream os = new FileOutputStream(new File(dir, name))) { os.write(data); }
            }
            return "Saved to Download/" + name;
        } catch (Throwable t) {
            return "Could not save: " + t.getMessage();
        }
    }
}
