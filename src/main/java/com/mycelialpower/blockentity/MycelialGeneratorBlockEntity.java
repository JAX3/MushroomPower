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
import com.mycelialpower.network.ConnectionType;
import com.mycelialpower.network.GeneratorNetwork;
import com.mycelialpower.network.GeneratorNetworkManager;
import com.mycelialpower.network.ManualLinkData;
import com.mycelialpower.network.ManualLinking;
import com.mycelialpower.network.NetworkMath;
import com.mycelialpower.network.NetworkStatus;
import com.mycelialpower.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
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
    private final BlockCapabilityCache<EnergyHandler, Direction>[] energyCaches = new BlockCapabilityCache[6];

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
    private int hugeMushroomsThisSession;

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
    private boolean linksChecked;
    @Nullable
    private GlobalPos globalPos;
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

    /**
     * Called once when the block is broken or replaced (never on chunk unload): drops the inventory and
     * removes this generator's manual links from both ends.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            dropContents(level, pos);
        }
        if (level instanceof ServerLevel serverLevel) {
            ManualLinking.onGeneratorDestroyed(serverLevel, pos);
        }
    }

    /** Drops the inventory into the world. */
    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < inventory.size(); slot++) {
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
        if (!linksChecked) {
            linksChecked = true;
            ManualLinking.pruneStaleLinks(level, worldPosition);
        }
        GeneratorNetwork network = manager.getNetwork(this);
        // Server tick count (not level game time) so members in different dimensions share one refresh clock.
        network.refresh(level.getServer().getTickCount());

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
        int stored = energy.getAmountAsInt();
        int signal = stored <= 0 ? 0 : 1 + (int) Math.floor(14.0D * stored / Math.max(1, energy.getCapacityAsInt()));
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
        inventory.shrink(GeneratorItemHandler.FUEL_SLOT);
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
        int stored = energy.receiveInternal(produced);
        int leftover = produced - stored;
        if (leftover > 0 && shareResources) {
            for (MycelialGeneratorBlockEntity member : network.members()) {
                if (member == this || member.isRemoved()) {
                    continue;
                }
                leftover -= member.energy.receiveInternal(leftover);
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
        if (!ServerConfig.get(ServerConfig.AUTO_OUTPUT) || energy.getAmountAsInt() <= 0) {
            return;
        }
        long budgetLong = (long) ServerConfig.get(ServerConfig.OUTPUT_TRANSFER_RATE) * interval;
        int budget = (int) Math.min(Integer.MAX_VALUE, budgetLong);
        for (Direction direction : DIRECTIONS) {
            if (budget <= 0 || energy.getAmountAsInt() <= 0) {
                break;
            }
            BlockPos neighbourPos = worldPosition.relative(direction);
            if (!level.isLoaded(neighbourPos)) {
                continue;
            }
            EnergyHandler target = energyCache(level, direction).getCapability();
            if (target == null || target == energy) {
                continue;
            }
            int offer = Math.min(budget, energy.getAmountAsInt());
            int accepted;
            // The neighbour's insert is transactional; our buffer is only reduced once it has committed.
            try (Transaction transaction = Transaction.openRoot()) {
                accepted = Math.max(0, Math.min(offer, target.insert(offer, transaction)));
                transaction.commit();
            }
            if (accepted > 0) {
                energy.extractInternal(accepted);
                budget -= accepted;
            }
        }
    }

    private BlockCapabilityCache<EnergyHandler, Direction> energyCache(ServerLevel level, Direction direction) {
        int index = direction.ordinal();
        BlockCapabilityCache<EnergyHandler, Direction> cache = energyCaches[index];
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, worldPosition.relative(direction), direction.getOpposite());
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
        int remaining = drain - tank.drainInternal(drain);
        if (remaining > 0 && shareResources) {
            for (MycelialGeneratorBlockEntity member : network.members()) {
                if (member == this || member.isRemoved()) {
                    continue;
                }
                remaining -= member.tank.drainInternal(remaining);
                if (remaining <= 0) {
                    break;
                }
            }
        }
    }

    /**
     * Empties one fluid container from the bucket slot into the tank. The container is copied into a
     * temporary one-slot handler so the transfer API can turn it into its empty form; everything happens in
     * one transaction that is only committed if the emptied container fits into the output slot.
     */
    private void processFluidContainer() {
        ItemStack container = inventory.getStackInSlot(GeneratorItemHandler.CONTAINER_IN_SLOT);
        if (container.isEmpty()) {
            return;
        }
        ItemStacksResourceHandler scratch = new ItemStacksResourceHandler(1);
        scratch.set(0, ItemResource.of(container), 1);
        ResourceHandler<FluidResource> source = ItemAccess.forHandlerIndex(scratch, 0).getCapability(Capabilities.Fluid.ITEM);
        if (source == null) {
            return;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = ResourceHandlerUtil.move(source, tank, resource -> true, Integer.MAX_VALUE, transaction);
            if (moved <= 0) {
                return;
            }
            ItemStack emptied = scratch.getResource(0).toStack(scratch.getAmountAsInt(0));
            if (!inventory.canOutput(emptied)) {
                return; // output slot full: abort, nothing changes
            }
            transaction.commit();
            inventory.shrink(GeneratorItemHandler.CONTAINER_IN_SLOT);
            inventory.addOutput(emptied);
        }
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
        if (MyceliumChance.succeeds(current, level.getRandom().nextDouble())) {
            int conversions = MyceliumChance.conversionsPerAttempt(
                    ServerConfig.get(ServerConfig.MAX_CONVERSIONS),
                    network.activeCount(),
                    ServerConfig.get(ServerConfig.NETWORK_EXTRA_CONVERSIONS),
                    ServerConfig.get(ServerConfig.GENERATORS_PER_EXTRA),
                    ServerConfig.get(ServerConfig.MAX_EXTRA_CONVERSIONS));
            MyceliumSpreadManager.ConversionResult outcome = MyceliumSpreadManager.convert(level, worldPosition, targets, conversions, level.getRandom());
            int converted = outcome.converted();
            hugeMushroomsThisSession += outcome.hugeMushrooms();
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
        GeneratorNetworkManager manager = level instanceof ServerLevel serverLevel ? GeneratorNetworkManager.get(serverLevel) : null;
        GeneratorNetwork network = manager != null ? manager.getNetwork(this) : null;
        ConnectionType connection = manager != null ? manager.connectionType(this) : ConnectionType.NONE;
        int activeLinks = manager != null ? manager.activeManualLinks(this) : 0;
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
                energy.getAmountAsInt(), energy.getCapacityAsInt(), perTick, active, extractionRate,
                fluidId, tank.getFluidAmount(), tank.getCapacity(), waterRate, ServerConfig.get(ServerConfig.WATER_INTERVAL),
                burnRemaining > 0 ? fuelItemId : "", burnRemaining, burnTotal,
                size, activeMembers, scale, total, networkStatus.ordinal(), ServerConfig.get(ServerConfig.MAX_NETWORK_SIZE),
                ServerConfig.get(ServerConfig.MYCELIUM_ENABLED), shownChance, increase, attemptTimer,
                ServerConfig.get(ServerConfig.ATTEMPT_INTERVAL), convertedThisSession, ServerConfig.get(ServerConfig.TRACK_CONVERSIONS),
                state.ordinal(),
                manualLinkCount(), activeLinks, ServerConfig.get(ServerConfig.MAX_MANUAL_CONNECTIONS), connection.ordinal(),
                hugeMushroomsThisSession);
    }

    private int manualLinkCount() {
        return level instanceof ServerLevel serverLevel ? ManualLinkData.get(serverLevel.getServer()).count(getGlobalPos()) : 0;
    }

    /** This generator's position including its dimension; the key used by the network manager and link data. */
    public GlobalPos getGlobalPos() {
        GlobalPos pos = globalPos;
        if (pos == null || level != null && !pos.dimension().equals(level.dimension())) {
            pos = GlobalPos.of(level == null ? Level.OVERWORLD : level.dimension(), worldPosition);
            globalPos = pos;
        }
        return pos;
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

    public ResourceHandler<ItemResource> getAutomationItemHandler() {
        return automationItems;
    }

    public GeneratorFluidTank getTank() {
        return tank;
    }

    public ResourceHandler<FluidResource> getExternalFluidHandler() {
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putChild("Inventory", inventory);
        output.putChild("Tank", tank);
        output.putInt("Energy", energy.getAmountAsInt());
        output.store("EnergyRemainder", Codec.DOUBLE, accumulator.getRemainder());

        ValueOutput fuel = output.child("Fuel");
        fuel.putInt("Remaining", burnRemaining);
        fuel.putInt("Total", burnTotal);
        fuel.putString("Item", fuelItemId);
        fuel.store("EnergyMultiplier", Codec.DOUBLE, fuelEnergyMultiplier);
        fuel.store("WaterMultiplier", Codec.DOUBLE, fuelWaterMultiplier);
        fuel.store("MyceliumBonus", Codec.DOUBLE, fuelMyceliumBonus);
        fuel.store("ScalingBonus", Codec.DOUBLE, fuelScalingBonus);

        output.putInt("WaterTimer", waterTimer);
        output.store("WaterRemainder", Codec.DOUBLE, waterRemainder);
        output.store("MyceliumChance", Codec.DOUBLE, getOwnChance());
        output.putInt("MyceliumTimer", attemptTimer);
        output.putLong("NetworkId", networkId);
        output.putBoolean("Active", active);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.readChild("Inventory", inventory);
        input.readChild("Tank", tank);
        energy.setEnergy(input.getIntOr("Energy", 0));
        accumulator.setRemainder(input.read("EnergyRemainder", Codec.DOUBLE).orElse(0.0D));

        ValueInput fuel = input.childOrEmpty("Fuel");
        burnRemaining = Math.max(0, fuel.getIntOr("Remaining", 0));
        burnTotal = Math.max(burnRemaining, fuel.getIntOr("Total", 0));
        fuelItemId = fuel.read("Item", Codec.STRING).orElse("");
        fuelEnergyMultiplier = clampNonNegative(fuel.read("EnergyMultiplier", Codec.DOUBLE).orElse(1.0D));
        fuelWaterMultiplier = clampNonNegative(fuel.read("WaterMultiplier", Codec.DOUBLE).orElse(1.0D));
        fuelMyceliumBonus = clampNonNegative(fuel.read("MyceliumBonus", Codec.DOUBLE).orElse(1.0D));
        fuelScalingBonus = clampNonNegative(fuel.read("ScalingBonus", Codec.DOUBLE).orElse(0.0D));

        waterTimer = Math.max(0, input.getIntOr("WaterTimer", 0));
        waterRemainder = Math.max(0.0D, Math.min(0.999999D, input.read("WaterRemainder", Codec.DOUBLE).orElse(0.0D)));
        chance = input.read("MyceliumChance", Codec.DOUBLE).map(value -> MyceliumChance.clamp(value, 1.0D)).orElse(-1.0D);
        attemptTimer = Math.max(0, input.getIntOr("MyceliumTimer", 0));
        networkId = input.getLongOr("NetworkId", 0L);
        active = input.getBooleanOr("Active", false);
    }

    private static double clampNonNegative(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0.0D;
    }
}
