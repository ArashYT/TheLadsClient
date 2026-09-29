package com.thelads.core.v26_2.feature;

import com.thelads.core.client.ReconnectPlan;
import com.thelads.core.modules.AutoReconnectModule;
import com.thelads.core.v26_2.gui.ReconnectActionsScreen26;
import com.thelads.core.v26_2.gui.ReconnectOptionsScreen26;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

/** Title-only, isolated QA. Every attempted reconnect invokes a counter; no network or world is opened. */
final class NativeReconnectProbe {
    private static boolean done;
    private static int passed;
    static void tick() {
        var mc = Minecraft.getInstance();
        if (done || !Boolean.getBoolean("thelads.verifyIntegrations") || !NativeReconnect.available()
            || !(mc.gui.screen() instanceof TitleScreen) || mc.getConnection() != null) return;
        done = true;
        var originalScreen = mc.gui.screen(); var originalSettings = NativeReconnect.settings();
        var module = NativeReconnect.module(); boolean enabled = module.isEnabled(); long modified = module.getLastModified();
        var options = new LinkedHashMap<com.thelads.core.config.Option, com.google.gson.JsonElement>();
        module.getOptions().forEach(option -> options.put(option, option.save()));
        Path oldFile = ReconnectSettings.verificationFile;
        try {
            Path game = FabricLoader.getInstance().getGameDir().toRealPath();
            check(game.getParent().getFileName().toString().equals("verification")
                && game.getParent().getParent().getFileName().toString().equals("artifacts"), "isolated verification directory");
            var directory = Files.createTempDirectory(game.getParent(), "native-reconnect-");
            ReconnectSettings.verificationFile = directory.resolve("reconnect.json");
            NativeReconnect.cancelAll();
            module.setEnabled(true); module.initial.set(true); module.infinite.set(false); module.reasonMode.setIndex(0);
            module.actionsEnabled.set(false);
            set("settings", new ReconnectSettings());
            Class.forName("net.minecraft.realms.RealmsConnect");
            check(true, "Realms capture mixin applies to actual class");
            int[] connects = {0}; mockTarget(connects, false);
            var parent = new JoinMultiplayerScreen(originalScreen);
            DisconnectedScreen screen = dialog(parent, Component.literal("QA disconnected"));
            check(screen instanceof ReconnectDialog && ((ReconnectDialog) screen).lads$hasReconnectControls(), "transformed disconnect screen owns native controls");
            check(button(screen, "Reconnect in ") != null && button(screen, "Cancel reconnect") != null, "actual retry and cancellation buttons created");
            var plan = (ReconnectPlan) get("PLAN"); check(plan.pending(), "eligible disconnect schedules countdown");
            int widgets = screen.children().size();
            screen.resize(320, 240); check(screen.children().size() == widgets, "resize does not duplicate controls");
            screen.keyPressed(new KeyEvent(256, 1, 0));
            check(mc.gui.screen() == screen && !plan.pending(), "first Escape cancels countdown and keeps reason visible");
            screen.keyPressed(new KeyEvent(256, 1, 0));
            check(mc.gui.screen() == parent && !NativeReconnect.canReconnect(), "second Escape returns to server list and clears target");
            check(connects[0] == 0, "cancelling never dispatches reconnect");

            mockTarget(connects, false); screen = dialog(parent, Component.literal("QA retry"));
            plan.cancel(); plan.schedule(List.of(1), false, System.nanoTime() - 2_000_000_000L);
            tickController(); check(connects[0] == 1 && plan.wasAutomatic(), "due countdown invokes mock target exactly once");
            tickController(); check(connects[0] == 1, "later client tick cannot repeat completed countdown");
            NativeReconnect.manual(); check(connects[0] == 2 && !plan.wasAutomatic(), "manual button resets automatic action eligibility");
            mc.gui.setScreen(parent); check(!NativeReconnect.canReconnect(), "manual navigation clears reconnect target");

            for (String reason : List.of("disconnect.loginFailedInfo", "disconnect.transfer", "multiplayer.disconnect.code_of_conduct")) {
                mockTarget(connects, false); dialog(parent, Component.translatable(reason));
                check(!plan.pending(), "protected disconnect never auto-retries: " + reason);
            }
            mockTarget(connects, false); dialog(parent, Component.translatable("multiplayer.disconnect.kicked"));
            check(!plan.pending(), "default reason filter suppresses kicked countdown");
            mockTarget(connects, true); screen = dialog(parent, Component.literal("QA world stopped"));
            check(((ReconnectDialog) screen).lads$reconnectParent() instanceof SelectWorldScreen, "local-world disconnect returns to actual world list");
            var worldParent = (SelectWorldScreen) ((ReconnectDialog) screen).lads$reconnectParent();
            isolateWorldListLoading(worldParent);
            NativeReconnect.cancelCountdown(); screen.keyPressed(new KeyEvent(256, 1, 0));
            check(mc.gui.screen() == worldParent && !NativeReconnect.canReconnect()
                && worldParent.children().stream().anyMatch(WorldSelectionList.class::isInstance),
                "local-world Escape initializes the exact repaired parent and clears retry state");

            NativeReconnect.cancelAll(); module.initial.set(false);
            var server = new ServerData("Original server", "localhost:25565", ServerData.Type.OTHER);
            var before = server.write().copy();
            NativeReconnect.server(ServerAddress.parseString(server.ip), server, null);
            check(!NativeReconnect.canReconnect(), "initial failure retry setting is respected");
            module.initial.set(true); check(NativeReconnect.canReconnect(), "initial failures become eligible only when enabled");
            check(server.write().equals(before), "capture leaves source server entry unchanged");
            server.ip = "manually-edited.invalid:25565"; server.name = "User edited";
            check(NativeReconnect.targetId().equals("localhost:25565"), "retry uses attempted server snapshot instead of mutating edited entries");
            module.setEnabled(false); tickController(); check(!NativeReconnect.canReconnect() && NativeReconnect.targetId().isEmpty(), "master off clears active lifecycle");
            module.setEnabled(true);

            configChecks(directory);
            set("settings", new ReconnectSettings());
            var editor = new ReconnectOptionsScreen26(originalScreen); mc.gui.setScreen(editor);
            var delays = editor.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
            delays.setValue("2, 4, 8"); editor.resize(420, 270); click(editor, "Save");
            check(NativeReconnect.settings().delays.equals(List.of(2, 4, 8)), "native retry editor saves values through actual button callback");
            check(mc.gui.screen() == originalScreen, "retry editor restores parent after saving");
            editor = new ReconnectOptionsScreen26(originalScreen); mc.gui.setScreen(editor);
            editor.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("-1");
            click(editor, "Save"); check(mc.gui.screen() == editor && NativeReconnect.settings().delays.equals(List.of(2, 4, 8)), "invalid delay leaves active settings unchanged");
            var state = new GuiRenderState(); editor.extractRenderState(new GuiGraphicsExtractor(mc, state, 0, 0), 0, 0, 0);
            int[] texts = {0}; state.forEachText(text -> texts[0]++); check(texts[0] > 5, "real retry editor extracts visible labels and fields");
            editor.onClose(); check(mc.gui.screen() == originalScreen, "editor cancel restores parent");

            var actions = new ReconnectActionsScreen26(originalScreen); mc.gui.setScreen(actions);
            click(actions, "Add"); check(actions.children().stream().anyMatch(MultiLineEditBox.class::isInstance), "actual actions editor creates message field");
            var edits = actions.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            edits.get(0).setValue("local-mock.invalid"); edits.get(1).setValue("2.5"); actions.resize(420, 270);
            edits = actions.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
            check(edits.get(0).getValue().equals("local-mock.invalid") && edits.get(1).getValue().equals("2.5"), "resizing preserves unsaved action editor text");
            click(actions, "Save");
            check(!module.actionsEnabled.get() && !NativeReconnect.settings().autoMessages.getFirst().enabled
                && NativeReconnect.settings().autoMessages.getFirst().messages.isEmpty(), "editing alone cannot activate actions or create messages");
            check(connects[0] == 2, "all screen/config checks used only mock target, no further reconnect");
            LoggerFactory.getLogger("TheLadsCore").info("Lads native reconnect probe END: {} checks passed, 0 failed; mock targets, no network/world reconnect or chat sends", passed);
        } catch (Throwable failed) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads native reconnect probe FAILED after {} checks", passed, failed);
        } finally {
            NativeReconnect.cancelAll(); ReconnectSettings.verificationFile = oldFile;
            try { set("settings", originalSettings); } catch (Exception ignored) {}
            options.forEach((option, value) -> option.load(value)); module.setEnabled(enabled); module.setLastModified(modified);
            mc.gui.setScreen(originalScreen);
        }
    }

    private static void isolateWorldListLoading(SelectWorldScreen parent) throws Exception {
        // In 26.2 an empty PLAY_WORLD list synchronously opens CreateWorldScreen during init.
        // Supply only its loading future, keeping the real parent/init/navigation under test.
        // UPLOAD_WORLD construction has no create-world side effect even in an empty saves folder.
        var seed = new WorldSelectionList.Builder(Minecraft.getInstance(), parent)
            .width(320).height(240).uploadWorld().build();
        try {
            var pending = WorldSelectionList.class.getDeclaredField("pendingLevels"); pending.setAccessible(true);
            pending.set(seed, new java.util.concurrent.CompletableFuture<List<net.minecraft.world.level.storage.LevelSummary>>());
            var list = SelectWorldScreen.class.getDeclaredField("list"); list.setAccessible(true); list.set(parent, seed);
        } finally {
            // The new list copies only pendingLevels from oldList; seed textures are not reused.
            seed.children().forEach(WorldSelectionList.Entry::close);
        }
    }

    private static void configChecks(Path directory) throws Exception {
        Path upstream = directory.resolve("autoreconnectrf.json"), nativeFile = directory.resolve("imported.json");
        String fixture = "{\"options\":{\"delays\":[2,5],\"initial\":true,\"infinite\":true,\"conditionType\":true,\"regexIds\":true,\"commandSigning\":true,\"conditionKeys\":[\"test.key\"],\"conditionPatterns\":[\"mock.*\"],\"autoMessages\":[{\"id\":\"mock.invalid\",\"delay\":2,\"messages\":[\"mock data only\"]}]}}";
        Files.writeString(upstream, fixture); var temporaryModule = new AutoReconnectModule();
        var imported = ReconnectSettings.load(temporaryModule, nativeFile, upstream, false);
        check(Files.readString(upstream).equals(fixture), "migration preserves upstream file byte for byte");
        check(imported.delays.equals(List.of(2, 5)) && temporaryModule.infinite.get() && temporaryModule.initial.get(), "migration preserves retry sequence and modes");
        check(temporaryModule.reasonMode.getIndex() == 1 && imported.conditionKeys.equals(List.of("test.key"))
            && imported.conditionPatterns.equals(List.of("mock.*")), "migration preserves reason conditions");
        check(temporaryModule.regexIds.get() && temporaryModule.signedCommands.get(), "migration preserves action matching/signing preferences");
        check(!temporaryModule.actionsEnabled.get() && !imported.autoMessages.getFirst().enabled, "imported messages remain inert until explicit activation");
        var reloaded = ReconnectSettings.load(temporaryModule, nativeFile, upstream, false);
        check(reloaded.autoMessages.getFirst().messages.equals(List.of("mock data only")) && reloaded.autoMessages.getFirst().delay == 2, "native disk round trip preserves complete action contents");
        var oversized = new ReconnectSettings(); var action = new ReconnectSettings.Action(); action.enabled = true;
        action.messages.add("x".repeat(257)); oversized.autoMessages.add(action); oversized.validate();
        check(!action.enabled && action.messages.getFirst().length() == 257, "oversized imported message is disabled without truncation");
        Files.writeString(nativeFile, "{bad json");
        var fallback = ReconnectSettings.load(temporaryModule, nativeFile, upstream, false);
        check(fallback.delays.equals(List.of(3, 10, 30, 60)) && Files.readString(nativeFile).equals("{bad json"), "malformed native file is preserved and uses safe defaults");
    }
    private static DisconnectedScreen dialog(Screen parent, Component reason) {
        var result = new DisconnectedScreen(parent, Component.literal("Lads QA"), reason);
        Minecraft.getInstance().gui.setScreen(result); return result;
    }
    private static void mockTarget(int[] connects, boolean local) throws Exception {
        NativeReconnect.cancelAll();
        set("target", new NativeReconnect.Target() {
            public String id() { return "mock.invalid"; } public void connect() { connects[0]++; } public boolean local() { return local; }
        });
        set("identity", Minecraft.getInstance().getUser().getProfileId()); set("connected", true);
    }
    private static Object get(String name) throws Exception { var field = NativeReconnect.class.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
    private static void set(String name, Object value) throws Exception { var field = NativeReconnect.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value); }
    private static void tickController() throws Exception { var method = NativeReconnect.class.getDeclaredMethod("tick", Minecraft.class); method.setAccessible(true); method.invoke(null, Minecraft.getInstance()); }
    private static Button button(Screen screen, String prefix) { return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button -> button.getMessage().getString().startsWith(prefix)).findFirst().orElse(null); }
    private static void click(Screen screen, String label) { var button = button(screen, label); if (button == null) throw new IllegalStateException("Missing button " + label); button.onPress(new KeyEvent(257, 28, 0)); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); passed++; }
}
