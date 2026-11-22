package com.ozerlabs.triterra.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BranchTest {
    @Test
    public void geoUriIsTheDocumentedLatLngForm() {
        assertEquals("geo:32.0853,34.7818", new Branch("x", "y", 32.0853, 34.7818).geoUri());
    }

    @Test
    public void placeholdersAreRenderableAndMarkedAsDemo() {
        assertFalse(PlaceholderBranches.LIST.isEmpty());
        for (Branch b : PlaceholderBranches.LIST) {
            assertTrue(b.name, b.name.contains("לדוגמה"));
            assertTrue(b.address.contains("נתוני דמה"));
            assertTrue(b.lat > 29 && b.lat < 34 && b.lng > 34 && b.lng < 36); // inside Israel
            assertTrue(b.geoUri().matches("geo:-?\\d+\\.\\d+,-?\\d+\\.\\d+"));
        }
    }
}
