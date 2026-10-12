package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.NativeHud;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SmoothHotbar: renderTooltip's second texture draw, the selected-slot frame, glides to the slot (NativeHud.selectionOffset).
 * Raised: vanilla draws 22 of the frame's 24 rows; off the bottom edge the last two show, so all 24 are drawn.
 * The Action Bar module (ActionBar) takes the action bar where it is set (a server's, as the text it shows, and a record's).
 */
@Mixin(GuiIngame.class)
public abstract class GuiIngameMixin {
    @Shadow private int recordPlayingUpFor;

    // With the Action Bar module on, vanilla still stores the message (other features and mods read it); only its timer is zeroed so vanilla never draws it.
    @Inject(method = "setRecordPlaying(Ljava/lang/String;Z)V", at = @At("RETURN"), require = 1)
    private void ladsActionBar(String message, boolean playing, CallbackInfo ci) {
        if (com.thelads.core.client.hud.ActionBar.set(message, playing)) recordPlayingUpFor = 0;
    }

    @Inject(method = "updateTick", at = @At("HEAD"), require = 1)
    private void ladsActionBarTick(CallbackInfo ci) {
        com.thelads.core.client.hud.ActionBar.tick();
    }

    @Redirect(method = "renderTooltip", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/client/gui/GuiIngame;drawTexturedModalRect(IIIIII)V"), require = 1)
    private void ladsSelection(GuiIngame gui, int x, int y, int u, int v, int width, int height) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(NativeHud.selectionOffset(x), 0, 0);
        gui.drawTexturedModalRect(x, y, u, v, width, com.thelads.core.v1_8_9.feature.Raised189.hotbar() > 0 ? 24 : height);
        GlStateManager.popMatrix();
    }

    @Unique private static Collection<Score> ladsLastSortedScoresRef = null;
    @Unique private static java.util.ArrayList<Score> ladsCachedFilteredScores = null;
    @Unique private static java.util.ArrayList<Score> ladsCachedSkippedScores = null;

    @Redirect(method = "renderScoreboard", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Iterables;filter(Ljava/lang/Iterable;Lcom/google/common/base/Predicate;)Ljava/lang/Iterable;", remap = false), require = 1)
    private Iterable<Score> ladsFilterScoreboard(Iterable<Score> scores, com.google.common.base.Predicate<Score> predicate) {
        if (scores == ladsLastSortedScoresRef && ladsCachedFilteredScores != null) {
            return ladsCachedFilteredScores;
        }
        java.util.ArrayList<Score> filtered = new java.util.ArrayList<>();
        for (Score score : scores) {
            if (predicate.apply(score)) {
                filtered.add(score);
            }
        }
        ladsLastSortedScoresRef = (Collection<Score>) scores;
        ladsCachedFilteredScores = filtered;
        ladsCachedSkippedScores = null;
        return filtered;
    }

    @Redirect(method = "renderScoreboard", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayList(Ljava/lang/Iterable;)Ljava/util/ArrayList;", ordinal = 0, remap = false), require = 1)
    private java.util.ArrayList<Score> ladsScoreboardList(Iterable<Score> elements) {
        if (elements instanceof java.util.ArrayList) {
            return (java.util.ArrayList<Score>) elements;
        }
        return com.google.common.collect.Lists.newArrayList(elements);
    }

    @Redirect(method = "renderScoreboard", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayList(Ljava/lang/Iterable;)Ljava/util/ArrayList;", ordinal = 1, remap = false), require = 1)
    private java.util.ArrayList<Score> ladsScoreboardSkippedList(Iterable<Score> elements) {
        if (ladsCachedSkippedScores != null) {
            return ladsCachedSkippedScores;
        }
        java.util.ArrayList<Score> skipped = com.google.common.collect.Lists.newArrayList(elements);
        ladsCachedSkippedScores = skipped;
        return skipped;
    }
}
