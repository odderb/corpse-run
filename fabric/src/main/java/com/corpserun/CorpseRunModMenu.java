package com.corpserun;

import com.corpserun.client.CorpseRunConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's config button. Only loaded by Mod Menu itself, so CorpseRun runs fine without it. */
public class CorpseRunModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CorpseRunConfigScreen::new;
    }
}
