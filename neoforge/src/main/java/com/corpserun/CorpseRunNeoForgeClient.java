package com.corpserun;

import com.corpserun.client.CorpseRenderer;
import com.corpserun.client.FootprintParticle;
import com.corpserun.client.SingingParticle;
import com.corpserun.config.CorpseRunClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client mod-bus events (CorpseRunNeoForgeClientEvents holds the game-bus ones). */
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CorpseRunNeoForgeClient {

    private CorpseRunNeoForgeClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        CorpseRunClientConfig.load();
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CorpseRun.CORPSE.get(), CorpseRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CorpseRun.FOOTPRINT_HEEL.get(), sprites -> new FootprintParticle.Provider(sprites, FootprintParticle.HEEL_SIZE));
        event.registerSpriteSet(CorpseRun.FOOTPRINT_TOE.get(), sprites -> new FootprintParticle.Provider(sprites, FootprintParticle.TOE_SIZE));
        event.registerSpriteSet(CorpseRun.SINGING.get(), SingingParticle.Provider::new);
    }
}
