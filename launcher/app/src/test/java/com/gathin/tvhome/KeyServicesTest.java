package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class KeyServicesTest {

    private static final String PKG = "com.gathin.tvhome";
    private static final Set<String> HOME = Collections.singleton("HomeKeyService");

    @Test
    public void putsBackARememberedHelperAfterForceStopEmptiedTheList() {
        assertEquals("com.gathin.tvhome/.HomeKeyService", KeyServices.repaired(null, HOME, PKG));
        assertEquals("com.gathin.tvhome/.HomeKeyService", KeyServices.repaired("", HOME, PKG));
        assertEquals("com.gathin.tvhome/.HomeKeyService", KeyServices.repaired("null", HOME, PKG));
    }

    @Test
    public void keepsOtherAppsServices() {
        assertEquals("other.app/.Svc:com.gathin.tvhome/.HomeKeyService",
                KeyServices.repaired("other.app/.Svc", HOME, PKG));
    }

    @Test
    public void leavesTheSettingAloneWhenNothingIsMissing() {
        assertNull(KeyServices.repaired("com.gathin.tvhome/.HomeKeyService", HOME, PKG));
        assertNull(KeyServices.repaired("com.gathin.tvhome/com.gathin.tvhome.HomeKeyService", HOME, PKG));
        assertNull(KeyServices.repaired("", Collections.emptySet(), PKG));
    }

    @Test
    public void onlyRestoresHelpersThatWereOn() {
        Set<String> both = new HashSet<>();
        both.add("HomeKeyService");
        both.add("InputKeyService");
        assertEquals("com.gathin.tvhome/.HomeKeyService:com.gathin.tvhome/.InputKeyService",
                KeyServices.repaired("", both, PKG));
        assertEquals("com.gathin.tvhome/.InputKeyService:com.gathin.tvhome/.HomeKeyService",
                KeyServices.repaired("com.gathin.tvhome/.InputKeyService", both, PKG));
    }

    @Test
    public void matchesOnlyWholeEntries() {
        assertTrue(KeyServices.isOn("a/.B:com.gathin.tvhome/.HomeKeyService", PKG, "HomeKeyService"));
        assertFalse(KeyServices.isOn("com.gathin.tvhome/.HomeKeyServiceX", PKG, "HomeKeyService"));
        assertFalse(KeyServices.isOn(null, PKG, "HomeKeyService"));
    }
}
