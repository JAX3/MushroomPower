package com.mycelialpower.blockentity;

import java.util.Locale;

/** Why a generator is or is not producing energy. */
public enum GeneratorState {
    RUNNING,
    NO_FUEL,
    NO_WATER,
    ENERGY_FULL;

    public String translationKey() {
        return "gui.mycelialpower.state." + name().toLowerCase(Locale.ROOT);
    }

    public static GeneratorState byOrdinal(int ordinal) {
        GeneratorState[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NO_FUEL;
    }
}
