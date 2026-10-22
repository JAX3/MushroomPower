package com.mycelialpower.network;

import com.mycelialpower.blockentity.MycelialGeneratorBlockEntity;
import com.mycelialpower.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Tracks every <em>loaded</em> Mycelial Generator on the server and groups them into networks.
 * <p>
 * Two generators are connected when they touch on one of their six faces (automatic connection) or when they
 * are linked with the Mycelium Wrench (manual connection, see {@link ManualLinkData}). Both kinds of
 * connection feed the same connected-component search, so a network built from any mix of them is one
 * network for the exponential formula, and every generator belongs to exactly one network.
 * <ul>
 *     <li>Adjacency is resolved purely from this in-memory map of loaded generators and from the saved link
 *     list. The manager never reads blocks from the world and therefore never loads chunks; a generator in
 *     an unloaded chunk simply does not count until its chunk loads again.</li>
 *     <li>Changes (placement, removal, chunk load/unload, linking, unlinking) only mark positions dirty. On the
 *     next query the affected networks and their neighbours are rebuilt with an iterative breadth-first search;
 *     untouched networks are kept.</li>
 *     <li>Networks are limited to {@code maxNetworkSize} members.</li>
 * </ul>
 * One manager exists per running server (it spans all dimensions so cross-dimension links can be supported).
 * All access happens on the server thread.
 */
public final class GeneratorNetworkManager {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Comparator<GlobalPos> ORDER = Comparator
            .comparing((GlobalPos p) -> p.dimension().location())
            .thenComparingLong(p -> p.pos().asLong());

    @Nullable
    private static GeneratorNetworkManager instance;

    /** Bumped (from any thread) when the config changes; the manager rebuilds on its next update. */
    private static volatile int configGeneration;

    private final MinecraftServer server;
    private final Map<GlobalPos, MycelialGeneratorBlockEntity> loaded = new HashMap<>();
    private final Map<GlobalPos, GeneratorNetwork> networks = new HashMap<>();
    private final Set<GlobalPos> dirty = new HashSet<>();
    private boolean fullRebuild;
    private int seenConfigGeneration = configGeneration;

    private GeneratorNetworkManager(MinecraftServer server) {
        this.server = server;
    }

    public static GeneratorNetworkManager get(ServerLevel level) {
        return get(level.getServer());
    }

    public static GeneratorNetworkManager get(MinecraftServer server) {
        GeneratorNetworkManager current = instance;
        if (current == null || current.server != server) {
            current = new GeneratorNetworkManager(server);
            instance = current;
        }
        return current;
    }

    /** Applies pending changes once per server tick (called from the tick event). */
    public static void tick() {
        GeneratorNetworkManager current = instance;
        if (current != null) {
            current.update();
        }
    }

    /** A dimension was unloaded: forget its generators (they re-register when it loads again). */
    public static void removeLevel(ResourceKey<Level> dimension) {
        GeneratorNetworkManager current = instance;
        if (current == null) {
            return;
        }
        current.loaded.keySet().removeIf(pos -> {
            if (pos.dimension().equals(dimension)) {
                current.dirty.add(pos);
                return true;
            }
            return false;
        });
    }

    public static void clearAll() {
        instance = null;
    }

    /** Marks every network for rebuild (used after config changes). Thread-safe. */
    public static void markAllDirty() {
        configGeneration++;
    }

    // ------------------------------------------------------------------ membership

    public void add(MycelialGeneratorBlockEntity generator) {
        GlobalPos key = generator.getGlobalPos();
        MycelialGeneratorBlockEntity previous = loaded.put(key, generator);
        if (previous != generator) {
            dirty.add(key);
        }
    }

    public void remove(MycelialGeneratorBlockEntity generator) {
        GlobalPos key = generator.getGlobalPos();
        if (loaded.get(key) == generator) {
            loaded.remove(key);
            dirty.add(key);
        }
    }

    public boolean isTracked(MycelialGeneratorBlockEntity generator) {
        return loaded.get(generator.getGlobalPos()) == generator;
    }

