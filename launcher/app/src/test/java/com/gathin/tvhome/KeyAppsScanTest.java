package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** "SCAN_n" in key_apps: buttons the TV's key layout leaves unmapped, named by scancode. */
public class KeyAppsScanTest {

    @Test
    public void scancodeNamesBecomeNegativeKeys() {
        assertEquals(-393, Config.keyFor("SCAN_393"));
        assertEquals(-398, Config.keyFor("scan_398"));
    }

    @Test
    public void badScancodesAreIgnored() {
        assertEquals(0, Config.keyFor("SCAN_"));
        assertEquals(0, Config.keyFor("SCAN_x1"));
        assertEquals(0, Config.keyFor("SCAN_0"));
        assertEquals(0, Config.keyFor("SCAN_-5"));
    }
}
