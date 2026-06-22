/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.Hud
 *  net.minecraft.world.entity.player.Player
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package squeek.appleskin.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import squeek.appleskin.client.HUDOverlayHandler;

@Mixin(value={Hud.class})
public class GuiMixin {
    @Inject(at={@At(value="HEAD")}, method={"extractFood"})
    private void renderFoodPre(GuiGraphicsExtractor context, Player player, int top, int right, CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("AppleSkin").isEnabled()) {
            return;
        }
        if (HUDOverlayHandler.INSTANCE != null) {
            HUDOverlayHandler.INSTANCE.onPreRenderFood(context, player, top, right);
        }
    }

    @Inject(at={@At(value="RETURN")}, method={"extractFood"})
    private void renderFoodPost(GuiGraphicsExtractor context, Player player, int top, int right, CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("AppleSkin").isEnabled()) {
            return;
        }
        if (HUDOverlayHandler.INSTANCE != null) {
            HUDOverlayHandler.INSTANCE.onRenderFood(context, player, top, right);
        }
    }

    @Inject(at={@At(value="RETURN")}, method={"extractHearts"})
    private void renderHealthPost(GuiGraphicsExtractor context, Player player, int left, int top, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("AppleSkin").isEnabled()) {
            return;
        }
        if (HUDOverlayHandler.INSTANCE != null) {
            HUDOverlayHandler.INSTANCE.onRenderHealth(context, player, left, top, lines, regeneratingHeartIndex, maxHealth, lastHealth, health, absorption, blinking);
        }
    }
}

