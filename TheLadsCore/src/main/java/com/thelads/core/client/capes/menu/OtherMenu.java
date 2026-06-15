package com.thelads.core.client.capes.menu;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import java.math.BigInteger;
import java.util.Random;
import java.util.UUID;
import com.thelads.core.client.capes.Capes;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class OtherMenu extends MainMenu {
    public OtherMenu(Screen parent, Options gameOptions) {
        super(parent, gameOptions);
    }

    @Override
    protected void init() {
        super.init();
        int buttonW = 200;

        this.addRenderableWidget(Button.builder(Component.translatable("options.capes.optifineeditor"), button -> {
            try {
                BigInteger random1Bi = new BigInteger(128, new Random());
                BigInteger random2Bi = new BigInteger(128, new Random(System.identityHashCode(new Object())));
                String serverId = random1Bi.xor(random2Bi).toString(16);
                if (this.minecraft != null) {
                    MinecraftSessionService sessionService = this.minecraft.services().sessionService();
                    UUID uuid = this.minecraft.getGameProfile().id();
                    sessionService.joinServer(uuid, this.minecraft.getUser().getAccessToken(), serverId);
                    String uuidStr = uuid.toString().replace("-", "");
                    String url = "https://optifine.net/capeChange?u=" + uuidStr + "&n=" + this.minecraft.getUser().getName() + "&s=" + serverId;
                    this.minecraft.setScreen(new ConfirmLinkScreen(bool -> {
                        if (bool) {
                            Util.getPlatform().openUri(url);
                        }
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(this);
                        }
                    }, url, true));
                }
            } catch (Exception exception) {
                Capes.INSTANCE.getLOGGER().error("Failed to authenticate for OptiFine cape editor.", exception);
            }
        }).pos(this.width / 2 - buttonW / 2, this.height / 7 + 24).size(buttonW, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.lastScreen);
            }
        }).pos(this.width / 2 - buttonW / 2, this.height / 7 + 48).size(buttonW, 20).build());
    }
}
