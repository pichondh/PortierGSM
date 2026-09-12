package com.edaxortho.interphone.watchdog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalWatchdogTest {

    @Test
    void aSuccessNeverTriggersReboot() {
        SignalWatchdog watchdog = new SignalWatchdog(3);
        for (int i = 0; i < 10; i++) {
            assertFalse(watchdog.onSignalTestResult(true));
        }
        assertEquals(0, watchdog.getConsecutiveFailures());
        assertFalse(watchdog.isRebootTriggered());
    }

    @Test
    void triggersExactlyOnceWhenThresholdReached() {
        SignalWatchdog watchdog = new SignalWatchdog(3);
        assertFalse(watchdog.onSignalTestResult(false)); // 1
        assertFalse(watchdog.onSignalTestResult(false)); // 2
        assertTrue(watchdog.onSignalTestResult(false));  // 3 -> seuil atteint
        assertTrue(watchdog.isRebootTriggered());
        // Les échecs suivants ne redéclenchent pas une nouvelle action.
        assertFalse(watchdog.onSignalTestResult(false)); // 4
        assertFalse(watchdog.onSignalTestResult(false)); // 5
        assertEquals(5, watchdog.getConsecutiveFailures());
    }

    @Test
    void aSuccessResetsTheCounterAndArmsTheWatchdogAgain() {
        SignalWatchdog watchdog = new SignalWatchdog(2);
        assertFalse(watchdog.onSignalTestResult(false));
        assertTrue(watchdog.onSignalTestResult(false)); // seuil atteint
        assertFalse(watchdog.onSignalTestResult(true));  // le module répond de nouveau
        assertEquals(0, watchdog.getConsecutiveFailures());
        assertFalse(watchdog.isRebootTriggered());

        // Un nouveau blocage doit pouvoir redéclencher une action.
        assertFalse(watchdog.onSignalTestResult(false));
        assertTrue(watchdog.onSignalTestResult(false));
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new SignalWatchdog(0));
        assertThrows(IllegalArgumentException.class, () -> new SignalWatchdog(-1));
    }
}
