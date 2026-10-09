package com.mycelialpower.fuel;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses fuel entries from the server config. Format (TOML string list element):
 * <pre>
 * "minecraft:red_mushroom;burn=600;energy=1.25;water=1.0;mycelium=1.0;scaling=0.0"
 * "#c:mushrooms;burn=400"
 * </pre>
 * The first token is an item id, or {@code #} followed by an item tag id. {@code burn} is required; the
 * remaining keys are optional and default to {@code energy=1, water=1, mycelium=1, scaling=0}.
 */
public final class FuelEntryParser {
    public static final int MIN_BURN = 1;
    public static final int MAX_BURN = 1_000_000;
    public static final double MAX_ENERGY_MULTIPLIER = 1000.0D;
    public static final double MAX_WATER_MULTIPLIER = 1000.0D;
    public static final double MAX_MYCELIUM_BONUS = 100.0D;
    public static final double MAX_SCALING_BONUS = 10.0D;

    private static final Pattern RESOURCE_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private FuelEntryParser() {
    }

    /** Returns the parsed definition, or empty if the entry is malformed or out of range. */
    public static Optional<FuelDefinition> parse(String entry) {
        try {
            return Optional.of(parseOrThrow(entry));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Validator used by the config spec for list elements. */
    public static boolean isValid(Object entry) {
        return entry instanceof String s && parse(s).isPresent();
    }

    public static FuelDefinition parseOrThrow(String entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Fuel entry is null");
        }
        String[] parts = entry.trim().split(";");
        if (parts.length == 0 || parts[0].isBlank()) {
            throw new IllegalArgumentException("Fuel entry has no item id: '" + entry + "'");
        }
        String idToken = parts[0].trim().toLowerCase(Locale.ROOT);
        boolean tag = idToken.startsWith("#");
        String id = tag ? idToken.substring(1) : idToken;
        if (!RESOURCE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid resource id '" + idToken + "' in fuel entry '" + entry + "'");
        }

        Integer burn = null;
        double energy = 1.0D;
        double water = 1.0D;
        double mycelium = 1.0D;
        double scaling = 0.0D;

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0 || eq == part.length() - 1) {
                throw new IllegalArgumentException("Expected key=value but found '" + part + "' in '" + entry + "'");
            }
            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(eq + 1).trim();
            switch (key) {
                case "burn" -> burn = parseInt(value, MIN_BURN, MAX_BURN, key, entry);
                case "energy" -> energy = parseDouble(value, 0.0D, MAX_ENERGY_MULTIPLIER, key, entry);
                case "water" -> water = parseDouble(value, 0.0D, MAX_WATER_MULTIPLIER, key, entry);
                case "mycelium" -> mycelium = parseDouble(value, 0.0D, MAX_MYCELIUM_BONUS, key, entry);
                case "scaling" -> scaling = parseDouble(value, 0.0D, MAX_SCALING_BONUS, key, entry);
                default -> throw new IllegalArgumentException("Unknown key '" + key + "' in fuel entry '" + entry + "'");
            }
        }
        if (burn == null) {
            throw new IllegalArgumentException("Fuel entry '" + entry + "' is missing required key 'burn'");
        }
        return new FuelDefinition(id, tag, burn, energy, water, mycelium, scaling);
    }

    private static int parseInt(String value, int min, int max, String key, String entry) {
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' must be a whole number in '" + entry + "'");
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException("'" + key + "' must be between " + min + " and " + max + " in '" + entry + "'");
        }
        return (int) parsed;
    }

    private static double parseDouble(String value, double min, double max, String key, String entry) {
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' must be a number in '" + entry + "'");
        }
        if (!Double.isFinite(parsed) || parsed < min || parsed > max) {
            throw new IllegalArgumentException("'" + key + "' must be between " + min + " and " + max + " in '" + entry + "'");
        }
        return parsed;
    }

    static String formatNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value) + ".0";
        }
        return Double.toString(value);
    }
}
