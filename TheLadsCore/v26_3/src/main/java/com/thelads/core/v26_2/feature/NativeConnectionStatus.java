// SPDX-License-Identifier: Apache-2.0
// Toast layout/behavior adapted from SignalLoss 1.2.1+26.2, Copyright Hexandcube.
package com.thelads.core.v26_2.feature;

import com.thelads.core.client.SignalLossPolicy;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.modules.SignalLossModule;
import java.nio.file.Files;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.LoggerFactory;

public final class NativeConnectionStatus {
    private static final SignalLossPolicy POLICY = new SignalLossPolicy();
    private static long lastTenths = -1;
    private static String warning = "";
    private static boolean registered;
    private NativeConnectionStatus() {}
    public static SignalLossModule module() { return (SignalLossModule) NativeQualityOfLife.module("SignalLoss"); }

    public static void register() {
        if (registered) return; registered = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> POLICY.joined(handler.getConnection(), System.nanoTime()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> POLICY.reset());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> NativeSignalLossCommands.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(client -> NativeSignalLossProbe.tick());
        var directory = FabricLoader.getInstance().getConfigDir();
        var upstream = directory.resolve("signalloss.json");
        var marker = directory.resolve("theladscore/signalloss-imported.txt");
        if (Files.isRegularFile(upstream) && !Files.exists(marker) && SignalLossConfigIO.defaults(module())) {
            try {
                SignalLossConfigIO.importLegacy(upstream, module()); ConfigManager.save();
                Files.createDirectories(marker.getParent()); Files.writeString(marker, "Read-only SignalLoss 1.2.1 preferences imported into Lads.\n");
            } catch (Exception failure) { LoggerFactory.getLogger("TheLadsCore").warn("SignalLoss preferences preserved; import did not complete: {}", failure.toString()); }
        }
    }
    static SignalLossPolicy.Settings settings() {
        var module = module();
        return new SignalLossPolicy.Settings(SignalLossModule.milliseconds(module.timeout, 2000),
            SignalLossModule.milliseconds(module.minimum, 2000), SignalLossModule.milliseconds(module.linger, 1000));
    }
    public static void resetPreferences() {
        SignalLossConfigIO.copy(new SignalLossModule(), module()); ConfigManager.save(); POLICY.reset();
    }
    public static void reloadPreferences() throws Exception {
        SignalLossConfigIO.reloadNative(ClientPaths.getConfigFile().toPath(), module()); POLICY.reset();
    }
    static SignalLossPolicy.Frame frame() {
        Minecraft minecraft = Minecraft.getInstance(); var module = module();
        var listener = minecraft.getConnection(); var connection = listener == null ? null : listener.getConnection();
        boolean eligible = module.isEnabled() && minecraft.level != null && minecraft.player != null && !minecraft.gui.hud.isHidden()
            && connection != null && connection.isConnected() && (module.singleplayer.get() || !minecraft.hasSingleplayerServer());
        long received = connection instanceof PacketActivitySource activity ? activity.lads$lastPacketNanos() : 0;
        return POLICY.update(connection, System.nanoTime(), received, eligible, minecraft.isPaused(),
            minecraft.options.screenEffectScale().get() <= 0, settings());
    }
    public static int warningSeconds() {
        var frame = frame(); return frame.progress() > 0 ? (int) Math.ceil(frame.seconds()) : 0;
    }
    public static void render(GuiGraphicsExtractor graphics) { renderFrame(graphics, frame()); }
    static void renderFrame(GuiGraphicsExtractor graphics, SignalLossPolicy.Frame frame) {
        if (frame.progress() <= 0) return;
        var minecraft = Minecraft.getInstance(); var module = module();
        long tenths = Math.round(frame.seconds() * 10);
        if (tenths != lastTenths) { lastTenths = tenths; warning = String.format(Locale.ROOT, "⚠ Waiting for server... (%.1fs)", frame.seconds()); }
        int width = minecraft.font.width(warning), height = minecraft.font.lineHeight;
        int x = switch (module.position.getIndex()) { case 0 -> 10; case 2 -> graphics.guiWidth() - width - 10; default -> (graphics.guiWidth() - width) / 2; };
        float eased = 1 - (1 - frame.progress()) * (1 - frame.progress());
        int hiddenY = -height - 17, y = Math.round(hiddenY + (10 - hiddenY) * eased);
        if (module.background.get()) graphics.fill(x - 6, y - 6, x + width + 6, y + height + 6,
            module.backgroundColor.isUseGlobal() ? HudSettings.getInstance().getGlobalBackground() : module.backgroundColor.getColor());
        graphics.text(minecraft.font, warning, x, y,
            module.textColor.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : module.textColor.getColor(), true);
    }
}
