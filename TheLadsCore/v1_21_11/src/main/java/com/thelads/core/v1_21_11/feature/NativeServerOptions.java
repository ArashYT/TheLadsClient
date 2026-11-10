package com.thelads.core.v1_21_11.feature;

import com.thelads.core.client.ladsapi.ServerSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Vanilla options a LadsClientAPI server may set or limit (ServerSettings "Minecraft"), asked by OptionInstance.get (ServerOptionMixin). */
public final class NativeServerOptions {
    /** Options.save() is writing options.txt: it gets the player's own values (ServerOptionsSaveMixin). */
    public static volatile boolean saving;

    private NativeServerOptions() {}

    /** The value the server's rules give this option, or null to leave it alone (saving, or not an option a server may touch). */
    public static Object effective(Object option, Object own) {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc == null ? null : mc.options;
        if (o == null || saving) return null;
        if (option == o.fov()) return (int) Math.round(ServerSettings.vanilla("FOV", (Integer) own));
        if (option == o.gamma()) {
            double percent = (Double) own * 100.0, effective = ServerSettings.vanilla("Brightness", percent);
            return effective == percent ? null : effective / 100.0; // untouched: the exact own value, not one the maths rounded
        }
        if (option == o.renderDistance()) return (int) Math.round(ServerSettings.vanilla("Render Distance", (Integer) own));
        if (option == o.bobView()) return ServerSettings.vanilla("View Bobbing", (Boolean) own);
        if (option == o.entityShadows()) return ServerSettings.vanilla("Entity Shadows", (Boolean) own);
        return null;
    }
}