package com.mycelialpower.fuel;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.config.ServerConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the configured fuel entries into item lookups. The single source of truth for both the
 * generator's gameplay logic and the JEI display. Rebuilt lazily whenever the config or tags change.
 */
public final class FuelRegistry {
    private static volatile Snapshot snapshot;

    private FuelRegistry() {
    }

    /** Drops the cached resolution; the next lookup re-reads the config and tags. */
    public static void invalidate() {
        snapshot = null;
    }

    /** The definition for an item stack, or {@code null} if it is not a fuel. */
    @Nullable
    public static FuelDefinition get(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Snapshot s = snapshot();
        FuelDefinition exact = s.exact.get(stack.getItem());
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<TagKey<Item>, FuelDefinition> entry : s.tags) {
            if (stack.is(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static boolean isFuel(ItemStack stack) {
        return get(stack) != null;
    }

    /** All valid definitions in config order. */
    public static List<FuelDefinition> definitions() {
        return snapshot().definitions;
    }

    /**
     * The items matched by a definition. Item entries that are shadowed by an earlier exact entry are
     * excluded so every item appears under exactly the definition the generator would use.
     */
    public static List<ItemStack> itemsFor(FuelDefinition definition) {
        List<ItemStack> result = new ArrayList<>();
        for (Item item : candidateItems(definition)) {
            if (item == Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (definition.equals(get(stack))) {
                result.add(stack);
            }
        }
        return result;
    }

    /** Re-resolves an item by registry id, for restoring saved fuel state. */
    public static Optional<FuelDefinition> byItemId(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(rl).map(item -> get(new ItemStack(item)));
    }

    private static Set<Item> candidateItems(FuelDefinition definition) {
        Set<Item> items = new LinkedHashSet<>();
        ResourceLocation rl = ResourceLocation.tryParse(definition.id());
        if (rl == null) {
            return items;
        }
        if (definition.tag()) {
            TagKey<Item> key = TagKey.create(Registries.ITEM, rl);
            BuiltInRegistries.ITEM.getTag(key).ifPresent(set -> {
                for (Holder<Item> holder : set) {
                    items.add(holder.value());
                }
            });
        } else {
            BuiltInRegistries.ITEM.getOptional(rl).ifPresent(items::add);
        }
        return items;
    }

    private static Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s == null) {
            s = build();
            snapshot = s;
        }
        return s;
    }

    private static Snapshot build() {
        List<? extends String> entries = ServerConfig.get(ServerConfig.FUELS);
        Map<Item, FuelDefinition> exact = new IdentityHashMap<>();
        List<Map.Entry<TagKey<Item>, FuelDefinition>> tags = new ArrayList<>();
        List<FuelDefinition> definitions = new ArrayList<>();
        for (String entry : entries) {
            FuelDefinition def;
            try {
                def = FuelEntryParser.parseOrThrow(entry);
            } catch (IllegalArgumentException e) {
                MycelialPower.LOGGER.warn("Ignoring invalid mushroom fuel entry: {}", e.getMessage());
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(def.id());
            if (rl == null) {
                continue;
            }
            if (def.tag()) {
                tags.add(Map.entry(TagKey.create(Registries.ITEM, rl), def));
                definitions.add(def);
            } else {
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(rl);
                if (item.isEmpty() || item.get() == Items.AIR) {
                    MycelialPower.LOGGER.warn("Ignoring mushroom fuel entry for unknown item '{}'", def.id());
                    continue;
                }
                if (exact.putIfAbsent(item.get(), def) != null) {
                    MycelialPower.LOGGER.warn("Duplicate mushroom fuel entry for '{}'; the first entry is used", def.id());
                    continue;
                }
                definitions.add(def);
            }
        }
        return new Snapshot(exact, Collections.unmodifiableList(tags), Collections.unmodifiableList(definitions));
    }

    private record Snapshot(Map<Item, FuelDefinition> exact,
                            List<Map.Entry<TagKey<Item>, FuelDefinition>> tags,
                            List<FuelDefinition> definitions) {
    }
}
