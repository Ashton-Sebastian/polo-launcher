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

    VehicleHub(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    void start() {
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

    String status() {
        if (configured == 0) return "no source configured (showing demo data)";
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
