package com.corpserun;

import com.corpserun.command.CorpseCommands;
import com.corpserun.ghost.GhostCall;
import com.corpserun.ghost.GhostCallPayload;
import com.corpserun.platform.NeoForgeRegistryHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(Constants.MOD_ID)
public class CorpseRunNeoForge {

    public CorpseRunNeoForge(IEventBus modBus) {
        // Queues the content in NeoForgeRegistryHelper's deferred registers and loads the server config, then hands
        // the registers to the mod bus.
        CorpseRun.init();
        NeoForgeRegistryHelper.attach(modBus);

        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").playToServer(
                GhostCallPayload.TYPE, GhostCallPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        GhostCall.handle(player);
                    }
                }));

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(RegisterCommandsEvent.class, event -> CorpseCommands.register(event.getDispatcher()));
        bus.addListener(ServerTickEvent.Post.class, event -> CorpseRunEvents.onServerTick(event.getServer()));
        bus.addListener(ServerStoppingEvent.class, event -> CorpseRunEvents.onServerStopping(event.getServer()));
    }
}
