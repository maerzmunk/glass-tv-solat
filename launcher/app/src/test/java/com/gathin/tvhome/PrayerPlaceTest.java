package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The place kept for prayer_auto: rounded to about a kilometre, and redrawn only after a real move. */
public class PrayerPlaceTest {

    @Test
    public void roundsToTwoDecimals() {
        assertEquals(1.47, PrayerPlace.round(1.4703), 1e-9);
        assertEquals(103.9, PrayerPlace.round(103.8971), 1e-9);
        assertEquals(-6.21, PrayerPlace.round(-6.2088), 1e-9);
    }

    @Test
    public void firstPlaceAlwaysCounts() {
        assertTrue(PrayerPlace.moved(null, 3.14, 101.69));
    }

    @Test
    public void nearbyIsNotAMove() {
        double[] pasirGudang = {1.47, 103.90};
        assertFalse(PrayerPlace.moved(pasirGudang, 1.48, 103.91));
        // Stored as floats: the same place read back must not count as a move.
        assertFalse(PrayerPlace.moved(new double[]{(float) 1.47, (float) 103.90}, 1.47, 103.90));
    }

    @Test
    public void anotherTownIsAMove() {
        double[] pasirGudang = {1.47, 103.90};
        assertTrue(PrayerPlace.moved(pasirGudang, 3.14, 101.69));   // Kuala Lumpur
        assertTrue(PrayerPlace.moved(pasirGudang, 1.47, 103.76));   // Johor Bahru, west along the coast
    }
}
