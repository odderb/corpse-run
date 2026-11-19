package com.corpserun.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.file.Path;

/** Loader-specific helpers used by the common code. */
public interface IPlatformHelper {

    /** The loader's config directory. */
    Path getConfigDir();

    /** Sends a payload (registered by the loader module) from the client to the server. Client side only. */
    void sendToServer(CustomPacketPayload payload);
}
