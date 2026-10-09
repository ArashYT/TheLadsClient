// Ported from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.capes.menu;

import com.thelads.core.v1_20_1.embedded.capes.Capes;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Options;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.math.BigInteger;
import java.util.Random;

public class OtherMenu extends MainMenu {

    public OtherMenu(Screen parent, Options gameOptions) {
        super(parent, gameOptions);
    }

    @Override
    protected void init() {
        super.init();

        int buttonW = 200;

        addRenderableWidget(Button.builder(Component.translatable("options.capes.optifineeditor"), button -> {
            try {
                BigInteger random1Bi = new BigInteger(128, new Random());
                BigInteger random2Bi = new BigInteger(128, new Random(System.identityHashCode(new Object())));
                String serverId = random1Bi.xor(random2Bi).toString(16);
                minecraft.getMinecraftSessionService().joinServer(minecraft.getUser().getGameProfile(), minecraft.getUser().getAccessToken(), serverId);
                String url = "https://optifine.net/capeChange?u=" + minecraft.getUser().getProfileId().toString().replace("-", "") + "&n=" + minecraft.getUser().getName() + "&s=" + serverId;
                minecraft.setScreen(new ConfirmLinkScreen(bool -> {
                    if (bool) {
                        Util.getPlatform().openUri(url);
                    }
                    minecraft.setScreen(this);
                }, url, true));
            } catch (Exception e) {
                Capes.LOGGER.error("Failed to authenticate for OptiFine cape editor.");
            }

        }).pos((width / 2) - (buttonW / 2), height / 7 + 24).size(buttonW, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            minecraft.setScreen(lastScreen);
        }).pos((width / 2) - (buttonW / 2), height / 7 + 2 * 24).size(buttonW, 20).build());

    }

}
