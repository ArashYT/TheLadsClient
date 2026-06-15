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
    private void ladsAddAccountCard(CallbackInfo ci) {
        int cardY = this.height - CARD_H - CARD_MARGIN - 22;
        this.addRenderableWidget(
            Button.builder(
                Component.literal("⇄ Switch Account"),
                btn -> Minecraft.getInstance().setScreen(new AccountSwitcherScreen((Screen)(Object)this))
            ).bounds(CARD_MARGIN, cardY, CARD_W, 20).build()
        );
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void ladsRenderAccountCard(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int cardX = CARD_MARGIN;
        int cardY = this.height - CARD_H - CARD_MARGIN;

        // Dark card with accent top border
        g.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H, 0xCC0D0D1A);
        g.fill(cardX, cardY, cardX + CARD_W, cardY + 1, ACCENT);

        // Resolve name + type from launcher profile or session fallback
        String username = Minecraft.getInstance().getUser().getName();
        String type = "offline";
        LadsProfileSync.LadsProfile profile = LadsProfileSync.getCached();
        if (profile != null) {
            if (profile.username() != null) username = profile.username();
            if (profile.type() != null) type = profile.type();
        }

        boolean ms = "microsoft".equalsIgnoreCase(type);

        // Avatar placeholder square with accent borders
        int headSize = 22;
        int headX = cardX + 8;
        int headY = cardY + (CARD_H - headSize) / 2;
        
        net.minecraft.world.entity.player.PlayerSkin skin = Minecraft.getInstance().getSkinManager().createLookup(new com.mojang.authlib.GameProfile(Minecraft.getInstance().getUser().getProfileId(), Minecraft.getInstance().getUser().getName()), false).get();
        net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(g, skin, headX, headY, headSize);

        // Name and account type
        int textX = headX + headSize + 7;
        g.text(this.font, Component.literal(truncate(username, 12)), textX, cardY + 10, ms ? 0xFFB0A0FF : 0xFFAAAAAA, false);
        g.text(this.font, Component.literal(ms ? "Microsoft" : "Offline"),  textX, cardY + 22, ms ? ACCENT : 0xFF666688, false);
    }

    @Unique
    private static String truncate(String s, int max) {
        if (s == null) return "Unknown";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
