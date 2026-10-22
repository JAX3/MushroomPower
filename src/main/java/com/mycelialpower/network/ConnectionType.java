package com.mycelialpower.network;

import java.util.Locale;

/** How a generator is attached to the rest of its network. */
public enum ConnectionType {
    NONE,
    PHYSICAL,
    MANUAL,
    BOTH;

    public static ConnectionType of(boolean physical, boolean manual) {
        if (physical && manual) {
            return BOTH;
        }
        return physical ? PHYSICAL : (manual ? MANUAL : NONE);
    }

    public static ConnectionType byOrdinal(int ordinal) {
        ConnectionType[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }

    public String translationKey() {
        return "gui.mycelialpower.connection." + name().toLowerCase(Locale.ROOT);
    }
}
