package com.polo6r.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;

/**
 * Controls the head unit's built-in FM/AM tuner through its radio service (com.nwd.radio.service,
 * the same service the stock Radio app uses), so Polo Launcher can show its own radio screen.
 *
 * RadioFeature (descriptor com.nwd.radio.service.RadioFeature) call codes:
 *  1 setCurrentFrequency(int freq, byte band, int 0)  [oneway]   2 getCurrentFrequency() -> Frequency
 *  3 seek(bool up)  4 search(bool)  5 changeBand()  6 AMS()  10 isHasStrero  12 isStreroOn
 *  20 saveCurrentFrequency(byte slot)  21 getPrefabFrequency() -> Frequency[]  23 getRadioState
 *  24 registCallback(cb)  25 unRegistCallback(cb)  26 prefeb(bool next)  28 getRtMessage()
 * RadioCallback (descriptor com.nwd.radio.service.RadioCallback) codes:
 *  1 state(B)  2 currentFrequency(B band, I freq, S ps, I)  3 nearOn(Z)  4 stereo(Z)  7 pty(B)
 *  8 presets(Frequency[])  13 radioText(S)  14 scanState(I)
 * Frequency = com.nwd.radio.service.data.Frequency {mBandType B, mFrequency I, mPSName S}.
 */
class RadioBridge {
    interface Sink { void onRadio(String json); }

    private static final String TAG = "PoloRadio";
    private static final String PKG = "com.nwd.radio.service";
    private static final String ACTION = "com.nwd.radio.service.ACTION_RADIO_SERVICE";
    private static final String FEATURE = "com.nwd.radio.service.RadioFeature";
    private static final String CALLBACK = "com.nwd.radio.service.RadioCallback";
    /** NWD source manager: radio = source 4. */
    private static final byte RADIO_SOURCE = 4;

    private final Context ctx;
    private final Sink sink;
    private Parcelable.Creator<?> freqCreator;
    private IBinder remote;
    private boolean bound;
    private final JSONObject state = new JSONObject();
    private int logs;

    RadioBridge(Context ctx, Sink sink) {
        this.ctx = ctx.getApplicationContext();
        this.sink = sink;
    }