    @Nullable
    public MycelialGeneratorBlockEntity getLoaded(GlobalPos pos) {
        return loaded.get(pos);
    }

    /** Marks a position (and through it its connections) for re-evaluation, e.g. after (un)linking. */
    public void markDirty(GlobalPos pos) {
        dirty.add(pos);
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
        GlobalPos key = generator.getGlobalPos();
        GeneratorNetwork network = networks.get(key);
        if (network == null) {
            // Defensive: unreachable while every tracked generator is assigned during update().
            fullRebuild = true;
            update();
            network = networks.get(key);
        }
        return network;
    }

    @Nullable
    public GeneratorNetwork peekNetwork(GlobalPos pos) {
        update();
        return networks.get(pos);
    }

    /** Whether the generator touches another loaded generator, and whether it has an active, loaded manual link. */
    public ConnectionType connectionType(MycelialGeneratorBlockEntity generator) {
        GlobalPos key = generator.getGlobalPos();
        boolean physical = false;
        for (Direction direction : DIRECTIONS) {
            if (loaded.containsKey(GlobalPos.of(key.dimension(), key.pos().relative(direction)))) {
                physical = true;
                break;
            }
        }
        boolean manual = false;
        for (GlobalPos partner : links().linksOf(key)) {
            if (loaded.containsKey(partner) && ManualLinking.isActive(key, partner)) {
                manual = true;
                break;
            }
        }
        return ConnectionType.of(physical, manual);
    }

    /** Number of this generator's manual links whose partner is loaded and that count under the current config. */
    public int activeManualLinks(MycelialGeneratorBlockEntity generator) {
        GlobalPos key = generator.getGlobalPos();
        int count = 0;
        for (GlobalPos partner : links().linksOf(key)) {
            if (loaded.containsKey(partner) && ManualLinking.isActive(key, partner)) {
                count++;
            }
        }
        return count;
    }

    private ManualLinkData links() {
        return ManualLinkData.get(server);
    }

    // ------------------------------------------------------------------ rebuilding

    /** Applies pending changes. Cheap when nothing changed. */
    public void update() {
        int generation = configGeneration;
        if (generation != seenConfigGeneration) {
            seenConfigGeneration = generation;
            fullRebuild = true;
        }
        Set<GlobalPos> seeds;
        if (fullRebuild) {
            fullRebuild = false;
            dirty.clear();
            for (GeneratorNetwork network : networks.values()) {
                network.invalidate();
            }
            networks.clear();
            seeds = new HashSet<>(loaded.keySet());
        } else if (!dirty.isEmpty()) {
            seeds = new HashSet<>();
            List<GlobalPos> changed = new ArrayList<>(dirty);
            dirty.clear();
            for (GlobalPos pos : changed) {
                collectAffected(pos, seeds);
                forEachConnection(pos, true, neighbour -> collectAffected(neighbour, seeds));
            }
        } else {
            return;
        }
        build(seeds);
    }

    /**
     * Visits the positions connected to {@code pos}: its six neighbours and, optionally, its manual link
     * partners whose link counts under the current config. Positions are visited whether or not loaded.
     */
    private void forEachConnection(GlobalPos pos, boolean includeManual, Consumer<GlobalPos> action) {
        for (Direction direction : DIRECTIONS) {
            action.accept(GlobalPos.of(pos.dimension(), pos.pos().relative(direction)));
        }
        if (includeManual) {
            for (GlobalPos partner : links().linksOf(pos)) {
                if (ManualLinking.isActive(pos, partner)) {
                    action.accept(partner);
                }
            }
        }
    }

    private void collectAffected(GlobalPos pos, Set<GlobalPos> seeds) {
        GeneratorNetwork network = networks.get(pos);
        if (network != null) {
            network.invalidate();
            for (MycelialGeneratorBlockEntity member : network.members()) {
                GlobalPos memberPos = member.getGlobalPos();
                networks.remove(memberPos);
                seeds.add(memberPos);
            }
            networks.remove(pos);
        }
        if (loaded.containsKey(pos)) {
            seeds.add(pos);
        }
    }

