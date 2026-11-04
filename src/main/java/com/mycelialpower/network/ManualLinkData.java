package com.mycelialpower.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mycelialpower.MycelialPower;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every manual (Mycelium Wrench) link of the world, stored once in the server's global saved data
 * ({@code data/mycelialpower/generator_links.dat}).
 * <p>
 * Keeping links in one world-level store instead of in each block entity means a destroyed generator's links
 * can be removed from both ends without loading the partner's chunk, and links to unloaded generators are
 * preserved untouched. Links are undirected and stored without duplicates.
 */
public final class ManualLinkData extends SavedData {
    /** One undirected link, as stored on disk. */
    private record Link(GlobalPos a, GlobalPos b) {
        static final Codec<Link> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                GlobalPos.CODEC.fieldOf("a").forGetter(Link::a),
                GlobalPos.CODEC.fieldOf("b").forGetter(Link::b)
        ).apply(instance, Link::new));
    }

    public static final Codec<ManualLinkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Link.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(ManualLinkData::toLinkList)
    ).apply(instance, ManualLinkData::fromLinkList));

    public static final SavedDataType<ManualLinkData> TYPE = new SavedDataType<>(
            MycelialPower.id("generator_links"), ManualLinkData::new, CODEC, null);

    private final Map<GlobalPos, Set<GlobalPos>> links = new HashMap<>();

    public ManualLinkData() {
    }

    /** Global (not per-dimension) storage, because links can span dimensions. */
    public static ManualLinkData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
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

    /** Each undirected link exactly once. */
    private List<Link> toLinkList() {
        List<Link> result = new ArrayList<>();
        for (Map.Entry<GlobalPos, Set<GlobalPos>> entry : links.entrySet()) {
            GlobalPos a = entry.getKey();
            for (GlobalPos b : entry.getValue()) {
                if (compare(a, b) < 0) {
                    result.add(new Link(a, b));
                }
            }
        }
        return result;
    }

    private static ManualLinkData fromLinkList(List<Link> list) {
        ManualLinkData data = new ManualLinkData();
        for (Link link : list) {
            if (!link.a().equals(link.b())) {
                data.links.computeIfAbsent(link.a(), k -> new HashSet<>()).add(link.b());
                data.links.computeIfAbsent(link.b(), k -> new HashSet<>()).add(link.a());
            }
        }
        return data;
    }

    private static int compare(GlobalPos a, GlobalPos b) {
        int dim = a.dimension().identifier().compareTo(b.dimension().identifier());
        return dim != 0 ? dim : Long.compare(a.pos().asLong(), b.pos().asLong());
    }
}
