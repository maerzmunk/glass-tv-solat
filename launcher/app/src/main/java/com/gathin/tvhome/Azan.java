package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Calendar;

/**
 * When a prayer time comes: the azan plays if the home screen or the screensaver is showing, and over
 * any other app a small card says the time has come, without sound. One alarm at a time, set for the
 * next prayer whenever Home or the screensaver starts and again each time it rings, so nothing of ours
 * runs in between. The sound is the theme's own file ({@code azan.mp3}, and {@code azan_subuh.mp3} for
 * Subuh when present) in the launcher's files folder; without one, Home shows the card too.
 */
final class Azan {
    private static final String TAG = "TvHome.Azan";
    static final String SOUND = "azan.mp3";
    static final String SOUND_SUBUH = "azan_subuh.mp3";
    static final String TEST_FILE = "azan-test";
    private static final String EXTRA_PRAYER = "prayer";
    private static final String EXTRA_AT = "at";
    /** A TV that was asleep at the time wakes to no stale azan or card. */
    private static final long LATE_MS = 3 * 60_000L;
    private static final long CARD_MS = 15_000L;

    /** Set by MainActivity and ClockDream; false whenever our process is not the one on screen. */
    static volatile boolean homeInFront;
    static volatile boolean dreaming;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static MediaPlayer player;
    /** When a try from azan-test rings; until then schedule() leaves that alarm alone. */
    private static long testAt;

    private Azan() {
    }

    /** Sets the alarm for the next prayer time, or clears it when prayer times are off. */
    static void schedule(Context ctx, Config cfg) {
        Context app = ctx.getApplicationContext();
        AlarmManager alarms = app.getSystemService(AlarmManager.class);
        Intent intent = new Intent(app, Receiver.class);
        if (!cfg.prayerTimes) {
            PendingIntent old = PendingIntent.getBroadcast(app, 0, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (old != null) alarms.cancel(old);
            return;
        }
        Calendar day = Calendar.getInstance();
        long now = day.getTimeInMillis();
        int prayer = -1;
        long at = 0;
        // A try without waiting: `touch .../files/azan-test` (or write 0-4 into it for that prayer), then
        // press Home; the alarm rings a minute later, and after it the real times take over again.
        File test = Config.dir(app) != null ? new File(Config.dir(app), TEST_FILE) : null;
        if (test != null && test.isFile()) {
            prayer = 1;
            try {
                String s = new String(java.nio.file.Files.readAllBytes(test.toPath())).trim();
                if (!s.isEmpty()) prayer = Math.max(0, Math.min(4, Integer.parseInt(s)));
            } catch (Exception ignored) {
                // Zohor
            }
            if (!test.delete()) Log.w(TAG, "cannot delete " + test);
            at = now + 60_000L;
            testAt = at;
        } else if (now < testAt) {
            return;   // a try is pending: Home coming back must not replace it with the real time
        }
        // Today's next time, else tomorrow's Subuh.
        for (int d = 0; d < 2 && prayer < 0; d++) {
            if (d == 1) day.add(Calendar.DAY_OF_MONTH, 1);
            int[] times = PrayerLine.on(day, cfg.prayerLat, cfg.prayerLon);
            for (int i = 0; i < times.length; i++) {
                Calendar c = (Calendar) day.clone();
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                c.add(Calendar.MINUTE, times[i]);
                if (c.getTimeInMillis() > now) {
                    prayer = i;
                    at = c.getTimeInMillis();
                    break;
                }
            }
        }
        intent.putExtra(EXTRA_PRAYER, prayer).putExtra(EXTRA_AT, at);
        PendingIntent pi = PendingIntent.getBroadcast(app, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // RTC, not RTC_WAKEUP: a sleeping TV stays asleep, and a late ring is dropped (LATE_MS).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setWindow(AlarmManager.RTC, at, 60_000L, pi);
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi);
        }
        Log.i(TAG, "next: " + PrayerTimes.NAMES[prayer] + " at " + new java.util.Date(at));
    }

    /** Home or the screensaver went away: the azan stops unless the other one took its place. */
    static void leftFront() {
        handler.postDelayed(() -> {
            if (!homeInFront && !dreaming) stop();
        }, 1000);
    }

    static void stop() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    /** The alarm: plays the azan or shows the card, then sets the alarm for the next prayer. */
    public static final class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            Context app = ctx.getApplicationContext();
            Config cfg = Config.load(app);
            testAt = 0;
            schedule(app, cfg);
            int prayer = intent.getIntExtra(EXTRA_PRAYER, -1);
            long at = intent.getLongExtra(EXTRA_AT, 0);
            if (!cfg.prayerTimes || prayer < 0 || System.currentTimeMillis() - at > LATE_MS) return;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(at);
            String time = PrayerLine.time(app, c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE));
            if ((homeInFront || dreaming) && cfg.azan && play(app, prayer)) return;
            // Keep the process alive while the card is up.
            PendingResult result = goAsync();
            card(app, PrayerTimes.NAMES[prayer], time, result);
        }
    }

    private static boolean play(Context app, int prayer) {
        File dir = Config.dir(app);
        if (dir == null) return false;
        File file = new File(dir, prayer == 0 ? SOUND_SUBUH : SOUND);
        if (!file.isFile()) file = new File(dir, SOUND);
        if (!file.isFile()) return false;
        stop();
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(file.getPath());
            mp.setOnCompletionListener(m -> stop());
            mp.prepare();
            mp.start();
            player = mp;
            Log.i(TAG, "azan for " + PrayerTimes.NAMES[prayer]);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "cannot play " + file + ": " + e.getMessage());
            stop();
            return false;
        }
    }

    /** "Zohor 12:52 / Telah masuk waktu Zohor" in the top corner for a few seconds, over whatever is on. */
    private static void card(Context app, String name, String time, BroadcastReceiver.PendingResult result) {
        String title = name + "  " + time;
        String line = "Telah masuk waktu " + name;
        if (!Settings.canDrawOverlays(app)) {
            // No overlay permission: Android's own toast still reaches the screen.
            Toast.makeText(app, line + " (" + time + ")", Toast.LENGTH_LONG).show();
            result.finish();
            return;
        }
        float density = app.getResources().getDisplayMetrics().density;
        TextView card = new TextView(app);
        SpannableString text = new SpannableString(title + "\n" + line);
        text.setSpan(new ForegroundColorSpan(0xFFFFFFFF), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(1.35f), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        card.setText(text);
        card.setTextColor(0xCCFFFFFF);
        card.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        card.setLineSpacing(4 * density, 1f);
        try {
            card.setTypeface(app.getResources().getFont(R.font.outfit));
        } catch (RuntimeException ignored) {
            // the system font is fine
        }
        int pad = Math.round(18 * density);
        card.setPadding(Math.round(22 * density), pad, Math.round(22 * density), pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE61F1F22);
        bg.setCornerRadius(18 * density);
        bg.setStroke(Math.round(density), 0x33FFFFFF);
        card.setBackground(bg);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Math.round(48 * density);
        lp.y = Math.round(36 * density);
        WindowManager wm = app.getSystemService(WindowManager.class);
        try {
            wm.addView(card, lp);
        } catch (RuntimeException e) {
            Log.w(TAG, "card not shown: " + e.getMessage());
            result.finish();
            return;
        }
        card.setAlpha(0f);
        card.animate().alpha(1f).setDuration(400).start();
        handler.postDelayed(() -> card.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            try {
                wm.removeView(card);
            } catch (RuntimeException ignored) {
                // already gone
            }
            result.finish();
        }).start(), CARD_MS);
    }
}
