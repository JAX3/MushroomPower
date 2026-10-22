package com.mycelialpower.network;

/**
 * Pure rules for manual (Mycelium Wrench) links. No Minecraft classes, so the rules are unit-tested.
 */
public final class LinkRules {
    private LinkRules() {
    }

    /** Why a link request was accepted or rejected. */
    public enum Result {
        SUCCESS,
        DISABLED,
        SAME_GENERATOR,
        ALREADY_LINKED,
        DIFFERENT_DIMENSION,
        TOO_FAR,
        FIRST_AT_LIMIT,
        SECOND_AT_LIMIT,
        NOT_LOADED,
        NOT_A_GENERATOR,
        MERGE_NOT_ALLOWED;

        public boolean success() {
            return this == SUCCESS;
        }

        public String translationKey() {
            return "message.mycelialpower.link." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Cross-dimension links are allowed only when {@code allow_cross_dimension_links = true} AND
     * {@code require_same_dimension = false}; {@code require_same_dimension} always wins.
     */
    public static boolean crossDimensionAllowed(boolean allowCrossDimension, boolean requireSameDimension) {
        return allowCrossDimension && !requireSameDimension;
    }

    /** Straight-line (Euclidean) distance check between block positions, overflow-safe. */
    public static boolean withinDistance(int ax, int ay, int az, int bx, int by, int bz, int maxDistance) {
        if (maxDistance <= 0) {
            return false;
        }
        long dx = (long) ax - bx;
        long dy = (long) ay - by;
        long dz = (long) az - bz;
        long max = maxDistance;
        return dx * dx + dy * dy + dz * dz <= max * max;
    }

    public static double distance(int ax, int ay, int az, int bx, int by, int bz) {
        long dx = (long) ax - bx;
        long dy = (long) ay - by;
        long dz = (long) az - bz;
        return Math.sqrt((double) (dx * dx + dy * dy + dz * dz));
    }

    /**
     * Validates a new link from the static facts about both ends. Loaded/exists checks are done by the
     * caller first because they need the world.
     *
     * @param sameDimension  both generators are in the same dimension
     * @param withinDistance result of {@link #withinDistance} (ignored for cross-dimension links)
     * @param firstLinks     current manual links of the first generator
     * @param secondLinks    current manual links of the second generator
     * @param alreadyLinked  the two generators are already linked
     * @param wouldMerge     the link joins two different multi-generator networks
     */
    public static Result check(boolean enabled, boolean samePosition, boolean sameDimension, boolean crossDimensionAllowed,
                               boolean withinDistance, int firstLinks, int secondLinks, int maxLinks,
                               boolean alreadyLinked, boolean wouldMerge, boolean mergingAllowed) {
        if (!enabled) {
            return Result.DISABLED;
        }
        if (samePosition) {
            return Result.SAME_GENERATOR;
        }
        if (alreadyLinked) {
            return Result.ALREADY_LINKED;
        }
        if (!sameDimension && !crossDimensionAllowed) {
            return Result.DIFFERENT_DIMENSION;
        }
        if (sameDimension && !withinDistance) {
            return Result.TOO_FAR;
        }
        if (firstLinks >= maxLinks) {
            return Result.FIRST_AT_LIMIT;
        }
        if (secondLinks >= maxLinks) {
            return Result.SECOND_AT_LIMIT;
        }
        if (wouldMerge && !mergingAllowed) {
            return Result.MERGE_NOT_ALLOWED;
        }
        return Result.SUCCESS;
    }

    /**
     * Whether an existing link still counts under the current config (links are kept but ignored when the
     * config later becomes stricter, so loosening it again restores them).
     */
    public static boolean active(boolean enabled, boolean sameDimension, boolean crossDimensionAllowed, boolean withinDistance) {
        if (!enabled) {
            return false;
        }
        if (!sameDimension) {
            return crossDimensionAllowed;
        }
        return withinDistance;
    }

    /** Remaining wrench uses; {@code durability <= 0} means unlimited ({@code Integer.MAX_VALUE}). */
    public static int remainingUses(int durability, int used) {
        if (durability <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, durability - Math.max(0, used));
    }
}
