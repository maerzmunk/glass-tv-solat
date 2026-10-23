package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.Calendar;

/** The prayer times as the home screen and the screensaver show them: one line, the next one highlighted. */
final class PrayerLine {

    private PrayerLine() {
    }

    /** Today's times at lat, lon in the TV's own time zone, as minutes after midnight. */
    static int[] today(double lat, double lon) {
        return on(Calendar.getInstance(), lat, lon);
    }

    /** The times on c's date at lat, lon, in c's time zone. */
    static int[] on(Calendar c, double lat, double lon) {
        double tz = c.getTimeZone().getOffset(c.getTimeInMillis()) / 3_600_000.0;
        return PrayerTimes.minutes(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                lat, lon, tz);
    }

    /** "5:49" or "17:49", following the TV's 12/24-hour setting like the clock does. */
    static String time(Context ctx, int minutes) {
        int h = minutes / 60 % 24, m = minutes % 60;
        if (!DateFormat.is24HourFormat(ctx)) h = h % 12 == 0 ? 12 : h % 12;
        return h + ":" + (m < 10 ? "0" : "") + m;
    }

    /** "Subuh 5:49   Zohor 13:01 …", the next prayer in {@code nextColor} and bold; after Isyak, tomorrow's Subuh. */
    static CharSequence today(Context ctx, double lat, double lon, int nextColor) {
        int[] times = today(lat, lon);
        Calendar c = Calendar.getInstance();
        int next = PrayerTimes.next(times, c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE));
        StringBuilder sb = new StringBuilder();
        int nextStart = 0, nextEnd = 0;
        for (int i = 0; i < times.length; i++) {
            if (i > 0) sb.append("   ");
            if (i == next) nextStart = sb.length();
            sb.append(PrayerTimes.NAMES[i]).append(' ').append(time(ctx, times[i]));
            if (i == next) nextEnd = sb.length();
        }
        SpannableString text = new SpannableString(sb);
        text.setSpan(new ForegroundColorSpan(nextColor), nextStart, nextEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD), nextStart, nextEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }
}
