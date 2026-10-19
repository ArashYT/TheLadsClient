// Behaviour adapted from the Lads 26.3 Tab Tweaks port (TabTweaks 1.5.11 by MicrocontrollersDev), LGPL-3.0-only.
package com.thelads.core.v1_20_1.hud.overlay.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.hud.HudCover;
import com.thelads.core.v1_20_1.hud.overlay.Nametags1201;
import com.thelads.core.v1_20_1.hud.overlay.TabTweaks1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TabList + PingView (and Nametags renames) on vanilla 1.20.1 PlayerTabOverlay, which Forge does not patch. Priority 900: applied
 * before mods that replace the list, so the move/scale still applies to theirs.
 */
@Mixin(value = PlayerTabOverlay.class, priority = 900)
public class TabOverlayMixin {
    private static final String RENDER = "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V";
    private static final String FILL = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V";
    private static final String LINE = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I";
    private static final String NAME = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I";
    private static final String FACE = "Lnet/minecraft/client/gui/components/PlayerFaceRenderer;draw(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;IIIZZ)V";

    @Shadow @Final private Minecraft minecraft;
    /** The row whose face is being drawn (its skin was just asked for). */
    @Unique private PlayerInfo lads$row;
    /** Where the list is drawn this frame (its move and scale on the GUI's pose); null outside its render. */
    @Unique private Matrix4f lads$pose;

    @WrapMethod(method = RENDER)
    private void lads$moveTab(GuiGraphics graphics, int screenWidth, Scoreboard scoreboard, Objective objective, Operation<Void> original) {
        TabTweaks1201.refresh();
        float scale = TabTweaks1201.tabScale;
        graphics.pose().pushPose();
        try {
            graphics.pose().scale(scale, scale, 1F);
            graphics.pose().translate(TabTweaks1201.moveTabHorizontal, TabTweaks1201.distanceY(), 0F);
            lads$pose = new Matrix4f(graphics.pose().last().pose());
            original.call(graphics, (int) (screenWidth / scale), scoreboard, TabTweaks1201.removeObjectives ? null : objective);
        } finally {
            lads$pose = null;
            graphics.pose().popPose();
        }
    }

    /** HudCover: the header, body and footer backgrounds are the list's box on screen; the Lads HUD widgets under it step aside. */
    @Unique
    private void lads$cover(int left, int top, int right, int bottom) {
        if (lads$pose == null) return;
        Vector3f a = lads$pose.transformPosition(new Vector3f(left, top, 0)), b = lads$pose.transformPosition(new Vector3f(right, bottom, 0));
        HudCover.add(HudCover.TAB, a.x(), a.y(), b.x(), b.y());
    }

