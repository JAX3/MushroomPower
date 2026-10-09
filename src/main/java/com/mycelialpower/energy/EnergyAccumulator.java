package com.mycelialpower.energy;

import com.mycelialpower.config.EnergyRounding;

/**
 * Converts an exact per-tick energy value into whole FE using a configurable rounding mode.
 * In {@link EnergyRounding#ACCUMULATE} mode the fractional part is carried between ticks, so the
 * long-run average output equals the exact formula with no energy created or lost.
 */
public final class EnergyAccumulator {
    private double remainder;

    public int produce(double exactAmount, EnergyRounding rounding) {
        if (!Double.isFinite(exactAmount) || exactAmount <= 0) {
            if (exactAmount == Double.POSITIVE_INFINITY) {
                return Integer.MAX_VALUE;
            }
            return 0;
        }
        double amount = Math.min(exactAmount, Integer.MAX_VALUE);
        switch (rounding) {
            case ACCUMULATE -> {
                double total = amount + remainder;
                double whole = Math.floor(total);
                if (whole >= Integer.MAX_VALUE) {
                    remainder = 0;
                    return Integer.MAX_VALUE;
                }
                remainder = total - whole;
                if (remainder < 0 || remainder >= 1 || Double.isNaN(remainder)) {
                    remainder = 0;
                }
                return (int) whole;
            }
            case NEAREST -> {
                return (int) Math.min(Integer.MAX_VALUE, Math.floor(amount + 0.5D));
            }
            case FLOOR -> {
                return (int) Math.floor(amount);
            }
            case CEILING -> {
                return (int) Math.min(Integer.MAX_VALUE, Math.ceil(amount));
            }
            default -> throw new IllegalStateException("Unknown rounding " + rounding);
        }
    }

    public double getRemainder() {
        return remainder;
    }

    public void setRemainder(double remainder) {
        this.remainder = (Double.isFinite(remainder) && remainder >= 0 && remainder < 1) ? remainder : 0;
    }

    public void reset() {
        remainder = 0;
    }
}
