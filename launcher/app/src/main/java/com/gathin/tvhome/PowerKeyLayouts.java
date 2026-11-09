package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Only offers res/raw/power_sleep.kcm to Android's input service, through the manifest's metadata; the
 * service never sends a broadcast here. Nothing changes until the layout is chosen for a remote over adb:
 * see docs/power-button-sleep.md.
 */
public class PowerKeyLayouts extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
    }
}
