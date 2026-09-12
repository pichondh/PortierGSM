package com.edaxortho.interphone.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkStatusUtilTest {

    @Test
    void registeredHomeNetwork() {
        assertTrue(NetworkStatusUtil.parseRegistered("+CREG: 0,1\r\n\r\nOK\r\n"));
    }

    @Test
    void registeredRoaming() {
        assertTrue(NetworkStatusUtil.parseRegistered("+CREG: 0,5\r\n\r\nOK\r\n"));
    }

    @Test
    void notRegisteredSearching() {
        assertFalse(NetworkStatusUtil.parseRegistered("+CREG: 0,2\r\n\r\nOK\r\n"));
    }

    @Test
    void notRegisteredNoSearch() {
        assertFalse(NetworkStatusUtil.parseRegistered("+CREG: 0,0\r\n\r\nOK\r\n"));
    }

    @Test
    void registrationDenied() {
        assertFalse(NetworkStatusUtil.parseRegistered("+CREG: 0,3\r\n\r\nOK\r\n"));
    }

    @Test
    void nullWhenModuleMute() {
        assertNull(NetworkStatusUtil.parseRegistered(null));
        assertNull(NetworkStatusUtil.parseRegistered(""));
        assertNull(NetworkStatusUtil.parseRegistered("ERROR"));
    }

    @Test
    void labelsAreHumanReadable() {
        assertEquals("Enregistré", NetworkStatusUtil.label(true));
        assertEquals("Non enregistré", NetworkStatusUtil.label(false));
        assertEquals("Inconnu", NetworkStatusUtil.label(null));
    }
}
