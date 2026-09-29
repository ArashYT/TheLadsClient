// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.util;

import com.thelads.core.v26_2.feature.raised.Raised;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

import java.util.concurrent.atomic.AtomicBoolean;

public class Pack {

    public static boolean pack = false;

    public static boolean getPack() {
        return pack;
    }

    public static void setPack(boolean pack) {
        Pack.pack = pack;
    }

    public static void checkResources() {
        AtomicBoolean exists = new AtomicBoolean(false);

        Minecraft.getInstance().getResourcePackRepository().openAllSelected().forEach(pack -> {
            if (!pack.packId().contentEquals(Raised.MOD_ID) && !pack.packId().contentEquals("theladscore")) {
                if (pack.getResource(PackType.CLIENT_RESOURCES, Identifier.fromNamespaceAndPath(Raised.MOD_ID, "textures/gui/sprites/hud/hotbar_selection.png")) != null) {
                    exists.set(true);
                }
            }
            pack.close();
        });

        setPack(exists.get());
    }

}
