package com.mycelialpower.registry;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.block.MycelialGeneratorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MycelialPower.MOD_ID);

    public static final DeferredBlock<MycelialGeneratorBlock> MYCELIAL_GENERATOR = BLOCKS.register("mycelial_generator",
            () -> new MycelialGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(MycelialGeneratorBlock.LIGHT))));

    private ModBlocks() {
    }
}
