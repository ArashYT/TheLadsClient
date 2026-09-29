package com.thelads.core.v26_2.feature;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.thelads.core.client.SignalLossPolicy;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.modules.SignalLossModule;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.slf4j.LoggerFactory;

/** Real native toast extraction plus isolated config IO; no fake packets enter a live connection. */
final class NativeSignalLossProbe {
    private static boolean done;
    private static int passed;
    static void tick() {
        if (done || !Boolean.getBoolean("thelads.verifyIntegrations") || !(Minecraft.getInstance().gui.screen() instanceof TitleScreen)) return;
        done = true;
        var module = NativeConnectionStatus.module(); boolean enabled = module.isEnabled(); long modified = module.getLastModified();
        var options = new LinkedHashMap<com.thelads.core.config.Option, com.google.gson.JsonElement>();
        module.getOptions().forEach(option -> options.put(option, option.save()));
        try {
            var game = FabricLoader.getInstance().getGameDir().toRealPath();
            check(game.getParent().getFileName().toString().equals("verification") && game.getParent().getParent().getFileName().toString().equals("artifacts"), "isolated directory");
            module.background.set(true); module.textColor.setUseGlobal(false); module.textColor.setColor(0xff123456);
            module.backgroundColor.setUseGlobal(false); module.backgroundColor.setColor(0x99112233);
            var frame = new SignalLossPolicy.Frame(1, 3.5, true, false);
            int[] x = new int[3];
            for (int i = 0; i < 3; i++) {
                module.position.setIndex(i); var state = extract(frame); var text = texts(state).getFirst();
                x[i] = field(text, "x"); check(field(text, "y") == 10, "fully shown toast uses top placement " + i);
                check(field(text, "color") == 0xff123456, "configured native text color " + i);
                check(elements(state) == 1, "configured toast background exists " + i);
            }
            check(x[0] == 10 && x[0] < x[1] && x[1] < x[2], "left center right produce distinct actual positions");
            module.background.set(false); var state = extract(frame); check(elements(state) == 0 && texts(state).size() == 1, "background switch retains text only");
            module.textColor.setUseGlobal(true); state = extract(frame);
            check(field(texts(state).getFirst(), "color") == HudSettings.getInstance().getGlobalColor(), "Lads global text color applies");
            state = extract(new SignalLossPolicy.Frame(.5f, 3.5, true, false));
            check(field(texts(state).getFirst(), "y") < 10, "intermediate animation frame slides above final position");
            state = extract(new SignalLossPolicy.Frame(0, 0, false, false)); check(texts(state).isEmpty() && elements(state) == 0, "hidden toast emits no geometry");
            module.setEnabled(false); check(NativeConnectionStatus.warningSeconds() == 0, "disabled production policy emits no warning");

            var directory = Files.createTempDirectory(game.getParent(), "signal-loss-");
            var legacy = directory.resolve("signalloss.json");
            String fixture = "{\"enabled\":false,\"timeoutThreshold\":1234,\"minWarningTime\":4567,\"lingerTime\":89,\"drawBackground\":false,\"showInSingleplayer\":true,\"toastPosition\":\"RIGHT\",\"textColor\":-15584170,\"backgroundColor\":-1726930381}";
            Files.writeString(legacy, fixture); var imported = new SignalLossModule(); SignalLossConfigIO.importLegacy(legacy, imported);
            check(Files.readString(legacy).equals(fixture), "legacy config preserved byte for byte");
            check(!imported.isEnabled() && imported.timeout.getValue().equals("1234") && imported.minimum.getValue().equals("4567") && imported.linger.getValue().equals("89"), "all timing and enabled fields imported");
            check(!imported.background.get() && imported.singleplayer.get() && imported.position.getIndex() == 2, "all visibility fields imported");
            check(imported.textColor.getColor() == -15584170 && imported.backgroundColor.getColor() == -1726930381, "both ARGB colors imported");
            SignalLossConfigIO.copy(imported, module); var nativeFile = directory.resolve("thelads_config.json");
            var data = ConfigManager.toJson(); Files.writeString(nativeFile, data.toString());
            SignalLossConfigIO.copy(new SignalLossModule(), module); SignalLossConfigIO.reloadNative(nativeFile, module);
            check(module.timeout.getValue().equals("1234") && module.singleplayer.get() && module.position.getIndex() == 2, "native saved section reloads completely");
            Files.writeString(legacy, "{\"enabled\":true,\"timeoutThreshold\":-1}");
            try { SignalLossConfigIO.importLegacy(legacy, module); throw new IllegalStateException("invalid timing accepted"); }
            catch (IllegalArgumentException expected) { check(module.timeout.getValue().equals("1234") && !module.isEnabled(), "invalid import cannot partially alter settings"); }
            check(NativeSignalLossCommands.parseColor("#12ab34") == 0xff12ab34, "RGB command value adds opaque alpha");
            check(NativeSignalLossCommands.parseColor("0x99112233") == 0x99112233, "ARGB command value preserves alpha");
            try { NativeSignalLossCommands.parseColor("#12345"); throw new IllegalStateException("invalid hex accepted"); }
            catch (IllegalArgumentException expected) { passed++; }
            var dispatcher = new CommandDispatcher<FabricClientCommandSource>(); NativeSignalLossCommands.register(dispatcher);
            var root = dispatcher.getRoot().getChild("signalloss");
            check(root.getChild("reload") != null && root.getChild("config").getChildren().size() == 10, "complete native config/reload command tree registers");
            LoggerFactory.getLogger("TheLadsCore").info("Lads native SignalLoss probe END: {} checks passed, 0 failed; native toast geometry and isolated config IO", passed);
        } catch (Throwable failure) { LoggerFactory.getLogger("TheLadsCore").error("Lads native SignalLoss probe FAILED after {} checks", passed, failure); }
        finally { options.forEach((option, value) -> option.load(value)); module.setEnabled(enabled); module.setLastModified(modified); }
    }
    private static GuiRenderState extract(SignalLossPolicy.Frame frame) { var state = new GuiRenderState(); NativeConnectionStatus.renderFrame(new GuiGraphicsExtractor(Minecraft.getInstance(), state, 0, 0), frame); return state; }
    private static ArrayList<GuiTextRenderState> texts(GuiRenderState state) { var text = new ArrayList<GuiTextRenderState>(); state.forEachText(text::add); return text; }
    private static int elements(GuiRenderState state) { int[] count = {0}; state.forEachElement(element -> count[0]++, GuiRenderState.TraverseRange.ALL); return count[0]; }
    private static int field(GuiTextRenderState text, String name) throws Exception { var field = GuiTextRenderState.class.getDeclaredField(name); field.setAccessible(true); return field.getInt(text); }
    private static void check(boolean condition, String description) { if (!condition) throw new IllegalStateException(description); passed++; }
}
