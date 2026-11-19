package com.corpserun;

import com.corpserun.client.EtherealVisibility;
import com.corpserun.client.GhostHud;
import com.corpserun.client.GhostScreenEffect;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Set;

/** Client game-bus events (CorpseRunNeoForgeClient holds the mod-bus ones). */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class CorpseRunNeoForgeClientEvents {

    private static final Set<ResourceLocation> GHOST_HIDDEN_LAYERS = Set.of(
            VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.AIR_LEVEL,
            VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.EXPERIENCE_LEVEL);

    private CorpseRunNeoForgeClientEvents() {
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        EtherealVisibility.update();
    }

    /** Before anything of the HUD is drawn: the ghost static goes over the finished world frame. */
    @SubscribeEvent
    public static void ghostScreenEffect(RenderGuiEvent.Pre event) {
        GhostScreenEffect.render(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void hideGhostHud(RenderGuiLayerEvent.Pre event) {
        if (GHOST_HIDDEN_LAYERS.contains(event.getName()) && GhostHud.hideGhostHud()) {
            event.setCanceled(true);
        }
    }
}