    void start() {
        new Thread(() -> {
            try {
                Context rc = ctx.createPackageContext(PKG, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                freqCreator = (Parcelable.Creator<?>) rc.getClassLoader()
                        .loadClass("com.nwd.radio.service.data.Frequency").getField("CREATOR").get(null);
            } catch (Throwable t) {
                Log.w(TAG, "radio service code not found", t);
            }
            try {
                bound = ctx.bindService(new Intent(ACTION).setPackage(PKG), conn, Context.BIND_AUTO_CREATE);
            } catch (Throwable t) {
                Log.w(TAG, "bind failed", t);
            }
            Log.i(TAG, "bind " + bound);
        }, "radio-bridge").start();
    }

    void stop() {
        if (remote != null) call(25, p -> p.writeStrongBinder(callback), false);
        if (bound) { try { ctx.unbindService(conn); } catch (Exception ignored) { } bound = false; }
    }

    boolean available() { return remote != null; }

    /* ---------------- audio: radio and CarPlay/Android Auto take turns ---------------- */
    /** NWD source manager: Android apps (ZLink = CarPlay/Android Auto, music players) = source 0. */
    private static final byte ANDROID_SOURCE = 0;
    private android.media.AudioManager am;
    private Object focusRequest;
    private volatile boolean radioPlaying, resumeOnGain;

    private void requestSource(byte id) {
        try { ctx.sendBroadcast(new Intent("com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE").putExtra("extra_source_id", id)); }
        catch (Exception ignored) { }
    }

    /** Play the radio: switch the car's audio to the tuner, pause CarPlay/other music, hold audio focus. */
    void requestRadioAudio() {
        if (am == null) am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        boolean otherMusic = am.isMusicActive();
        if (otherMusic) pauseOtherMedia();          // pause CarPlay / Bluetooth music
        // the unit's setting service grabs audio focus right after a switch to the radio: ignore that,
        // then take focus ourselves so CarPlay pressing play (it asks for focus) switches the radio off
        long now = System.currentTimeMillis();
        ignoreFocusUntil = now + 1900;
        requestSource(RADIO_SOURCE);
        watchPlayers();
        radioStartedAt = now;
        radioPlayers.clear();
        // players that already exist (the radio's own, CarPlay's channel that stays open while paused) are known;
        // only a brand-new music player counts as other music starting
        if (android.os.Build.VERSION.SDK_INT >= 26)
            for (android.media.AudioPlaybackConfiguration c : am.getActivePlaybackConfigurations()) radioPlayers.add(c.hashCode());
        radioPlaying = true;
        main.removeCallbacks(takeFocus);
        main.postDelayed(takeFocus, 2000);
        resumeOnGain = false;
        state(true);
    }

    /** Stop the radio: hand the car's audio back to Android (CarPlay, music apps). */
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile long ignoreFocusUntil;
    private final Runnable takeFocus = () -> { if (radioPlaying) { Log.i(TAG, "holding audio focus for the radio"); requestFocus(); } };

    /** Someone else took over the audio (CarPlay play, BT music): switch the radio off. */
    private void otherAudioStarted(String why, boolean comeBack) {
        if (!radioPlaying) return;
        Log.i(TAG, why + ": radio off");
        radioPlaying = false;
        resumeOnGain = comeBack;
        main.removeCallbacks(takeFocus);
        if (!comeBack) abandonFocus();
        requestSource(ANDROID_SOURCE);
        state(false);
    }

    void stopRadioAudio() {
        main.removeCallbacks(takeFocus);
        abandonFocus();
        radioPlaying = false;
        resumeOnGain = false;
        requestSource(ANDROID_SOURCE);
        state(false);
    }

    /*
     * Radio vs CarPlay: the radio service plays the tuner through its own Android media player. When the radio
     * starts we note that player; if any OTHER music player starts afterwards (CarPlay via ZLink, Bluetooth music),
     * hand the car's audio back to Android so the radio stops instead of playing over it. Spoken directions use a
     * different audio type, so they don't stop the radio (the unit's "Navigation mixing" setting turns music down).
     */
    private final java.util.Set<Integer> radioPlayers = new java.util.HashSet<>();
    private volatile long radioStartedAt;
    private boolean watching;

    private void watchPlayers() {
        if (watching || android.os.Build.VERSION.SDK_INT < 26) return;
        watching = true;
        am.registerAudioPlaybackCallback(new android.media.AudioManager.AudioPlaybackCallback() {
            @Override public void onPlaybackConfigChanged(java.util.List<android.media.AudioPlaybackConfiguration> configs) {
                if (!radioPlaying) return;
                boolean settling = System.currentTimeMillis() - radioStartedAt < 3000;
                for (android.media.AudioPlaybackConfiguration c : configs) {
                    int usage = c.getAudioAttributes().getUsage();
                    if (usage != android.media.AudioAttributes.USAGE_MEDIA && usage != android.media.AudioAttributes.USAGE_GAME) continue;
                    int id = c.hashCode();                         // stable per player
                    if (settling) { radioPlayers.add(id); continue; }  // the radio's own player
                    if (!radioPlayers.contains(id)) { otherAudioStarted("new music player", false); return; }
                }
            }
        }, new android.os.Handler(android.os.Looper.getMainLooper()));
    }

    private void state(boolean on) { try { state.put("on", on); } catch (Exception ignored) { } push(); }

    private void pauseOtherMedia() {
        try {
            long t = android.os.SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
        } catch (Exception ignored) { }
    }

    private final android.media.AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        Log.i(TAG, "audio focus " + change);
        if (System.currentTimeMillis() < ignoreFocusUntil) return;           // the unit's own grab when switching to radio
        switch (change) {
            case android.media.AudioManager.AUDIOFOCUS_LOSS:                // CarPlay / a music app started playing
                otherAudioStarted("audio focus taken", false);
                break;
            case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:      // e.g. Siri: pause, come back after
                otherAudioStarted("audio focus taken for a moment", true);
                break;
            case android.media.AudioManager.AUDIOFOCUS_GAIN:
                if (resumeOnGain) { resumeOnGain = false; requestRadioAudio(); }
                break;
            default: break;                                                  // directions (may duck): the unit's navigation mixing handles it
        }
    };

