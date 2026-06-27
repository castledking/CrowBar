package codes.castled.crowbar.compat;

import codes.castled.crowbar.CrowBarConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class CrowBarModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CrowBarConfigScreen::new;
    }
}
