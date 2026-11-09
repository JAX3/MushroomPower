package com.mycelialpower.mycelium;

import com.mojang.authlib.GameProfile;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.util.Matchers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * World-side half of the mycelium mechanic: finds eligible target blocks with a bounded search and
 * converts them while respecting chunk loading, spawn protection, the world border and protection mods.
 * The escalating probability itself is handled by {@link MyceliumChance}.
 */
public final class MyceliumSpreadManager {
    /** Vanilla huge mushrooms are at most 13 blocks tall (doubled stem) with a cap radius of 3. */
    private static final int HUGE_MUSHROOM_MAX_HEIGHT = 14;
    private static final int HUGE_MUSHROOM_RADIUS = 3;
    private MyceliumSpreadManager() {
    }

    /**
     * Collects every eligible target within the configured radius. Only positions in loaded chunks are
     * inspected. The search is a cube of at most 17^3 = 4913 positions (radius is capped at 8 in the
     * config) and runs once per attempt interval.
     */
    public static List<BlockPos> findTargets(ServerLevel level, BlockPos origin) {
        int radius = ServerConfig.get(ServerConfig.SEARCH_RADIUS);
        boolean airAbove = ServerConfig.get(ServerConfig.REQUIRE_AIR_ABOVE);
        boolean needLight = ServerConfig.get(ServerConfig.REQUIRE_LIGHT);
        int minLight = ServerConfig.get(ServerConfig.MIN_LIGHT);
        BlockState result = Matchers.conversionResult();

        List<BlockPos> targets = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                cursor.set(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
                if (!level.isLoaded(cursor)) {
                    continue;
                }
                for (int dy = -radius; dy <= radius; dy++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (level.isOutsideBuildHeight(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(result.getBlock()) || !Matchers.isConvertible(state)) {
                        continue;
                    }
                    above.setWithOffset(cursor, Direction.UP);
                    if (airAbove && !level.getBlockState(above).isAir()) {
                        continue;
                    }
                    if (needLight && level.getMaxLocalRawBrightness(above) < minLight) {
                        continue;
                    }
                    targets.add(cursor.immutable());
                }
            }
        }
        return targets;
    }

    /** Outcome of one successful attempt: blocks converted and huge mushrooms that sprouted on them. */
    public record ConversionResult(int converted, int hugeMushrooms) {
    }

    /**
     * Converts up to {@code maxConversions} random targets (targets denied by protection are skipped). Each
     * converted block may then sprout a huge mushroom.
     */
    public static ConversionResult convert(ServerLevel level, BlockPos origin, List<BlockPos> targets, int maxConversions, RandomSource random) {
        int converted = 0;
        int mushrooms = 0;
        List<BlockPos> remaining = new ArrayList<>(targets);
        while (converted < maxConversions && !remaining.isEmpty()) {
            BlockPos target = remaining.remove(random.nextInt(remaining.size()));
            if (tryConvert(level, origin, target)) {
                converted++;
                if (tryGrowHugeMushroom(level, origin, target, random)) {
                    mushrooms++;
                }
            }
        }
        return new ConversionResult(converted, mushrooms);
    }

