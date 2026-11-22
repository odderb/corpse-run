package com.corpserun.client;

import com.corpserun.config.ConfigSetting;
import com.corpserun.config.CorpseRunConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The server settings screen: every setting of {@link CorpseRunConfig#FILE_NAME} in a scrolling list, a name on the
 * left and an on/off button or number field on the right. Changes are kept on the screen until Done, which saves them
 * and applies them right away (on the integrated server's thread when a world is open, since it reads them as it
 * ticks); Cancel (or Escape) drops them.
 */
public class ServerSettingsScreen extends OptionsSubScreen {

    private static final int COLUMN_WIDTH = 150;
    private static final int ROW_HEIGHT = 20;
    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d{0,9}");
    private static final Pattern DECIMAL_NUMBER = Pattern.compile("\\d{0,9}(\\.\\d{0,6})?");

    /** Read once, so resizing (which rebuilds the controls) doesn't re-read the file. */
    private final List<ConfigSetting> settings = CorpseRunConfig.settings();
    /** Values changed on this screen but not saved yet, by key, as text; kept across resizes. */
    private final Map<String, String> pending = new LinkedHashMap<>();

    public ServerSettingsScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("corpserun.config.server.title"));
    }

    @Override
    protected void addOptions() {
        for (ConfigSetting setting : this.settings) {
            Component name = Component.translatableWithFallback("corpserun.config.server." + setting.key(), setting.key());
            Tooltip tooltip = Tooltip.create(Component.translatableWithFallback(
                            "corpserun.config.server." + setting.key() + ".tooltip", setting.comment())
                    .append("\n\n")
                    .append(Component.translatable("corpserun.config.default", setting.defaultValue())));

            StringWidget label = new StringWidget(COLUMN_WIDTH, ROW_HEIGHT, name, this.font).alignLeft();
            label.setTooltip(tooltip);
            AbstractWidget control = control(setting, name);
            control.setTooltip(tooltip);
            this.list.addSmall(label, control);
        }
    }

    private AbstractWidget control(ConfigSetting setting, Component name) {
        String key = setting.key();
        String value = this.pending.getOrDefault(key, setting.value());
        if (setting.kind() == ConfigSetting.Kind.BOOLEAN) {
            return CycleButton.onOffBuilder(Boolean.parseBoolean(value))
                    .displayOnlyValue()
                    .create(0, 0, COLUMN_WIDTH, ROW_HEIGHT, name,
                            (button, on) -> this.pending.put(key, String.valueOf(on)));
        }
        Pattern format = setting.kind() == ConfigSetting.Kind.INT ? WHOLE_NUMBER : DECIMAL_NUMBER;
        EditBox box = new EditBox(this.font, 0, 0, COLUMN_WIDTH, ROW_HEIGHT, name);
        box.setFilter(text -> format.matcher(text).matches());
        box.setValue(plain(value));
        box.setResponder(text -> {
            if (text.isEmpty()) {
                this.pending.remove(key);   // an emptied field keeps the value it had
            } else {
                this.pending.put(key, text);
            }
        });
        return box;
    }

    /** Whole numbers without the ".0" a double is written with, so they're easy to edit. */
    private static String plain(String value) {
        return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            save();
            this.onClose();
        }).width(COLUMN_WIDTH).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(COLUMN_WIDTH).build());
    }

    private void save() {
        Map<String, String> values = Map.copyOf(this.pending);
        if (values.isEmpty()) {
            return;
        }
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> CorpseRunConfig.update(values));
        } else {
            CorpseRunConfig.update(values);
        }
    }
}
