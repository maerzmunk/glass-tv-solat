package com.gathin.tvhome;

/**
 * The five daily prayer times for a place, worked out on the TV from the sun's position, so the
 * launcher never goes online for them. The method is Malaysia's (JAKIM): Subuh and Isyak at the sun
 * 18° below the horizon, Asar when a shadow is its object's length plus the noon shadow (Shafi'i), and
 * a little ihtiyati (precaution) added before rounding up. Against JAKIM's 2026 e-Solat timetable for
 * WLY01 (Kuala Lumpur) every time of every day is within a minute.
 */
final class PrayerTimes {

    static final String[] NAMES = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};

    private static final double FAJR_ANGLE = 18;
    private static final double ISHA_ANGLE = 18;
    /** The sun's upper edge on the horizon, with refraction. */
    private static final double SUNSET_ANGLE = 0.833;
    /** Minutes added to each time before rounding up; fitted to JAKIM's published timetable. */
    private static final double[] IHTIYATI = {1.4, 2.2, 1.3, 1.5, 1.5};

    private PrayerTimes() {
    }

    /**
     * Minutes after local midnight for Subuh, Zohor, Asar, Maghrib and Isyak on the given date.
     *
     * @param tzHours the place's offset from UTC that day, e.g. 8 for Malaysia
     */
    static int[] minutes(int year, int month, int day, double lat, double lon, double tzHours) {
        double jd = julianDay(year, month, day) - lon / (15 * 24.0);
        // First guesses as fractions of a day, refined once against the sun at those moments.
        double[] t = {5, 12, 13, 18, 18};
        for (int pass = 0; pass < 2; pass++) {
            double[] f = new double[5];
            for (int i = 0; i < 5; i++) f[i] = t[i] / 24;
            t = new double[]{
                    sunAngleTime(jd, f[0], FAJR_ANGLE, lat, true),
                    midDay(jd, f[1]),
                    asrTime(jd, f[2], lat),
                    sunAngleTime(jd, f[3], SUNSET_ANGLE, lat, false),
                    sunAngleTime(jd, f[4], ISHA_ANGLE, lat, false)};
        }
        int[] out = new int[5];
        for (int i = 0; i < 5; i++) {
            double local = t[i] + tzHours - lon / 15;
            out[i] = (int) Math.ceil(local * 60 + IHTIYATI[i] - 1e-6);
        }
        return out;
    }

    /** Index of the next prayer at {@code nowMinutes} after midnight; after Isyak it is tomorrow's Subuh. */
    static int next(int[] times, int nowMinutes) {
        for (int i = 0; i < times.length; i++) if (nowMinutes < times[i]) return i;
        return 0;
    }

    // ---- the sun, after praytimes.org (Hamid Zarrabi-Zadeh, LGPL/MIT-style), trimmed to what we need ----

    private static double julianDay(int year, int month, int day) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** {declination in degrees, equation of time in hours} at Julian day jd. */
    private static double[] sun(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * sin(g) + 0.020 * sin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = fixHour(Math.toDegrees(Math.atan2(cos(e) * sin(l), cos(l))) / 15);
        double decl = Math.toDegrees(Math.asin(sin(e) * sin(l)));
        double eqt = q / 15 - ra;
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double t) {
        return fixHour(12 - sun(jd + t)[1]);
    }

    private static double sunAngleTime(double jd, double t, double angle, double lat, boolean beforeNoon) {
        double decl = sun(jd + t)[0];
        double noon = midDay(jd, t);
        double cosH = (-sin(angle) - sin(decl) * sin(lat)) / (cos(decl) * cos(lat));
        double h = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosH)))) / 15;
        return noon + (beforeNoon ? -h : h);
    }

    private static double asrTime(double jd, double t, double lat) {
        double decl = sun(jd + t)[0];
        double angle = -Math.toDegrees(Math.atan(1 / (1 + Math.tan(Math.toRadians(Math.abs(lat - decl))))));
        return sunAngleTime(jd, t, angle, lat, false);
    }

    private static double sin(double deg) {
        return Math.sin(Math.toRadians(deg));
    }

    private static double cos(double deg) {
        return Math.cos(Math.toRadians(deg));
    }

    private static double fixAngle(double a) {
        a = a - 360 * Math.floor(a / 360);
        return a < 0 ? a + 360 : a;
    }

    private static double fixHour(double h) {
        h = h - 24 * Math.floor(h / 24);
        return h < 0 ? h + 24 : h;
    }
}
