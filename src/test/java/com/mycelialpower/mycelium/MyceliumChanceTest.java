package com.mycelialpower.mycelium;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyceliumChanceTest {
    private static final double INITIAL = 0.01D;
    private static final double INCREASE = 0.005D;
    private static final double MAX = 1.0D;

    @Test
    void matchesSpecificationTable() {
        assertEquals(0.010D, MyceliumChance.chanceOnAttempt(1, INITIAL, INCREASE, MAX), 1e-9);
        assertEquals(0.015D, MyceliumChance.chanceOnAttempt(2, INITIAL, INCREASE, MAX), 1e-9);
        assertEquals(0.020D, MyceliumChance.chanceOnAttempt(3, INITIAL, INCREASE, MAX), 1e-9);
        assertEquals(0.055D, MyceliumChance.chanceOnAttempt(10, INITIAL, INCREASE, MAX), 1e-9);
        assertEquals(0.105D, MyceliumChance.chanceOnAttempt(20, INITIAL, INCREASE, MAX), 1e-9);
        assertEquals(0.505D, MyceliumChance.chanceOnAttempt(100, INITIAL, INCREASE, MAX), 1e-9);
        // 1% + 197 x 0.5% = 99.5% on attempt 198; attempt 199 reaches exactly 100%.
        assertEquals(0.995D, MyceliumChance.chanceOnAttempt(198, INITIAL, INCREASE, MAX), 1e-9);
        assertEquals(1.000D, MyceliumChance.chanceOnAttempt(199, INITIAL, INCREASE, MAX), 0.0D);
    }

    @Test
    void noFloatingPointDriftBelowCertainty() {
        double chance = INITIAL;
        for (int i = 0; i < 10_000; i++) {
            chance = MyceliumChance.afterFailure(chance, INCREASE, 1.0D, MAX);
        }
        assertEquals(1.0D, chance, 0.0D);
    }

    @Test
    void certainChanceAlwaysSucceeds() {
        assertTrue(MyceliumChance.succeeds(1.0D, 0.9999999D));
        assertTrue(MyceliumChance.succeeds(1.0D - 1e-12, 0.9999999D));
        assertFalse(MyceliumChance.succeeds(0.0D, 0.0D));
        assertTrue(MyceliumChance.succeeds(0.3D, 0.29D));
        assertFalse(MyceliumChance.succeeds(0.3D, 0.30D));
    }

    @Test
    void maxChanceCapsGrowth() {
        assertEquals(0.5D, MyceliumChance.afterFailure(0.49D, 0.1D, 1.0D, 0.5D), 0.0D);
    }

    @Test
    void growthFactorAndExtraConversions() {
        assertEquals(1.0D, MyceliumChance.networkGrowthFactor(1, 0.1D));
        assertEquals(1.4D, MyceliumChance.networkGrowthFactor(5, 0.1D), 1e-9);
        assertEquals(0.02D, MyceliumChance.afterFailure(0.01D, 0.005D, 2.0D, 1.0D), 1e-12);
        assertEquals(1, MyceliumChance.conversionsPerAttempt(1, 3, true, 4, 4));
        assertEquals(3, MyceliumChance.conversionsPerAttempt(1, 8, true, 4, 4));
        assertEquals(5, MyceliumChance.conversionsPerAttempt(1, 64, true, 4, 4));
        assertEquals(1, MyceliumChance.conversionsPerAttempt(1, 64, false, 4, 4));
    }
}
