package com.moepus.serverwarashi.client.compat.xaero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class XaeroMapAccessTest {
    @Test
    void supportsTheConfiguredXaeroWorldMapVersion() {
        assertDoesNotThrow(XaeroMapAccess::verifyCompatibility);
    }
}
