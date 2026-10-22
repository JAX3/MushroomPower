package com.mycelialpower.compat.jei;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.client.screen.MycelialGeneratorScreen;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.network.ManualLinking;
import com.mycelialpower.registry.ModItems;
import com.mycelialpower.util.NumberFormatting;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * JEI entry point. Loaded only by JEI, so the mod has no hard dependency on it. All displayed values
 * come from the synced server config through {@link MycelialFuelRecipeFactory}.
 */
@JeiPlugin
public class MycelialPowerJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = MycelialPower.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new MycelialGeneratorCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(MycelialGeneratorCategory.TYPE, MycelialFuelRecipeFactory.create());

        ItemStack generator = new ItemStack(ModItems.MYCELIAL_GENERATOR.get());
        registration.addIngredientInfo(generator, VanillaTypes.ITEM_STACK,
                Component.translatable("jei.mycelialpower.info.generator",
                        NumberFormatting.decimal(ServerConfig.get(ServerConfig.BASE_GENERATION)),
                        NumberFormatting.decimal(ServerConfig.get(ServerConfig.SCALING_MULTIPLIER)),
                        ServerConfig.get(ServerConfig.MAX_NETWORK_SIZE)),
                Component.translatable("jei.mycelialpower.info.water",
                        ServerConfig.get(ServerConfig.WATER_PER_INTERVAL),
                        NumberFormatting.ticksToTime(ServerConfig.get(ServerConfig.WATER_INTERVAL)),
                        NumberFormatting.grouped(ServerConfig.get(ServerConfig.TANK_CAPACITY))),
                myceliumInfo());
        registerWrenchInfo(registration);
    }

    /** Wrench info page: how to link/unlink, how links affect scaling, and the server's limits. */
    private static void registerWrenchInfo(IRecipeRegistration registration) {
        ItemStack wrench = new ItemStack(ModItems.MYCELIUM_WRENCH.get());
        if (!ServerConfig.get(ServerConfig.WRENCH_ENABLED)) {
            registration.addIngredientInfo(wrench, VanillaTypes.ITEM_STACK, Component.translatable("jei.mycelialpower.info.wrench_disabled"));
            return;
        }
        int durability = ServerConfig.get(ServerConfig.WRENCH_DURABILITY);
        boolean crossDimension = ManualLinking.crossDimensionAllowed();
        registration.addIngredientInfo(wrench, VanillaTypes.ITEM_STACK,
                Component.translatable("jei.mycelialpower.info.wrench_usage"),
                Component.translatable("jei.mycelialpower.info.wrench_scaling",
                        NumberFormatting.decimal(ServerConfig.get(ServerConfig.SCALING_MULTIPLIER))),
                Component.translatable("jei.mycelialpower.info.wrench_limits",
                        ServerConfig.get(ServerConfig.MAX_LINK_DISTANCE),
                        ServerConfig.get(ServerConfig.MAX_MANUAL_CONNECTIONS),
                        Component.translatable(crossDimension ? "jei.mycelialpower.info.wrench_cross_yes" : "jei.mycelialpower.info.wrench_cross_no"),
                        durability > 0 ? Component.translatable("jei.mycelialpower.info.wrench_uses", durability)
                                : Component.translatable("jei.mycelialpower.info.wrench_unlimited"),
                        Component.translatable(ServerConfig.get(ServerConfig.ALLOW_LINK_MERGING)
                                ? "jei.mycelialpower.info.wrench_merge_yes" : "jei.mycelialpower.info.wrench_merge_no")),
                Component.translatable("jei.mycelialpower.info.wrench_resources"));
    }

    private static Component myceliumInfo() {
        if (!ServerConfig.get(ServerConfig.MYCELIUM_ENABLED)) {
            return Component.translatable("jei.mycelialpower.info.mycelium_disabled");
        }
        double initial = ServerConfig.initialChance();
        double increase = ServerConfig.get(ServerConfig.CHANCE_INCREASE);
        double max = ServerConfig.maxChance();
        int attemptsToMax = increase > 0 ? (int) Math.ceil(Math.max(0.0D, max - initial) / increase) + 1 : -1;
        return Component.translatable("jei.mycelialpower.info.mycelium",
                NumberFormatting.ticksToTime(ServerConfig.get(ServerConfig.ATTEMPT_INTERVAL)),
                ServerConfig.get(ServerConfig.SEARCH_RADIUS),
                NumberFormatting.percent(initial),
                NumberFormatting.percent(increase),
                NumberFormatting.percent(max),
                attemptsToMax > 0 ? Integer.toString(attemptsToMax) : "-",
                Component.translatable(ServerConfig.get(ServerConfig.RESET_ON_SUCCESS)
                        ? "jei.mycelialpower.info.mycelium_reset" : "jei.mycelialpower.info.mycelium_keep"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.MYCELIAL_GENERATOR.get()), MycelialGeneratorCategory.TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // A plain addRecipeClickArea would draw JEI's own "Show Recipes" tooltip on top of the screen's burn
        // tooltip. This click area has no tooltip; the screen adds a "click to show fuels" hint instead.
        registration.addGuiContainerHandler(MycelialGeneratorScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(MycelialGeneratorScreen screen, double guiMouseX, double guiMouseY) {
                return List.of(new IGuiClickableArea() {
                    @Override
                    public Rect2i getArea() {
                        return new Rect2i(MycelialGeneratorScreen.BURN_X, MycelialGeneratorScreen.BURN_Y,
                                MycelialGeneratorScreen.BURN_W, MycelialGeneratorScreen.BURN_H);
                    }

                    // Not annotated: only some JEI 19.x builds declare it. Where present it stops the tooltip outright.
                    public boolean isTooltipEnabled() {
                        return false;
                    }

                    @Override
                    public List<Component> getTooltipStrings() {
                        return List.of();
                    }

                    @Override
                    public void onClick(IFocusFactory focusFactory, IRecipesGui recipesGui) {
                        recipesGui.showTypes(List.of(MycelialGeneratorCategory.TYPE));
                    }
                });
            }
        });
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new GeneratorRecipeTransferHandler(registration.getTransferHelper()),
                MycelialGeneratorCategory.TYPE);
    }
}
