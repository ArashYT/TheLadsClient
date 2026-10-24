package com.thelads.core.v1_20_1.hudextras.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.modules.RaisedModule;
import com.thelads.core.modules.TitleTweakerModule;
import com.thelads.core.modules.VanillaHudModule;
import com.thelads.core.v1_20_1.hud.HudParts1201;
import com.thelads.core.v1_20_1.hudextras.HudExtras1201;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla HUD (hardcore hearts, XP bar and level, held item name, effect icons), Title Tweaker and Raised's move and scale of the
 * hearts, armour, hunger and air rows on 1.20.1's Gui. Forge's GUI calls renderHearts, renderExperienceBar, renderEffects, tick and
 * setTitle too; its own title and bar overlays are handled in ForgeOverlays1201.
 */
@Mixin(Gui.class)
public abstract class GuiTweaksMixin1201 {
    private static final String G = "Lnet/minecraft/client/gui/GuiGraphics;";
    private static final String PROFILER = "Lnet/minecraft/util/profiling/ProfilerFiller;";
    @Shadow private int toolHighlightTimer;
    @Shadow private ItemStack lastToolHighlight;
    /** A Raised part of renderPlayerHealth has pushed its move (popped when the next part or the method ends). */
    @Unique private boolean lads$piece;

    @ModifyExpressionValue(method = "renderHearts", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelData;isHardcore()Z"))
    private boolean lads$hardcore(boolean hardcore) {
        return hardcore || HudExtras.VANILLA.hardcoreHearts;
    }

    @WrapOperation(method = "renderExperienceBar", at = @At(value = "INVOKE", target = G + "blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void lads$xpBar(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int w, int h, Operation<Void> original) {
        if (!HudExtras.VANILLA.hideXpBar) original.call(graphics, texture, x, y, u, v, w, h);
    }

    @WrapOperation(method = "renderExperienceBar", at = @At(value = "INVOKE", target = G + "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private int lads$xpLevel(GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow, Operation<Integer> original) {
        return HudExtras.VANILLA.hideXpLevel ? 0 : original.call(graphics, font, text, x, y, color, shadow);
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void lads$itemName(CallbackInfo ci) {
        toolHighlightTimer = HudExtras.VANILLA.highlightTicks(toolHighlightTimer, !lastToolHighlight.isEmpty());
    }

    // Around the whole method, so the Lads effect replacement (ScoreboardMixin) cancelling inside it can't leave the move pushed.
    @WrapMethod(method = "renderEffects")
    private void lads$effects(GuiGraphics graphics, Operation<Void> original) {
        VanillaHudModule vanilla = HudExtras.VANILLA;
        if (vanilla.hideEffects) return;
        if (vanilla.effectsDx == 0 && vanilla.effectsDy == 0) {
            original.call(graphics);
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(vanilla.effectsDx, vanilla.effectsDy, 0);
            original.call(graphics);
        } finally {
            graphics.pose().popPose();
        }
    }

    /** Title Tweaker: right after the title's translate to the screen centre (render's second translate), its move and scale. */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 1, shift = At.Shift.AFTER))
    private void lads$title(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        TitleTweakerModule titles = HudExtras.TITLES;
        if (!titles.on) return;
        float scale = HudExtras1201.titleScale(graphics.guiWidth());
        graphics.pose().translate(titles.dx, titles.dy, 0);
        graphics.pose().scale(scale, scale, 1);
    }

    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void lads$setTitle(Component title, CallbackInfo ci) {
        if (title != null && HudExtras.title(title.getString(), false)) ci.cancel();
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
    private void lads$setSubtitle(Component subtitle, CallbackInfo ci) {
        if (subtitle != null && HudExtras.title(subtitle.getString(), true)) ci.cancel();
    }

    // Raised's own move and scale of the rows renderPlayerHealth draws one after another between its profiler sections
    // (armor, health, food, air). Forge's GUI draws them as overlays of their own (ForgeOverlays1201).
    @Inject(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = PROFILER + "push(Ljava/lang/String;)V", shift = At.Shift.AFTER))
    private void lads$armor(GuiGraphics graphics, CallbackInfo ci) {
        lads$piece(graphics, RaisedModule.ARMOR);
    }

    @Inject(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = PROFILER + "popPush(Ljava/lang/String;)V", ordinal = 0))
    private void lads$health(GuiGraphics graphics, CallbackInfo ci) {
        lads$piece(graphics, RaisedModule.HEALTH);
    }

    @Inject(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = PROFILER + "popPush(Ljava/lang/String;)V", ordinal = 1))
    private void lads$food(GuiGraphics graphics, CallbackInfo ci) {
        lads$piece(graphics, RaisedModule.FOOD);
    }

    @Inject(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = PROFILER + "popPush(Ljava/lang/String;)V", ordinal = 2))
    private void lads$air(GuiGraphics graphics, CallbackInfo ci) {
        lads$piece(graphics, RaisedModule.AIR);
    }

    @Inject(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = PROFILER + "pop()V"))
    private void lads$rowsEnd(GuiGraphics graphics, CallbackInfo ci) {
        lads$piece(graphics, -1);
    }

    @Unique
    private void lads$piece(GuiGraphics graphics, int piece) {
        if (lads$piece) {
            graphics.pose().popPose();
            lads$piece = false;
        }
        RaisedModule raised = HudExtras.RAISED;
        if (piece < 0 || raised == null || !raised.moved[piece]) return;
        graphics.pose().pushPose();
        HudParts1201.move(graphics.pose(), piece, graphics.guiWidth(), graphics.guiHeight());
        lads$piece = true;
    }
}
