package com.mycelialpower.network;

import com.mycelialpower.config.EnergyRounding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkMathTest {
    private static final double BASE = 40.0D;
    private static final double MULTIPLIER = 1.2D;

    @Test
    void matchesSpecificationTable() {
        int[] generators = {1, 2, 3, 4, 5, 10};
        long[] expected = {40, 96, 173, 276, 415, 2064};
        for (int i = 0; i < generators.length; i++) {
            double total = NetworkMath.networkOutput(BASE, generators[i], MULTIPLIER, 0);
            assertEquals(expected[i], EnergyRounding.NEAREST.roundForDisplay(total), "generators=" + generators[i]);
        }
    }

    @Test
    void preciseValuesAreKeptInternally() {
        assertEquals(172.8D, NetworkMath.networkOutput(BASE, 3, MULTIPLIER, 0), 1e-9);
        assertEquals(57.6D, NetworkMath.generatorOutput(BASE, 1.0D, MULTIPLIER, 3, 0), 1e-9);
    }

    @Test
    void perGeneratorOutputsSumToNetworkFormula() {
        for (int n = 1; n <= 64; n++) {
            double each = NetworkMath.generatorOutput(BASE, 1.0D, MULTIPLIER, n, 0);
            double total = NetworkMath.networkOutput(BASE, n, MULTIPLIER, 0);
            if (total < NetworkMath.MAX_PER_TICK) {
                assertEquals(total, each * n, total * 1e-12, "n=" + n);
            }
        }
    }

    @Test
    void fuelMultiplierScalesOutput() {
        assertEquals(50.0D, NetworkMath.generatorOutput(BASE, 1.25D, MULTIPLIER, 1, 0), 1e-9);
    }

    @Test
    void capScalesEveryMemberProportionally() {
        double each = NetworkMath.generatorOutput(BASE, 1.0D, MULTIPLIER, 10, 1000.0D);
        assertEquals(100.0D, each, 1e-9);
        assertEquals(1000.0D, NetworkMath.networkOutput(BASE, 10, MULTIPLIER, 1000.0D), 1e-9);
    }

    @Test
    void extremeNetworksNeverOverflow() {
        double each = NetworkMath.generatorOutput(1_000_000D, 1000D, 10D, 1024, 0);
        assertTrue(Double.isFinite(each));
        assertTrue(each > 0 && each <= Integer.MAX_VALUE);
        double total = NetworkMath.networkOutput(1_000_000D, 1024, 10D, 0);
        assertTrue(Double.isFinite(total) && total > 0);
    }

    @Test
    void invalidInputsProduceZeroNotNegative() {
        assertEquals(0.0D, NetworkMath.generatorOutput(-5, 1, 1.2, 3, 0));
        assertEquals(0.0D, NetworkMath.generatorOutput(40, -1, 1.2, 3, 0));
        assertEquals(0.0D, NetworkMath.generatorOutput(Double.NaN, 1, 1.2, 3, 0));
        assertEquals(1.0D, NetworkMath.scaleFactor(1.2, 0));
        assertEquals(0.0D, NetworkMath.networkOutput(40, 0, 1.2, 0));
    }

    @Test
    void saturatedAddClamps() {
        assertEquals(Long.MAX_VALUE, NetworkMath.saturatedAdd(Long.MAX_VALUE - 1, 10));
        assertEquals(15L, NetworkMath.saturatedAdd(5, 10));
    }
}
