package com.corpserun.platform;

import com.corpserun.Constants;
import com.corpserun.platform.services.IPlatformHelper;
import com.corpserun.platform.services.IRegistryHelper;

import java.util.ServiceLoader;

/**
 * Loader-specific implementations for the common code, found with {@link ServiceLoader}. Each loader module lists
 * its implementations in {@code META-INF/services}.
 */
public final class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final IRegistryHelper REGISTRY = load(IRegistryHelper.class);

    private Services() {
    }

    private static <T> T load(Class<T> clazz) {
        T service = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of " + clazz.getName() + " found"));
        Constants.LOG.debug("Loaded {} for service {}", service, clazz);
        return service;
    }
}
