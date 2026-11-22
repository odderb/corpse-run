package com.corpserun.client;

import com.corpserun.config.CorpseRunConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The mod's config menu, opened from the NeoForge mod list's Config button or from Mod Menu on Fabric: leads to the
 * client settings ({@link ClientSettingsScreen}) and the server settings ({@link ServerSettingsScreen}).
 * <p>
 * Server settings can't be edited while connected to someone else's server: this client's copy of
 * {@link CorpseRunConfig#FILE_NAME} only applies to singleplayer worlds and worlds this client hosts over LAN.
 */
public class CorpseRunConfigScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;

    @Nullable
    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public CorpseRunConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("corpserun.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);

        LinearLayout options = this.layout.addToContents(LinearLayout.vertical().spacing(8));
        Button client = options.addChild(Button.builder(Component.translatable("corpserun.config.client"),
                button -> this.minecraft.setScreen(new ClientSettingsScreen(this))).width(BUTTON_WIDTH).build());
        client.setTooltip(Tooltip.create(Component.translatable("corpserun.config.client.tooltip")));

        Button server = options.addChild(Button.builder(Component.translatable("corpserun.config.server"),
                button -> this.minecraft.setScreen(new ServerSettingsScreen(this))).width(BUTTON_WIDTH).build());
        boolean remote = this.minecraft.level != null && !this.minecraft.hasSingleplayerServer();
        server.active = !remote;
        server.setTooltip(Tooltip.create(Component.translatable(remote
                ? "corpserun.config.server.remote.tooltip"
                : "corpserun.config.server.tooltip")));

        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(BUTTON_WIDTH).build());

        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
