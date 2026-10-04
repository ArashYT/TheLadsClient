package com.thelads.core.v1_8_9.feature;

import com.thelads.core.modules.SkinLayersModule;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;

/** The SkinLayers module on 1.8.9, over the bundled 3D Skin Layers (see SkinLayersGateMixin189 and RenderPlayerMixin). */
public final class SkinLayers189 {
    public static final boolean LOADED = Loader.isModLoaded("skinlayers3d");
    private static boolean wasOn = true;
    private SkinLayers189() {}

    /** Vanilla's flat layers must be shown: the mod is there and the module is off. */
    public static boolean flatLayers() { return LOADED && !SkinLayersModule.layersOn(); }

    /** Each client tick: switching the module off drops the 3D layers already made; switched on, the mod makes them as players render. */
    public static void tick(Minecraft mc) {
        boolean on = SkinLayersModule.layersOn();
        if (on == wasOn) return;
        wasOn = on;
        if (!on && LOADED && mc.theWorld != null) for (EntityPlayer player : mc.theWorld.playerEntities) refresh(player);
    }

    /** The mod's own refresh (as after a skin change): the player's 3D layers are made again when next needed. */
    static void refresh(EntityPlayer player) {
        if (!LOADED) return;
        try {
            Object mod = Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase").getField("instance").get(null);
            mod.getClass().getMethod("refreshLayers", EntityPlayer.class).invoke(mod, player);
        } catch (ReflectiveOperationException | LinkageError failure) {
            LogManager.getLogger("TheLadsCore").warn("Could not refresh 3D skin layers", failure);
        }
    }
}
