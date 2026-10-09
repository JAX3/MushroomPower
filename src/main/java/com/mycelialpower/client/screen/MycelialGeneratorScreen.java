package com.mycelialpower.client.screen;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.blockentity.GeneratorState;
import com.mycelialpower.menu.GeneratorStatus;
import com.mycelialpower.menu.MycelialGeneratorMenu;
import com.mycelialpower.network.NetworkStatus;
import com.mycelialpower.util.NumberFormatting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Generator GUI. Everything shown comes from the server-sent {@link GeneratorStatus}; the screen only
 * formats it.
 */
public class MycelialGeneratorScreen extends AbstractContainerScreen<MycelialGeneratorMenu> {
    public static final ResourceLocation TEXTURE = MycelialPower.id("textures/gui/mycelial_generator.png");

    // Layout (relative to leftPos/topPos). Must match textures/gui/mycelial_generator.png.
    public static final int ENERGY_X = 9, ENERGY_Y = 19, BAR_W = 14, BAR_H = 70;
    public static final int TANK_X = 29, TANK_Y = 19;
    public static final int BURN_X = 52, BURN_Y = 38, BURN_W = 16, BURN_H = 14;
    public static final int LAMP_X = 8, LAMP_Y = 112;
    public static final int PANEL_X = 78, PANEL_Y = 19, PANEL_W = 170, ROW_H = 11;
    private static final float TEXT_SCALE = 0.75F;
    private static final boolean JEI_LOADED = ModList.get().isLoaded("jei");

    private static final int COLOR_HEADER = 0xFFB98CFF;
    private static final int COLOR_TEXT = 0xFFDCDCE6;
    private static final int COLOR_DIM = 0xFF9A9AAA;

    private final List<HoverArea> hoverAreas = new ArrayList<>();

