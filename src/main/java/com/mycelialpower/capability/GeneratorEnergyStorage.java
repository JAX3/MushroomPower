package com.mycelialpower.capability;

import com.mycelialpower.config.ServerConfig;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The generator's internal energy buffer, exposed through NeoForge's transfer API ({@link EnergyHandler}).
 * Other blocks may only extract (inserting always returns 0), which makes generator-to-generator energy
 * loops and duplication impossible. External extraction is transactional: a rolled-back transaction
 * restores the buffer exactly. Capacity and transfer rate are read from the server config.
 */
public class GeneratorEnergyStorage implements EnergyHandler {
    private final Runnable onChanged;
    private final Journal journal = new Journal();
    private int energy;
    private long extractedThisTick;

    public GeneratorEnergyStorage(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public int capacity() {
        return Math.max(1, ServerConfig.get(ServerConfig.MAX_ENERGY_STORAGE));
    }

    /** Adds generated energy directly (not part of a transaction). Returns the amount stored. */
    public int receiveInternal(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int accepted = Math.min(space(), amount);
        if (accepted > 0) {
            energy += accepted;
            onChanged.run();
        }
        return accepted;
    }

    /** Removes energy directly after a committed transfer (pushing to a neighbour, sharing). */
    public int extractInternal(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int extracted = Math.min(energy, amount);
        if (extracted > 0) {
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
    public long getAmountAsLong() {
        return energy;
    }

    @Override
    public long getCapacityAsLong() {
        return capacity();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        int limit = Math.min(amount, ServerConfig.get(ServerConfig.OUTPUT_TRANSFER_RATE));
        int extracted = Math.min(energy, Math.max(0, limit));
        if (extracted > 0) {
            journal.updateSnapshots(transaction);
            energy -= extracted;
        }
        return extracted;
    }

    /** Snapshots for transactional extraction; counts committed extraction for the GUI's "out" rate. */
    private final class Journal extends SnapshotJournal<Integer> {
        @Override
        protected Integer createSnapshot() {
            return energy;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            energy = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            int extracted = originalState - energy;
            if (extracted != 0) {
                if (extracted > 0) {
                    extractedThisTick += extracted;
                }
                onChanged.run();
            }
        }
    }
}
