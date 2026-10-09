package com.mycelialpower.util;

import com.mycelialpower.MycelialPower;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Matches registry entries against a config list of ids and {@code #tags}. Resolution is cached and
 * dropped on config reload or tag reload via {@link #invalidate()}.
 */
public final class ConfiguredMatcher<T> {
    private final Registry<T> registry;
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final Supplier<List<? extends String>> source;
    private volatile Resolved<T> resolved;

    public ConfiguredMatcher(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey, Supplier<List<? extends String>> source) {
        this.registry = registry;
        this.registryKey = registryKey;
        this.source = source;
    }

    public boolean test(Holder<T> holder) {
        Resolved<T> r = resolve();
        if (r.values.contains(holder.value())) {
            return true;
        }
        for (TagKey<T> tag : r.tags) {
            if (holder.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** Explicitly listed entries (not tags), used for display. */
    public Set<T> explicitValues() {
        return resolve().values;
    }

    public void invalidate() {
        resolved = null;
    }

    private Resolved<T> resolve() {
        Resolved<T> r = resolved;
        if (r != null) {
            return r;
        }
        Set<T> values = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<TagKey<T>> tags = new ArrayList<>();
        for (String raw : source.get()) {
            String entry = raw.trim();
            boolean isTag = entry.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(isTag ? entry.substring(1) : entry);
            if (id == null) {
                MycelialPower.LOGGER.warn("Ignoring invalid id '{}' in {} config list", raw, registryKey.location());
                continue;
            }
            if (isTag) {
                tags.add(TagKey.create(registryKey, id));
            } else {
                registry.getOptional(id).ifPresentOrElse(values::add,
                        () -> MycelialPower.LOGGER.warn("Ignoring unknown {} '{}' in config", registryKey.location(), id));
            }
        }
        r = new Resolved<>(Collections.unmodifiableSet(values), List.copyOf(tags));
        resolved = r;
        return r;
    }

    private record Resolved<T>(Set<T> values, List<TagKey<T>> tags) {
    }
}
