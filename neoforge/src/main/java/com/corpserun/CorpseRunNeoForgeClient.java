package com.corpserun;

import com.corpserun.client.CorpseRenderer;
import com.corpserun.client.CorpseRunConfigScreen;
import com.corpserun.client.FootprintParticle;
import com.corpserun.client.SingingParticle;
import com.corpserun.config.CorpseRunClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client mod-bus events (CorpseRunNeoForgeClientEvents holds the game-bus ones). */
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CorpseRunNeoForgeClient {

    private CorpseRunNeoForgeClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        CorpseRunClientConfig.load();
        // Enables the Config button in the mod list.
        ModList.get().getModContainerById(Constants.MOD_ID).ifPresent(container -> container.registerExtensionPoint(
                IConfigScreenFactory.class, (modContainer, parent) -> new CorpseRunConfigScreen(parent)));
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
