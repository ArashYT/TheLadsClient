package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/** 3D outer skin layers on every player, drawn by the bundled 3D Skin Layers mod; the adapters switch its player layers with this module. */
public class SkinLayersModule extends Module {
    public static final String NAME = "SkinLayers";

    public SkinLayersModule() {
        super(NAME, "Shows the outer layer of every player's skin (hat, jacket, sleeves and trousers) in 3D.");
        setEnabled(true);
        addOption(new com.thelads.core.config.BoolOption("3D Preview",true));
    }

    /** Players get 3D layers unless this module is switched off. */
    public static boolean layersOn() {
        Module module = ModuleManager.getInstance().getModule(NAME);
        return module == null || module.isEnabled();
    }
}
