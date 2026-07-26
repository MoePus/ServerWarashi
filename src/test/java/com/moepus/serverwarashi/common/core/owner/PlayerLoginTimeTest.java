package com.moepus.serverwarashi.common.core.owner;

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerLoginTimeTest {
    @Test
    void derivesCacheInsertionFromOneMonthExpiration() {
        Calendar expiration = Calendar.getInstance();
        expiration.clear();
        expiration.set(2026, Calendar.MARCH, 28, 12, 30, 0);
        Calendar expected = Calendar.getInstance();
        expected.clear();
        expected.set(2026, Calendar.FEBRUARY, 28, 12, 30, 0);

        assertEquals(expected.getTimeInMillis(),
                PlayerLoginTime.fromExpiration(new Date(expiration.getTimeInMillis())));
        assertEquals(0L, PlayerLoginTime.fromExpiration(null));
    }
}
