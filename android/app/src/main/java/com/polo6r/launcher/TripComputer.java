package com.polo6r.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * VW-style trip computer worked out from the odometer and fuel level the car sends
 * (this Polo's CAN adapter doesn't send the car's own trip computer values).
 *
 *  ss  = this trip      (reset when the car is switched on after being off for 2+ minutes) - shown as Since Start distance
 *  day = today          (reset at the first start of a new day) - Since Start time, consumption and avg. speed
 *  rf  = since refuelling (reset when the fuel level jumps up by 3+ litres)
 *  lt  = long-term      (since Polo Launcher first saw the car; reset from the Vehicle screen)
 *
 * Fuel is reported in whole litres, so consumption is shown once at least 1 litre has been used and 30 km
 * driven, and gets more accurate the further you drive.
 */
class TripComputer {
    private static final String[] M = {"ss", "day", "rf", "lt"};
    private static final double REFUEL_JUMP_L = 3;
    // Calibrated to the car's own range reading: 250 km with 22 l in the tank (17 Nov 2026) = 11.4 km/l.
    private static final double ESTIMATE_KML = 250.0 / 22.0;
    private static final long NEW_START_AFTER_MS = 2 * 60 * 1000;
    private static final double MIN_CONSUMPTION_KM = 30;

    private final SharedPreferences p;
    private long lastTick;
    private boolean pendingStartReset, pendingDayReset;

    TripComputer(Context c) {
        p = c.getSharedPreferences("polo_trip", Context.MODE_PRIVATE);
        // the app may have been closed while the car was off: check the saved switch-off time now
        android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isInteractive()) carOn();
    }

    /** Head unit screen off / on = car off / on. The switch-off time is saved so it survives the app being closed. */
    synchronized void onScreen(boolean on) {
        if (!on) { p.edit().putLong("off_at", System.currentTimeMillis()).apply(); lastTick = 0; return; }
        carOn();
    }

    private static String today() {
        return new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date());
    }

    private synchronized void carOn() {
        if (!today().equals(p.getString("day_date", ""))) pendingDayReset = true; // first start of a new day
        long off = p.getLong("off_at", 0);
        if (off <= 0) return;
        if (System.currentTimeMillis() - off >= NEW_START_AFTER_MS) pendingStartReset = true;
        p.edit().remove("off_at").apply();
    }

    synchronized void resetLongTerm() {
        double odo = p.getFloat("last_odo", -1);
        if (odo >= 0) reset("lt", odo);
    }

    /** Feed the latest car values; returns the computed trip values to merge into the vehicle data. */
    synchronized JSONObject update(JSONObject car) {
        JSONObject out = new JSONObject();
        try {
            double odo = car.optDouble("odometer_km", Double.NaN);
            double fuel = car.optDouble("fuel_litres", Double.NaN);
            if (Double.isNaN(odo)) return out;
            long now = System.currentTimeMillis();
            SharedPreferences.Editor e = p.edit();

            if (!p.contains("lt_odo")) { for (String m : M) reset(m, odo); }
            if (!p.contains("day_odo")) {                       // upgrade: today so far = this trip so far
                SharedPreferences.Editor u = p.edit();
                for (String k : new String[]{"_odo", "_min", "_used", "_int"})
                    u.putFloat("day" + k, p.getFloat("ss" + k, k.equals("_odo") ? (float) odo : 0));
                u.putString("day_date", today()).apply();
                pendingDayReset = false;
            }
            if (pendingStartReset) { reset("ss", odo); pendingStartReset = false; }
            if (pendingDayReset) { reset("day", odo); p.edit().putString("day_date", today()).apply(); pendingDayReset = false; }

            // time with the car on (updates arrive every few seconds; ignore gaps = car was off)
            double speed = Math.max(0, car.optDouble("speed_kmh", 0));
            if (lastTick > 0 && now - lastTick < 60000) {
                double dtMin = (now - lastTick) / 60000.0;
                for (String m : M) {
                    e.putFloat(m + "_min", (float) (p.getFloat(m + "_min", 0) + dtMin));
                    e.putFloat(m + "_int", (float) (p.getFloat(m + "_int", 0) + speed * dtMin / 60.0));
                }
            }
            lastTick = now;

            if (!Double.isNaN(fuel) && fuel > 0) {                // 0 = no reading yet, not an empty tank
                double low = p.getFloat("fuel_low", (float) fuel);
                if (fuel >= low + REFUEL_JUMP_L) {               // refuelled
                    e.putFloat("rf_odo", (float) odo).putFloat("rf_min", 0).putFloat("rf_used", 0).putFloat("rf_int", 0);
                    low = fuel;
                } else if (fuel < low) {                          // fuel used (count each litre once)
                    double used = low - fuel;
                    for (String m : M) e.putFloat(m + "_used", (float) (p.getFloat(m + "_used", 0) + used));
                    low = fuel;
                }
                e.putFloat("fuel_low", (float) low);
            }
            e.putFloat("last_odo", (float) odo);
            e.apply();

            double bestKml = Double.NaN;
            for (String m : M) {
                double odoDist = Math.max(0, odo - p.getFloat(m + "_odo", (float) odo));
                // the car's speed reading is unreliable, so distance comes from the odometer alone
                double dist = odoDist;
                double min = p.getFloat(m + "_min", 0);
                double used = p.getFloat(m + "_used", 0);
                out.put(m + "_distance_km", dist);
                out.put(m + "_time_min", min);
                if (min >= 1 && dist > 0) out.put(m + "_avg_speed_kmh", dist / (min / 60.0));
                // the car reports fuel in whole litres: over a short distance one litre step gives nonsense
                // (e.g. 33 l/100 km after 3 km), so only show consumption once 30 km have been driven
                if (used >= 1 && dist >= MIN_CONSUMPTION_KM) {
                    double kml = dist / used;
                    out.put(m + "_consumption_kml", kml);
                    if ((m.equals("rf") || m.equals("lt")) && used >= 3 && Double.isNaN(bestKml)) bestKml = kml; // rf first, then lt
                }
            }
            // Since Start: distance is this trip; time, consumption and average speed are for today
            for (String k : new String[]{"_time_min", "_avg_speed_kmh", "_consumption_kml"}) {
                out.remove("ss" + k);
                if (out.has("day" + k)) out.put("ss" + k, out.get("day" + k));
            }
            if (!Double.isNaN(fuel)) {
                if (!Double.isNaN(bestKml)) {
                    out.put("range_km", fuel * bestKml);
                    out.put("range_estimated", false);
                } else {
                    // not enough driving measured yet: use the car's own range figure (calibrated consumption)
                    out.put("range_km", fuel * ESTIMATE_KML);
                    out.put("range_estimated", true);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private void reset(String m, double odo) {
        p.edit().putFloat(m + "_odo", (float) odo).putFloat(m + "_min", 0).putFloat(m + "_used", 0).putFloat(m + "_int", 0).apply();
    }
}
