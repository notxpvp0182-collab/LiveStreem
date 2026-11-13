package dev.ahmed.liquidglass.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.ahmed.liquidglass.ui.clickgui.LiquidClickGuiScreen;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new LiquidClickGuiScreen();
    }
}
