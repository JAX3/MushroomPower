package com.mycelialpower.menu;

import com.mycelialpower.blockentity.MycelialGeneratorBlockEntity;
import com.mycelialpower.capability.GeneratorItemHandler;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.fuel.FuelRegistry;
import com.mycelialpower.net.GeneratorSyncPayload;
import com.mycelialpower.registry.ModBlocks;
import com.mycelialpower.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Generator container. Slots 0-2 are the machine (fuel, container in, container out), followed by the
 * 27 main inventory slots and 9 hotbar slots of the player. Display values arrive from the server via
 * {@link GeneratorSyncPayload}.
 */
public class MycelialGeneratorMenu extends AbstractContainerMenu {
    public static final int MACHINE_SLOTS = GeneratorItemHandler.SLOTS;
    public static final int PLAYER_INVENTORY_START = MACHINE_SLOTS;
    public static final int PLAYER_INVENTORY_SLOTS = 36;

    public static final int FUEL_X = 52;
    public static final int FUEL_Y = 19;
    public static final int CONTAINER_IN_X = 52;
    public static final int CONTAINER_IN_Y = 57;
    public static final int CONTAINER_OUT_X = 52;
    public static final int CONTAINER_OUT_Y = 89;
    public static final int PLAYER_INV_X = 48;
    public static final int PLAYER_INV_Y = 150;

    @Nullable
    private final MycelialGeneratorBlockEntity generator;
    private final ContainerLevelAccess access;
    private final Player player;
    private GeneratorStatus status = GeneratorStatus.EMPTY;
    @Nullable
    private GeneratorStatus lastSent;
    private int syncTimer;

    /** Server-side constructor. */
    public MycelialGeneratorMenu(int containerId, Inventory playerInventory, MycelialGeneratorBlockEntity generator) {
        this(containerId, playerInventory, generator, generator.getInventory(),
                ContainerLevelAccess.create(generator.getLevel(), generator.getBlockPos()));
    }

    /** Client-side factory, reading the block position written by the block when opening the menu. */
    public static MycelialGeneratorMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof MycelialGeneratorBlockEntity generator) {
            return new MycelialGeneratorMenu(containerId, playerInventory, generator, generator.getInventory(), ContainerLevelAccess.NULL);
        }
        return new MycelialGeneratorMenu(containerId, playerInventory, null, new ItemStackHandler(MACHINE_SLOTS), ContainerLevelAccess.NULL);
    }

    private MycelialGeneratorMenu(int containerId, Inventory playerInventory, @Nullable MycelialGeneratorBlockEntity generator,
                                  IItemHandler machineInventory, ContainerLevelAccess access) {
        super(ModMenus.MYCELIAL_GENERATOR.get(), containerId);
        this.generator = generator;
        this.access = access;
        this.player = playerInventory.player;

        addSlot(new SlotItemHandler(machineInventory, GeneratorItemHandler.FUEL_SLOT, FUEL_X, FUEL_Y));
        addSlot(new SlotItemHandler(machineInventory, GeneratorItemHandler.CONTAINER_IN_SLOT, CONTAINER_IN_X, CONTAINER_IN_Y));
        addSlot(new SlotItemHandler(machineInventory, GeneratorItemHandler.CONTAINER_OUT_SLOT, CONTAINER_OUT_X, CONTAINER_OUT_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
    }

    public GeneratorStatus getStatus() {
        return status;
    }

    /** Called on the client when a sync packet arrives. */
    public void setStatus(GeneratorStatus status) {
        this.status = status;
    }

    @Nullable
    public MycelialGeneratorBlockEntity getGenerator() {
        return generator;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (generator == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (lastSent != null && ++syncTimer < ServerConfig.get(ServerConfig.GUI_SYNC_INTERVAL)) {
            return;
        }
        syncTimer = 0;
        GeneratorStatus current = generator.createStatus();
        if (!current.equals(lastSent)) {
            lastSent = current;
            status = current;
            PacketDistributor.sendToPlayer(serverPlayer, new GeneratorSyncPayload(containerId, current));
        }
    }

    /**
     * Moves fuel matching {@code items} from the player's inventory into the fuel slot (JEI recipe transfer).
     * Moves one item, or as many as fit when {@code maxTransfer} is set.
     */
    public void transferFuelFromInventory(java.util.Set<net.minecraft.world.item.Item> items, boolean maxTransfer) {
        Slot fuelSlot = slots.get(GeneratorItemHandler.FUEL_SLOT);
        for (int i = PLAYER_INVENTORY_START; i < PLAYER_INVENTORY_START + PLAYER_INVENTORY_SLOTS; i++) {
            Slot source = slots.get(i);
            ItemStack stack = source.getItem();
            if (stack.isEmpty() || !items.contains(stack.getItem()) || !FuelRegistry.isFuel(stack)) {
                continue;
            }
            ItemStack inSlot = fuelSlot.getItem();
            if (!inSlot.isEmpty() && !ItemStack.isSameItemSameComponents(inSlot, stack)) {
                return;
            }
            int space = fuelSlot.getMaxStackSize(stack) - inSlot.getCount();
            if (space <= 0) {
                return;
            }
            int amount = Math.min(space, maxTransfer ? stack.getCount() : 1);
            ItemStack moved = source.remove(amount);
            if (moved.isEmpty()) {
                continue;
            }
            if (inSlot.isEmpty()) {
                fuelSlot.set(moved);
            } else {
                inSlot.grow(moved.getCount());
                fuelSlot.set(inSlot);
            }
            if (!maxTransfer) {
                return;
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (generator != null && generator.isRemoved()) {
            return false;
        }
        return stillValid(access, player, ModBlocks.MYCELIAL_GENERATOR.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerEnd = PLAYER_INVENTORY_START + PLAYER_INVENTORY_SLOTS;

        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (FuelRegistry.isFuel(stack)) {
            if (!moveItemStackTo(stack, GeneratorItemHandler.FUEL_SLOT, GeneratorItemHandler.FUEL_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getCapability(Capabilities.FluidHandler.ITEM) != null) {
            if (!moveItemStackTo(stack, GeneratorItemHandler.CONTAINER_IN_SLOT, GeneratorItemHandler.CONTAINER_IN_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_INVENTORY_START + 27) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START + 27, playerEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_START + 27, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
