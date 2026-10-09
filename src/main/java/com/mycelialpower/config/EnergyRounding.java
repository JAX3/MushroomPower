package com.mycelialpower.config;

/**
 * How a generator turns its exact (fractional) FE/t value into whole Forge Energy units each tick.
 */
public enum EnergyRounding {
    /** Carry the fractional remainder to the next tick. Long-run output matches the exact formula. */
    ACCUMULATE,
    /** Round to the nearest whole FE every tick (half rounds up). */
    NEAREST,
    /** Always round down. */
    FLOOR,
    /** Always round up. */
    CEILING;

    /** Rounds a value for display or for a single-shot computation. ACCUMULATE displays as NEAREST. */
    public long roundForDisplay(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            return value == Double.POSITIVE_INFINITY ? Long.MAX_VALUE : 0L;
        }
        double clamped = Math.min(value, (double) Long.MAX_VALUE);
        return switch (this) {
            case FLOOR -> (long) Math.floor(clamped);
            case CEILING -> (long) Math.ceil(clamped);
            case NEAREST, ACCUMULATE -> (long) Math.floor(clamped + 0.5D);
        };
    }
}
