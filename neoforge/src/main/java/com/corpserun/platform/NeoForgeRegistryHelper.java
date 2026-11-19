package com.corpserun.platform;

import com.corpserun.Constants;
import com.corpserun.platform.services.IRegistryHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * NeoForge registers through one {@link DeferredRegister} per registry. Entries must be added before
 * {@link #attach} hands the registers to the mod event bus (CorpseRunNeoForge does this in its constructor).
 */
public class NeoForgeRegistryHelper implements IRegistryHelper {

    private static final Map<ResourceKey<?>, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();

    @Override
    public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
        return deferred(registry).register(name, factory);
    }

    @Override
    public <R, T extends R> Holder<R> registerForHolder(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
        return deferred(registry).register(name, factory);
    }

    /** Adds every register created so far to the mod event bus. */
    public static void attach(IEventBus modBus) {
        REGISTERS.values().forEach(register -> register.register(modBus));
    }

    @SuppressWarnings("unchecked")
    private static <R> DeferredRegister<R> deferred(ResourceKey<? extends Registry<R>> registry) {
        return (DeferredRegister<R>) REGISTERS.computeIfAbsent(registry, key -> DeferredRegister.create(registry, Constants.MOD_ID));
    }
}
