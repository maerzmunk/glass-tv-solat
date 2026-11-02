package com.gathin.tvhome;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * The place the prayer times are worked out for when a theme sets "prayer_auto": Android's own network
 * location (Wi-Fi based; a TV has no GPS), asked at most twice a day while Home is in front, and kept
 * rounded to about a kilometre, so a stick taken to another town follows it. Until the TV has given a
 * place, and whenever it cannot (location off, no permission), the theme's prayer_lat and prayer_lon
 * stay. Nothing runs in the background: one request when Home comes up, and Android answers it.
 */
final class PrayerPlace {
    private static final String TAG = "TvHome.Place";
    private static final String PREFS = "prayer_place";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_ASKED = "asked";
    private static final long REFRESH_MS = 12 * 60 * 60_000L;
    /** Android's last fix is taken as it is when it is younger than this. */
    private static final long FRESH_MS = 24 * 60 * 60_000L;
    /** About 2 km: closer than that the times differ by well under a minute, so nothing is redrawn. */
    static final double MOVED_DEG = 0.02;

    interface Listener {
        void onPlace(double lat, double lon);
    }

    private PrayerPlace() {
    }

    /** The place last found, as {lat, lon}, or null when there is none yet. */
    static double[] saved(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (!p.contains(KEY_LAT) || !p.contains(KEY_LON)) return null;
        return new double[]{p.getFloat(KEY_LAT, 0f), p.getFloat(KEY_LON, 0f)};
    }

    /** Asks Android where the TV is, when due; {@code onNew} hears of a place that differs from the last one. */
    static void refresh(Context ctx, Config cfg, Listener onNew) {
        if (!cfg.prayerAuto) return;
        Context app = ctx.getApplicationContext();
        SharedPreferences p = prefs(app);
        long now = System.currentTimeMillis();
        if (now - p.getLong(KEY_ASKED, 0) < REFRESH_MS) return;
        if (app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "no location permission; keeping the theme's place");
            return;
        }
        LocationManager lm = app.getSystemService(LocationManager.class);
        if (lm == null || !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Log.i(TAG, "network location is off; keeping the theme's place");
            return;
        }
        p.edit().putLong(KEY_ASKED, now).apply();
        try {
            Location last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null && now - last.getTime() < FRESH_MS) {
                take(app, last, onNew);
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, app.getMainExecutor(),
                        l -> take(app, l, onNew));
            } else {
                requestOnce(app, lm, onNew);
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "location request failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private static void requestOnce(Context app, LocationManager lm, Listener onNew) {
        // Every method spelled out: before Android 11 they are abstract in the interface.
        lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, new LocationListener() {
            @Override
            public void onLocationChanged(Location l) {
                take(app, l, onNew);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        }, Looper.getMainLooper());
    }

    private static void take(Context app, Location l, Listener onNew) {
        if (l == null) {
            Log.i(TAG, "no fix; keeping the last place");
            return;
        }
        double lat = round(l.getLatitude()), lon = round(l.getLongitude());
        double[] old = saved(app);
        prefs(app).edit().putFloat(KEY_LAT, (float) lat).putFloat(KEY_LON, (float) lon).apply();
        Log.i(TAG, "place " + lat + ", " + lon);
        if (moved(old, lat, lon) && onNew != null) {
            new Handler(Looper.getMainLooper()).post(() -> onNew.onPlace(lat, lon));
        }
    }

    /** Two decimals, about a kilometre: enough for the times, and no closer than that is kept. */
    static double round(double deg) {
        return Math.round(deg * 100) / 100.0;
    }

    /** Whether a new place is far enough from the old one (null: none yet) to work the times out again. */
    static boolean moved(double[] old, double lat, double lon) {
        return old == null || Math.abs(old[0] - lat) >= MOVED_DEG || Math.abs(old[1] - lon) >= MOVED_DEG;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
