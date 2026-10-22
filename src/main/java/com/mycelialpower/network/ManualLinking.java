package com.mycelialpower.network;

import com.mycelialpower.blockentity.MycelialGeneratorBlockEntity;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Server-authoritative operations behind the Mycelium Wrench. All checks run here; the client only sends the
 * ordinary "use item on block" interaction.
 */
public final class ManualLinking {
    private static final DustParticleOptions FUNGAL_GREEN = new DustParticleOptions(new Vector3f(0.45F, 1.0F, 0.55F), 1.0F);
    private static final int MAX_PARTICLE_POINTS = 96;

    private ManualLinking() {
    }

    /** Whether an existing link counts under the current config (enabled, dimension rules, distance). */
    public static boolean isActive(GlobalPos a, GlobalPos b) {
        boolean sameDimension = a.dimension().equals(b.dimension());
        return LinkRules.active(
                ServerConfig.get(ServerConfig.WRENCH_ENABLED),
                sameDimension,
                crossDimensionAllowed(),
                !sameDimension || withinDistance(a.pos(), b.pos()));
    }

    public static boolean crossDimensionAllowed() {
        return LinkRules.crossDimensionAllowed(ServerConfig.get(ServerConfig.ALLOW_CROSS_DIMENSION_LINKS),
                ServerConfig.get(ServerConfig.REQUIRE_SAME_DIMENSION));
    }

    private static boolean withinDistance(BlockPos a, BlockPos b) {
        return LinkRules.withinDistance(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(),
                ServerConfig.get(ServerConfig.MAX_LINK_DISTANCE));
    }

    /**
     * Links two generators. The first one must still exist; its chunk has to be loaded so the server can
     * verify it (it is never loaded on our behalf).
     */
    public static LinkRules.Result tryLink(MinecraftServer server, GlobalPos first, GlobalPos second) {
        if (!ServerConfig.get(ServerConfig.WRENCH_ENABLED)) {
            return LinkRules.Result.DISABLED;
        }
        GeneratorNetworkManager manager = GeneratorNetworkManager.get(server);
        ServerLevel firstLevel = server.getLevel(first.dimension());
        ServerLevel secondLevel = server.getLevel(second.dimension());
        if (firstLevel == null || secondLevel == null || !firstLevel.isLoaded(first.pos()) || !secondLevel.isLoaded(second.pos())) {
            return LinkRules.Result.NOT_LOADED;
        }
        if (!firstLevel.getBlockState(first.pos()).is(ModBlocks.MYCELIAL_GENERATOR.get())
                || !secondLevel.getBlockState(second.pos()).is(ModBlocks.MYCELIAL_GENERATOR.get())) {
            return LinkRules.Result.NOT_A_GENERATOR;
        }
        if (!(firstLevel.getBlockEntity(first.pos()) instanceof MycelialGeneratorBlockEntity a)
                || !(secondLevel.getBlockEntity(second.pos()) instanceof MycelialGeneratorBlockEntity b)) {
            return LinkRules.Result.NOT_A_GENERATOR;
        }

        ManualLinkData data = ManualLinkData.get(server);
        boolean sameDimension = first.dimension().equals(second.dimension());
        GeneratorNetwork networkA = manager.getNetwork(a);
        GeneratorNetwork networkB = manager.getNetwork(b);
        boolean wouldMerge = networkA != networkB && networkA.size() > 1 && networkB.size() > 1;

        LinkRules.Result result = LinkRules.check(
                true,
                first.equals(second),
                sameDimension,
                crossDimensionAllowed(),
                sameDimension && withinDistance(first.pos(), second.pos()),
                data.count(first),
                data.count(second),
                ServerConfig.get(ServerConfig.MAX_MANUAL_CONNECTIONS),
                data.isLinked(first, second),
                wouldMerge,
                ServerConfig.get(ServerConfig.ALLOW_LINK_MERGING));
        if (!result.success()) {
            return result;
        }
        data.add(first, second);
        manager.markDirty(first);
        manager.markDirty(second);
        manager.update();
        if (sameDimension) {
            showLinkEffect(secondLevel, first.pos(), second.pos());
        } else {
            showLinkEffect(firstLevel, first.pos(), first.pos());
            showLinkEffect(secondLevel, second.pos(), second.pos());
        }
        return result;
    }

    /** Removes every manual link of the generator. Returns how many were removed. */
    public static int unlinkAll(MinecraftServer server, GlobalPos pos) {
        Set<GlobalPos> partners = ManualLinkData.get(server).removeAll(pos);
        GeneratorNetworkManager manager = GeneratorNetworkManager.get(server);
        manager.markDirty(pos);
        for (GlobalPos partner : partners) {
            manager.markDirty(partner);
        }
        manager.update();
        return partners.size();
    }

    /** Called when a generator block is permanently removed (broken, replaced), not on chunk unload. */
    public static void onGeneratorDestroyed(ServerLevel level, BlockPos pos) {
        unlinkAll(level.getServer(), GlobalPos.of(level.dimension(), pos));
    }

    /**
     * Drops links whose partner position is loaded but no longer holds a generator (e.g. removed by a world
     * editor without block updates). Partners in unloaded chunks are left untouched.
     */
    public static void pruneStaleLinks(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        ManualLinkData data = ManualLinkData.get(server);
        GlobalPos self = GlobalPos.of(level.dimension(), pos);
        List<GlobalPos> stale = new ArrayList<>();
        for (GlobalPos partner : data.linksOf(self)) {
            ServerLevel partnerLevel = server.getLevel(partner.dimension());
            if (partnerLevel == null) {
                continue;
            }
            if (partnerLevel.isLoaded(partner.pos()) && !partnerLevel.getBlockState(partner.pos()).is(ModBlocks.MYCELIAL_GENERATOR.get())) {
                stale.add(partner);
            }
        }
        if (!stale.isEmpty()) {
            GeneratorNetworkManager manager = GeneratorNetworkManager.get(server);
            for (GlobalPos partner : stale) {
                data.remove(self, partner);
                manager.markDirty(partner);
            }
            manager.markDirty(self);
        }
    }

    /** Green fungal particles along the new link, plus a chime. Visual only; sent to nearby players. */
    private static void showLinkEffect(ServerLevel level, BlockPos from, BlockPos to) {
        level.playSound(null, to, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F);
        if (!ServerConfig.get(ServerConfig.SHOW_LINK_PARTICLES)) {
            return;
        }
        Vec3 a = Vec3.atCenterOf(from);
        Vec3 b = Vec3.atCenterOf(to);
        double length = a.distanceTo(b);
        int points = (int) Math.min(MAX_PARTICLE_POINTS, Math.max(1, Math.ceil(length * 2)));
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            double wave = Math.sin(t * Math.PI) * Math.min(1.5D, length * 0.08D);
            Vec3 p = a.lerp(b, t).add(0, wave, 0);
            level.sendParticles(FUNGAL_GREEN, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
        for (BlockPos end : new BlockPos[]{from, to}) {
            Vec3 c = Vec3.atCenterOf(end);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 0.6, c.z, 12, 0.4, 0.3, 0.4, 0.0);
        }
    }
}
