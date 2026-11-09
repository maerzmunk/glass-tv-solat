package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/** The power button under the power_sleep layout, and nothing else, puts the box to sleep. */
public class PowerKeyTest {
    @Test
    public void remappedPowerButtonSleeps() {
        assertTrue(Config.isSleepKey(KeyEvent.KEYCODE_STB_POWER, 116));
        assertTrue(Config.isSleepKey(KeyEvent.KEYCODE_STB_POWER, 152));
    }

    @Test
    public void otherKeysPassThrough() {
        assertFalse(Config.isSleepKey(KeyEvent.KEYCODE_STB_POWER, 0));
        assertFalse(Config.isSleepKey(KeyEvent.KEYCODE_STB_POWER, 393));
        assertFalse(Config.isSleepKey(KeyEvent.KEYCODE_POWER, 116));
    }
}
