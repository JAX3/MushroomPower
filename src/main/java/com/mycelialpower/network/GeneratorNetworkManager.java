package com.mycelialpower.network;

import com.mycelialpower.blockentity.MycelialGeneratorBlockEntity;
import com.mycelialpower.config.ServerConfig;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tracks every <em>loaded</em> Mycelial Generator of one dimension and groups them into connected
 * networks.
 * <ul>
 *     <li>Generators register when their block entity loads and unregister when it is removed or its
 *     chunk unloads, so adjacency is resolved purely from this in-memory map. The manager never reads
 *     blocks from the world and therefore never loads chunks.</li>
 *     <li>Changes only mark positions dirty. On the next query the affected networks (and their
 *     neighbours) are rebuilt with an iterative breadth-first search; untouched networks are kept.</li>
 *     <li>Networks are limited to {@code maxNetworkSize} members.</li>
 * </ul>
 * All access happens on the server thread.
 */
public final class GeneratorNetworkManager {
    private static final Map<ResourceKey<Level>, GeneratorNetworkManager> MANAGERS = new HashMap<>();
    private static final Direction[] DIRECTIONS = Direction.values();

    private final Long2ObjectOpenHashMap<MycelialGeneratorBlockEntity> loaded = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<GeneratorNetwork> networks = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet dirty = new LongOpenHashSet();
    private boolean fullRebuild;
    private int seenConfigGeneration = configGeneration;

    /** Bumped (from any thread) when the config changes; each manager rebuilds on its next update. */
    private static volatile int configGeneration;

