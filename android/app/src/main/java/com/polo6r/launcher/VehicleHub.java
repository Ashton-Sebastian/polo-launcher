package com.polo6r.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Collects vehicle data (fuel, range, trip computer) from the head unit's CAN-bus app and pushes it to the UI.
 *
 * The source is configured in assets/vehicle_sources.json: each entry names a broadcast intent action
 * the vendor app sends and which extras hold which value. Until a real source delivers data, the UI
 * shows clearly-labelled demo values instead.
 */
public class VehicleHub {
    public interface Listener { void onData(String json); }

    private final Context ctx;
    private final Listener listener;
    private final List<BroadcastReceiver> receivers = new ArrayList<>();
    private final JSONObject state = new JSONObject();
    private int configured = 0;
    private long lastUpdate = 0;
    private CanBridge can;
    private TripComputer trip;
    private JSONObject lastMapped;
    private final android.os.Handler ticker = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean screenOn = true;
    // Live battery voltage from the unit's core service (com.nwd.kernel): ask, it answers with a broadcast.
    private static final String ACT_QUERY_VOLT = "com.nwd.action.ACTION_QUERY_CAN_BATTERY_VOLTAGE";
    private static final String ACT_VOLT = "com.nwd.action.ACTION_CAN_BATTERY_VOLTAGE";
    private volatile double liveVolt = Double.NaN;
    private volatile long lastCanVoltAt;
    private int voltLogs = 0, tickCount = 0;
    private BroadcastReceiver voltRx;
    private android.content.BroadcastReceiver screenRx;

