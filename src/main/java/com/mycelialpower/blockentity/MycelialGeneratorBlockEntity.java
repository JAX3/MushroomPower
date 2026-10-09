package com.mycelialpower.blockentity;

import com.mycelialpower.block.MycelialGeneratorBlock;
import com.mycelialpower.capability.AutomationItemHandler;
import com.mycelialpower.capability.GeneratorEnergyStorage;
import com.mycelialpower.capability.GeneratorFluidTank;
import com.mycelialpower.capability.GeneratorItemHandler;
import com.mycelialpower.capability.SidedFluidHandler;
import com.mycelialpower.config.EnergyRounding;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.energy.EnergyAccumulator;
import com.mycelialpower.fuel.FuelDefinition;
import com.mycelialpower.fuel.FuelRegistry;
import com.mycelialpower.menu.GeneratorStatus;
import com.mycelialpower.menu.MycelialGeneratorMenu;
import com.mycelialpower.mycelium.MyceliumChance;
import com.mycelialpower.mycelium.MyceliumSpreadManager;
import com.mycelialpower.network.GeneratorNetwork;
import com.mycelialpower.network.GeneratorNetworkManager;
import com.mycelialpower.network.NetworkMath;
import com.mycelialpower.network.NetworkStatus;
import com.mycelialpower.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-authoritative state and logic of one Mycelial Generator.
 * <p>
 * Each server tick the generator:
 * <ol>
 *     <li>looks up its (cached) network and refreshes the network statistics once per tick;</li>
 *     <li>starts a new mushroom when the previous one is exhausted and it can run;</li>
 *     <li>produces {@code base x fuel x (multiplier + fuelBonus) ^ (n - 1)} FE/t, consumes water on its interval
 *     and advances the mycelium attempt timer;</li>
 *     <li>pushes energy to neighbouring receivers.</li>
 * </ol>
 * Nothing here runs on the client.
 */
public class MycelialGeneratorBlockEntity extends BlockEntity implements MenuProvider {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final GeneratorItemHandler inventory = new GeneratorItemHandler(this::setChanged);
    private final AutomationItemHandler automationItems = new AutomationItemHandler(inventory);
    private final GeneratorFluidTank tank = new GeneratorFluidTank(this::setChanged);
    private final SidedFluidHandler externalFluid = new SidedFluidHandler(tank);
    private final GeneratorEnergyStorage energy = new GeneratorEnergyStorage(this::setChanged);
    private final EnergyAccumulator accumulator = new EnergyAccumulator();

    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IEnergyStorage, Direction>[] energyCaches = new BlockCapabilityCache[6];

    // Fuel (snapshot of the definition that was consumed, so config edits never alter an item mid-burn)
    private int burnRemaining;
    private int burnTotal;
    private String fuelItemId = "";
    private double fuelEnergyMultiplier = 1.0D;
    private double fuelWaterMultiplier = 1.0D;
    private double fuelMyceliumBonus = 1.0D;
    private double fuelScalingBonus;

    // Water
    private int waterTimer;
    private double waterRemainder;

    // Mycelium
    private double chance = -1.0D;
    private int attemptTimer;
    private int convertedThisSession;

    // Network
    private long networkId;

    // Runtime (not persisted except 'active')
    private boolean active;
    private GeneratorState state = GeneratorState.NO_FUEL;
    private double lastExactOutput;
    private long extractedWindow;
    private int windowTicks;
    private double extractionRate;
    private int outputTimer;
    private int containerTimer;
    private boolean registered;
    private int lastComparatorSignal = -1;

