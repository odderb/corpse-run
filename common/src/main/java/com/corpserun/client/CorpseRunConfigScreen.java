package com.corpserun.client;

import com.corpserun.config.CorpseRunClientConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The client settings screen, opened from the NeoForge mod list's Config button or from Mod Menu on Fabric. Changes
 * are kept on the screen until Done, which saves them to {@link CorpseRunClientConfig#FILE_NAME} and applies them
 * right away; Cancel (or Escape) drops them.
 */
public class CorpseRunConfigScreen extends Screen {

    private static final int WIDGET_WIDTH = 310;
    private static final int BUTTON_WIDTH = 150;

    @Nullable
    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    private boolean screenEffect = CorpseRunClientConfig.ghostScreenEffect();
    private boolean hideHud = CorpseRunClientConfig.ghostHideHud();
    private float nightVision = CorpseRunClientConfig.ghostNightVision();

    public CorpseRunConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("corpserun.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);

        LinearLayout options = this.layout.addToContents(LinearLayout.vertical().spacing(8));
        options.addChild(CycleButton.onOffBuilder(this.screenEffect)
                .withTooltip(value -> Tooltip.create(Component.translatable("corpserun.config.ghostScreenEffect.tooltip")))
                .create(0, 0, WIDGET_WIDTH, Button.DEFAULT_HEIGHT, Component.translatable("corpserun.config.ghostScreenEffect"),
                        (button, value) -> this.screenEffect = value));
        options.addChild(CycleButton.onOffBuilder(this.hideHud)
                .withTooltip(value -> Tooltip.create(Component.translatable("corpserun.config.ghostHideHud.tooltip")))
                .create(0, 0, WIDGET_WIDTH, Button.DEFAULT_HEIGHT, Component.translatable("corpserun.config.ghostHideHud"),
                        (button, value) -> this.hideHud = value));
        NightVisionSlider slider = new NightVisionSlider();
        slider.setTooltip(Tooltip.create(Component.translatable("corpserun.config.ghostNightVision.tooltip")));
        options.addChild(slider);

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            CorpseRunClientConfig.update(this.screenEffect, this.hideHud, this.nightVision);
            this.onClose();
        }).width(BUTTON_WIDTH).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(BUTTON_WIDTH).build());

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

    /** Night vision strength in 5% steps, with 0 shown as Off. */
    private final class NightVisionSlider extends AbstractSliderButton {

        NightVisionSlider() {
            super(0, 0, WIDGET_WIDTH, Button.DEFAULT_HEIGHT, CommonComponents.EMPTY, CorpseRunConfigScreen.this.nightVision);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            Component value = this.value <= 0.0
                    ? CommonComponents.OPTION_OFF
                    : Component.translatable("corpserun.config.percent", Math.round(this.value * 100));
            this.setMessage(Component.translatable("corpserun.config.ghostNightVision", value));
        }

        @Override
        protected void applyValue() {
            // Snap to 5% so the saved value is a tidy number.
            this.value = Mth.clamp(Math.round(this.value * 20) / 20.0, 0.0, 1.0);
            CorpseRunConfigScreen.this.nightVision = (float) this.value;
        }
    }
}
