package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the remote's key helpers (HomeKeyService, InputKeyService) on. Android 9 takes an accessibility
 * service off its enabled list when its app is force-stopped, and nothing puts it back, so the remote's
 * Home and app buttons stop working until someone runs adb again.
 *
 * Opt-in: it does nothing unless the setup granted WRITE_SECURE_SETTINGS
 * ({@code pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS}). With it, each helper
 * seen on is remembered, and a remembered helper found off is put back when Home comes to the front.
 * To switch a helper off for good, revoke the permission first.
 */
final class KeyServices {

    private static final String PREFS = "key_services";
    private static final String[] SERVICES = {"HomeKeyService", "InputKeyService"};

    private KeyServices() {
    }

    static void keepOn(Context context) {
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            String pkg = context.getPackageName();
            String enabled = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            Set<String> remembered = new HashSet<>(prefs.getStringSet("on", new HashSet<>()));
            Set<String> seen = new HashSet<>(remembered);
            for (String name : SERVICES) if (isOn(enabled, pkg, name)) seen.add(name);
            if (!seen.equals(remembered)) prefs.edit().putStringSet("on", seen).apply();
            String repaired = repaired(enabled, seen, pkg);
            if (repaired == null) return;
            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, repaired);
            Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            android.util.Log.i("TvHome.Keys", "key helpers back on: " + repaired);
        } catch (RuntimeException e) {
            android.util.Log.w("TvHome.Keys", "could not check the key helpers", e);
        }
    }

    /** Whether {@code enabled} (the colon-separated setting) lists this app's service, in either form. */
    static boolean isOn(String enabled, String pkg, String name) {
        if (enabled == null) return false;
        for (String part : enabled.split(":")) {
            String s = part.trim();
            if (s.equals(pkg + "/." + name) || s.equals(pkg + "/" + pkg + "." + name)) return true;
        }
        return false;
    }

    /**
     * The setting with every remembered service that is missing added back, keeping the other apps'
     * services; null when nothing is missing.
     */
    static String repaired(String enabled, Set<String> remembered, String pkg) {
        List<String> parts = new ArrayList<>();
        if (enabled != null && !enabled.trim().isEmpty() && !enabled.trim().equals("null")) {
            for (String part : enabled.split(":")) if (!part.trim().isEmpty()) parts.add(part.trim());
        }
        boolean changed = false;
        for (String name : SERVICES) {
            if (remembered.contains(name) && !isOn(enabled, pkg, name)) {
                parts.add(pkg + "/." + name);
                changed = true;
            }
        }
        return changed ? String.join(":", parts) : null;
    }
}
