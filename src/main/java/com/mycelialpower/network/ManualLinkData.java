package com.mycelialpower.network;

import com.mycelialpower.MycelialPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Every manual (Mycelium Wrench) link of the world, stored once in the overworld's saved data
 * ({@code data/mycelialpower_generator_links.dat}).
 * <p>
 * Keeping links in one world-level store instead of in each block entity means a destroyed generator's links
 * can be removed from both ends without loading the partner's chunk, and links to unloaded generators are
 * preserved untouched. Links are undirected and stored without duplicates.
 */
public final class ManualLinkData extends SavedData {
    public static final String NAME = "mycelialpower_generator_links";
    private static final Factory<ManualLinkData> FACTORY = new Factory<>(ManualLinkData::new, ManualLinkData::load, null);

    private final Map<GlobalPos, Set<GlobalPos>> links = new HashMap<>();

    public static ManualLinkData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Set<GlobalPos> linksOf(GlobalPos pos) {
        Set<GlobalPos> set = links.get(pos);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    public int count(GlobalPos pos) {
        Set<GlobalPos> set = links.get(pos);
        return set == null ? 0 : set.size();
    }

    public boolean isLinked(GlobalPos a, GlobalPos b) {
        Set<GlobalPos> set = links.get(a);
        return set != null && set.contains(b);
    }

    /** Adds an undirected link. Returns false for self-links and duplicates. */
    public boolean add(GlobalPos a, GlobalPos b) {
        if (a.equals(b) || isLinked(a, b)) {
            return false;
        }
        links.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        links.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        setDirty();
        return true;
    }

    public boolean remove(GlobalPos a, GlobalPos b) {
        boolean removed = removeHalf(a, b);
        removed |= removeHalf(b, a);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** Removes every link of {@code pos} and returns the former partners. */
    public Set<GlobalPos> removeAll(GlobalPos pos) {
        Set<GlobalPos> partners = links.remove(pos);
        if (partners == null || partners.isEmpty()) {
            return Collections.emptySet();
        }
        for (GlobalPos partner : partners) {
            removeHalf(partner, pos);
        }
        setDirty();
        return partners;
    }

    private boolean removeHalf(GlobalPos from, GlobalPos to) {
        Set<GlobalPos> set = links.get(from);
        if (set == null || !set.remove(to)) {
            return false;
        }
        if (set.isEmpty()) {
            links.remove(from);
        }
        return true;
    }

    public int totalLinks() {
        int sum = 0;
        for (Set<GlobalPos> set : links.values()) {
            sum += set.size();
        }
        return sum / 2;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<GlobalPos, Set<GlobalPos>> entry : links.entrySet()) {
            GlobalPos a = entry.getKey();
            for (GlobalPos b : entry.getValue()) {
                if (compare(a, b) < 0) { // each undirected link once
                    CompoundTag link = new CompoundTag();
                    link.put("A", write(a));
                    link.put("B", write(b));
                    list.add(link);
                }
            }
        }
        tag.put("Links", list);
        return tag;
    }

    public static ManualLinkData load(CompoundTag tag, HolderLookup.Provider registries) {
        ManualLinkData data = new ManualLinkData();
        ListTag list = tag.getList("Links", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag link = list.getCompound(i);
            GlobalPos a = read(link.getCompound("A"));
            GlobalPos b = read(link.getCompound("B"));
            if (a != null && b != null && !a.equals(b)) {
                data.links.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                data.links.computeIfAbsent(b, k -> new HashSet<>()).add(a);
            }
        }
        return data;
    }

    private static int compare(GlobalPos a, GlobalPos b) {
        int dim = a.dimension().location().compareTo(b.dimension().location());
        return dim != 0 ? dim : Long.compare(a.pos().asLong(), b.pos().asLong());
    }

    private static CompoundTag write(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", pos.dimension().location().toString());
        tag.putLong("Pos", pos.pos().asLong());
        return tag;
    }

    @Nullable
    private static GlobalPos read(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null || !tag.contains("Pos")) {
            MycelialPower.LOGGER.warn("Skipping malformed generator link entry {}", tag);
            return null;
        }
        ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        return GlobalPos.of(key, BlockPos.of(tag.getLong("Pos")));
    }
}