    VehicleHub(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        // Live data from the unit's Car Info app (NWD firmware)
        trip = new TripComputer(ctx);
        screenRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                boolean on = Intent.ACTION_SCREEN_ON.equals(i.getAction());
                screenOn = on;
                trip.onScreen(on);
                if (on && can != null) { can.onCarOn(); can.requestCarData(); }
                if (on) HomeRedirectService.ensureEnabled(c); // turns the fix back on if allowed (it then shows the logo)
            }
        };
        IntentFilter sf = new IntentFilter(Intent.ACTION_SCREEN_ON);
        sf.addAction(Intent.ACTION_SCREEN_OFF);
        ctx.registerReceiver(screenRx, sf);
        // every second while the car is on: ask the CAN box for fresh car data (rpm, doors, fuel...) and the unit for
        // the battery voltage; every 2 s update the trip computer even if nothing changed (time keeps running)
        ticker.postDelayed(new Runnable() {
            @Override public void run() {
                if (screenOn) {
                    if (can != null) can.requestCarData();
                    try { ctx.sendBroadcast(new Intent(ACT_QUERY_VOLT)); } catch (Exception ignored) { }
                    JSONObject m = lastMapped;
                    if (m != null && (tickCount++ % 2 == 0)) push(m);
                }
                ticker.postDelayed(this, 1000);
            }
        }, 1000);
        can = new CanBridge(ctx, (mapped, raw) -> {
            lastMapped = mapped;
            if (mapped.has("battery_v")) lastCanVoltAt = System.currentTimeMillis();
            push(mapped);
        });
        can.start();
        voltRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                Bundle x = i.getExtras();
                if (x == null) return;
                Object v = x.get("extra_voltage_value");
                if (voltLogs++ < 5) android.util.Log.i("PoloCan", "voltage reply " + x.keySet() + " value=" + v + " (" + (v == null ? "null" : v.getClass().getSimpleName()) + ")");
                double d;
                if (v instanceof Number) d = ((Number) v).doubleValue();
                else { try { d = Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return; } }
                while (d > 40) d /= 10;           // 123 -> 12.3, 1234 -> 12.34
                if (d < 5) return;                 // not a usable reading
                liveVolt = Math.round(d * 10) / 10.0;
                JSONObject m = lastMapped;
                if (m != null) push(m);
            }
        };
        IntentFilter vf = new IntentFilter(ACT_VOLT);
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(voltRx, vf, Context.RECEIVER_EXPORTED);
        else ctx.registerReceiver(voltRx, vf);
        startBroadcastSources();
    }

    private void push(JSONObject mapped) {
        {
            JSONObject out = new JSONObject();
            synchronized (VehicleHub.this) {
                try {
                    // values the car sends win; the trip computer fills in what it doesn't send
                    JSONObject computed = trip.update(mapped);
                    java.util.Iterator<String> it = computed.keys();
                    while (it.hasNext()) { String k = it.next(); out.put(k, computed.get(k)); }
                    it = mapped.keys();
                    while (it.hasNext()) { String k = it.next(); out.put(k, mapped.get(k)); }
                    // the car's own battery reading (CAN) while it is arriving; otherwise the head unit's supply reading
                    boolean canFresh = System.currentTimeMillis() - lastCanVoltAt < 5000;
                    if (!canFresh && !Double.isNaN(liveVolt)) out.put("battery_v", liveVolt);
                    it = out.keys();
                    while (it.hasNext()) { String k = it.next(); state.put(k, out.get(k)); }
                } catch (Exception ignored) { }
                lastUpdate = System.currentTimeMillis();
            }
            if (out.length() > 0) listener.onData(out.toString());
        }
    }

    /** Optional extra data sources from assets/vehicle_sources.json (broadcast based). */
    private void startBroadcastSources() {
        JSONObject cfg = loadConfig();
        if (cfg == null) return;
        JSONArray list = cfg.optJSONArray("broadcasts");
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            JSONObject e = list.optJSONObject(i);
            if (e == null || !e.optBoolean("enabled", false)) continue;
            String action = e.optString("action", "");
            JSONObject fields = e.optJSONObject("fields");
            if (action.isEmpty() || fields == null) continue;
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) { handle(intent.getExtras(), fields); }
            };
            IntentFilter f = new IntentFilter(action);
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
            else ctx.registerReceiver(r, f);
            receivers.add(r);
            configured++;
        }
    }

    void stop() {
        if (can != null) can.stop();
        ticker.removeCallbacksAndMessages(null);
        if (voltRx != null) { try { ctx.unregisterReceiver(voltRx); } catch (Exception ignored) { } }
        if (screenRx != null) { try { ctx.unregisterReceiver(screenRx); } catch (Exception ignored) { } }
        for (BroadcastReceiver r : receivers) {
            try { ctx.unregisterReceiver(r); } catch (Exception ignored) { }
        }
        receivers.clear();
    }

    private synchronized void handle(Bundle extras, JSONObject fields) {
        if (extras == null) return;
        JSONObject update = new JSONObject();
        Iterator<String> keys = fields.keys();
        while (keys.hasNext()) {
            String name = keys.next();
            JSONObject m = fields.optJSONObject(name);
            if (m == null) continue;
            String extra = m.optString("extra", "");
            if (!extras.containsKey(extra)) continue;
            Object raw = extras.get(extra);
            double v;
            if (raw instanceof Number) v = ((Number) raw).doubleValue();
            else {
                try { v = Double.parseDouble(String.valueOf(raw)); } catch (Exception ex) { continue; }
            }
            v *= m.optDouble("scale", 1.0);
            try {
                update.put(name, v);
                state.put(name, v);
            } catch (Exception ignored) { }
        }
        if (update.length() == 0) return;
        lastUpdate = System.currentTimeMillis();
        listener.onData(update.toString());
    }

    synchronized String snapshot() {
        return lastUpdate == 0 ? "null" : state.toString();
    }

    void resetLongTerm() { if (trip != null) trip.resetLongTerm(); }

    String rawCarData() { return can == null ? "{}" : can.rawDump(); }

    String status() {
        if (can != null && lastUpdate > 0) return can.status();
        if (configured == 0) return can != null ? can.status() : "no source configured";
        if (lastUpdate == 0) return configured + " source(s) configured, no data received yet";
        return "live, last update " + ((System.currentTimeMillis() - lastUpdate) / 1000) + "s ago";
    }

    private JSONObject loadConfig() {
        try (InputStream in = ctx.getAssets().open("vehicle_sources.json")) {
            byte[] buf = new byte[in.available()];
            int n = in.read(buf);
            return new JSONObject(new String(buf, 0, n, "UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }
}
