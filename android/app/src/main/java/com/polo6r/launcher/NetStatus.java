package com.polo6r.launcher;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import org.json.JSONObject;

/** Current connection for the status-bar icon: {"type":"wifi|cell|eth|none","level":0-4,"internet":true}. */
final class NetStatus {
    private NetStatus() { }

    @SuppressWarnings("deprecation")
    static String json(Context c) {
        String type = "none";
        int level = 0;
        boolean internet = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= 23) {
                Network n = cm.getActiveNetwork();
                NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
                if (nc != null) {
                    if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) type = "wifi";
                    else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) type = "cell";
                    else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) type = "eth";
                    else type = "other";
                    internet = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                }
            } else {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni != null && ni.isConnected()) {
                    type = ni.getType() == ConnectivityManager.TYPE_WIFI ? "wifi"
                            : ni.getType() == ConnectivityManager.TYPE_MOBILE ? "cell" : "eth";
                    internet = true;
                }
            }
            if ("wifi".equals(type)) {
                WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                level = WifiManager.calculateSignalLevel(wm.getConnectionInfo().getRssi(), 4); // 0..3
            } else if ("cell".equals(type) && Build.VERSION.SDK_INT >= 28) {
                TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm.getSignalStrength() != null) level = tm.getSignalStrength().getLevel(); // 0..4
            }
        } catch (Exception ignored) { }
        try {
            return new JSONObject().put("type", type).put("level", level).put("internet", internet).toString();
        } catch (Exception e) {
            return "{\"type\":\"none\"}";
        }
    }
}
