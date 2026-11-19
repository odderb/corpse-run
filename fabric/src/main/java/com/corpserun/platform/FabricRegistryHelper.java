package com.corpserun.platform;

import com.corpserun.Constants;
import com.corpserun.platform.services.IRegistryHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

import java.util.function.Supplier;

/** Fabric registers directly: the entry exists as soon as this returns. */
public class FabricRegistryHelper implements IRegistryHelper {

    @Override
    public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
        T value = Registry.register(registry(registry), Constants.id(name), factory.get());
        return () -> value;
    }

    @Override
    public <R, T extends R> Holder<R> registerForHolder(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
        return Registry.registerForHolder(registry(registry), Constants.id(name), factory.get());
    }

    @SuppressWarnings("unchecked")
    private static <R> Registry<R> registry(ResourceKey<? extends Registry<R>> key) {
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.get(key.location());
        if (registry == null) {
            throw new IllegalArgumentException("Unknown registry " + key.location());
        }
        return registry;
    }
}
