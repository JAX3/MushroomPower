package com.mycelialpower.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkRulesTest {
    private static LinkRules.Result check(boolean sameDim, boolean cross, boolean within, int first, int second,
                                          boolean linked, boolean merge, boolean mergeAllowed) {
        return LinkRules.check(true, false, sameDim, cross, within, first, second, 8, linked, merge, mergeAllowed);
    }

    @Test
    void distanceIsEuclideanAndInclusive() {
        assertTrue(LinkRules.withinDistance(0, 64, 0, 64, 64, 0, 64));
        assertFalse(LinkRules.withinDistance(0, 64, 0, 65, 64, 0, 64));
        // 3D diagonal: sqrt(40^2 + 40^2 + 40^2) = 69.3 > 64
        assertFalse(LinkRules.withinDistance(0, 0, 0, 40, 40, 40, 64));
        assertTrue(LinkRules.withinDistance(0, 0, 0, 36, 36, 36, 64));
        assertFalse(LinkRules.withinDistance(0, 0, 0, 1, 0, 0, 0));
    }

    @Test
    void extremeCoordinatesDoNotOverflow() {
        assertFalse(LinkRules.withinDistance(-30_000_000, -64, -30_000_000, 30_000_000, 320, 30_000_000, 4096));
        assertTrue(LinkRules.distance(-30_000_000, 0, 0, 30_000_000, 0, 0) > 59_999_999);
    }

    @Test
    void dimensionRules() {
        assertFalse(LinkRules.crossDimensionAllowed(false, true));
        assertFalse(LinkRules.crossDimensionAllowed(true, true));   // require_same_dimension wins
        assertFalse(LinkRules.crossDimensionAllowed(false, false));
        assertTrue(LinkRules.crossDimensionAllowed(true, false));
        assertEquals(LinkRules.Result.DIFFERENT_DIMENSION, check(false, false, true, 0, 0, false, false, true));
        assertEquals(LinkRules.Result.SUCCESS, check(false, true, false, 0, 0, false, false, true)); // distance ignored across dimensions
    }

    @Test
    void checkOrderAndLimits() {
        assertEquals(LinkRules.Result.DISABLED,
                LinkRules.check(false, false, true, false, true, 0, 0, 8, false, false, true));
        assertEquals(LinkRules.Result.SAME_GENERATOR,
                LinkRules.check(true, true, true, false, true, 0, 0, 8, false, false, true));
        assertEquals(LinkRules.Result.ALREADY_LINKED, check(true, false, true, 0, 0, true, false, true));
        assertEquals(LinkRules.Result.TOO_FAR, check(true, false, false, 0, 0, false, false, true));
        assertEquals(LinkRules.Result.FIRST_AT_LIMIT, check(true, false, true, 8, 0, false, false, true));
        assertEquals(LinkRules.Result.SECOND_AT_LIMIT, check(true, false, true, 7, 8, false, false, true));
        assertEquals(LinkRules.Result.MERGE_NOT_ALLOWED, check(true, false, true, 1, 1, false, true, false));
        assertEquals(LinkRules.Result.SUCCESS, check(true, false, true, 1, 1, false, true, true));
        assertEquals(LinkRules.Result.SUCCESS, check(true, false, true, 7, 7, false, false, false));
    }

    @Test
    void activeLinksFollowCurrentConfig() {
        assertTrue(LinkRules.active(true, true, false, true));
        assertFalse(LinkRules.active(false, true, false, true));
        assertFalse(LinkRules.active(true, true, false, false));     // limit lowered after linking
        assertFalse(LinkRules.active(true, false, false, true));
        assertTrue(LinkRules.active(true, false, true, false));
    }

    @Test
    void durability() {
        assertEquals(Integer.MAX_VALUE, LinkRules.remainingUses(0, 500));
        assertEquals(7, LinkRules.remainingUses(10, 3));
        assertEquals(0, LinkRules.remainingUses(10, 15));
    }
}
