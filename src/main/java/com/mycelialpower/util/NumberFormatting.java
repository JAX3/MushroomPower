package com.mycelialpower.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Locale-independent number formatting used by the GUI and JEI so that large energy values stay readable.
 */
public final class NumberFormatting {
    private static final String[] SUFFIXES = {"", "k", "M", "G", "T", "P", "E"};
    private static final ThreadLocal<DecimalFormat> GROUPED = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT)));
    private static final ThreadLocal<DecimalFormat> DECIMAL = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)));

    private NumberFormatting() {
    }

    /** {@code 2064 -> "2,064"}. */
    public static String grouped(long value) {
        return GROUPED.get().format(value);
    }

    /** {@code 57.6 -> "57.6"}, {@code 1234.567 -> "1,234.57"}. */
    public static String decimal(double value) {
        if (!Double.isFinite(value)) {
            return value > 0 ? "∞" : "0";
        }
        return DECIMAL.get().format(value);
    }

    /** Compact form for bars and narrow labels: {@code 950 -> "950"}, {@code 45200 -> "45.2k"}, {@code 3.1e6 -> "3.1M"}. */
    public static String compact(double value) {
        if (!Double.isFinite(value)) {
            return value > 0 ? "∞" : "0";
        }
        boolean negative = value < 0;
        double abs = Math.abs(value);
        if (abs < 1000) {
            String s = abs == Math.rint(abs) ? Long.toString((long) abs) : DECIMAL.get().format(abs);
            return negative ? "-" + s : s;
        }
        int index = 0;
        while (abs >= 1000 && index < SUFFIXES.length - 1) {
            abs /= 1000;
            index++;
        }
        String number;
        if (abs >= 100) {
            number = Long.toString((long) Math.floor(abs));
        } else if (abs >= 10) {
            number = trim(String.format(Locale.ROOT, "%.1f", Math.floor(abs * 10) / 10));
        } else {
            number = trim(String.format(Locale.ROOT, "%.2f", Math.floor(abs * 100) / 100));
        }
        return (negative ? "-" : "") + number + SUFFIXES[index];
    }

    /** Fraction to percentage text: {@code 0.055 -> "5.5%"}. */
    public static String percent(double fraction) {
        return decimal(fraction * 100.0D) + "%";
    }

    /** Ticks to {@code m:ss} or {@code s.s} text. */
    public static String ticksToTime(long ticks) {
        if (ticks <= 0) {
            return "0s";
        }
        long totalSeconds = ticks / 20;
        if (totalSeconds < 60) {
            return trim(String.format(Locale.ROOT, "%.1f", ticks / 20.0D)) + "s";
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes >= 60) {
            return (minutes / 60) + "h " + (minutes % 60) + "m";
        }
        return minutes + "m " + seconds + "s";
    }

    private static String trim(String number) {
        if (number.indexOf('.') < 0) {
            return number;
        }
        String s = number.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
