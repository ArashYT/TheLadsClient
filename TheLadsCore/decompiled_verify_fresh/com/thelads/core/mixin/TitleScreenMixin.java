/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.screens.TitleScreen
 *  net.minecraft.network.chat.Component
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.thelads.core.client.LadsProfileSync;
import com.thelads.core.client.auth.AccountSwitcherScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={TitleScreen.class})
public abstract class TitleScreenMixin
extends Screen {
    @Unique
    private static final int CARD_W = 130;
    @Unique
    private static final int CARD_H = 46;
    @Unique
    private static final int CARD_MARGIN = 10;
    @Unique
    private static final int ACCENT = -9673729;

    protected TitleScreenMixin() {
        super((Component)Component.empty());
    }

    @Inject(method={"init"}, at={@At(value="TAIL")}, require=0)
    private void ladsAddAccountCard(CallbackInfo ci) {
        int cardY = this.height - 46 - 10 - 22;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u21c4 Switch Account"), btn -> Minecraft.getInstance().setScreen((Screen)new AccountSwitcherScreen(this))).bounds(10, cardY, 130, 20).build());
    }

    @Inject(method={"extractRenderState"}, at={@At(value="HEAD")}, require=0)
    private void ladsRenderAccountCard(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int cardX = 10;
        int cardY = this.height - 46 - 10;
        g.fill(cardX, cardY, cardX + 130, cardY + 46, -871559910);
        g.fill(cardX, cardY, cardX + 130, cardY + 1, -9673729);
        String username = Minecraft.getInstance().getUser().getName();
        String type = "offline";
        LadsProfileSync.LadsProfile profile = LadsProfileSync.getCached();
        if (profile != null) {
            if (profile.username() != null) {
                username = profile.username();
            }
            if (profile.type() != null) {
                type = profile.type();
            }
        }
        boolean ms = "microsoft".equalsIgnoreCase(type);
        int headSize = 22;
        int headX = cardX + 8;
        int headY = cardY + (46 - headSize) / 2;
        g.fill(headX, headY, headX + headSize, headY + headSize, -14016448);
        g.fill(headX, headY, headX + headSize, headY + 1, -9673729);
        g.fill(headX, headY + headSize - 1, headX + headSize, headY + headSize, -9673729);
        int textX = headX + headSize + 7;
        g.text(this.font, (Component)Component.literal((String)TitleScreenMixin.truncate(username, 12)), textX, cardY + 10, ms ? -5201665 : -5592406, false);
        g.text(this.font, (Component)Component.literal((String)(ms ? "Microsoft" : "Offline")), textX, cardY + 22, ms ? -9673729 : -10066296, false);
    }

    @Unique
    private static String truncate(String s, int max) {
        if (s == null) {
            return "Unknown";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }
}