    public MycelialGeneratorScreen(MycelialGeneratorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 232;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = MycelialGeneratorMenu.PLAYER_INV_X;
        inventoryLabelY = MycelialGeneratorMenu.PLAYER_INV_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (menu.getCarried().isEmpty() && hoveredSlot == null) {
            for (HoverArea area : hoverAreas) {
                if (area.contains(mouseX - leftPos, mouseY - topPos)) {
                    graphics.renderComponentTooltip(font, area.lines(), mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        GeneratorStatus s = menu.getStatus();

        // Energy bar
        int energyHeight = scaled(s.energy(), s.energyCapacity(), BAR_H);
        if (energyHeight > 0) {
            graphics.fillGradient(x + ENERGY_X, y + ENERGY_Y + BAR_H - energyHeight, x + ENERGY_X + BAR_W, y + ENERGY_Y + BAR_H,
                    0xFFC77DFF, 0xFF5B2A9E);
        }

        // Water tank
        renderFluid(graphics, s, x + TANK_X, y + TANK_Y);

        // Burn progress (remaining fuel, draining downwards)
        int burnHeight = scaled(s.burnRemaining(), s.burnTotal(), BURN_H);
        if (burnHeight > 0) {
            graphics.fillGradient(x + BURN_X, y + BURN_Y + BURN_H - burnHeight, x + BURN_X + BURN_W, y + BURN_Y + BURN_H,
                    0xFFE8A35A, 0xFF8A3B1C);
        }

        // State lamp
        int lamp = switch (GeneratorState.byOrdinal(s.state())) {
            case RUNNING -> 0xFF59E36B;
            case ENERGY_FULL -> 0xFFE3D459;
            case NO_WATER -> 0xFF4E8CE8;
            case NO_FUEL -> 0xFFD94A4A;
        };
        graphics.fill(x + LAMP_X, y + LAMP_Y, x + LAMP_X + 8, y + LAMP_Y + 8, 0xFF111214);
        graphics.fill(x + LAMP_X + 1, y + LAMP_Y + 1, x + LAMP_X + 7, y + LAMP_Y + 7, lamp);

        // Mycelium attempt progress bar
        int barX = x + PANEL_X + 92;
        int barY = y + PANEL_Y + ROW_H * 8 + 1;
        int barW = PANEL_W - 96;
        graphics.fill(barX, barY, barX + barW, barY + 5, 0xFF111214);
        if (s.myceliumEnabled()) {
            int filled = scaled(s.attemptProgress(), s.attemptInterval(), barW - 2);
            if (filled > 0) {
                graphics.fillGradient(barX + 1, barY + 1, barX + 1 + filled, barY + 4, 0xFF9BE07A, 0xFF4E8F3A);
            }
        }
    }

    private void renderFluid(GuiGraphics graphics, GeneratorStatus s, int x, int y) {
        int height = scaled(s.fluidAmount(), s.tankCapacity(), BAR_H);
        if (height <= 0 || s.fluidId().isEmpty()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(s.fluidId());
        Fluid fluid = id == null ? Fluids.WATER : BuiltInRegistries.FLUID.get(id);
        FluidStack stack = new FluidStack(fluid == Fluids.EMPTY ? Fluids.WATER : fluid, Math.max(1, s.fluidAmount()));
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = extensions.getStillTexture(stack);
        if (stillTexture == null) {
            graphics.fill(x, y + BAR_H - height, x + BAR_W, y + BAR_H, 0xFF3F76E4);
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        int tint = extensions.getTintColor(stack);
        float r = ((tint >> 16) & 0xFF) / 255.0F;
        float g = ((tint >> 8) & 0xFF) / 255.0F;
        float b = (tint & 0xFF) / 255.0F;
        float a = ((tint >> 24) & 0xFF) / 255.0F;
        if (a <= 0) {
            a = 1.0F;
        }
        int top = y + BAR_H - height;
        graphics.enableScissor(x, top, x + BAR_W, y + BAR_H);
        for (int tileY = y + BAR_H - 16; tileY > top - 16; tileY -= 16) {
            graphics.blit(x, tileY, 0, 16, 16, sprite, r, g, b, a);
        }
        graphics.disableScissor();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE6DBFF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFC8C8D0, false);

        GeneratorStatus s = menu.getStatus();
        hoverAreas.clear();
        addBarTooltips(s);

        GeneratorState state = GeneratorState.byOrdinal(s.state());
        drawSmall(graphics, Component.translatable(state.translationKey()), LAMP_X + 11, LAMP_Y + 1, COLOR_TEXT);
        hoverAreas.add(new HoverArea(LAMP_X, LAMP_Y, 60, 9, List.of(
                Component.translatable(state.translationKey()).withStyle(ChatFormatting.WHITE),
                Component.translatable(state.translationKey() + ".desc").withStyle(ChatFormatting.GRAY))));

        // Fuel
        Component fuelValue;
        if (!s.fuelItem().isEmpty()) {
            fuelValue = Component.translatable("gui.mycelialpower.fuel.burning", fuelName(s.fuelItem()),
                    NumberFormatting.ticksToTime(s.burnRemaining()));
        } else {
            fuelValue = Component.translatable("gui.mycelialpower.fuel.none");
        }
        row(graphics, 0, "gui.mycelialpower.section.fuel", fuelValue, List.of(
                Component.translatable("gui.mycelialpower.section.fuel").withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.translatable("gui.mycelialpower.tooltip.fuel.remaining", NumberFormatting.grouped(s.burnRemaining()),
                        NumberFormatting.grouped(s.burnTotal())).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.mycelialpower.tooltip.fuel.hint").withStyle(ChatFormatting.DARK_GRAY)));

        // Water
        String perInterval = NumberFormatting.decimal(s.waterPerInterval());
        String interval = NumberFormatting.ticksToTime(s.waterInterval());
        row(graphics, 1, "gui.mycelialpower.section.water", Component.translatable("gui.mycelialpower.water.line",
                        NumberFormatting.grouped(s.fluidAmount()), NumberFormatting.grouped(s.tankCapacity()), perInterval, interval),
                List.of(Component.translatable("gui.mycelialpower.section.water").withStyle(ChatFormatting.AQUA),
                        Component.translatable("gui.mycelialpower.tooltip.water.amount", NumberFormatting.grouped(s.fluidAmount()),
                                NumberFormatting.grouped(s.tankCapacity())).withStyle(ChatFormatting.GRAY),
                        Component.translatable("gui.mycelialpower.tooltip.water.rate", perInterval, NumberFormatting.grouped(s.waterInterval()))
                                .withStyle(ChatFormatting.GRAY)));

        // Energy
        row(graphics, 2, "gui.mycelialpower.section.energy", Component.translatable("gui.mycelialpower.energy.line",
                        NumberFormatting.compact(s.energy()), NumberFormatting.compact(s.energyCapacity())),
                List.of(Component.translatable("gui.mycelialpower.section.energy").withStyle(ChatFormatting.LIGHT_PURPLE),
                        Component.translatable("gui.mycelialpower.tooltip.energy.stored", NumberFormatting.grouped(s.energy()),
                                NumberFormatting.grouped(s.energyCapacity())).withStyle(ChatFormatting.GRAY)));
        row(graphics, 3, null, Component.translatable("gui.mycelialpower.energy.rates",
                        NumberFormatting.compact(s.generation()), NumberFormatting.compact(s.extraction())),
                List.of(Component.translatable("gui.mycelialpower.tooltip.energy.generation", NumberFormatting.decimal(s.generation()))
                                .withStyle(ChatFormatting.GRAY),
                        Component.translatable("gui.mycelialpower.tooltip.energy.extraction", NumberFormatting.decimal(s.extraction()))
                                .withStyle(ChatFormatting.GRAY)));

        // Network
        List<Component> networkTooltip = List.of(
                Component.translatable("gui.mycelialpower.section.network").withStyle(ChatFormatting.GREEN),
                Component.translatable("gui.mycelialpower.tooltip.network.formula").withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.mycelialpower.tooltip.network.members", s.networkSize(), s.maxNetworkSize())
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.mycelialpower.tooltip.network.total", NumberFormatting.decimal(s.networkTotal()))
                        .withStyle(ChatFormatting.GRAY));
        row(graphics, 4, "gui.mycelialpower.section.network",
                Component.translatable("gui.mycelialpower.network.line", s.networkSize(), s.networkActive()), networkTooltip);
        row(graphics, 5, null, Component.translatable("gui.mycelialpower.network.multiplier",
                NumberFormatting.decimal(s.networkScale()), NumberFormatting.compact(Math.round(s.networkTotal()))), networkTooltip);
        NetworkStatus networkStatus = NetworkStatus.values()[Math.floorMod(s.networkStatus(), NetworkStatus.values().length)];
        row(graphics, 6, null, Component.translatable("gui.mycelialpower.network.status", Component.translatable(networkStatus.translationKey())),
                List.of(Component.translatable(networkStatus.translationKey()).withStyle(ChatFormatting.GREEN),
                        Component.translatable(networkStatus.translationKey() + ".desc").withStyle(ChatFormatting.GRAY)));

        // Mycelium
        List<Component> myceliumTooltip = List.of(
                Component.translatable("gui.mycelialpower.section.mycelium").withStyle(ChatFormatting.DARK_GREEN),
                Component.translatable("gui.mycelialpower.tooltip.mycelium.chance", NumberFormatting.percent(s.chance()))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.mycelialpower.tooltip.mycelium.increase", NumberFormatting.percent(s.chanceIncrease()))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.mycelialpower.tooltip.mycelium.next",
                        NumberFormatting.ticksToTime(Math.max(0, s.attemptInterval() - s.attemptProgress()))).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.mycelialpower.tooltip.mycelium.rule").withStyle(ChatFormatting.DARK_GRAY));
        if (s.myceliumEnabled()) {
            row(graphics, 7, "gui.mycelialpower.section.mycelium", Component.translatable("gui.mycelialpower.mycelium.line",
                    NumberFormatting.percent(s.chance()), NumberFormatting.percent(s.chanceIncrease())), myceliumTooltip);
            row(graphics, 8, null, Component.translatable("gui.mycelialpower.mycelium.next"), myceliumTooltip);
            if (s.trackConversions()) {
                row(graphics, 9, null, Component.translatable("gui.mycelialpower.mycelium.converted", NumberFormatting.grouped(s.converted())),
                        myceliumTooltip);
            }
        } else {
            row(graphics, 7, "gui.mycelialpower.section.mycelium", Component.translatable("gui.mycelialpower.mycelium.disabled"),
                    myceliumTooltip);
        }
    }

    private void addBarTooltips(GeneratorStatus s) {
        hoverAreas.add(new HoverArea(ENERGY_X, ENERGY_Y, BAR_W, BAR_H, List.of(
                Component.translatable("gui.mycelialpower.section.energy").withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.translatable("gui.mycelialpower.tooltip.energy.stored", NumberFormatting.grouped(s.energy()),
                        NumberFormatting.grouped(s.energyCapacity())).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.mycelialpower.tooltip.energy.generation", NumberFormatting.decimal(s.generation()))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.mycelialpower.tooltip.energy.extraction", NumberFormatting.decimal(s.extraction()))
                        .withStyle(ChatFormatting.GRAY))));
        Component fluidName = s.fluidId().isEmpty() ? Component.translatable("gui.mycelialpower.tank.empty") : fluidName(s.fluidId());
        hoverAreas.add(new HoverArea(TANK_X, TANK_Y, BAR_W, BAR_H, List.of(
                fluidName.copy().withStyle(ChatFormatting.AQUA),
                Component.translatable("gui.mycelialpower.tooltip.water.amount", NumberFormatting.grouped(s.fluidAmount()),
                        NumberFormatting.grouped(s.tankCapacity())).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.mycelialpower.tooltip.water.rate", NumberFormatting.decimal(s.waterPerInterval()),
                        NumberFormatting.grouped(s.waterInterval())).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.mycelialpower.tooltip.water.fill").withStyle(ChatFormatting.DARK_GRAY))));
        List<Component> burnTooltip = new ArrayList<>(List.of(
                s.fuelItem().isEmpty() ? Component.translatable("gui.mycelialpower.fuel.none")
                        : fuelName(s.fuelItem()).copy().withStyle(ChatFormatting.GOLD),
                Component.translatable("gui.mycelialpower.tooltip.fuel.remaining", NumberFormatting.grouped(s.burnRemaining()),
                        NumberFormatting.grouped(s.burnTotal())).withStyle(ChatFormatting.GRAY)));
        if (JEI_LOADED) {
            burnTooltip.add(Component.translatable("gui.mycelialpower.tooltip.fuel.show_recipes").withStyle(ChatFormatting.DARK_GRAY));
        }
        hoverAreas.add(new HoverArea(BURN_X, BURN_Y, BURN_W, BURN_H, burnTooltip));
    }

    private void row(GuiGraphics graphics, int index, String headerKey, Component value, List<Component> tooltip) {
        int y = PANEL_Y + index * ROW_H;
        MutableComponent line = Component.empty();
        if (headerKey != null) {
            line.append(Component.translatable(headerKey).withColor(COLOR_HEADER & 0xFFFFFF)).append(" ");
            line.append(value.copy().withColor(COLOR_TEXT & 0xFFFFFF));
        } else {
            line.append("  ").append(value.copy().withColor(COLOR_DIM & 0xFFFFFF));
        }
        drawSmall(graphics, line, PANEL_X, y, COLOR_TEXT);
        hoverAreas.add(new HoverArea(PANEL_X, y - 1, PANEL_W, ROW_H, tooltip));
    }

    private void drawSmall(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static Component fuelName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return Component.literal(itemId);
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item).getHoverName();
    }

    private static Component fluidName(String fluidId) {
        ResourceLocation id = ResourceLocation.tryParse(fluidId);
        if (id == null) {
            return Component.literal(fluidId);
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        return fluid.getFluidType().getDescription();
    }

    private static int scaled(long value, long max, int pixels) {
        if (value <= 0 || max <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.min(pixels, value * pixels / max));
    }

    private record HoverArea(int x, int y, int width, int height, List<Component> lines) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