    private void build(Set<GlobalPos> seedSet) {
        List<GlobalPos> ordered = new ArrayList<>(seedSet);
        ordered.sort(ORDER);
        int maxSize = ServerConfig.get(ServerConfig.MAX_NETWORK_SIZE);
        boolean merge = ServerConfig.get(ServerConfig.MERGE_NETWORKS);

        if (!merge) {
            assignMissingIds(ordered);
        }

        Set<GlobalPos> assigned = new HashSet<>();
        ArrayDeque<GlobalPos> queue = new ArrayDeque<>();
        for (GlobalPos seed : ordered) {
            MycelialGeneratorBlockEntity start = loaded.get(seed);
            if (start == null || assigned.contains(seed) || networks.containsKey(seed)) {
                continue;
            }
            List<MycelialGeneratorBlockEntity> members = new ArrayList<>();
            boolean[] limited = {false};
            queue.clear();
            queue.add(seed);
            assigned.add(seed);
            while (!queue.isEmpty()) {
                GlobalPos current = queue.poll();
                MycelialGeneratorBlockEntity generator = loaded.get(current);
                members.add(generator);
                // Touching faces: with mergeNetworks = false they must also share the network id.
                for (Direction direction : DIRECTIONS) {
                    GlobalPos neighbour = GlobalPos.of(current.dimension(), current.pos().relative(direction));
                    MycelialGeneratorBlockEntity other = loaded.get(neighbour);
                    if (other != null && (merge || other.getNetworkId() == generator.getNetworkId())) {
                        enqueue(neighbour, members, queue, assigned, maxSize, limited);
                    }
                }
                // Manual links are an explicit player choice and always connect.
                for (GlobalPos partner : links().linksOf(current)) {
                    if (loaded.containsKey(partner) && ManualLinking.isActive(current, partner)) {
                        enqueue(partner, members, queue, assigned, maxSize, limited);
                    }
                }
            }

            long id = merge ? unifyIds(members) : start.getNetworkId();
            GeneratorNetwork network = new GeneratorNetwork(id, members, limited[0]);
            for (MycelialGeneratorBlockEntity member : members) {
                networks.put(member.getGlobalPos(), network);
            }
        }
    }

    private void enqueue(GlobalPos pos, List<MycelialGeneratorBlockEntity> members, ArrayDeque<GlobalPos> queue,
                         Set<GlobalPos> assigned, int maxSize, boolean[] limited) {
        if (assigned.contains(pos) || networks.containsKey(pos)) {
            return;
        }
        if (members.size() + queue.size() >= maxSize) {
            limited[0] = true;
            return;
        }
        assigned.add(pos);
        queue.add(pos);
    }

    /** With merging disabled, a new generator adopts the id of the largest touching network. */
    private void assignMissingIds(List<GlobalPos> ordered) {
        Map<Long, Integer> sizes = new HashMap<>();
        for (MycelialGeneratorBlockEntity generator : loaded.values()) {
            if (generator.getNetworkId() != 0L) {
                sizes.merge(generator.getNetworkId(), 1, Integer::sum);
            }
        }
        for (GlobalPos pos : ordered) {
            MycelialGeneratorBlockEntity generator = loaded.get(pos);
            if (generator == null || generator.getNetworkId() != 0L) {
                continue;
            }
            long best = 0L;
            int bestSize = -1;
            for (Direction direction : DIRECTIONS) {
                MycelialGeneratorBlockEntity other = loaded.get(GlobalPos.of(pos.dimension(), pos.pos().relative(direction)));
                if (other != null && other.getNetworkId() != 0L) {
                    int size = sizes.getOrDefault(other.getNetworkId(), 0);
                    if (size > bestSize) {
                        bestSize = size;
                        best = other.getNetworkId();
                    }
                }
            }
            long id = best != 0L ? best : newId();
            generator.setNetworkId(id);
            sizes.merge(id, 1, Integer::sum);
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

    /** Position helper for callers holding a level and block position. */
    public static GlobalPos key(Level level, BlockPos pos) {
        return GlobalPos.of(level.dimension(), pos);
    }
}
