package com.corpserun;

import com.corpserun.command.CorpseCommands;
import com.corpserun.ghost.GhostCall;
import com.corpserun.ghost.GhostCallPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class CorpseRunFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Registers the content (FabricRegistryHelper) and loads the server config.
        CorpseRun.init();

        PayloadTypeRegistry.playC2S().register(GhostCallPayload.TYPE, GhostCallPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(GhostCallPayload.TYPE, (payload, context) -> GhostCall.handle(context.player()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CorpseCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(CorpseRunEvents::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(CorpseRunEvents::onServerStopping);
    }
}
