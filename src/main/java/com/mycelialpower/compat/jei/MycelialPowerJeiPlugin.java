package com.mycelialpower.compat.jei;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.client.screen.MycelialGeneratorScreen;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.registry.ModItems;
import com.mycelialpower.util.NumberFormatting;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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
        registration.addRecipeClickArea(MycelialGeneratorScreen.class, MycelialGeneratorScreen.BURN_X, MycelialGeneratorScreen.BURN_Y,
                MycelialGeneratorScreen.BURN_W, MycelialGeneratorScreen.BURN_H, MycelialGeneratorCategory.TYPE);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new GeneratorRecipeTransferHandler(registration.getTransferHelper()),
                MycelialGeneratorCategory.TYPE);
    }
}