    public MycelialGeneratorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MYCELIAL_GENERATOR.get(), pos, blockState);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            GeneratorNetworkManager.get(serverLevel).add(this);
            registered = true;
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unregister();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        unregister();
    }

    private void unregister() {
        if (registered && level instanceof ServerLevel serverLevel) {
            GeneratorNetworkManager.get(serverLevel).remove(this);
        }
        registered = false;
    }

    /** Drops the inventory. Called once by the block when it is broken or replaced. */
    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, MycelialGeneratorBlockEntity generator) {
        if (level instanceof ServerLevel serverLevel) {
            generator.tickServer(serverLevel, blockState);
        }
    }

    private void tickServer(ServerLevel level, BlockState blockState) {
        GeneratorNetworkManager manager = GeneratorNetworkManager.get(level);
        if (!registered || !manager.isTracked(this)) {
            manager.add(this);
            registered = true;
        }
        GeneratorNetwork network = manager.getNetwork(this);
        long gameTime = level.getGameTime();
        network.refresh(gameTime);

        tank.syncCapacity();
        energy.clampToCapacity();
        if (chance < 0) {
            chance = ServerConfig.initialChance();
        }

        if (++containerTimer >= ServerConfig.get(ServerConfig.CONTAINER_INTERVAL)) {
            containerTimer = 0;
            processFluidContainer();
        }

        boolean wasActive = active;
        boolean shareResources = ServerConfig.get(ServerConfig.SHARE_RESOURCES);

        // While no fuel is burning, check water against the fuel waiting in the slot.
        FuelDefinition pending = burnRemaining <= 0 ? FuelRegistry.get(inventory.getStackInSlot(GeneratorItemHandler.FUEL_SLOT)) : null;
        double waterMultiplier = pending != null ? pending.waterMultiplier() : fuelWaterMultiplier;
        boolean waterOk = hasWaterFor(nextWaterDrain(network, waterMultiplier), network, shareResources);
        boolean energyOk = !ServerConfig.get(ServerConfig.PAUSE_WHEN_ENERGY_FULL) || hasEnergySpace(network, shareResources);

        if (burnRemaining <= 0 && pending != null && waterOk && energyOk) {
            startNextFuel();
        }

        if (burnRemaining <= 0 && (pending == null || (waterOk && energyOk))) {
            active = false;
            state = GeneratorState.NO_FUEL;
        } else if (!waterOk) {
            active = false;
            state = GeneratorState.NO_WATER;
        } else if (!energyOk) {
            active = false;
            state = GeneratorState.ENERGY_FULL;
        } else {
            active = true;
            state = GeneratorState.RUNNING;
        }

        if (active) {
            runGeneration(network, shareResources);
            tickWater(network, shareResources);
            tickMycelium(level, network);
        } else {
            lastExactOutput = 0.0D;
            if (wasActive && ServerConfig.get(ServerConfig.RESET_ON_STOP)) {
                resetChance(network);
            }
        }

        outputEnergy(level);
        trackExtraction();
        updateBlockState(level, blockState);
        updateComparators(level, blockState);
    }

    private void updateComparators(ServerLevel level, BlockState blockState) {
        int stored = energy.getEnergyStored();
        int signal = stored <= 0 ? 0 : 1 + (int) Math.floor(14.0D * stored / Math.max(1, energy.getMaxEnergyStored()));
        if (signal != lastComparatorSignal) {
            lastComparatorSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, blockState.getBlock());
        }
    }

    private void startNextFuel() {
        ItemStack stack = inventory.getStackInSlot(GeneratorItemHandler.FUEL_SLOT);
        FuelDefinition definition = FuelRegistry.get(stack);
        if (definition == null) {
            return;
        }
        fuelItemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        fuelEnergyMultiplier = definition.energyMultiplier();
        fuelWaterMultiplier = definition.waterMultiplier();
        fuelMyceliumBonus = definition.myceliumBonus();
        fuelScalingBonus = definition.scalingBonus();
        burnTotal = definition.burnTicks();
        burnRemaining = burnTotal;
        inventory.extractItem(GeneratorItemHandler.FUEL_SLOT, 1, false);
        setChanged();
    }

    // ------------------------------------------------------------------ energy

    /** Exact FE/t this generator produces with the given network state. */
    public double computeOutput(GeneratorNetwork network) {
        double base = ServerConfig.get(ServerConfig.BASE_GENERATION);
        boolean scaling = ServerConfig.get(ServerConfig.SCALING_ENABLED);
        int count = scaling ? network.scalingCount() : 1;
        double multiplier = ServerConfig.get(ServerConfig.SCALING_MULTIPLIER) + (scaling ? fuelScalingBonus : 0.0D);
        return NetworkMath.generatorOutput(base, fuelEnergyMultiplier, multiplier, count, ServerConfig.get(ServerConfig.MAX_NETWORK_OUTPUT));
    }

    private void runGeneration(GeneratorNetwork network, boolean shareResources) {
        double exact = computeOutput(network);
        lastExactOutput = exact;
        EnergyRounding rounding = ServerConfig.get(ServerConfig.ROUNDING);
        int produced = accumulator.produce(exact, rounding);
        int stored = energy.receiveInternal(produced, false);
        int leftover = produced - stored;
        if (leftover > 0 && shareResources) {
            for (MycelialGeneratorBlockEntity member : network.members()) {
                if (member == this || member.isRemoved()) {
                    continue;
                }
                leftover -= member.energy.receiveInternal(leftover, false);
                if (leftover <= 0) {
                    break;
                }
            }
        }
        // Any leftover here is discarded (only possible when pauseWhenEnergyFull = false).
        burnRemaining--;
        setChanged();
    }

    private boolean hasEnergySpace(GeneratorNetwork network, boolean shareResources) {
        if (energy.space() > 0) {
            return true;
        }
        if (shareResources) {
            for (MycelialGeneratorBlockEntity member : network.members()) {
                if (member != this && !member.isRemoved() && member.energy.space() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void outputEnergy(ServerLevel level) {
        int interval = ServerConfig.get(ServerConfig.OUTPUT_INTERVAL);
        if (++outputTimer < interval) {
            return;
        }
        outputTimer = 0;
        if (!ServerConfig.get(ServerConfig.AUTO_OUTPUT) || energy.getEnergyStored() <= 0) {
            return;
        }
        long budgetLong = (long) ServerConfig.get(ServerConfig.OUTPUT_TRANSFER_RATE) * interval;
        int budget = (int) Math.min(Integer.MAX_VALUE, budgetLong);
        for (Direction direction : DIRECTIONS) {
            if (budget <= 0 || energy.getEnergyStored() <= 0) {
                break;
            }
            BlockPos neighbourPos = worldPosition.relative(direction);
            if (!level.isLoaded(neighbourPos)) {
                continue;
            }
            IEnergyStorage target = energyCache(level, direction).getCapability();
            if (target == null || target == energy || !target.canReceive()) {
                continue;
            }
            int offer = energy.extractInternal(budget, true);
            int accepted = target.receiveEnergy(offer, false);
            accepted = Math.max(0, Math.min(accepted, offer));
            if (accepted > 0) {
                energy.extractInternal(accepted, false);
                budget -= accepted;
            }
        }
    }

    private BlockCapabilityCache<IEnergyStorage, Direction> energyCache(ServerLevel level, Direction direction) {
        int index = direction.ordinal();
        BlockCapabilityCache<IEnergyStorage, Direction> cache = energyCaches[index];
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level, worldPosition.relative(direction), direction.getOpposite());
            energyCaches[index] = cache;
        }
        return cache;
    }

    private void trackExtraction() {
        extractedWindow += energy.takeExtractedThisTick();
        if (++windowTicks >= 20) {
            extractionRate = extractedWindow / (double) windowTicks;
            extractedWindow = 0;
            windowTicks = 0;
        }
    }

    // ------------------------------------------------------------------ water

    /** mB the next consumption will drain (exact, before carrying the remainder). */
    private double waterPerInterval(GeneratorNetwork network) {
        return waterPerInterval(network, fuelWaterMultiplier);
    }

    private double waterPerInterval(GeneratorNetwork network, double waterMultiplier) {
        double amount = ServerConfig.get(ServerConfig.WATER_PER_INTERVAL) * waterMultiplier;
        if (ServerConfig.get(ServerConfig.WATER_SCALES_WITH_NETWORK) && ServerConfig.get(ServerConfig.SCALING_ENABLED)) {
            amount = NetworkMath.safeMultiply(amount, network.scaleFactor());
        }
        return Math.min(amount, Integer.MAX_VALUE);
    }

    private int nextWaterDrain(GeneratorNetwork network, double waterMultiplier) {
        double exact = waterPerInterval(network, waterMultiplier) + waterRemainder;
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(exact));
    }

    private boolean hasWaterFor(int amount, GeneratorNetwork network, boolean shareResources) {
        if (!ServerConfig.get(ServerConfig.REQUIRE_WATER) || amount <= 0) {
            return true;
        }
        long available = tank.getFluidAmount();
        if (available >= amount) {
            return true;
        }
        if (shareResources) {
            for (MycelialGeneratorBlockEntity member : network.members()) {
                if (member != this && !member.isRemoved()) {
                    available += member.tank.getFluidAmount();
                    if (available >= amount) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void tickWater(GeneratorNetwork network, boolean shareResources) {
        if (++waterTimer < ServerConfig.get(ServerConfig.WATER_INTERVAL)) {
            return;
        }
        waterTimer = 0;
        double exact = waterPerInterval(network) + waterRemainder;
        int drain = (int) Math.min(Integer.MAX_VALUE, Math.floor(exact));
        waterRemainder = Math.max(0.0D, Math.min(0.999999D, exact - drain));
        if (drain <= 0) {
            return;
        }
        int remaining = drain - tank.drain(drain, IFluidHandler.FluidAction.EXECUTE).getAmount();
        if (remaining > 0 && shareResources) {
            for (MycelialGeneratorBlockEntity member : network.members()) {
                if (member == this || member.isRemoved()) {
                    continue;
                }
                remaining -= member.tank.drain(remaining, IFluidHandler.FluidAction.EXECUTE).getAmount();
                if (remaining <= 0) {
                    break;
                }
            }
        }
    }

    private void processFluidContainer() {
        ItemStack container = inventory.getStackInSlot(GeneratorItemHandler.CONTAINER_IN_SLOT);
        if (container.isEmpty()) {
            return;
        }
        ItemStack single = container.copyWithCount(1);
        FluidActionResult simulated = FluidUtil.tryEmptyContainer(single, tank, Integer.MAX_VALUE, null, false);
        if (!simulated.isSuccess()) {
            return;
        }
        ItemStack result = simulated.getResult();
        if (!inventory.insertOutput(result, true).isEmpty()) {
            return;
        }
        FluidActionResult executed = FluidUtil.tryEmptyContainer(single, tank, Integer.MAX_VALUE, null, true);
        if (!executed.isSuccess()) {
            return;
        }
        inventory.extractItem(GeneratorItemHandler.CONTAINER_IN_SLOT, 1, false);
        inventory.insertOutput(executed.getResult(), false);
    }

    // ------------------------------------------------------------------ mycelium

    private void tickMycelium(ServerLevel level, GeneratorNetwork network) {
        if (!ServerConfig.get(ServerConfig.MYCELIUM_ENABLED)) {
            attemptTimer = 0;
            return;
        }
        if (++attemptTimer < ServerConfig.get(ServerConfig.ATTEMPT_INTERVAL)) {
            return;
        }
        attemptTimer = 0;
        List<BlockPos> targets = MyceliumSpreadManager.findTargets(level, worldPosition);
        if (targets.isEmpty()) {
            return; // not an eligible attempt: the chance is unchanged
        }
        boolean shared = ServerConfig.get(ServerConfig.SHARE_CHANCE);
        double current = shared ? network.sharedChance() : chance;
        double max = ServerConfig.maxChance();
        if (MyceliumChance.succeeds(current, level.random.nextDouble())) {
            int conversions = MyceliumChance.conversionsPerAttempt(
                    ServerConfig.get(ServerConfig.MAX_CONVERSIONS),
                    network.activeCount(),
                    ServerConfig.get(ServerConfig.NETWORK_EXTRA_CONVERSIONS),
                    ServerConfig.get(ServerConfig.GENERATORS_PER_EXTRA),
                    ServerConfig.get(ServerConfig.MAX_EXTRA_CONVERSIONS));
            int converted = MyceliumSpreadManager.convert(level, worldPosition, targets, conversions, level.random);
            if (converted > 0) {
                convertedThisSession += converted;
                if (ServerConfig.get(ServerConfig.RESET_ON_SUCCESS)) {
                    resetChance(network);
                }
            }
            // If every target was protected nothing changed, so the chance is left as is.
        } else {
            double next = MyceliumChance.afterFailure(current, ServerConfig.get(ServerConfig.CHANCE_INCREASE), growthFactor(network), max);
            if (shared) {
                network.setSharedChance(next);
            } else {
                chance = next;
            }
        }
        setChanged();
    }

    /** Multiplier applied to the chance increase: fuel bonus x optional network bonus. */
    public double growthFactor(GeneratorNetwork network) {
        double factor = fuelMyceliumBonus;
        if (ServerConfig.get(ServerConfig.NETWORK_GROWTH_BONUS)) {
            factor *= MyceliumChance.networkGrowthFactor(network.activeCount(), ServerConfig.get(ServerConfig.GROWTH_BONUS_PER_GENERATOR));
        }
        return factor;
    }

    private void resetChance(GeneratorNetwork network) {
        double initial = ServerConfig.initialChance();
        if (ServerConfig.get(ServerConfig.SHARE_CHANCE)) {
            network.setSharedChance(initial);
        } else {
            chance = initial;
        }
        setChanged();
    }

    public double getOwnChance() {
        return chance < 0 ? ServerConfig.initialChance() : chance;
    }

    public void setOwnChance(double value) {
        if (value != chance) {
            chance = value;
            setChanged();
        }
    }

    // ------------------------------------------------------------------ block state

    private void updateBlockState(ServerLevel level, BlockState blockState) {
        int light = active ? ServerConfig.get(ServerConfig.ACTIVE_LIGHT_LEVEL) : 0;
        if (blockState.getValue(MycelialGeneratorBlock.ACTIVE) != active || blockState.getValue(MycelialGeneratorBlock.LIGHT) != light) {
            level.setBlock(worldPosition, blockState.setValue(MycelialGeneratorBlock.ACTIVE, active)
                    .setValue(MycelialGeneratorBlock.LIGHT, light), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ GUI status

    /** Builds the snapshot sent to players viewing this generator. */
    public GeneratorStatus createStatus() {
        GeneratorNetwork network = level instanceof ServerLevel serverLevel ? GeneratorNetworkManager.get(serverLevel).getNetwork(this) : null;
        int size = network == null ? 1 : network.size();
        int activeMembers = network == null ? (active ? 1 : 0) : network.activeCount();
        double scale = network == null ? 1.0D : network.scaleFactor();
        double total = network == null ? lastExactOutput : network.totalOutput();
        NetworkStatus networkStatus = network == null ? NetworkStatus.IDLE : network.status();
        boolean shared = ServerConfig.get(ServerConfig.SHARE_CHANCE);
        double shownChance = shared && network != null ? network.sharedChance() : getOwnChance();
        double growth = network == null ? fuelMyceliumBonus : growthFactor(network);
        double increase = Math.max(0.0D, ServerConfig.get(ServerConfig.CHANCE_INCREASE)) * growth;
        FluidStack fluid = tank.getFluid();
        String fluidId = fluid.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
        double waterRate = network == null ? ServerConfig.get(ServerConfig.WATER_PER_INTERVAL) : waterPerInterval(network);
        double perTick = active ? lastExactOutput : (network == null ? 0.0D : previewOutput(network));
        return new GeneratorStatus(
                energy.getEnergyStored(), energy.getMaxEnergyStored(), perTick, active, extractionRate,
                fluidId, tank.getFluidAmount(), tank.getCapacity(), waterRate, ServerConfig.get(ServerConfig.WATER_INTERVAL),
                burnRemaining > 0 ? fuelItemId : "", burnRemaining, burnTotal,
                size, activeMembers, scale, total, networkStatus.ordinal(), ServerConfig.get(ServerConfig.MAX_NETWORK_SIZE),
                ServerConfig.get(ServerConfig.MYCELIUM_ENABLED), shownChance, increase, attemptTimer,
                ServerConfig.get(ServerConfig.ATTEMPT_INTERVAL), convertedThisSession, ServerConfig.get(ServerConfig.TRACK_CONVERSIONS),
                state.ordinal());
    }

    /** Output this generator would have if it were running now (for the idle GUI). */
    private double previewOutput(GeneratorNetwork network) {
        return burnRemaining > 0 ? computeOutput(network) : 0.0D;
    }

    // ------------------------------------------------------------------ accessors

    public boolean isActive() {
        return active;
    }

    public double getLastExactOutput() {
        return lastExactOutput;
    }

    public GeneratorState getState() {
        return state;
    }

    public long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(long networkId) {
        if (this.networkId != networkId) {
            this.networkId = networkId;
            setChanged();
        }
    }

    public GeneratorItemHandler getInventory() {
        return inventory;
    }

    public IItemHandler getAutomationItemHandler() {
        return automationItems;
    }

    public GeneratorFluidTank getTank() {
        return tank;
    }

    public IFluidHandler getExternalFluidHandler() {
        return externalFluid;
    }

    public GeneratorEnergyStorage getEnergyStorage() {
        return energy;
    }

    public int getBurnRemaining() {
        return burnRemaining;
    }

    public int getConvertedThisSession() {
        return convertedThisSession;
    }

    public int getAttemptTimer() {
        return attemptTimer;
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mycelialpower.mycelial_generator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MycelialGeneratorMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        CompoundTag tankTag = new CompoundTag();
        tank.writeToNBT(registries, tankTag);
        tag.put("Tank", tankTag);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putDouble("EnergyRemainder", accumulator.getRemainder());

        CompoundTag fuel = new CompoundTag();
        fuel.putInt("Remaining", burnRemaining);
        fuel.putInt("Total", burnTotal);
        fuel.putString("Item", fuelItemId);
        fuel.putDouble("EnergyMultiplier", fuelEnergyMultiplier);
        fuel.putDouble("WaterMultiplier", fuelWaterMultiplier);
        fuel.putDouble("MyceliumBonus", fuelMyceliumBonus);
        fuel.putDouble("ScalingBonus", fuelScalingBonus);
        tag.put("Fuel", fuel);

        tag.putInt("WaterTimer", waterTimer);
        tag.putDouble("WaterRemainder", waterRemainder);
        tag.putDouble("MyceliumChance", getOwnChance());
        tag.putInt("MyceliumTimer", attemptTimer);
        tag.putLong("NetworkId", networkId);
        tag.putBoolean("Active", active);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            CompoundTag inv = tag.getCompound("Inventory");
            inv.putInt("Size", GeneratorItemHandler.SLOTS);
            inventory.deserializeNBT(registries, inv);
        }
        if (tag.contains("Tank")) {
            tank.readFromNBT(registries, tag.getCompound("Tank"));
        }
        energy.setEnergy(tag.getInt("Energy"));
        accumulator.setRemainder(tag.getDouble("EnergyRemainder"));

        CompoundTag fuel = tag.getCompound("Fuel");
        burnRemaining = Math.max(0, fuel.getInt("Remaining"));
        burnTotal = Math.max(burnRemaining, fuel.getInt("Total"));
        fuelItemId = fuel.getString("Item");
        fuelEnergyMultiplier = fuel.contains("EnergyMultiplier") ? clampNonNegative(fuel.getDouble("EnergyMultiplier")) : 1.0D;
        fuelWaterMultiplier = fuel.contains("WaterMultiplier") ? clampNonNegative(fuel.getDouble("WaterMultiplier")) : 1.0D;
        fuelMyceliumBonus = fuel.contains("MyceliumBonus") ? clampNonNegative(fuel.getDouble("MyceliumBonus")) : 1.0D;
        fuelScalingBonus = clampNonNegative(fuel.getDouble("ScalingBonus"));

        waterTimer = Math.max(0, tag.getInt("WaterTimer"));
        waterRemainder = Math.max(0.0D, Math.min(0.999999D, tag.getDouble("WaterRemainder")));
        chance = tag.contains("MyceliumChance") ? MyceliumChance.clamp(tag.getDouble("MyceliumChance"), 1.0D) : -1.0D;
        attemptTimer = Math.max(0, tag.getInt("MyceliumTimer"));
        networkId = tag.getLong("NetworkId");
        active = tag.getBoolean("Active");
    }

    private static double clampNonNegative(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0.0D;
    }

    /** Only the block state is needed on the client (the GUI uses its own packet), so keep chunk data small. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return null;
    }
}
