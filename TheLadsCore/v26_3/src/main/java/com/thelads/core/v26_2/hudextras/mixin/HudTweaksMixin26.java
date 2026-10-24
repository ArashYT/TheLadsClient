package com.thelads.core.v26_2.hudextras.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.modules.RaisedModule;
import com.thelads.core.modules.TitleTweakerModule;
import com.thelads.core.modules.VanillaHudModule;
import com.thelads.core.v26_2.hudextras.HudExtras26;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla HUD (hardcore hearts, XP bar and level, held item name, effect icons), Title Tweaker and Raised's own move and scale of the
 * hotbar, hearts, armour, hunger, air and mount hearts on 26.3's Hud.
 */
@Mixin(Hud.class)
public abstract class HudTweaksMixin26 {
    private static final String G = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;";
    private static final String P = "Lnet/minecraft/world/entity/player/Player;";
    @Shadow private int toolHighlightTimer;
    @Shadow private ItemStack lastToolHighlight;
    @Shadow private Component title;
    @Shadow private Component subtitle;

    @Shadow public abstract Font getFont();

    @ModifyExpressionValue(method = "extractHearts", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelData;isHardcore()Z"))
    private boolean lads$hardcore(boolean hardcore) {
        return hardcore || HudExtras.VANILLA.hardcoreHearts;
    }

    // The contextual bar is the XP bar, the jump bar or the locator bar: only the XP one hides.
    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractBackground(" + G + "Lnet/minecraft/client/DeltaTracker;)V"))
    private void lads$xpBarBack(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
        if (!(bar instanceof ExperienceBar && HudExtras.VANILLA.hideXpBar)) original.call(bar, graphics, delta);
    }

    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractRenderState(" + G + "Lnet/minecraft/client/DeltaTracker;)V"))
    private void lads$xpBar(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
        if (!(bar instanceof ExperienceBar && HudExtras.VANILLA.hideXpBar)) original.call(bar, graphics, delta);
    }

    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(" + G + "Lnet/minecraft/client/gui/Font;I)V"))
    private void lads$xpLevel(GuiGraphicsExtractor graphics, Font font, int level, Operation<Void> original) {
        if (!HudExtras.VANILLA.hideXpLevel) original.call(graphics, font, level);
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void lads$itemName(CallbackInfo ci) {
        toolHighlightTimer = HudExtras.VANILLA.highlightTicks(toolHighlightTimer, !lastToolHighlight.isEmpty());
    }

    @WrapMethod(method = "extractEffects")
    private void lads$effects(GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
        VanillaHudModule vanilla = HudExtras.VANILLA;
        if (vanilla.hideEffects) return;
        if (vanilla.effectsDx == 0 && vanilla.effectsDy == 0) {
            original.call(graphics, delta);
            return;
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(vanilla.effectsDx, vanilla.effectsDy);
            original.call(graphics, delta);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** Title Tweaker: right after the title's translate to the screen centre, its move and scale. */
    @Inject(method = "extractTitle", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;translate(FF)Lorg/joml/Matrix3x2f;", ordinal = 0, shift = At.Shift.AFTER))
    private void lads$title(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        TitleTweakerModule titles = HudExtras.TITLES;
        if (!titles.on) return;
        Font font = getFont();
        float scale = HudExtras26.titleScale(title == null ? 0 : font.width(title), subtitle == null ? 0 : font.width(subtitle), graphics.guiWidth());
        graphics.pose().translate(titles.dx, titles.dy);
        graphics.pose().scale(scale, scale);
    }

    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void lads$setTitle(Component text, CallbackInfo ci) {
        if (text != null && HudExtras.title(text.getString(), false)) ci.cancel();
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
    private void lads$setSubtitle(Component text, CallbackInfo ci) {
        if (text != null && HudExtras.title(text.getString(), true)) ci.cancel();
    }

    // Raised's own move and scale of each vanilla part, around the call that draws it.
    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Hud;extractItemHotbar(" + G + "Lnet/minecraft/client/DeltaTracker;)V"))
    private void lads$hotbar(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
        boolean moved = HudExtras26.move(graphics.pose(), RaisedModule.HOTBAR, graphics.guiWidth(), graphics.guiHeight());
        try {
            original.call(hud, graphics, delta);
        } finally {
            if (moved) graphics.pose().popMatrix();
        }
    }

    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractVehicleHealth(" + G + ")V"))
    private void lads$mount(Hud hud, GuiGraphicsExtractor graphics, Operation<Void> original) {
        boolean moved = HudExtras26.move(graphics.pose(), RaisedModule.MOUNT, graphics.guiWidth(), graphics.guiHeight());
        try {
            original.call(hud, graphics);
        } finally {
            if (moved) graphics.pose().popMatrix();
        }
    }

    @WrapOperation(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractArmor(" + G + P + "IIII)V"))
    private void lads$armor(GuiGraphicsExtractor graphics, Player player, int a, int b, int c, int d, Operation<Void> original) {
        boolean moved = HudExtras26.move(graphics.pose(), RaisedModule.ARMOR, graphics.guiWidth(), graphics.guiHeight());
        try {
            original.call(graphics, player, a, b, c, d);
        } finally {
            if (moved) graphics.pose().popMatrix();
        }
    }

    @WrapOperation(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractHearts(" + G + P + "IIIIFIIIZ)V"))
    private void lads$hearts(Hud hud, GuiGraphicsExtractor graphics, Player player, int a, int b, int c, int d, float e, int f, int g, int h,
                             boolean blink, Operation<Void> original) {
        boolean moved = HudExtras26.move(graphics.pose(), RaisedModule.HEALTH, graphics.guiWidth(), graphics.guiHeight());
        try {
            original.call(hud, graphics, player, a, b, c, d, e, f, g, h, blink);
        } finally {
            if (moved) graphics.pose().popMatrix();
        }
    }

    @WrapOperation(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractFood(" + G + P + "II)V"))
    private void lads$food(Hud hud, GuiGraphicsExtractor graphics, Player player, int a, int b, Operation<Void> original) {
        boolean moved = HudExtras26.move(graphics.pose(), RaisedModule.FOOD, graphics.guiWidth(), graphics.guiHeight());
        try {
            original.call(hud, graphics, player, a, b);
        } finally {
            if (moved) graphics.pose().popMatrix();
        }
    }

    @WrapOperation(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractAirBubbles(" + G + P + "III)V"))
    private void lads$air(Hud hud, GuiGraphicsExtractor graphics, Player player, int a, int b, int c, Operation<Void> original) {
        boolean moved = HudExtras26.move(graphics.pose(), RaisedModule.AIR, graphics.guiWidth(), graphics.guiHeight());
        try {
            original.call(hud, graphics, player, a, b, c);
        } finally {
            if (moved) graphics.pose().popMatrix();
        }
    }
}
