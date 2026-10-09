package com.mycelialpower.capability;

import com.mycelialpower.config.ServerConfig;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The generator's internal energy buffer. Exposed to other blocks as extract-only: it never accepts
 * energy from outside, which makes generator-to-generator energy loops (and duplication) impossible.
 * Capacity and transfer rate are read from the server config so reloads apply immediately.
 */
public class GeneratorEnergyStorage implements IEnergyStorage {
    private final Runnable onChanged;
    private int energy;
    private long extractedThisTick;

    public GeneratorEnergyStorage(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public int capacity() {
        return Math.max(1, ServerConfig.get(ServerConfig.MAX_ENERGY_STORAGE));
    }

    /** Adds generated energy. Returns the amount actually stored. */
    public int receiveInternal(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        int space = Math.max(0, capacity() - energy);
        int accepted = Math.min(space, amount);
        if (!simulate && accepted > 0) {
            energy += accepted;
            onChanged.run();
        }
        return accepted;
    }

    /** Removes energy for internal transfers (pushing to neighbours, sharing). */
    public int extractInternal(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        int extracted = Math.min(energy, amount);
        if (!simulate && extracted > 0) {
            energy -= extracted;
            extractedThisTick += extracted;
            onChanged.run();
        }
        return extracted;
    }

    public int space() {
        return Math.max(0, capacity() - energy);
    }

    /** Clamps stored energy after a capacity reduction. */
    public void clampToCapacity() {
        if (energy > capacity()) {
            energy = capacity();
            onChanged.run();
        }
    }

    public long takeExtractedThisTick() {
        long value = extractedThisTick;
        extractedThisTick = 0;
        return value;
    }

    public void setEnergy(int energy) {
        this.energy = Math.max(0, Math.min(energy, capacity()));
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return 0;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int limit = Math.min(maxExtract, ServerConfig.get(ServerConfig.OUTPUT_TRANSFER_RATE));
        return extractInternal(limit, simulate);
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity();
    }

    @Override
    public boolean canExtract() {
        return ServerConfig.get(ServerConfig.OUTPUT_TRANSFER_RATE) > 0;
    }

    @Override
    public boolean canReceive() {
        return false;
    }
}
