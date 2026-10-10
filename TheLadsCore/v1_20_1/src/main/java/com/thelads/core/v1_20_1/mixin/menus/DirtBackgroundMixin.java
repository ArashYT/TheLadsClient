package com.thelads.core.v1_20_1.mixin.menus;

import java.util.Map;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * No dirt anywhere: every bind of vanilla's dirt textures gets a Lads texture instead, so the menu background, the list
 * backgrounds and their top/bottom bars, the world-creation screen (its tab bar and separators too), Forge's scroll panels
 * (the Mods screen's description) and other mods' lists, however they draw it, show the Lads colours. Vanilla tints the dirt tile
 * dark itself (0.25, lists 0.125), so the Lads tile is brighter by that factor. In-world menus keep their translucent overlay:
 * it never used the tile.
 */
@Mixin(TextureManager.class)
public abstract class DirtBackgroundMixin {
    /** Vanilla texture path (namespace minecraft) to the Lads one, assets/theladscore/textures/gui/. */
    @Unique private static final Map<String, ResourceLocation> LADS_TEXTURES = Map.of(
        "textures/gui/options_background.png", new ResourceLocation("theladscore", "textures/gui/menu_background.png"),
        "textures/gui/light_dirt_background.png", new ResourceLocation("theladscore", "textures/gui/menu_background_light.png"),
        "textures/gui/tab_button.png", new ResourceLocation("theladscore", "textures/gui/tab_button.png"),
        "textures/gui/header_separator.png", new ResourceLocation("theladscore", "textures/gui/header_separator.png"),
        "textures/gui/footer_separator.png", new ResourceLocation("theladscore", "textures/gui/footer_separator.png"));

    @ModifyVariable(method = "getTexture(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/texture/AbstractTexture;",
        at = @At("HEAD"), argsOnly = true, require = 1)
    private ResourceLocation ladsNoDirt(ResourceLocation location) {
        if (location == null || !"minecraft".equals(location.getNamespace())) return location;
        return LADS_TEXTURES.getOrDefault(location.getPath(), location);
    }
}
