package com.mycelialpower.compat.jei;

import com.mycelialpower.menu.MycelialGeneratorMenu;
import com.mycelialpower.net.FuelTransferPayload;
import com.mycelialpower.registry.ModMenus;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI "+" button support: moves the shown mushroom from the player's inventory into the generator fuel
 * slot (one item, or a full stack with shift). The move itself happens on the server.
 */
public class GeneratorRecipeTransferHandler implements IRecipeTransferHandler<MycelialGeneratorMenu, MycelialFuelRecipe> {
    private final IRecipeTransferHandlerHelper helper;

    public GeneratorRecipeTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends MycelialGeneratorMenu> getContainerClass() {
        return MycelialGeneratorMenu.class;
    }

    @Override
    public Optional<MenuType<MycelialGeneratorMenu>> getMenuType() {
        return Optional.of(ModMenus.MYCELIAL_GENERATOR.get());
    }

    @Override
    public RecipeType<MycelialFuelRecipe> getRecipeType() {
        return MycelialGeneratorCategory.TYPE;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(MycelialGeneratorMenu container, MycelialFuelRecipe recipe, IRecipeSlotsView recipeSlots,
                                               Player player, boolean maxTransfer, boolean doTransfer) {
        boolean found = false;
        for (ItemStack candidate : recipe.inputs()) {
            if (player.getInventory().contains(candidate)) {
                found = true;
                break;
            }
        }
        if (!found) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.mycelialpower.transfer.missing"));
        }
        if (doTransfer) {
            List<ResourceLocation> ids = new ArrayList<>();
            for (ItemStack stack : recipe.inputs()) {
                if (ids.size() >= FuelTransferPayload.MAX_ITEMS) {
                    break;
                }
                ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
            PacketDistributor.sendToServer(new FuelTransferPayload(container.containerId, ids, maxTransfer));
        }
        return null;
    }
}
