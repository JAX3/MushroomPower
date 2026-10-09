package com.mycelialpower.mycelium;

import com.mojang.authlib.GameProfile;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.util.Matchers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * World-side half of the mycelium mechanic: finds eligible target blocks with a bounded search and
 * converts them while respecting chunk loading, spawn protection, the world border and protection mods.
 * The escalating probability itself is handled by {@link MyceliumChance}.
 */
public final class MyceliumSpreadManager {
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

    /**
     * Converts up to {@code maxConversions} random targets. Returns the number actually converted
     * (targets denied by protection are skipped).
     */
    public static int convert(ServerLevel level, BlockPos origin, List<BlockPos> targets, int maxConversions, RandomSource random) {
        int converted = 0;
        List<BlockPos> remaining = new ArrayList<>(targets);
        while (converted < maxConversions && !remaining.isEmpty()) {
            BlockPos target = remaining.remove(random.nextInt(remaining.size()));
            if (tryConvert(level, origin, target)) {
                converted++;
            }
        }
        return converted;
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
        level.playSound(null, target, SoundEvents.FUNGUS_PLACE, SoundSource.BLOCKS, 0.6F, 0.9F + level.random.nextFloat() * 0.2F);
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
