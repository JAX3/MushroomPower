package com.mycelialpower.registry;

import com.mycelialpower.MycelialPower;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MycelialPower.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GENERATOR_RUNNING = SOUNDS.register("block.mycelial_generator.running",
            () -> SoundEvent.createVariableRangeEvent(MycelialPower.id("block.mycelial_generator.running")));

    private ModSounds() {
    }
}
