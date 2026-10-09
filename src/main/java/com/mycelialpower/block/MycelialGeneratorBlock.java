package com.mycelialpower.block;

import com.mojang.serialization.MapCodec;
import com.mycelialpower.blockentity.MycelialGeneratorBlockEntity;
import com.mycelialpower.config.ClientConfig;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.registry.ModBlockEntities;
import com.mycelialpower.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Mycelial Generator. Visual variants (facing, running/idle, emitted light) are block state
 * properties of this single block.
 */
public class MycelialGeneratorBlock extends BaseEntityBlock {
    public static final MapCodec<MycelialGeneratorBlock> CODEC = simpleCodec(MycelialGeneratorBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** Light emitted while running; set from the config by the block entity. */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);

    private static final DustParticleOptions ENERGY_DUST = new DustParticleOptions(new Vector3f(0.62F, 0.35F, 1.0F), 0.8F);

    public MycelialGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false)
                .setValue(LIGHT, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE, LIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MycelialGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.MYCELIAL_GENERATOR.get(), MycelialGeneratorBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MycelialGeneratorBlockEntity generator) {
            player.openMenu(generator, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (ServerConfig.get(ServerConfig.ALLOW_BUCKET_INTERACTION)
                && FluidUtil.getFluidHandler(stack).isPresent()
                && FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof MycelialGeneratorBlockEntity generator) {
                generator.dropContents(level, pos);
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Comparator output follows the stored energy. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MycelialGeneratorBlockEntity generator) {
            int stored = generator.getEnergyStorage().getEnergyStored();
            int max = generator.getEnergyStorage().getMaxEnergyStored();
            return stored <= 0 ? 0 : 1 + (int) Math.floor(14.0D * stored / Math.max(1, max));
        }
        return 0;
    }

    /** Client-side effects: spore particles, an energy glow and the machine sound while running. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;

        if (ClientConfig.SOUNDS.get() && random.nextInt(20) == 0) {
            float volume = ClientConfig.SOUND_VOLUME.get().floatValue();
            if (volume > 0) {
                level.playLocalSound(x, y, z, ModSounds.GENERATOR_RUNNING.get(), SoundSource.BLOCKS, volume,
                        0.85F + random.nextFloat() * 0.3F, false);
            }
        }
        if (!ClientConfig.PARTICLES.get()) {
            return;
        }
        int density = ClientConfig.PARTICLE_DENSITY.get();
        for (int i = 0; i < density; i++) {
            level.addParticle(ParticleTypes.MYCELIUM,
                    pos.getX() + random.nextDouble(), pos.getY() + 1.05D + random.nextDouble() * 0.3D, pos.getZ() + random.nextDouble(),
                    0.0D, 0.0D, 0.0D);
        }
        if (density > 0 && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR,
                    pos.getX() + random.nextDouble(), pos.getY() + 1.0D, pos.getZ() + random.nextDouble(),
                    0.0D, 0.02D, 0.0D);
        }
        if (density > 0 && random.nextInt(2) == 0) {
            Direction facing = state.getValue(FACING);
            double offset = 0.53D;
            double px = x + facing.getStepX() * offset + (facing.getStepX() == 0 ? (random.nextDouble() - 0.5D) * 0.5D : 0.0D);
            double pz = z + facing.getStepZ() * offset + (facing.getStepZ() == 0 ? (random.nextDouble() - 0.5D) * 0.5D : 0.0D);
            double py = y + (random.nextDouble() - 0.5D) * 0.5D;
            level.addParticle(ENERGY_DUST, px, py, pz, 0.0D, 0.0D, 0.0D);
        }
    }
}
