package com.mycelialpower.compat.jade;

import com.mycelialpower.block.MycelialGeneratorBlock;
import com.mycelialpower.blockentity.MycelialGeneratorBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration (optional: only loaded by Jade through {@link WailaPlugin}).
 *
 * <p>Looking at a Mycelial Generator shows its state, FE/t, the burning mushroom, network size and scaling, plus a
 * fuel progress bar. Stored energy, the water tank and the slots come from Jade's built-in support for NeoForge's
 * energy, fluid and item capabilities. Each part can be toggled in Jade's settings (Plugins &gt; Mycelial Power).</p>
 */
@WailaPlugin
public class MycelialPowerJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(GeneratorDataProvider.INSTANCE, MycelialGeneratorBlockEntity.class);
        registration.registerProgress(GeneratorFuelProgressProvider.INSTANCE, MycelialGeneratorBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(GeneratorInfoProvider.INSTANCE, MycelialGeneratorBlock.class);
        registration.registerProgressClient(GeneratorFuelProgressProvider.INSTANCE);
    }
}
