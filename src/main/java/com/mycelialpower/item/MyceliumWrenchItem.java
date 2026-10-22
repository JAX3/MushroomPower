package com.mycelialpower.item;

import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.network.LinkRules;
import com.mycelialpower.network.ManualLinkData;
import com.mycelialpower.network.ManualLinking;
import com.mycelialpower.registry.ModBlocks;
import com.mycelialpower.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Links Mycelial Generators that are not touching.
 * <ul>
 *     <li>Right-click a generator: select it (first end).</li>
 *     <li>Right-click another generator: link the two, merging their networks.</li>
 *     <li>Shift + right-click a generator: remove all of its manual links.</li>
 *     <li>Shift + right-click air: clear the selection.</li>
 * </ul>
 * Everything is decided on the server; the selection is stored on the item stack and synced to the client
 * for the tooltip and the selection highlight.
 */
public class MyceliumWrenchItem extends Item {
    public MyceliumWrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!level.getBlockState(pos).is(ModBlocks.MYCELIAL_GENERATOR.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide || player == null || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        GlobalPos clicked = GlobalPos.of(level.dimension(), pos);

        if (!ServerConfig.get(ServerConfig.WRENCH_ENABLED)) {
            fail(player, Component.translatable(LinkRules.Result.DISABLED.translationKey()));
            return InteractionResult.CONSUME;
        }

        if (player.isSecondaryUseActive()) {
            int removed = ManualLinking.unlinkAll(serverLevel.getServer(), clicked);
            if (clicked.equals(stack.get(ModDataComponents.SELECTED_GENERATOR.get()))) {
                stack.remove(ModDataComponents.SELECTED_GENERATOR.get());
            }
            if (removed > 0) {
                ok(player, Component.translatable("message.mycelialpower.unlinked", removed));
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 0.7F);
            } else {
                fail(player, Component.translatable("message.mycelialpower.no_links"));
            }
            return InteractionResult.CONSUME;
        }

        GlobalPos selected = stack.get(ModDataComponents.SELECTED_GENERATOR.get());
        if (selected == null) {
            stack.set(ModDataComponents.SELECTED_GENERATOR.get(), clicked);
            ok(player, Component.translatable("message.mycelialpower.selected", formatPos(clicked),
                    ManualLinkData.get(serverLevel.getServer()).count(clicked),
                    ServerConfig.get(ServerConfig.MAX_MANUAL_CONNECTIONS)));
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.8F, 1.2F);
            return InteractionResult.CONSUME;
        }

        LinkRules.Result result = ManualLinking.tryLink(serverLevel.getServer(), selected, clicked);
        if (result.success()) {
            stack.remove(ModDataComponents.SELECTED_GENERATOR.get());
            ok(player, Component.translatable(result.translationKey(), formatPos(selected), formatPos(clicked)));
            consumeUse(stack, player, context.getHand());
        } else {
            if (result == LinkRules.Result.SAME_GENERATOR) {
                // Clicking the selected generator again simply keeps it selected.
                fail(player, Component.translatable(result.translationKey()));
            } else {
                fail(player, Component.translatable(result.translationKey(),
                        ServerConfig.get(ServerConfig.MAX_LINK_DISTANCE),
                        ServerConfig.get(ServerConfig.MAX_MANUAL_CONNECTIONS)));
            }
            level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5F, 1.4F);
            if (result == LinkRules.Result.NOT_LOADED || result == LinkRules.Result.NOT_A_GENERATOR) {
                stack.remove(ModDataComponents.SELECTED_GENERATOR.get());
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && stack.has(ModDataComponents.SELECTED_GENERATOR.get())) {
            if (!level.isClientSide) {
                stack.remove(ModDataComponents.SELECTED_GENERATOR.get());
                ok(player, Component.translatable("message.mycelialpower.selection_cleared"));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private static void consumeUse(ItemStack stack, Player player, InteractionHand hand) {
        int durability = ServerConfig.get(ServerConfig.WRENCH_DURABILITY);
        if (durability <= 0 || player.getAbilities().instabuild) {
            return;
        }
        int used = stack.getOrDefault(ModDataComponents.WRENCH_USES.get(), 0) + 1;
        if (LinkRules.remainingUses(durability, used) <= 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
            stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.mycelialpower.wrench_broke").withStyle(ChatFormatting.RED), true);
        } else {
            stack.set(ModDataComponents.WRENCH_USES.get(), used);
        }
    }

    private static void ok(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GREEN), true);
    }

    private static void fail(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
    }

    public static Component formatPos(GlobalPos pos) {
        return Component.translatable("message.mycelialpower.position", pos.pos().getX(), pos.pos().getY(), pos.pos().getZ(),
                pos.dimension().location().toString());
    }

    // ------------------------------------------------------------------ appearance

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.SELECTED_GENERATOR.get());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return ServerConfig.get(ServerConfig.WRENCH_DURABILITY) > 0 && stack.getOrDefault(ModDataComponents.WRENCH_USES.get(), 0) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int durability = Math.max(1, ServerConfig.get(ServerConfig.WRENCH_DURABILITY));
        int remaining = LinkRules.remainingUses(durability, stack.getOrDefault(ModDataComponents.WRENCH_USES.get(), 0));
        return Math.round(13.0F * remaining / durability);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int durability = Math.max(1, ServerConfig.get(ServerConfig.WRENCH_DURABILITY));
        int remaining = LinkRules.remainingUses(durability, stack.getOrDefault(ModDataComponents.WRENCH_USES.get(), 0));
        float fraction = remaining / (float) durability;
        return Mth.hsvToRgb(0.75F + 0.1F * (1 - fraction), 0.7F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mycelialpower.mycelium_wrench.desc").withStyle(ChatFormatting.GRAY));
        GlobalPos selected = stack.get(ModDataComponents.SELECTED_GENERATOR.get());
        if (selected != null) {
            tooltip.add(Component.translatable("item.mycelialpower.mycelium_wrench.selected", formatPos(selected))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("item.mycelialpower.mycelium_wrench.status.waiting").withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("item.mycelialpower.mycelium_wrench.status.none").withStyle(ChatFormatting.DARK_GRAY));
        }
        int durability = ServerConfig.get(ServerConfig.WRENCH_DURABILITY);
        if (durability > 0) {
            int remaining = LinkRules.remainingUses(durability, stack.getOrDefault(ModDataComponents.WRENCH_USES.get(), 0));
            tooltip.add(Component.translatable("item.mycelialpower.mycelium_wrench.uses", remaining, durability).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("item.mycelialpower.mycelium_wrench.help.select").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("item.mycelialpower.mycelium_wrench.help.link").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("item.mycelialpower.mycelium_wrench.help.unlink").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("item.mycelialpower.mycelium_wrench.help.limits",
                ServerConfig.get(ServerConfig.MAX_LINK_DISTANCE), ServerConfig.get(ServerConfig.MAX_MANUAL_CONNECTIONS))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.mycelialpower.mycelium_wrench.flavor").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
