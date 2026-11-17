package com.polo6r.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads live car data from the head unit's "Car Info" app (com.nwd.can.setting, NWD firmware).
 *
 * Car Info exposes a service other apps can bind to (action com.nwd.can.service.ACTION_CAN_SERVICE).
 * We register a callback for car-info updates; each update carries a com.nwd.can.sdk.data.CarInfo object,
 * which we unpack with Car Info's own class (loaded from its APK) and read field by field.
 */
class CanBridge {
    interface Sink { void onData(JSONObject mapped, JSONObject raw); }

    private static final String TAG = "PoloCan";
    private static final String PKG = "com.nwd.can.setting";
    private static final String ACTION = "com.nwd.can.service.ACTION_CAN_SERVICE";
    private static final String FEATURE = "com.nwd.can.sdk.outer.adil.ICanRemote4OuterFeature";
    private static final String CALLBACK = "com.nwd.can.sdk.outer.adil.ICanRemoteModelCallback";
    // ICanRemote4OuterFeature call codes
    private static final int ADD_CAR_INFO = 17, ADD_CAN_DATA = 23, ADD_CAN_CAR_INFO = 27;
    // ICanRemoteModelCallback call codes
    private static final int CB_CAN_DATA = 1, CB_CAR_INFO = 2;

    private final Context ctx;
    private final Sink sink;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Parcelable.Creator<?> creator;
    private IBinder remote;
    private boolean bound;
    private String status = "not started";
    private long lastData;
    private JSONObject lastRaw = new JSONObject();
    private final Map<String, String> rawFrames = new LinkedHashMap<String, String>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > 60; }
    };

    CanBridge(Context ctx, Sink sink) {
        this.ctx = ctx.getApplicationContext();
        this.sink = sink;
    }

    void start() {
        try {
            Context car = ctx.createPackageContext(PKG, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Class<?> c = car.getClassLoader().loadClass("com.nwd.can.sdk.data.CarInfo");
            creator = (Parcelable.Creator<?>) c.getField("CREATOR").get(null);
        } catch (Throwable t) {
            status = "Car Info app not found (" + t.getClass().getSimpleName() + ")";
            Log.w(TAG, status, t);
            return;
        }
        bind();
    }

    private void bind() {
        try {
            Intent i = new Intent(ACTION).setPackage(PKG);
            bound = ctx.bindService(i, conn, Context.BIND_AUTO_CREATE);
            status = bound ? "connecting to Car Info…" : "Car Info service refused the connection";
        } catch (Throwable t) {
            status = "could not connect: " + t;
        }
        if (!bound) main.postDelayed(this::bind, 30000);
    }

    void stop() {
        if (bound) { try { ctx.unbindService(conn); } catch (Exception ignored) { } bound = false; }
    }

    String status() {
        if (lastData > 0) return "live from Car Info, last update " + ((System.currentTimeMillis() - lastData) / 1000) + "s ago";
        return status;
    }

    /** Every CarInfo field with a value, plus the last raw CAN frames (for diagnostics / mapping). */
    String rawDump() {
        try {
            JSONObject o = new JSONObject();
            o.put("status", status());
            o.put("carInfo", lastRaw);
            JSONObject frames = new JSONObject();
            synchronized (rawFrames) { for (Map.Entry<String, String> e : rawFrames.entrySet()) frames.put(e.getKey(), e.getValue()); }
            o.put("canFrames", frames);
            return o.toString(1);
        } catch (Exception e) { return "{}"; }
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            remote = b;
            int ok = 0;
            for (int code : new int[]{ADD_CAR_INFO, ADD_CAN_CAR_INFO, ADD_CAN_DATA}) {
                Parcel d = Parcel.obtain(), r = Parcel.obtain();
                try {
                    d.writeInterfaceToken(FEATURE);
                    d.writeStrongBinder(callback);
                    b.transact(code, d, r, 0);
                    r.readException();
                    ok++;
                } catch (Throwable t) {
                    Log.w(TAG, "register " + code + " failed", t);
                } finally { d.recycle(); r.recycle(); }
            }
            status = "connected to Car Info (" + ok + "/3 feeds), waiting for data – switch the ignition on";
            Log.i(TAG, status);
        }

        @Override public void onServiceDisconnected(ComponentName n) {
            remote = null;
            status = "Car Info disconnected, reconnecting…";
        }

        @Override public void onBindingDied(ComponentName n) {
            stop();
            main.postDelayed(CanBridge.this::bind, 5000);
        }
    };

    private final Binder callback = new CallbackBinder();

    private class CallbackBinder extends Binder implements IInterface {
        CallbackBinder() { attachInterface(this, CALLBACK); }

        @Override public IBinder asBinder() { return this; }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(CALLBACK); return true; }
            try {
                data.enforceInterface(CALLBACK);
                if (code == CB_CAR_INFO) {
                    if (data.readInt() != 0) onCarInfo(creator.createFromParcel(data));
                } else if (code == CB_CAN_DATA) {
                    byte[] raw = data.createByteArray();
                    if (raw != null) onFrame(raw);
                }
            } catch (Throwable t) {
                Log.w(TAG, "callback " + code, t);
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    }

    private void onFrame(byte[] raw) {
        StringBuilder hex = new StringBuilder();
        for (byte x : raw) hex.append(String.format("%02X ", x));
        // key by the first 4 bytes (header + frame type) so each kind of frame keeps its latest value
        String key = hex.length() >= 12 ? hex.substring(0, 12).trim() : hex.toString().trim();
        synchronized (rawFrames) { rawFrames.remove(key); rawFrames.put(key, hex.toString().trim()); }
    }

    private void onCarInfo(Object ci) throws Exception {
        JSONObject raw = new JSONObject();
        for (Class<?> c = ci.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(ci);
                if (v == null) continue;
                if (v instanceof Number && (((Number) v).doubleValue() == 0 || ((Number) v).doubleValue() == -1)) continue; // -1 = not provided by this car
                if (v instanceof String && ((String) v).isEmpty()) continue;
                if (v instanceof Number || v instanceof String || v instanceof Boolean) raw.put(f.getName(), v);
            }
        }
        // each update is the complete CarInfo, so replace (a value that drops to "not provided" disappears, e.g. speed when parked)
        lastRaw = raw;
        lastData = System.currentTimeMillis();
        Log.i(TAG, "CarInfo " + raw);
        sink.onData(map(lastRaw), lastRaw);
    }

    /** CarInfo fields -> the launcher's vehicle data names. Unknown / unsupported values are simply absent. */
    static JSONObject map(JSONObject r) throws Exception {
        JSONObject m = new JSONObject();
        Double t = parseNum(r.optString("mstrOuttemp", null));
        if (t != null) m.put("temp_c", t);
        putIf(m, "speed_kmh", r, "mInstantanSpeed");
        putIf(m, "rpm", r, "mEngineSpeed");
        putIf(m, "odometer_km", r, "mDrivingMile");      // Polo 6R: odometer (km)
        putIf(m, "fuel_litres", r, "mOilSurplus");       // Polo 6R: fuel remaining (litres)
        putIf(m, "range_km", r, "mCanDriverMileage");    // range, if the adapter ever sends it
        putIf(m, "coolant_c", r, "mWaterTemp");
        putIf(m, "battery_v", r, "mBatteryVoltage");
        // trip computer memories 1/2/3 = since start / since refuelling / long-term
        String[] p = {"ss", "rf", "lt"};
        for (int i = 0; i < 3; i++) {
            String n = String.valueOf(i + 1);
            putIf(m, p[i] + "_distance_km", r, "mDrivingMile" + n);
            putIf(m, p[i] + "_time_min", r, "mDriverTime" + n);
            putIf(m, p[i] + "_consumption_l100", r, i == 0 ? "mAverageOil" : "mAverageOil" + n);
            putIf(m, p[i] + "_avg_speed_kmh", r, i == 0 ? "mEquallySpeed" : "mEquallySpeed" + (n));
        }
        return m;
    }

    private static void putIf(JSONObject m, String to, JSONObject r, String from) throws Exception {
        if (r.has(from)) m.put(to, r.getDouble(from));
    }

    private static Double parseNum(String s) {
        if (s == null) return null;
        java.util.regex.Matcher x = java.util.regex.Pattern.compile("-?\\d+(\\.\\d+)?").matcher(s);
        return x.find() ? Double.valueOf(x.group()) : null;
    }
}
