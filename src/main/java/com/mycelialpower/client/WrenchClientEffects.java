package com.mycelialpower.client;

import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.item.MyceliumWrenchItem;
import com.mycelialpower.registry.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * Highlights the generator selected by the Mycelium Wrench the player is holding, with purple particles
 * tracing the block's edges. Client-only and purely visual; the selection itself is synced as an item
 * data component.
 */
public final class WrenchClientEffects {
    private static final DustParticleOptions SELECTION = new DustParticleOptions(new Vector3f(0.75F, 0.4F, 1.0F), 0.7F);
    private static final double MAX_DISTANCE_SQR = 64.0D * 64.0D;
    private static int ticks;

    private WrenchClientEffects() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused() || ++ticks % 3 != 0) {
            return;
        }
        if (!ServerConfig.get(ServerConfig.SHOW_LINK_PARTICLES)) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            GlobalPos selected = stack.get(ModDataComponents.SELECTED_GENERATOR.get());
            if (!(stack.getItem() instanceof MyceliumWrenchItem) || selected == null
                    || !selected.dimension().equals(minecraft.level.dimension())) {
                continue;
            }
            BlockPos pos = selected.pos();
            if (minecraft.player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) {
                return;
            }
            RandomSource random = minecraft.level.random;
            for (int i = 0; i < 6; i++) {
                // A random point on one of the cube's 12 edges: two coordinates on a face boundary, one free.
                double[] c = {random.nextInt(2), random.nextInt(2), random.nextInt(2)};
                c[random.nextInt(3)] = random.nextDouble();
                double inflate = 0.03D;
                minecraft.level.addParticle(SELECTION,
                        pos.getX() - inflate + c[0] * (1 + 2 * inflate),
                        pos.getY() - inflate + c[1] * (1 + 2 * inflate),
                        pos.getZ() - inflate + c[2] * (1 + 2 * inflate),
                        0.0D, 0.0D, 0.0D);
            }
            return;
        }
    }
}
