package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ServerListEntryUi;
import com.thelads.core.client.ViaForce;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A saved server's row: the version left of the player count (red when it differs from the client's, where vanilla's red version would
 * replace the count), the name clipped so the two never overlap, the change-version button on a mismatch, and no way past the pinned
 * network with the arrows or Shift+arrows.
 */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
abstract class ServerEntryMixin {
    @Shadow @Final private JoinMultiplayerScreen screen;
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ServerData serverData;

    @Unique
    private ServerSelectionList.OnlineServerEntry lads$self() {
        return (ServerSelectionList.OnlineServerEntry) (Object) this;
    }

    @Unique
    private ServerListEntryUi.Version lads$version() {
        ServerData.State state = serverData.state();
        boolean mismatched = state == ServerData.State.INCOMPATIBLE;
        return ServerListEntryUi.version(mismatched || state == ServerData.State.SUCCESSFUL, mismatched, mismatched && ViaForce.supports(serverData.protocol));
    }

    @Unique
    private String lads$versionText() {
        String version = ChatFormatting.stripFormatting(serverData.version.getString());
        return lads$version() == ServerListEntryUi.Version.NONE || version == null ? "" : version;
    }

    /** {name, version} widths on the first line. */
    @Unique
    private int[] lads$firstLine() {
        Font font = minecraft.font;
        return ServerListEntryUi.firstLine(lads$self().getContentWidth(), font.width(serverData.name), font.width(lads$versionText()), font.width(serverData.status));
    }

    /** The button's label on this row, null when it shows none. */
    @Unique
    private String lads$buttonLabel() {
        if (lads$version() != ServerListEntryUi.Version.RED_WITH_BUTTON) return null;
        return ServerListEntryUi.buttonLabel(lads$self().getContentWidth(), minecraft.font::width);
    }

    @Unique
    private int lads$buttonX(int width) {
        return lads$self().getContentRight() - ServerListEntryUi.BUTTON_RIGHT - width;
    }

    @Unique
    private boolean lads$overButton(String label, double x, double y) {
        int width = ServerListEntryUi.buttonWidth(label, minecraft.font::width), left = lads$buttonX(width), top = lads$self().getContentY() + ServerListEntryUi.BUTTON_Y;
        return x >= left && x < left + width && y >= top && y < top + ServerListEntryUi.BUTTON_HEIGHT;
    }

    /** Vanilla's red version replaces the player count on a mismatch: the count stays, the version has its own place. */
    @ModifyExpressionValue(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/MutableComponent;withStyle(Lnet/minecraft/ChatFormatting;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent ladsCountStays(MutableComponent redVersion) {
        return serverData.status.copy();
    }

    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void ladsClipName(GuiGraphicsExtractor graphics, Font font, String name, int x, int y, int color, Operation<Void> original) {
        original.call(graphics, font, ServerListEntryUi.clip(name, lads$firstLine()[0], font::width), x, y, color);
    }

    /** The button takes the MOTD's second line. */
    @ModifyArg(method = "extractContent", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), index = 1)
    private int ladsMotdLines(int lines) {
        return lads$buttonLabel() != null ? 1 : lines;
    }

    @Inject(method = "extractContent", at = @At("TAIL"))
    private void ladsVersionAndButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        ServerListEntryUi.Version version = lads$version();
        if (version == ServerListEntryUi.Version.NONE) return;
        Font font = minecraft.font;
        ServerSelectionList.OnlineServerEntry self = lads$self();
        String full = lads$versionText(), shown = ServerListEntryUi.clip(full, lads$firstLine()[1], font::width);
        int right = self.getContentX() + ServerListEntryUi.versionRight(self.getContentWidth(), font.width(serverData.status)), x = right - font.width(shown), y = self.getContentY() + 1;
        graphics.text(font, shown, x, y, version == ServerListEntryUi.Version.MUTED ? ServerListEntryUi.VERSION_MUTED : ServerListEntryUi.VERSION_RED);
        if (shown.length() < full.length() && mouseX >= x && mouseX <= right && mouseY >= y && mouseY <= y + 8) graphics.setTooltipForNextFrame(Component.literal(full), mouseX, mouseY);
        String label = lads$buttonLabel();
        if (label == null) return;
        boolean over = lads$overButton(label, mouseX, mouseY);
        int width = ServerListEntryUi.buttonWidth(label, font::width);
        ServerListEntryUi.drawButton(new GuiGraphicsExtractorLadsAdapter(graphics, font), lads$buttonX(width), self.getContentY() + ServerListEntryUi.BUTTON_Y, width, label, over);
        if (over) graphics.setTooltipForNextFrame(Component.literal(ServerListEntryUi.CHANGE_VERSION), mouseX, mouseY);
    }

    /** Sets ViaFabricPlus' forced version of this server only to what the ping reported, saves and joins. Only ever from a click here. */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ladsButtonPress(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        String label = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? lads$buttonLabel() : null;
        if (label == null || !lads$overButton(label, event.x(), event.y())) return;
        if (ViaForce.force(serverData, serverData.protocol)) {
            screen.getServers().save();
            screen.join(serverData);
        }
        cir.setReturnValue(true);
    }

    @Unique
    private int lads$index() {
        ServerList servers = screen.getServers();
        for (int i = 0; i < servers.size(); i++) if (servers.get(i) == serverData) return i;
        return -1;
    }

    @Unique
    private int lads$first() {
        ServerList servers = screen.getServers();
        return servers.size() == 0 ? 0 : ServerListDrag.firstMovable(servers.get(0).ip);
    }

    @ModifyExpressionValue(method = {"extractContent", "mouseClicked"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList$OnlineServerEntry;mouseOverTopLeftQuarter(III)Z"))
    private boolean ladsNoUp(boolean over) {
        return over && ServerListDrag.canSwap(lads$index(), lads$index() - 1, lads$first());
    }

    @ModifyExpressionValue(method = {"extractContent", "mouseClicked"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList$OnlineServerEntry;mouseOverBottomLeftQuarter(III)Z"))
    private boolean ladsNoDown(boolean over) {
        return over && ServerListDrag.canSwap(lads$index(), lads$index() + 1, lads$first());
    }

    @WrapWithCondition(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private boolean ladsArrows(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
        String path = sprite.getPath();
        if (path.startsWith("server_list/move_up")) return ServerListDrag.canSwap(lads$index(), lads$index() - 1, lads$first());
        if (path.startsWith("server_list/move_down")) return ServerListDrag.canSwap(lads$index(), lads$index() + 1, lads$first());
        return true;
    }

    /** Every move of a row, the arrows' and Shift+arrows', ends here. */
    @Inject(method = "swap", at = @At("HEAD"), cancellable = true)
    private void ladsKeepPinned(int from, int to, CallbackInfo ci) {
        if (!ServerListDrag.canSwap(from, to, lads$first())) ci.cancel();
    }
}
