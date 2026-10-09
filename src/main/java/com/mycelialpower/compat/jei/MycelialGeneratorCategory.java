package com.mycelialpower.compat.jei;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.config.EnergyRounding;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.network.NetworkMath;
import com.mycelialpower.registry.ModItems;
import com.mycelialpower.util.NumberFormatting;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;

/** JEI category listing every mushroom fuel with its generation, duration and water use. */
public class MycelialGeneratorCategory implements IRecipeCategory<MycelialFuelRecipe> {
    public static final RecipeType<MycelialFuelRecipe> TYPE = RecipeType.create(MycelialPower.MOD_ID, "mycelial_generator", MycelialFuelRecipe.class);
    public static final ResourceLocation TEXTURE = MycelialPower.id("textures/gui/jei_mycelial_generator.png");

    public static final int WIDTH = 168;
    public static final int HEIGHT = 74;
    private static final int TEXT_X = 30;
    private static final int ENERGY_ROW_Y = 15;

    private final IDrawableStatic background;
    private final IDrawable icon;
    private final Component title;

    public MycelialGeneratorCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(TEXTURE, 0, 0, WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.MYCELIAL_GENERATOR.get()));
        this.title = Component.translatable("jei.mycelialpower.category.mycelial_generator");
    }

    @Override
    public RecipeType<MycelialFuelRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    /** Present in newer JEI 19.x builds; harmless where it does not exist. */
    public int getWidth() {
        return WIDTH;
    }

    public int getHeight() {
        return HEIGHT;
    }

    /**
     * Older JEI 19.x builds require a background, newer ones deprecate it in favour of getWidth/getHeight
     * but still draw it. Providing both keeps the category working on the whole 19.x line.
     */
    @SuppressWarnings({"deprecation", "removal"})
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MycelialFuelRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 6, 6)
                .addItemStacks(recipe.inputs());

        IRecipeSlotBuilder water = builder.addSlot(RecipeIngredientRole.INPUT, 7, 27);
        long amount = Math.max(1, recipe.waterPerItem());
        for (Fluid fluid : recipe.fluids()) {
            water.addFluidStack(fluid, amount);
        }
        water.setFluidRenderer(Math.max(amount, ServerConfig.get(ServerConfig.TANK_CAPACITY)), false, 14, 40);
    }

    @Override
    public void draw(MycelialFuelRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        var def = recipe.definition();
        int y = 4;
        graphics.drawString(font, Component.translatable("jei.mycelialpower.burn_time",
                NumberFormatting.ticksToTime(def.burnTicks()), NumberFormatting.grouped(def.burnTicks())), TEXT_X, y, 0xFFE6DBFF, false);
        y += 11;
        graphics.drawString(font, Component.translatable("jei.mycelialpower.generation",
                NumberFormatting.decimal(recipe.fePerTick())), TEXT_X, y, 0xFFC77DFF, false);
        y += 11;
        graphics.drawString(font, Component.translatable("jei.mycelialpower.total_energy",
                NumberFormatting.compact(recipe.totalEnergy())), TEXT_X, y, 0xFFC77DFF, false);
        y += 11;
        graphics.drawString(font, Component.translatable("jei.mycelialpower.water",
                NumberFormatting.grouped(recipe.waterPerItem())), TEXT_X, y, 0xFF7FB6FF, false);
        y += 11;
        graphics.drawString(font, Component.translatable("jei.mycelialpower.multipliers",
                NumberFormatting.decimal(def.energyMultiplier()), NumberFormatting.decimal(def.waterMultiplier())), TEXT_X, y, 0xFFB0B0BE, false);
        y += 11;
        graphics.drawString(font, Component.translatable("jei.mycelialpower.bonuses",
                NumberFormatting.decimal(def.myceliumBonus()), NumberFormatting.decimal(def.scalingBonus())), TEXT_X, y, 0xFF8FD27A, false);
    }

    @SuppressWarnings({"deprecation", "removal"})
    public List<Component> getTooltipStrings(MycelialFuelRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX < TEXT_X || mouseX > WIDTH) {
            return List.of();
        }
        int row = (int) ((mouseY - 4) / 11);
        return switch (row) {
            case 0 -> List.of(Component.translatable("jei.mycelialpower.tooltip.burn").withStyle(ChatFormatting.GRAY));
            case 1, 2 -> scalingTooltip(recipe);
            case 3 -> List.of(Component.translatable("jei.mycelialpower.tooltip.water",
                    NumberFormatting.decimal(recipe.waterPerInterval()), NumberFormatting.grouped(recipe.waterInterval()))
                    .withStyle(ChatFormatting.GRAY));
            case 4 -> List.of(Component.translatable("jei.mycelialpower.tooltip.multipliers").withStyle(ChatFormatting.GRAY));
            case 5 -> List.of(Component.translatable("jei.mycelialpower.tooltip.bonuses").withStyle(ChatFormatting.GRAY),
                    Component.translatable("jei.mycelialpower.tooltip.mycelium_info").withStyle(ChatFormatting.DARK_GRAY));
            default -> List.of();
        };
    }

    /** Explains exponential network scaling with values computed by the same code as the generator. */
    private static List<Component> scalingTooltip(MycelialFuelRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        double base = ServerConfig.get(ServerConfig.BASE_GENERATION);
        boolean scaling = ServerConfig.get(ServerConfig.SCALING_ENABLED);
        double multiplier = ServerConfig.get(ServerConfig.SCALING_MULTIPLIER) + recipe.definition().scalingBonus();
        double cap = ServerConfig.get(ServerConfig.MAX_NETWORK_OUTPUT);
        EnergyRounding rounding = ServerConfig.get(ServerConfig.ROUNDING);
        lines.add(Component.translatable("jei.mycelialpower.tooltip.scaling.title").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!scaling) {
            lines.add(Component.translatable("jei.mycelialpower.tooltip.scaling.disabled").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.translatable("jei.mycelialpower.tooltip.scaling.formula").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("jei.mycelialpower.tooltip.scaling.values", NumberFormatting.decimal(base),
                NumberFormatting.decimal(multiplier)).withStyle(ChatFormatting.GRAY));
        int maxExample = Math.min(ServerConfig.get(ServerConfig.JEI_EXAMPLE_NETWORK_SIZE), ServerConfig.get(ServerConfig.MAX_NETWORK_SIZE));
        for (int n = 1; n <= maxExample; n++) {
            double perGenerator = NetworkMath.generatorOutput(base, recipe.definition().energyMultiplier(), multiplier, n, cap);
            double total = NetworkMath.safeMultiply(perGenerator, n);
            lines.add(Component.translatable("jei.mycelialpower.tooltip.scaling.row", n,
                    NumberFormatting.grouped(rounding.roundForDisplay(total))).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (ServerConfig.get(ServerConfig.COUNT_INACTIVE)) {
            lines.add(Component.translatable("jei.mycelialpower.tooltip.scaling.inactive_counted").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("jei.mycelialpower.tooltip.scaling.active_only").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }
}
