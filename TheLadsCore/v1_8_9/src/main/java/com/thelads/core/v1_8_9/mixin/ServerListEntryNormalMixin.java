package com.thelads.core.v1_8_9.mixin;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ServerListEntryUi;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A saved server's row: pings in parallel, keeps the old MOTD and count while a refresh pings, shows the version left of the player count (red
 * when it isn't 1.8, where vanilla's red version would replace the count) with the name clipped so the two never overlap, and draws
 * translucent as the dragged row's ghost (ServerListDrag.ghost).
 */
@Mixin(ServerListEntryNormal.class)
public abstract class ServerListEntryNormalMixin {
    @Shadow @Final private ServerData field_148301_e;
    @Shadow @Final private GuiMultiplayer owner;

    @Unique
    private static final ExecutorService ladsPingerPool = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("Lads Server Pinger #%d").setDaemon(true).build()
    );

    @Unique private String ladsPreviousMOTD;
    @Unique private String ladsPreviousPop;
    /** The width vanilla gave this row in the frame being drawn. */
    @Unique private int ladsWidth;

    @Inject(method = "drawEntry", at = @At("HEAD"))
    private void ladsPreserveBeforeDraw(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
        ladsWidth = listWidth;
        if (this.field_148301_e != null) {
            if (this.field_148301_e.serverMOTD != null && !this.field_148301_e.serverMOTD.isEmpty()) {
                this.ladsPreviousMOTD = this.field_148301_e.serverMOTD;
            }
            if (this.field_148301_e.populationInfo != null && !this.field_148301_e.populationInfo.isEmpty()) {
                this.ladsPreviousPop = this.field_148301_e.populationInfo;
            }
        }
    }

    @Redirect(method = "drawEntry", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ThreadPoolExecutor;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;"))
    private Future<?> ladsParallelPing(ThreadPoolExecutor executor, Runnable task) {
        if (this.field_148301_e != null) {
            // Restore previous MOTD and population while the refresh ping is running so the entry does not blank out or flicker
            if ((this.field_148301_e.serverMOTD == null || this.field_148301_e.serverMOTD.isEmpty()) && this.ladsPreviousMOTD != null) {
                this.field_148301_e.serverMOTD = this.ladsPreviousMOTD;
            }
            if ((this.field_148301_e.populationInfo == null || this.field_148301_e.populationInfo.isEmpty()) && this.ladsPreviousPop != null) {
                this.field_148301_e.populationInfo = this.ladsPreviousPop;
            }
        }
        return ladsPingerPool.submit(task);
    }

    @Unique
    private ServerListEntryUi.Version lads$version() {
        ServerData data = field_148301_e;
        return ServerListEntryUi.version(data.field_78841_f && data.pingToServer >= 0, data.version != 47, false);
    }

    @Unique
    private String lads$versionText() {
        String version = EnumChatFormatting.getTextWithoutFormattingCodes(field_148301_e.gameVersion);
        return lads$version() == ServerListEntryUi.Version.NONE || version == null ? "" : version;
    }

    @Unique
    private static FontRenderer lads$font() {
        return Minecraft.getMinecraft().fontRendererObj;
    }

    @Unique
    private int[] lads$firstLine() {
        ServerData data = field_148301_e;
        return ServerListEntryUi.firstLine(ladsWidth, lads$font().getStringWidth(data.serverName), lads$font().getStringWidth(lads$versionText()), lads$font().getStringWidth(data.populationInfo));
    }

    @ModifyArg(method = "drawEntry", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 0), index = 0)
    private String ladsClipName(String name) {
        return ServerListEntryUi.clip(name, lads$firstLine()[0], lads$font()::getStringWidth);
    }

    /** Vanilla's red version replaces the player count on a mismatch: the count stays, the version has its own place. */
    @ModifyArg(method = "drawEntry", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I", ordinal = 0), index = 0)
    private String ladsCountWidth(String status) {
        return field_148301_e.populationInfo;
    }

    @ModifyArg(method = "drawEntry", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 2), index = 0)
    private String ladsCount(String status) {
        return field_148301_e.populationInfo;
    }

    @ModifyArg(method = "drawEntry", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I"), index = 3)
    private int ladsGhostText(int color) {
        return ServerListDrag.ghost() ? ServerListDrag.GHOST_ALPHA << 24 | color & 0xFFFFFF : color;
    }

    @Redirect(method = "drawEntry", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V"))
    private void ladsGhostColor(float r, float g, float b, float a) {
        GlStateManager.color(r, g, b, ServerListDrag.ghost() ? ServerListDrag.GHOST_ALPHA / 255f : a);
    }

    @Inject(method = "drawEntry", at = @At("RETURN"))
    private void ladsVersion(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
        ServerListEntryUi.Version version = lads$version();
        if (version == ServerListEntryUi.Version.NONE) return;
        FontRenderer font = lads$font();
        String full = lads$versionText(), shown = ServerListEntryUi.clip(full, lads$firstLine()[1], font::getStringWidth);
        int right = x + ServerListEntryUi.versionRight(listWidth, font.getStringWidth(field_148301_e.populationInfo)), left = right - font.getStringWidth(shown);
        int color = version == ServerListEntryUi.Version.MUTED ? ServerListEntryUi.VERSION_MUTED : ServerListEntryUi.VERSION_RED;
        font.drawString(shown, left, y + 1, ServerListDrag.ghost() ? ServerListDrag.GHOST_ALPHA << 24 | color & 0xFFFFFF : color);
        if (shown.length() < full.length() && mouseX >= left && mouseX <= right && mouseY >= y && mouseY <= y + 8) owner.setHoveringText(full);
    }
}