    @ModifyArg(method = "getPlayerInfos", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;limit(J)Ljava/util/stream/Stream;"))
    private long lads$maxPlayers(long maxSize) {
        return TabTweaks1201.layout ? TabTweaks1201.maxTabPlayers : maxSize;
    }

    @ModifyExpressionValue(method = RENDER, at = @At(value = "CONSTANT", args = "intValue=20"))
    private int lads$playersPerColumn(int original) {
        return TabTweaks1201.layout ? TabTweaks1201.playersPerColumn : original;
    }

    @ModifyExpressionValue(method = RENDER, at = @At(value = "CONSTANT", args = "intValue=13"))
    private int lads$slotWidth(int original) {
        return TabTweaks1201.showPingInTab ? 30 : original;
    }

    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(I)I"))
    private int lads$rowColor(int color) {
        return TabTweaks1201.layout ? TabTweaks1201.tabPlayerListColor : color;
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = FILL, ordinal = 0), index = 4)
    private int lads$headerColor(int left, int top, int right, int bottom, int color) {
        lads$cover(left, top, right, bottom);
        return TabTweaks1201.layout ? TabTweaks1201.tabHeaderColor : color;
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = FILL, ordinal = 1), index = 4)
    private int lads$bodyColor(int left, int top, int right, int bottom, int color) {
        lads$cover(left, top, right, bottom);
        return TabTweaks1201.layout ? TabTweaks1201.tabBodyColor : color;
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = FILL, ordinal = 3), index = 4)
    private int lads$footerColor(int left, int top, int right, int bottom, int color) {
        lads$cover(left, top, right, bottom);
        return TabTweaks1201.layout ? TabTweaks1201.tabFooterColor : color;
    }

    @WrapOperation(method = RENDER, at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;", opcode = Opcodes.GETFIELD))
    private Component lads$header(PlayerTabOverlay instance, Operation<Component> original) {
        return TabTweaks1201.removeHeader ? null : original.call(instance);
    }

    /** Both reads (the null check and the line split) see the same footer, with the player count line when asked. */
    @WrapOperation(method = RENDER, at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;", opcode = Opcodes.GETFIELD))
    private Component lads$footer(PlayerTabOverlay instance, Operation<Component> original) {
        Component footer = TabTweaks1201.removeFooter ? null : original.call(instance);
        if (!TabTweaks1201.showPlayerCount || minecraft.getConnection() == null) return footer;
        int count = minecraft.getConnection().getListedOnlinePlayers().size();
        Component line = Component.literal(TabTweaks1201.formatPlayerCount(count));
        return (footer == null || footer.getString().isEmpty()) ? line : Component.empty().append(footer).append("\n").append(line);
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = LINE, ordinal = 0))
    private int lads$headerShadow(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, Operation<Integer> original) {
        return TabTweaks1201.removeHeaderShadow ? graphics.drawString(font, text, x, y, color, false) : original.call(graphics, font, text, x, y, color);
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = LINE, ordinal = 1))
    private int lads$footerShadow(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, Operation<Integer> original) {
        return TabTweaks1201.removeFooterShadow ? graphics.drawString(font, text, x, y, color, false) : original.call(graphics, font, text, x, y, color);
    }

    /** The name: moved over the hidden face (or one pixel right of the larger hat) and drawn without shadow when asked. */
    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = NAME, ordinal = 0))
    private int lads$name(GuiGraphics graphics, Font font, Component text, int x, int y, int color, Operation<Integer> original) {
        int moved = TabTweaks1201.removeHeads ? x - 8 : TabTweaks1201.improvedHeads ? x + 1 : x;
        return TabTweaks1201.removeBodyShadow ? graphics.drawString(font, text, moved, y, color, false) : original.call(graphics, font, text, moved, y, color);
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerInfo;getSkinLocation()Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation lads$captureRow(PlayerInfo info, Operation<ResourceLocation> original) {
        lads$row = info;
        return original.call(info);
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = FACE))
    private void lads$face(GuiGraphics graphics, ResourceLocation skin, int x, int y, int size, boolean hat, boolean upsideDown, Operation<Void> original) {
        PlayerInfo row = lads$row;
        lads$row = null;
        if (TabTweaks1201.removeHeads) return;
        // Server-side NPCs are listed with version 2 UUIDs.
        if (TabTweaks1201.removeNpcHeads && row != null && row.getProfile().getId() != null && row.getProfile().getId().version() == 2) return;
        if (TabTweaks1201.improvedHeads) TabTweaks1201.drawHead(graphics, skin, x, y, size, hat, upsideDown);
        else original.call(graphics, skin, x, y, size, hat, upsideDown);
    }

    @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
    private void lads$latency(GuiGraphics graphics, int slotWidth, int x, int y, PlayerInfo info, CallbackInfo callback) {
        if (TabTweaks1201.removePing) callback.cancel();
        else if (TabTweaks1201.showPingInTab) {
            TabTweaks1201.drawLatency(graphics, slotWidth, x, y, info);
            callback.cancel();
        }
    }

    /** Nametags renames in the tab list; column widths use the same method, so they fit the new names. */
    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component lads$rename(Component name) {
        return Nametags1201.rename(name);
    }
}
