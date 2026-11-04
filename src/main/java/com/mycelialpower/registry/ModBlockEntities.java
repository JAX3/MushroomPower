package com.mycelialpower.registry;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.blockentity.MycelialGeneratorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MycelialPower.MOD_ID);

    // BlockEntityType.Builder was removed in 1.21.2; NeoForge provides this (factory, blocks...) constructor.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MycelialGeneratorBlockEntity>> MYCELIAL_GENERATOR =
            BLOCK_ENTITY_TYPES.register("mycelial_generator",
                    () -> new BlockEntityType<>(MycelialGeneratorBlockEntity::new, ModBlocks.MYCELIAL_GENERATOR.get()));

    private ModBlockEntities() {
    }
}