    /**
     * Grows a vanilla huge red or brown mushroom on top of a freshly converted block. Uses the vanilla
     * configured features (so it looks and behaves exactly like bone-mealed mushrooms) and fires NeoForge's
     * BlockGrowFeatureEvent so other mods can cancel or replace it. Never loads chunks: every chunk the
     * mushroom could touch must already be loaded.
     */
    private static boolean tryGrowHugeMushroom(ServerLevel level, BlockPos origin, BlockPos ground, RandomSource random) {
        if (!ServerConfig.get(ServerConfig.HUGE_MUSHROOM_ENABLED)) {
            return false;
        }
        int minDistance = ServerConfig.get(ServerConfig.HUGE_MUSHROOM_MIN_DISTANCE);
        if (Math.max(Math.abs(ground.getX() - origin.getX()), Math.abs(ground.getZ() - origin.getZ())) < minDistance) {
            return false;
        }
        if (random.nextDouble() >= ServerConfig.get(ServerConfig.HUGE_MUSHROOM_CHANCE)) {
            return false;
        }
        BlockPos stem = ground.above();
        if (!level.isEmptyBlock(stem) || stem.getY() + HUGE_MUSHROOM_MAX_HEIGHT >= level.getMaxY()) {
            return false;
        }
        if (!level.hasChunksAt(stem.getX() - HUGE_MUSHROOM_RADIUS, stem.getZ() - HUGE_MUSHROOM_RADIUS,
                stem.getX() + HUGE_MUSHROOM_RADIUS, stem.getZ() + HUGE_MUSHROOM_RADIUS)) {
            return false;
        }
        if (!permitted(level, origin, stem)) {
            return false;
        }
        boolean red = random.nextDouble() < ServerConfig.get(ServerConfig.HUGE_MUSHROOM_RED_RATIO);
        ResourceKey<Feature> key = red ? TreeFeatures.HUGE_RED_MUSHROOM : TreeFeatures.HUGE_BROWN_MUSHROOM;
        Optional<Holder.Reference<Feature>> feature =
                level.registryAccess().lookupOrThrow(Registries.FEATURE).get(key);
        if (feature.isEmpty()) {
            return false;
        }
        BlockGrowFeatureEvent event = EventHooks.fireBlockGrowFeature(level, random, stem, feature.get());
        Holder<Feature> chosen = event.getFeature();
        if (event.isCanceled() || chosen == null) {
            return false;
        }
        if (!chosen.value().place(level, level.getChunkSource().getGenerator(), random, stem)) {
            return false; // not enough room; the feature places nothing in that case
        }
        level.playSound(null, stem, SoundEvents.FUNGUS_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, stem.getX() + 0.5D, stem.getY() + 1.0D, stem.getZ() + 0.5D,
                20, 1.0D, 1.0D, 1.0D, 0.0D);
        return true;
    }

    /** Spawn protection and protection-mod check for a position, without changing anything. */
    private static boolean permitted(ServerLevel level, BlockPos origin, BlockPos pos) {
        boolean spawnProtection = ServerConfig.get(ServerConfig.RESPECT_SPAWN_PROTECTION);
        boolean placeEvents = ServerConfig.get(ServerConfig.FIRE_PLACE_EVENTS);
        if (!spawnProtection && !placeEvents) {
            return true;
        }
        FakePlayer player = fakePlayer(level, origin);
        if (spawnProtection && !level.mayInteract(player, pos)) {
            return false;
        }
        if (placeEvents) {
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(pos.below()), player);
            return !NeoForge.EVENT_BUS.post(event).isCanceled();
        }
        return true;
    }

    private static boolean tryConvert(ServerLevel level, BlockPos origin, BlockPos target) {
        if (!level.isLoaded(target)) {
            return false;
        }
        BlockState oldState = level.getBlockState(target);
        if (!Matchers.isConvertible(oldState)) {
            return false;
        }
        BlockState newState = Matchers.conversionResult();
        FakePlayer player = null;
        if (ServerConfig.get(ServerConfig.RESPECT_SPAWN_PROTECTION) || ServerConfig.get(ServerConfig.FIRE_PLACE_EVENTS)) {
            player = fakePlayer(level, origin);
        }
        if (ServerConfig.get(ServerConfig.RESPECT_SPAWN_PROTECTION) && !level.mayInteract(player, target)) {
            return false;
        }

        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, target);
        if (!level.setBlock(target, newState, Block.UPDATE_ALL)) {
            return false;
        }
        if (ServerConfig.get(ServerConfig.FIRE_PLACE_EVENTS)) {
            BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(target.below()), player);
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
                // A protection mod vetoed the change: put the original block back.
                level.setBlock(target, oldState, Block.UPDATE_ALL);
                return false;
            }
        }
        level.gameEvent(null, GameEvent.BLOCK_CHANGE, target);
        level.playSound(null, target, SoundEvents.FUNGUS_PLACE, SoundSource.BLOCKS, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        level.levelEvent(2001, target, Block.getId(newState));
        return true;
    }

    private static FakePlayer fakePlayer(ServerLevel level, BlockPos origin) {
        String name = ServerConfig.get(ServerConfig.FAKE_PLAYER_NAME);
        if (name.isBlank() || name.length() > 16) {
            name = "[MycelialPower]";
        }
        UUID uuid = UUID.nameUUIDFromBytes(("mycelialpower:" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(uuid, name));
        player.setPos(origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D);
        return player;
    }
}
