package com.mycelialpower.fuel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuelEntryParserTest {
    @Test
    void parsesFullEntry() {
        FuelDefinition def = FuelEntryParser.parseOrThrow("minecraft:red_mushroom;burn=600;energy=1.25;water=1.0;mycelium=2;scaling=0.05");
        assertEquals("minecraft:red_mushroom", def.id());
        assertFalse(def.tag());
        assertEquals(600, def.burnTicks());
        assertEquals(1.25D, def.energyMultiplier());
        assertEquals(1.0D, def.waterMultiplier());
        assertEquals(2.0D, def.myceliumBonus());
        assertEquals(0.05D, def.scalingBonus());
    }

    @Test
    void parsesTagWithDefaults() {
        FuelDefinition def = FuelEntryParser.parseOrThrow(" #c:mushrooms ; burn = 400 ");
        assertTrue(def.tag());
        assertEquals("c:mushrooms", def.id());
        assertEquals(400, def.burnTicks());
        assertEquals(1.0D, def.energyMultiplier());
        assertEquals(1.0D, def.waterMultiplier());
        assertEquals(1.0D, def.myceliumBonus());
        assertEquals(0.0D, def.scalingBonus());
    }

    @Test
    void roundTripsThroughConfigString() {
        FuelDefinition def = FuelEntryParser.parseOrThrow("#c:mushrooms;burn=400;energy=1.5");
        assertEquals(def, FuelEntryParser.parseOrThrow(def.toConfigString()));
    }

    @Test
    void rejectsInvalidEntries() {
        assertFalse(FuelEntryParser.isValid("minecraft:red_mushroom"));            // missing burn
        assertFalse(FuelEntryParser.isValid("minecraft:red_mushroom;burn=0"));     // out of range
        assertFalse(FuelEntryParser.isValid("minecraft:red_mushroom;burn=-5"));
        assertFalse(FuelEntryParser.isValid("minecraft:red_mushroom;burn=10;energy=-1"));
        assertFalse(FuelEntryParser.isValid("minecraft:red_mushroom;burn=10;energy=NaN"));
        assertFalse(FuelEntryParser.isValid("minecraft:red_mushroom;burn=10;foo=1"));
        assertFalse(FuelEntryParser.isValid("Not An Id;burn=10"));
        assertFalse(FuelEntryParser.isValid(";burn=10"));
        assertFalse(FuelEntryParser.isValid(42));
        assertFalse(FuelEntryParser.isValid(null));
        assertTrue(FuelEntryParser.isValid("modid:glowshroom;burn=1000000"));
        assertFalse(FuelEntryParser.isValid("modid:glowshroom;burn=99999999999"));
    }
}
