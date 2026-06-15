/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.User
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.network.chat.Component
 */
package com.thelads.core.client.auth;

import com.thelads.core.client.auth.MicrosoftAuthenticator;
import com.thelads.core.mixin.MinecraftClientAccessor;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AccountSwitcherScreen
extends Screen {
    private final Screen parent;
    private String statusMessage = "Select an option below";
    private final MicrosoftAuthenticator auth = new MicrosoftAuthenticator();

    public AccountSwitcherScreen(Screen parent) {
        super((Component)Component.literal((String)"Account Switcher"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int startY = this.height / 2 - 30;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Login with Microsoft"), btn -> this.startMicrosoftAuth()).bounds(cx - 100, startY, 200, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Use Offline Account"), btn -> {
            User u = new User("LadsPlayer", UUID.randomUUID(), "0", Optional.empty(), Optional.empty());
            ((MinecraftClientAccessor)Minecraft.getInstance()).setUser(u);
            this.statusMessage = "Switched to offline mode.";
        }).bounds(cx - 100, startY + 26, 200, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Back"), btn -> Minecraft.getInstance().setScreen(this.parent)).bounds(cx - 100, startY + 60, 200, 20).build());
    }

    private void startMicrosoftAuth() {
        this.statusMessage = "Requesting device code\u2026";
        this.auth.requestDeviceCode().thenAcceptAsync(info -> {
            this.statusMessage = "Open " + info.verificationUri + " \u2014 code: " + info.userCode;
            ((CompletableFuture)((CompletableFuture)((CompletableFuture)((CompletableFuture)this.auth.pollForToken((MicrosoftAuthenticator.DeviceCodeInfo)info, msg -> {
                this.statusMessage = msg;
            }).thenCompose(ms -> {
                this.statusMessage = "Got MS token, authenticating\u2026";
                return this.auth.authXboxLive((String)ms);
            })).thenCompose(xbl -> this.auth.authXsts((String)xbl))).thenCompose(xsts -> {
                String tok = xsts.get("Token").getAsString();
                String uhs = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
                return this.auth.authMinecraft(uhs, tok);
            })).thenCompose(mc -> {
                String token = mc.get("access_token").getAsString();
                return this.auth.getMinecraftProfile(token).thenApply(profile -> {
                    String name = profile.get("name").getAsString();
                    String id = profile.get("id").getAsString();
                    UUID uuid = UUID.fromString(id.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
                    User msUser = new User(name, uuid, token, Optional.empty(), Optional.empty());
                    ((MinecraftClientAccessor)Minecraft.getInstance()).setUser(msUser);
                    this.statusMessage = "Logged in as " + name + "!";
                    return profile;
                });
            })).exceptionally(e -> {
                this.statusMessage = "Error: " + e.getMessage();
                return null;
            });
        });
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, -301134566);
        g.fill(0, 0, this.width, 2, -9673729);
        super.extractRenderState(g, mouseX, mouseY, delta);
        int cx = this.width / 2;
        g.centeredText(this.font, "\u21c4  Account Switcher", cx, 22, -7568385);
        g.fill(cx - 90, 36, cx + 90, 37, 0x44FFFFFF);
        User u = Minecraft.getInstance().getUser();
        String activeLabel = "Active: " + u.getName();
        int cardW = 200;
        int cardH = 24;
        int cardX = cx - cardW / 2;
        int cardY = this.height / 2 - 72;
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, -870704594);
        g.fill(cardX, cardY, cardX + cardW, cardY + 1, -9673729);
        g.centeredText(this.font, activeLabel, cx, cardY + 8, -16711800);
        String status = "Status: " + this.statusMessage;
        int sw = this.font.width(status);
        if (sw > this.width - 20) {
            status = status.substring(0, Math.max(0, (this.width - 20) * status.length() / sw - 3)) + "...";
        }
        g.centeredText(this.font, status, cx, this.height / 2 + 52, -5592406);
    }
}

