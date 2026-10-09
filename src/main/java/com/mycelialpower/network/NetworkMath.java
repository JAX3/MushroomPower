package com.mycelialpower.network;

/**
 * Pure, overflow-safe implementation of the exponential network formula:
 * <pre>
 * Network FE/t = Base FE/t x Generators x (Multiplier ^ (Generators - 1))
 * </pre>
 * Each generator contributes {@code Base x FuelMultiplier x (Multiplier + FuelScalingBonus) ^ (Generators - 1)},
 * so a network of identical generators produces exactly the formula above.
 * <p>
 * All values are computed in {@code double} and clamped; nothing here can overflow or go negative.
 */
public final class NetworkMath {
    /** Hard ceiling for any single per-tick value handed to integer energy code. */
    public static final double MAX_PER_TICK = Integer.MAX_VALUE;

    private NetworkMath() {
    }

    /** {@code multiplier ^ (count - 1)}, clamped to a finite non-negative value. */
    public static double scaleFactor(double multiplier, int count) {
        if (count <= 1 || !(multiplier > 0)) {
            return count <= 1 ? 1.0D : 0.0D;
        }
        double result = Math.pow(multiplier, count - 1);
        if (Double.isNaN(result) || result < 0) {
            return 0.0D;
        }
        if (Double.isInfinite(result)) {
            return Double.MAX_VALUE;
        }
        return result;
    }

    /** Raw (uncapped) network total for {@code count} identical generators with fuel multiplier 1. */
    public static double rawNetworkOutput(double base, int count, double multiplier) {
        if (count <= 0 || !(base > 0)) {
            return 0.0D;
        }
        return safeMultiply(safeMultiply(base, count), scaleFactor(multiplier, count));
    }

    /**
     * Network total after applying the optional cap ({@code cap <= 0} means uncapped) and the hard
     * integer ceiling.
     */
    public static double networkOutput(double base, int count, double multiplier, double cap) {
        double raw = rawNetworkOutput(base, count, multiplier);
        if (cap > 0 && raw > cap) {
            raw = cap;
        }
        return raw;
    }

    /**
     * Exact FE/t produced by one active generator.
     *
     * @param base           base FE/t per generator
     * @param fuelMultiplier the burning fuel's energy multiplier
     * @param multiplier     the network scaling multiplier (already including any fuel scaling bonus)
     * @param count          the number of generators counted for scaling (at least 1)
     * @param networkCap     optional maximum network FE/t ({@code <= 0} disables the cap)
     */
    public static double generatorOutput(double base, double fuelMultiplier, double multiplier, int count, double networkCap) {
        if (!(base > 0) || !(fuelMultiplier > 0)) {
            return 0.0D;
        }
        int n = Math.max(1, count);
        double perGenerator = safeMultiply(safeMultiply(base, fuelMultiplier), scaleFactor(multiplier, n));
        if (networkCap > 0) {
            double raw = rawNetworkOutput(base, n, multiplier);
            if (raw > networkCap && raw > 0) {
                perGenerator = perGenerator * (networkCap / raw);
            }
        }
        return clampPerTick(perGenerator);
    }

    public static double clampPerTick(double value) {
        if (Double.isNaN(value) || value <= 0) {
            return 0.0D;
        }
        return Math.min(value, MAX_PER_TICK);
    }

    /** Multiplies two non-negative values, saturating at {@link Double#MAX_VALUE} instead of reaching infinity. */
    public static double safeMultiply(double a, double b) {
        double r = a * b;
        if (Double.isNaN(r)) {
            return 0.0D;
        }
        return Double.isInfinite(r) ? Double.MAX_VALUE : r;
    }

    /** Adds two non-negative longs, saturating at {@link Long#MAX_VALUE}. */
    public static long saturatedAdd(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) {
            return a > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return r;
    }
}
