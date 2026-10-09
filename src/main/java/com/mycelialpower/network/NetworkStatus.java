package com.mycelialpower.network;

/** Summary of a network's state for display. */
public enum NetworkStatus {
    /** At least one generator is running and scaling applies. */
    ONLINE,
    /** No generator in the network is running. */
    IDLE,
    /** The network reached the configured maximum size; touching generators form separate networks. */
    SIZE_LIMIT,
    /** Network scaling is disabled in the config. */
    SCALING_DISABLED,
    /** The total output is being limited by maxNetworkOutput. */
    OUTPUT_CAPPED;

    public String translationKey() {
        return "gui.mycelialpower.network_status." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
