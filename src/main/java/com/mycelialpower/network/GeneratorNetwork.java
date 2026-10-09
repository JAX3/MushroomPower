package com.mycelialpower.network;

import com.mycelialpower.blockentity.MycelialGeneratorBlockEntity;
import com.mycelialpower.config.ServerConfig;

import java.util.Collections;
import java.util.List;

/**
 * One connected component of loaded generators. Built by {@link GeneratorNetworkManager}; never
 * persisted. Statistics are refreshed at most once per game tick from the members' state of the
 * previous tick, which keeps the result independent of tick order and avoids recursion.
 */
public final class GeneratorNetwork {
    private final long id;
    private final List<MycelialGeneratorBlockEntity> members;
    private final boolean sizeLimited;
    private boolean valid = true;

    private long refreshedTick = Long.MIN_VALUE;
    private int activeCount;
    private double totalOutput;
    private double sharedChance = -1.0D;

    GeneratorNetwork(long id, List<MycelialGeneratorBlockEntity> members, boolean sizeLimited) {
        this.id = id;
        this.members = Collections.unmodifiableList(members);
        this.sizeLimited = sizeLimited;
    }

    public long id() {
        return id;
    }

    public List<MycelialGeneratorBlockEntity> members() {
        return members;
    }

    public int size() {
        return members.size();
    }

    public boolean isValid() {
        return valid;
    }

    void invalidate() {
        valid = false;
    }

    public boolean isSizeLimited() {
        return sizeLimited;
    }

    /** Recomputes cached statistics once per game tick. */
    public void refresh(long gameTime) {
        if (refreshedTick == gameTime) {
            return;
        }
        refreshedTick = gameTime;
        int active = 0;
        double total = 0.0D;
        for (MycelialGeneratorBlockEntity member : members) {
            if (member.isRemoved()) {
                continue;
            }
            if (member.isActive()) {
                active++;
                total += member.getLastExactOutput();
            }
        }
        activeCount = active;
        totalOutput = Double.isFinite(total) ? total : Double.MAX_VALUE;
    }

    public int activeCount() {
        return activeCount;
    }

    /** Sum of the exact FE/t produced by all members during the previous tick. */
    public double totalOutput() {
        return totalOutput;
    }

    /** Number of generators counted in the exponent ({@code n} in {@code multiplier ^ (n - 1)}). */
    public int scalingCount() {
        if (!ServerConfig.get(ServerConfig.SCALING_ENABLED)) {
            return 1;
        }
        int count = ServerConfig.get(ServerConfig.COUNT_INACTIVE) ? members.size() : activeCount;
        return Math.max(1, count);
    }

    /** The plain network scale factor {@code multiplier ^ (n - 1)} (without fuel bonuses). */
    public double scaleFactor() {
        return NetworkMath.scaleFactor(ServerConfig.get(ServerConfig.SCALING_MULTIPLIER), scalingCount());
    }

    public NetworkStatus status() {
        if (activeCount == 0) {
            return NetworkStatus.IDLE;
        }
        if (!ServerConfig.get(ServerConfig.SCALING_ENABLED)) {
            return NetworkStatus.SCALING_DISABLED;
        }
        double cap = ServerConfig.get(ServerConfig.MAX_NETWORK_OUTPUT);
        if (cap > 0 && NetworkMath.rawNetworkOutput(ServerConfig.get(ServerConfig.BASE_GENERATION), scalingCount(),
                ServerConfig.get(ServerConfig.SCALING_MULTIPLIER)) > cap) {
            return NetworkStatus.OUTPUT_CAPPED;
        }
        if (sizeLimited) {
            return NetworkStatus.SIZE_LIMIT;
        }
        return NetworkStatus.ONLINE;
    }

    /** Shared mycelium chance (only used when shareChanceAcrossNetwork is enabled). */
    public double sharedChance() {
        if (sharedChance < 0) {
            double max = ServerConfig.initialChance();
            for (MycelialGeneratorBlockEntity member : members) {
                max = Math.max(max, member.getOwnChance());
            }
            sharedChance = max;
        }
        return sharedChance;
    }

    public void setSharedChance(double chance) {
        sharedChance = chance;
        for (MycelialGeneratorBlockEntity member : members) {
            if (!member.isRemoved()) {
                member.setOwnChance(chance);
            }
        }
    }
}