    public static GeneratorNetworkManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level.dimension(), key -> new GeneratorNetworkManager());
    }

    public static void forEach(java.util.function.Consumer<GeneratorNetworkManager> action) {
        MANAGERS.values().forEach(action);
    }

    public static void removeLevel(ResourceKey<Level> dimension) {
        MANAGERS.remove(dimension);
    }

    public static void clearAll() {
        MANAGERS.clear();
    }

    /** Marks every network of every dimension for rebuild (used after config changes). Thread-safe. */
    public static void markAllDirty() {
        configGeneration++;
    }

    public void add(MycelialGeneratorBlockEntity generator) {
        long key = generator.getBlockPos().asLong();
        MycelialGeneratorBlockEntity previous = loaded.put(key, generator);
        if (previous != generator) {
            dirty.add(key);
        }
    }

    public void remove(MycelialGeneratorBlockEntity generator) {
        long key = generator.getBlockPos().asLong();
        if (loaded.get(key) == generator) {
            loaded.remove(key);
            dirty.add(key);
        }
    }

    public boolean isTracked(MycelialGeneratorBlockEntity generator) {
        return loaded.get(generator.getBlockPos().asLong()) == generator;
    }

    public int loadedCount() {
        return loaded.size();
    }

    /** The network containing the generator, registering it first if necessary. */
    public GeneratorNetwork getNetwork(MycelialGeneratorBlockEntity generator) {
        if (!isTracked(generator)) {
            add(generator);
        }
        update();
        GeneratorNetwork network = networks.get(generator.getBlockPos().asLong());
        if (network == null) {
            // Defensive: should be unreachable because every tracked generator is assigned during update().
            fullRebuild = true;
            update();
            network = networks.get(generator.getBlockPos().asLong());
        }
        return network;
    }

    @Nullable
    public GeneratorNetwork peekNetwork(BlockPos pos) {
        update();
        return networks.get(pos.asLong());
    }

    /** Applies pending changes. Cheap when nothing changed. */
    public void update() {
        LongOpenHashSet seeds;
        int generation = configGeneration;
        if (generation != seenConfigGeneration) {
            seenConfigGeneration = generation;
            fullRebuild = true;
        }
        if (fullRebuild) {
            fullRebuild = false;
            dirty.clear();
            for (GeneratorNetwork network : networks.values()) {
                network.invalidate();
            }
            networks.clear();
            seeds = new LongOpenHashSet(loaded.keySet());
        } else if (!dirty.isEmpty()) {
            seeds = new LongOpenHashSet();
            long[] changed = dirty.toLongArray();
            dirty.clear();
            for (long pos : changed) {
                collectAffected(pos, seeds);
                for (Direction direction : DIRECTIONS) {
                    collectAffected(BlockPos.offset(pos, direction), seeds);
                }
            }
        } else {
            return;
        }
        build(seeds);
    }

    private void collectAffected(long pos, LongOpenHashSet seeds) {
        GeneratorNetwork network = networks.get(pos);
        if (network != null) {
            network.invalidate();
            for (MycelialGeneratorBlockEntity member : network.members()) {
                long memberPos = member.getBlockPos().asLong();
                networks.remove(memberPos);
                seeds.add(memberPos);
            }
            networks.remove(pos);
        }
        if (loaded.containsKey(pos)) {
            seeds.add(pos);
        }
    }

    private void build(LongOpenHashSet seeds) {
        long[] ordered = seeds.toLongArray();
        Arrays.sort(ordered);
        int maxSize = ServerConfig.get(ServerConfig.MAX_NETWORK_SIZE);
        boolean merge = ServerConfig.get(ServerConfig.MERGE_NETWORKS);

        if (!merge) {
            assignMissingIds(ordered);
        }

        LongOpenHashSet assigned = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (long seed : ordered) {
            MycelialGeneratorBlockEntity start = loaded.get(seed);
            if (start == null || assigned.contains(seed) || networks.containsKey(seed)) {
                continue;
            }
            List<MycelialGeneratorBlockEntity> members = new ArrayList<>();
            boolean limited = false;
            queue.clear();
            queue.enqueue(seed);
            assigned.add(seed);
            while (!queue.isEmpty()) {
                long current = queue.dequeueLong();
                MycelialGeneratorBlockEntity generator = loaded.get(current);
                members.add(generator);
                for (Direction direction : DIRECTIONS) {
                    long neighbour = BlockPos.offset(current, direction);
                    MycelialGeneratorBlockEntity other = loaded.get(neighbour);
                    if (other == null || assigned.contains(neighbour) || networks.containsKey(neighbour)) {
                        continue;
                    }
                    if (!merge && other.getNetworkId() != generator.getNetworkId()) {
                        continue;
                    }
                    if (members.size() + queue.size() >= maxSize) {
                        limited = true;
                        continue;
                    }
                    assigned.add(neighbour);
                    queue.enqueue(neighbour);
                }
            }

            long id = merge ? unifyIds(members) : start.getNetworkId();
            GeneratorNetwork network = new GeneratorNetwork(id, members, limited);
            for (MycelialGeneratorBlockEntity member : members) {
                networks.put(member.getBlockPos().asLong(), network);
            }
        }
    }

    /** With merging disabled, a new generator adopts the id of the largest touching network. */
    private void assignMissingIds(long[] ordered) {
        Long2IntOpenHashMap sizes = new Long2IntOpenHashMap();
        for (MycelialGeneratorBlockEntity generator : loaded.values()) {
            if (generator.getNetworkId() != 0L) {
                sizes.addTo(generator.getNetworkId(), 1);
            }
        }
        for (long pos : ordered) {
            MycelialGeneratorBlockEntity generator = loaded.get(pos);
            if (generator == null || generator.getNetworkId() != 0L) {
                continue;
            }
            long best = 0L;
            int bestSize = -1;
            for (Direction direction : DIRECTIONS) {
                MycelialGeneratorBlockEntity other = loaded.get(BlockPos.offset(pos, direction));
                if (other != null && other.getNetworkId() != 0L) {
                    int size = sizes.get(other.getNetworkId());
                    if (size > bestSize) {
                        bestSize = size;
                        best = other.getNetworkId();
                    }
                }
            }
            long id = best != 0L ? best : newId();
            generator.setNetworkId(id);
            sizes.addTo(id, 1);
        }
    }

    /** With merging enabled, all members carry one id so that disabling merging later keeps networks intact. */
    private static long unifyIds(List<MycelialGeneratorBlockEntity> members) {
        long id = 0L;
        for (MycelialGeneratorBlockEntity member : members) {
            if (member.getNetworkId() != 0L) {
                id = member.getNetworkId();
                break;
            }
        }
        if (id == 0L) {
            id = newId();
        }
        for (MycelialGeneratorBlockEntity member : members) {
            if (member.getNetworkId() != id) {
                member.setNetworkId(id);
            }
        }
        return id;
    }

    private static long newId() {
        long id;
        do {
            id = ThreadLocalRandom.current().nextLong();
        } while (id == 0L);
        return id;
    }
}
