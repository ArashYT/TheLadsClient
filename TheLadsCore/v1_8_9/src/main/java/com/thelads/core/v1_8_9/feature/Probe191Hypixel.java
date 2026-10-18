package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.thelads.core.client.hypixel.HypixelQa;
import com.thelads.core.client.hypixel.RealPing;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.server.MinecraftServer;

/**
 * QA only (LADS_VERIFY_189_ONLY=hypixel): the common Hypixel routine (HypixelQa) with 1.8.9's commands (run by the integrated server:
 * a 1.8.9 chat line holds 100 characters), tab key, chat and settings screens and screenshots (lads-qa/screenshots/hypixel-*.png).
 * The integrated server knows no hypixel:ping, so the Mod API's answer is an S3F custom payload handed to Hypixel189.payload, the
 * method NetworkManagerMixin calls as such a packet arrives.
 */
final class Probe191Hypixel {
    private static HypixelQa qa;

    private Probe191Hypixel() {}

    static boolean run(Minecraft mc) throws Exception {
        if (qa == null) qa = new HypixelQa();
        if (!qa.tick(GAME)) return retry(1);
        check(qa.failures().isEmpty(), "Hypixel modules and real ping: " + qa.failures());
        return after(5);
    }

    private static final HypixelQa.Game GAME = new HypixelQa.Game() {
        @Override public void command(final String command) {
            final MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
            server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
        }

        @Override public boolean serverAnswersPings() { return false; }

        @Override public void pong(byte[] payload) {
            Hypixel189.payload(new S3FPacketCustomPayload(RealPing.CHANNEL, new PacketBuffer(Unpooled.wrappedBuffer(payload))));
        }

        @Override public void holdTab(boolean down) {
            KeyBinding.setKeyBindState(Minecraft.getMinecraft().gameSettings.keyBindPlayerList.getKeyCode(), down);
        }

        @Override public void openChat() { Minecraft.getMinecraft().displayGuiScreen(new GuiChat()); }

        @Override public com.thelads.core.client.gui.LadsSettingsScreen openSettings(String module) {
            LadsSettingsScreen189 menu = new LadsSettingsScreen189(null);
            Minecraft.getMinecraft().displayGuiScreen(menu);
            if (module != null) menu.openModule(module);
            return menu.ui();
        }

        @Override public void closeScreen() { Minecraft.getMinecraft().displayGuiScreen(null); }

        @Override public void screenshot(String name) { CoreProbe.screenshot(Minecraft.getMinecraft(), name); }

        @Override public boolean stockRefill() {
            command("gamemode 0 @p");
            command("replaceitem entity @p slot.hotbar.0 minecraft:cobblestone 1");
            command("replaceitem entity @p slot.inventory.0 minecraft:cobblestone 16");
            Minecraft.getMinecraft().thePlayer.inventory.currentItem = 0;
            return true;
        }

        @Override public void emptyHand() { command("replaceitem entity @p slot.hotbar.0 minecraft:air"); }

        @Override public String held() {
            net.minecraft.item.ItemStack stack = Minecraft.getMinecraft().thePlayer.getHeldItem();
            return stack == null ? "" : net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()) + " x" + stack.stackSize;
        }

        @Override public Boolean xaeroShown() { return Minimap189.xaeroShown(); }

        @Override public boolean flipXaero() { return Minimap189.flipXaero(); }
    };
}
