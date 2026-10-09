package com.mycelialpower.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only cosmetic settings ({@code config/mycelialpower-client.toml}). */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue PARTICLES;
    public static final ModConfigSpec.IntValue PARTICLE_DENSITY;
    public static final ModConfigSpec.BooleanValue SOUNDS;
    public static final ModConfigSpec.DoubleValue SOUND_VOLUME;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Visual and audio effects of running generators. Purely cosmetic.").push("effects");
        PARTICLES = b.comment("Show spore particles around running generators.").define("particles", true);
        PARTICLE_DENSITY = b.comment("Spore particles spawned per animation tick (0-8).").defineInRange("particleDensity", 2, 0, 8);
        SOUNDS = b.comment("Play the machine running sound.").define("sounds", true);
        SOUND_VOLUME = b.comment("Volume of the machine running sound.").defineInRange("soundVolume", 0.35D, 0.0D, 1.0D);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {
    }
}