    private void requestFocus() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.media.AudioFocusRequest r = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(focusListener)
                    .setWillPauseWhenDucked(false)
                    .build();
            focusRequest = r;
            am.requestAudioFocus(r);
        } else {
            am.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonFocus() {
        if (am == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 26 && focusRequest != null) am.abandonAudioFocusRequest((android.media.AudioFocusRequest) focusRequest);
        else am.abandonAudioFocus(focusListener);
    }

    /* ---------------- commands from the radio screen ---------------- */

    void seek(boolean up) { call(3, p -> p.writeInt(up ? 1 : 0), false); }
    void scan() { call(4, p -> p.writeInt(1), false); }
    void stopScan() { call(4, p -> p.writeInt(0), false); }
    void changeBand() { call(5, null, false); refreshSoon(); }
    void autoStore() { call(6, null, false); }
    void preset(boolean next) { call(26, p -> p.writeInt(next ? 1 : 0), false); }
    void savePreset(int slot) { call(20, p -> p.writeByte((byte) slot), false); refreshSoon(); }

    void tune(int freq, int band) {
        call(1, p -> { p.writeInt(freq); p.writeByte((byte) band); p.writeInt(0); }, true);
    }

    /** Read everything now (current station, presets, radio text, stereo) and push it to the screen. */
    void refresh() {
        new Thread(() -> {
            try {
                Parcel r = callForReply(2, null);
                if (r != null) { if (r.readInt() != 0) putFreq(state, freqCreator.createFromParcel(r)); r.recycle(); }
                r = callForReply(21, null);
                if (r != null) { state.put("presets", readFreqArray(r)); r.recycle(); }
                r = callForReply(28, null);
                if (r != null) { state.put("rt", nz(r.readString())); r.recycle(); }
                r = callForReply(12, null);
                if (r != null) { state.put("stereo", r.readInt() != 0); r.recycle(); }
                push();
            } catch (Throwable t) { Log.w(TAG, "refresh", t); }
        }, "radio-refresh").start();
    }

    private void refreshSoon() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::refresh, 600);
    }

    String snapshot() {
        try { return new JSONObject(state.toString()).put("available", remote != null).put("on", radioPlaying).toString(); }
        catch (Exception e) { return "{\"available\":false}"; }
    }

    /* ---------------- binder plumbing ---------------- */

    private interface Args { void write(Parcel p) throws Exception; }

    private void call(int code, Args args, boolean oneway) {
        IBinder b = remote;
        if (b == null) return;
        Parcel d = Parcel.obtain(), r = oneway ? null : Parcel.obtain();
        try {
            d.writeInterfaceToken(FEATURE);
            if (args != null) args.write(d);
            b.transact(code, d, r, oneway ? IBinder.FLAG_ONEWAY : 0);
            if (r != null) r.readException();
        } catch (Throwable t) {
            Log.w(TAG, "call " + code, t);
        } finally { d.recycle(); if (r != null) r.recycle(); }
    }

    private Parcel callForReply(int code, Args args) {
        IBinder b = remote;
        if (b == null) return null;
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(FEATURE);
            if (args != null) args.write(d);
            b.transact(code, d, r, 0);
            r.readException();
            d.recycle();
            return r;
        } catch (Throwable t) {
            Log.w(TAG, "call " + code, t);
            d.recycle(); r.recycle();
            return null;
        }
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            remote = b;
            call(24, p -> p.writeStrongBinder(callback), false);
            Log.i(TAG, "connected to radio service");
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName n) { remote = null; push(); }
        @Override public void onBindingDied(ComponentName n) {
            remote = null;
            try { ctx.unbindService(this); } catch (Exception ignored) { }
            bound = false;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(RadioBridge.this::start, 3000);
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
                switch (code) {
                    case 1: state.put("state", data.readByte()); break;
                    case 2: {
                        byte band = data.readByte();
                        int freq = data.readInt();
                        String ps = data.readString();
                        int extra = data.readInt();
                        state.put("band", band).put("freq", freq).put("ps", nz(ps)).put("extra", extra);
                        if (logs++ < 6) Log.i(TAG, "station band=" + band + " freq=" + freq + " ps=" + ps + " extra=" + extra);
                        break;
                    }
                    case 3: state.put("local", data.readInt() != 0); break;
                    case 4: state.put("stereo", data.readInt() != 0); break;
                    case 7: state.put("pty", data.readByte()); break;
                    case 8: state.put("presets", readFreqArray(data)); break;
                    case 13: state.put("rt", nz(data.readString())); break;
                    case 14: state.put("scan", data.readInt()); break;
                    default: break;
                }
                push();
            } catch (Throwable t) {
                Log.w(TAG, "callback " + code, t);
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    }

    private JSONArray readFreqArray(Parcel p) throws Exception {
        JSONArray a = new JSONArray();
        int n = p.readInt();
        for (int i = 0; i < n; i++) {
            JSONObject o = new JSONObject();
            if (p.readInt() != 0 && freqCreator != null) putFreq(o, freqCreator.createFromParcel(p));
            a.put(o);
        }
        return a;
    }

    private static void putFreq(JSONObject o, Object f) throws Exception {
        if (f == null) return;
        for (Field x : f.getClass().getDeclaredFields()) {
            x.setAccessible(true);
            Object v = x.get(f);
            switch (x.getName()) {
                case "mBandType": o.put("band", v); break;
                case "mFrequency": o.put("freq", v); break;
                case "mPSName": o.put("ps", v == null ? "" : String.valueOf(v).trim()); break;
                default: break;
            }
        }
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }

    private void push() { sink.onRadio(snapshot()); }
}
