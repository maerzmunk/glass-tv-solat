package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Worked-out times against JAKIM's e-Solat timetable for WLY01 (Kuala Lumpur), 2026. */
public class PrayerTimesTest {

    private static final double LAT = 3.1390, LON = 101.6869;

    /** {Subuh, Zohor, Asar, Maghrib, Isyak} as "H:mm", each within a minute of JAKIM's. */
    private static void check(int month, int day, String... jakim) {
        int[] t = PrayerTimes.minutes(2026, month, day, LAT, LON, 8);
        for (int i = 0; i < 5; i++) {
            String[] hm = jakim[i].split(":");
            int want = Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
            assertTrue(month + "/" + day + " " + PrayerTimes.NAMES[i] + ": " + t[i] + " vs " + want,
                    Math.abs(t[i] - want) <= 1);
        }
    }

    @Test
    public void matchesJakimThroughTheYear() {
        check(10, 21, "5:49", "13:01", "16:18", "19:00", "20:10");
        check(10, 1, "5:53", "13:06", "16:16", "19:07", "20:16");
        check(10, 31, "5:48", "13:00", "16:20", "18:58", "20:09");
    }

    @Test
    public void nextIsTomorrowsSubuhAfterIsyak() {
        int[] t = {349, 781, 978, 1140, 1210};
        assertEquals(0, PrayerTimes.next(t, 300));
        assertEquals(1, PrayerTimes.next(t, 349));
        assertEquals(3, PrayerTimes.next(t, 1000));
        assertEquals(0, PrayerTimes.next(t, 1300));
    }
}
