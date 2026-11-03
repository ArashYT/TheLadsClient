package com.thelads.core.v1_20_1.mixin.menus;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ServerListEntryUi;
import com.thelads.core.client.ViaForce;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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
    /** The overlay's u in textures/gui/server_selection.png: move down, move up. */
    @Unique private static final float DOWN_U = 64, UP_U = 96;
    @Shadow @Final private JoinMultiplayerScreen screen;
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ServerData serverData;
    @Shadow private boolean pingCompleted() { return false; }
    @Shadow private boolean isCompatible() { return false; }
    /** The width vanilla gave this row in the frame being drawn. */
    @Unique private int ladsWidth = ServerListColumns.FULL_WIDTH;

    @Unique
    private ServerListEntryUi.Version lads$version() {
        boolean mismatched = !isCompatible();
        return ServerListEntryUi.version(pingCompleted() && serverData.ping >= 0, mismatched, mismatched && ViaForce.supports(serverData.protocol));
    }

    @Unique
    private String lads$versionText() {
        String version = ChatFormatting.stripFormatting(serverData.version.getString());
        return lads$version() == ServerListEntryUi.Version.NONE || version == null ? "" : version;
    }

    @Unique
    private int[] lads$firstLine() {
        Font font = minecraft.font;
        return ServerListEntryUi.firstLine(ladsWidth, font.width(serverData.name), font.width(lads$versionText()), font.width(serverData.status));
    }

    /** The button's label on this row, null when it shows none. */
    @Unique
    private String lads$buttonLabel() {
        if (lads$version() != ServerListEntryUi.Version.RED_WITH_BUTTON) return null;
        return ServerListEntryUi.buttonLabel(ladsWidth, minecraft.font::width);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void ladsRowWidth(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        ladsWidth = width;
    }

    /** Vanilla's red version replaces the player count on a mismatch: the count stays, the version has its own place. */
    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/MutableComponent;withStyle(Lnet/minecraft/ChatFormatting;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent ladsCountStays(MutableComponent redVersion) {
        return serverData.status.copy();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private int ladsClipName(GuiGraphics graphics, Font font, String name, int x, int y, int color, boolean shadow, Operation<Integer> original) {
        return original.call(graphics, font, ServerListEntryUi.clip(name, lads$firstLine()[0], font::width), x, y, color, shadow);
    }

    /** The button takes the MOTD's second line. */
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), index = 1)
    private int ladsMotdLines(int lines) {
        return lads$buttonLabel() != null ? 1 : lines;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ladsVersionAndButton(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        ServerListEntryUi.Version version = lads$version();
        if (version == ServerListEntryUi.Version.NONE) return;
        Font font = minecraft.font;
        String full = lads$versionText(), shown = ServerListEntryUi.clip(full, lads$firstLine()[1], font::width);
        int right = left + ServerListEntryUi.versionRight(width, font.width(serverData.status)), x = right - font.width(shown), y = top + 1;
        graphics.drawString(font, shown, x, y, version == ServerListEntryUi.Version.MUTED ? ServerListEntryUi.VERSION_MUTED : ServerListEntryUi.VERSION_RED, false);
        if (shown.length() < full.length() && mouseX >= x && mouseX <= right && mouseY >= y && mouseY <= y + 8) screen.setToolTip(List.of(Component.literal(full)));
        String label = lads$buttonLabel();
        if (label == null) return;
        int buttonWidth = ServerListEntryUi.buttonWidth(label, font::width), bx = left + width - ServerListEntryUi.BUTTON_RIGHT - buttonWidth, by = top + ServerListEntryUi.BUTTON_Y;
        boolean over = mouseX >= bx && mouseX < bx + buttonWidth && mouseY >= by && mouseY < by + ServerListEntryUi.BUTTON_HEIGHT;
        ServerListEntryUi.drawButton(new GuiGraphicsLadsAdapter1201(graphics, font), bx, by, buttonWidth, label, over);
        if (over) screen.setToolTip(List.of(Component.literal(ServerListEntryUi.CHANGE_VERSION)));
    }

    /** Sets ViaFabricPlus' forced version of this server only to what the ping reported, saves and joins. Only ever from a click here. */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ladsButtonPress(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        String label = button == 0 ? lads$buttonLabel() : null;
        if (label == null) return;
        ServerListColumns.Rows rows = (ServerListColumns.Rows) ((ServerListScreenAccessor) screen).ladsServerList();
        int row = lads$index();
        int buttonWidth = ServerListEntryUi.buttonWidth(label, minecraft.font::width), bx = rows.lads$rowLeft(row) + ladsWidth - ServerListEntryUi.BUTTON_RIGHT - buttonWidth;
        int by = rows.lads$rowTop(row) + ServerListEntryUi.BUTTON_Y;
        if (x < bx || x >= bx + buttonWidth || y < by || y >= by + ServerListEntryUi.BUTTON_HEIGHT) return;
        if (ViaForce.force(serverData, serverData.protocol)) {
            screen.getServers().save();
            screen.setSelected((ServerSelectionList.Entry) (Object) this);
            screen.joinSelectedServer();
        }
        cir.setReturnValue(true);
    }

    /** Vanilla reads the click against the single row's left edge; with columns each row has its own. */
    @ModifyExpressionValue(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList;getRowLeft()I"))
    private int ladsRowLeft(int left) {
        return ((ServerListColumns.Rows) ((ServerListScreenAccessor) screen).ladsServerList()).lads$rowLeft(lads$index());
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

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"))
    private boolean ladsArrows(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        if (u == UP_U) return ServerListDrag.canSwap(lads$index(), lads$index() - 1, lads$first());
        if (u == DOWN_U) return ServerListDrag.canSwap(lads$index(), lads$index() + 1, lads$first());
        return true;
    }

    /** Every move of a row, the arrows' and Shift+arrows', ends here. A click on the pinned row's icon where the arrow would be just selects it. */
    @Inject(method = "swap", at = @At("HEAD"), cancellable = true)
    private void ladsKeepPinned(int from, int to, CallbackInfo ci) {
        if (ServerListDrag.canSwap(from, to, lads$first())) return;
        screen.setSelected((ServerSelectionList.Entry) (Object) this);
        ci.cancel();
    }
}
