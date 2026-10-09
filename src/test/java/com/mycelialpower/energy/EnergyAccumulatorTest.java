package com.mycelialpower.energy;

import com.mycelialpower.config.EnergyRounding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyAccumulatorTest {
    @Test
    void accumulateIsExactOverTime() {
        EnergyAccumulator accumulator = new EnergyAccumulator();
        long total = 0;
        for (int tick = 0; tick < 1000; tick++) {
            total += accumulator.produce(57.6D, EnergyRounding.ACCUMULATE);
        }
        assertEquals(57_600L, total);
    }

    @Test
    void networkOfThreeProducesExactly1728PerTenTicks() {
        EnergyAccumulator[] generators = {new EnergyAccumulator(), new EnergyAccumulator(), new EnergyAccumulator()};
        long total = 0;
        for (int tick = 0; tick < 10; tick++) {
            for (EnergyAccumulator generator : generators) {
                total += generator.produce(57.6D, EnergyRounding.ACCUMULATE);
            }
        }
        assertEquals(1728L, total);
    }

    @Test
    void otherModes() {
        EnergyAccumulator accumulator = new EnergyAccumulator();
        assertEquals(58, accumulator.produce(57.6D, EnergyRounding.NEAREST));
        assertEquals(57, accumulator.produce(57.6D, EnergyRounding.FLOOR));
        assertEquals(58, accumulator.produce(57.2D, EnergyRounding.CEILING));
    }

    @Test
    void clampsToIntRange() {
        EnergyAccumulator accumulator = new EnergyAccumulator();
        assertEquals(Integer.MAX_VALUE, accumulator.produce(1e30, EnergyRounding.ACCUMULATE));
        assertEquals(Integer.MAX_VALUE, accumulator.produce(Double.POSITIVE_INFINITY, EnergyRounding.NEAREST));
        assertEquals(0, accumulator.produce(Double.NaN, EnergyRounding.ACCUMULATE));
        assertEquals(0, accumulator.produce(-4, EnergyRounding.ACCUMULATE));
    }

    @Test
    void invalidSavedRemainderIsDiscarded() {
        EnergyAccumulator accumulator = new EnergyAccumulator();
        accumulator.setRemainder(5.0D);
        assertEquals(0.0D, accumulator.getRemainder());
        accumulator.setRemainder(0.5D);
        assertEquals(1, accumulator.produce(0.5D, EnergyRounding.ACCUMULATE));
    }
}
