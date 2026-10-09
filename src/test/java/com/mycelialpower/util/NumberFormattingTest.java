package com.mycelialpower.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberFormattingTest {
    @Test
    void grouped() {
        assertEquals("2,064", NumberFormatting.grouped(2064));
        assertEquals("0", NumberFormatting.grouped(0));
    }

    @Test
    void compact() {
        assertEquals("950", NumberFormatting.compact(950));
        assertEquals("57.6", NumberFormatting.compact(57.6));
        assertEquals("45.2k", NumberFormatting.compact(45_200));
        assertEquals("100k", NumberFormatting.compact(100_000));
        assertEquals("2.14G", NumberFormatting.compact(Integer.MAX_VALUE));
        assertEquals("1.5M", NumberFormatting.compact(1_500_000));
    }

    @Test
    void percentAndTime() {
        assertEquals("5.5%", NumberFormatting.percent(0.055));
        assertEquals("100%", NumberFormatting.percent(1.0));
        assertEquals("30s", NumberFormatting.ticksToTime(600));
        assertEquals("1m 0s", NumberFormatting.ticksToTime(1200));
        assertEquals("1s", NumberFormatting.ticksToTime(20));
        assertEquals("0.5s", NumberFormatting.ticksToTime(10));
    }
}
