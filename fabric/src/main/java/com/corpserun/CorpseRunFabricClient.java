package com.corpserun;

import com.corpserun.client.CorpseRenderer;
import com.corpserun.client.CorpseHighlight;
import com.corpserun.client.EtherealVisibility;
import com.corpserun.client.FootprintParticle;
import com.corpserun.client.SingingParticle;
import com.corpserun.config.CorpseRunClientConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.RenderType;

/** Client setup. The ghost screen effect and HUD hiding are hooked in GuiMixin (Fabric has no pre-HUD event). */
public class CorpseRunFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CorpseRunClientConfig.load();

        EntityRendererRegistry.register(CorpseRun.CORPSE.get(), CorpseRenderer::new);
        ParticleFactoryRegistry.getInstance().register(CorpseRun.FOOTPRINT_HEEL.get(), sprites -> new FootprintParticle.Provider(sprites, FootprintParticle.HEEL_SIZE));
        ParticleFactoryRegistry.getInstance().register(CorpseRun.FOOTPRINT_TOE.get(), sprites -> new FootprintParticle.Provider(sprites, FootprintParticle.TOE_SIZE));
        ParticleFactoryRegistry.getInstance().register(CorpseRun.SINGING.get(), SingingParticle.Provider::new);
        // NeoForge reads render_type from the block model; Fabric needs it set here.
        BlockRenderLayerMap.INSTANCE.putBlock(CorpseRun.ETHEREAL_BLOCK.get(), RenderType.translucent());

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            EtherealVisibility.update();
            CorpseHighlight.update();
        });
    }
}
