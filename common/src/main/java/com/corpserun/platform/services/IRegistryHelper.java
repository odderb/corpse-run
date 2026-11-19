package com.corpserun.platform.services;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.function.Supplier;

/**
 * Registers the mod's content with the loader's registry system. Every entry is created under the mod's namespace.
 * The returned suppliers and holders are never null, so they can be stored in static final fields; on NeoForge
 * they resolve once the registries have been filled, on Fabric immediately.
 */
public interface IRegistryHelper {

    <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory);

    /** For registries whose entries are passed around as holders (mob effects). */
    <R, T extends R> Holder<R> registerForHolder(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory);
}
