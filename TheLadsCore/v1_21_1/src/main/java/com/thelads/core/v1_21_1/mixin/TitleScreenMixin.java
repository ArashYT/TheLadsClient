package com.thelads.core.v1_21_1.mixin;

import com.thelads.core.v1_21_1.gui.AccountSwitcherScreen121;
import com.thelads.core.v1_21_1.gui.LadsSettingsScreen121;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin() {
        super(Component.empty());
    }

    @Unique private static final int CARD_W      = 130;
    @Unique private static final int CARD_H      = 46;
    @Unique private static final int CARD_MARGIN = 10;
    @Unique private static final int ACCENT      = 0xFF6C63FF;

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void ladsInjectTitleScreenButtons(CallbackInfo ci) {
        // Inject "Lads Settings" directly above "Options" in primary vertical list
        int optionsY = -1;
        for (GuiEventListener listener : this.children()) {
            if (listener instanceof AbstractWidget widget) {
                Component msg = widget.getMessage();
                boolean isOptions = false;
                if (msg != null) {
                    if (msg.getContents() instanceof TranslatableContents tc && "menu.options".equals(tc.getKey())) {
                        isOptions = true;
                    }
                    if (msg.getString().toLowerCase().contains("options")) {
                        isOptions = true;
                    }
                }
                if (isOptions) {
                    optionsY = widget.getY();
                    break;
                }
            }
        }

        if (optionsY != -1) {
            // Shift all lower buttons down by 24px (restrict to center vertical column)
            for (GuiEventListener listener : this.children()) {
                if (listener instanceof AbstractWidget widget) {
                    int y = widget.getY();
                    if (y >= optionsY && y < this.height - 40 && Math.abs(widget.getX() - (this.width / 2)) < 150) {
                        widget.setY(y + 24);
                    }
                }
            }

            // Insert Lads Settings at optionsY
            this.addRenderableWidget(
                Button.builder(
                    Component.literal("Lads Settings"),
                    btn -> Minecraft.getInstance().setScreen(new LadsSettingsScreen121((Screen)(Object)this))
                ).bounds(this.width / 2 - 100, optionsY, 200, 20).build()
            );
        }

        // Account switcher button added AFTER shift loop to prevent collision with Account Card
        int cardY = this.height - CARD_H - CARD_MARGIN - 22;
        this.addRenderableWidget(
            Button.builder(
                Component.literal("⇄ Switch Account"),
                btn -> Minecraft.getInstance().setScreen(new AccountSwitcherScreen121((Screen)(Object)this))
            ).bounds(CARD_MARGIN, cardY, CARD_W, 20).build()
        );
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void ladsRenderAccountCard(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int cardX = CARD_MARGIN;
        int cardY = this.height - CARD_H - CARD_MARGIN;

        guiGraphics.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H, 0xCC0D0D1A);
        guiGraphics.fill(cardX, cardY, cardX + CARD_W, cardY + 1, ACCENT);

        Minecraft mc = Minecraft.getInstance();
        String username = mc.getUser().getName();
        int headSize = 22;
        int headX = cardX + 8;
        int headY = cardY + (CARD_H - headSize) / 2;

        try {
            PlayerSkin skin = mc.getSkinManager().getInsecureSkin(mc.getGameProfile());
            PlayerFaceRenderer.draw(guiGraphics, skin.texture(), headX, headY, headSize);
        } catch (Exception e) {
            guiGraphics.fill(headX, headY, headX + headSize, headY + headSize, ACCENT);
        }

        int textX = headX + headSize + 7;
        String displayName = username.length() <= 12 ? username : username.substring(0, 11) + "…";
        guiGraphics.drawString(this.font, displayName, textX, cardY + 10, 0xFFFFFFFF, false);
        guiGraphics.drawString(this.font, "Active", textX, cardY + 22, ACCENT, false);
    }
}
