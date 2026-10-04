package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.ChatHeads;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ToggleNametagsModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world run with -Dthelads.verifyChatHeads=true): Chat Heads in the QA world, with the Chat module's timestamps on.
 * A second player, Lads_Tester (Lads nickname "Testy"), joins the client's tab list. Then: own chat sent to the integrated server
 * (signed, back with the sender), the tester's signed chat, server lines with rank prefixes and colour codes for the tester and for
 * the own player (long enough to wrap), a similar unknown name and a plain server line. Each message's head is checked, then
 * chatheads-1-closed, -2-open (chat screen), -3-aligned (Keep text aligned), -4-hudcap (HUD FPS cap 20: replayed HUD frames) and
 * -5-off (module off, vanilla chat) are saved. Everything is put back. With a chat_heads jar loaded it checks that Lads stood down.
 */
final class ChatHeadsCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final long PHASE = 1_500_000_000L;
    private static final String[] SHOTS = {"chatheads-1-closed", "chatheads-2-open", "chatheads-3-aligned", "chatheads-4-hudcap", "chatheads-5-off"};
    private static final GameProfile TESTER = new GameProfile(UUID.fromString("6f3b2c1a-0d4e-4f5a-9b8c-7d6e5f4a3b2c"), "Lads_Tester");
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<Module, Boolean> ENABLED = new LinkedHashMap<>();
    private static int step = -1, saved;
    private static long due;
    private static boolean capturing, hudCap;
    private static int hudLimit;
    private ChatHeadsCapture() {}

    static boolean requested() { return Boolean.getBoolean("thelads.verifyChatHeads"); }
    static boolean busy() { return step >= 0 && step < SHOTS.length; }
    /** Requested and not finished: the world capture waits for it, so the run does not end first. */
    static boolean pending() { return requested() && step < SHOTS.length; }

    static void tick(boolean ready) {
        if (step >= 0 || !ready || !requested()) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            for (String name : new String[] {ChatHeads.NAME, "Chat", "Nametags"}) {
                Module module = ModuleManager.getInstance().getModule(name);
                ENABLED.put(module, module.isEnabled());
                for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
            }
            hudCap = HudSettings.getInstance().isHudFpsCapEnabled();
            hudLimit = HudSettings.getInstance().getHudFpsLimit();
            Module heads = ModuleManager.getInstance().getModule(ChatHeads.NAME);
            heads.getOptions().forEach(Option::reset);
            heads.setEnabled(true);
            ((BoolOption) ModuleManager.getInstance().getModule("Chat").getOption("Timestamps")).set(true);
            ToggleNametagsModule tags = (ToggleNametagsModule) ModuleManager.getInstance().getModule("Nametags");
            tags.setEnabled(true);
            tags.nicknames.setValue("Lads_Tester=Testy");
            tabList(mc.getConnection(), true);
            ChatComponent chat = mc.gui.hud.getChat();
            chat.clearMessages(false);
            String own = mc.getUser().getName();
            mc.getConnection().sendChat("my own chat, signed and sent back by the server");
            mc.gui.chatListener().handlePlayerChatMessage(PlayerChatMessage.unsigned(TESTER.id(), "hi, this is a second player's signed chat"),
                TESTER, ChatType.bind(ChatType.CHAT, mc.level.registryAccess(), Component.literal(TESTER.name())));
            mc.gui.chatListener().handleSystemMessage(Component.literal("§c[Admin] §fLads_Tester§7: §fplugin chat with a rank and colour codes"), true);
            mc.gui.chatListener().handleSystemMessage(Component.empty().append(Component.literal("[VIP+] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(own).withStyle(ChatFormatting.WHITE)).append(Component.literal(" » a long plugin line from me that "
                    + "wraps onto more lines: only its first line has the head, the rest lines up after it")), true);
            mc.gui.chatListener().handleSystemMessage(Component.literal("Lads_Tester2 joined the game").withStyle(ChatFormatting.YELLOW), true);
            mc.gui.chatListener().handleSystemMessage(Component.literal("Server restarting in 5 minutes"), true);
            LOGGER.info("Lads chat heads capture BEGIN: {} shots; own player {}, second player {} (Testy)", SHOTS.length, own, TESTER.name());
            step = 0;
            due = System.nanoTime() + PHASE;
        } catch (Exception failure) {
            fail("start: " + failure);
            finish();
        }
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame). */
    static void frame(RenderTarget target, Path game) {
        if (step < 0 || step >= SHOTS.length || capturing || System.nanoTime() < due) return;
        capturing = true;
        if (step == 0) checkHeads();
        String name = SHOTS[step];
        try {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); saved++; LOGGER.info("Lads chat heads frame {}", output); }
                catch (Exception failure) { fail(name + ": " + failure); }
                finally { image.close(); Minecraft.getInstance().execute(ChatHeadsCapture::next); }
            });
        } catch (Exception failure) {
            fail(name + ": " + failure);
            next();
        }
    }

    /** The shot of this step is saved: set up the next one. */
    private static void next() {
        capturing = false;
        Minecraft mc = Minecraft.getInstance();
        Module heads = ModuleManager.getInstance().getModule(ChatHeads.NAME);
        try {
            switch (step) {
                case 0 -> mc.setScreenAndShow(new ChatScreen("", false));
                case 1 -> ((BoolOption) heads.getOption(ChatHeads.ALIGNED)).set(true);
                case 2 -> {
                    ((BoolOption) heads.getOption(ChatHeads.ALIGNED)).set(false);
                    mc.setScreenAndShow(null);
                    HudSettings.getInstance().setHudFpsCapEnabled(true);
                    HudSettings.getInstance().setHudFpsLimit(20);
                }
                case 3 -> {
                    HudSettings.getInstance().setHudFpsCapEnabled(hudCap);
                    HudSettings.getInstance().setHudFpsLimit(hudLimit);
                    heads.setEnabled(false);
                }
                default -> { finish(); return; }
            }
        } catch (Exception failure) { fail(SHOTS[step] + " next: " + failure); }
        step++;
        due = System.nanoTime() + PHASE;
    }

    /** Every message has the head it should: by sender for signed chat, by name for the rest, none without a player. */
    private static void checkHeads() {
        Minecraft mc = Minecraft.getInstance();
        try {
            var field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") List<GuiMessage> messages = (List<GuiMessage>) field.get(mc.gui.hud.getChat());
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("chat_heads")) {
                // A Chat Heads jar left in the mods folder: Lads stands down, so the shots show only that jar's heads.
                check(messages.stream().noneMatch(m -> ((NativeChatHeads.Sender) (Object) m).lads$head() != null), "chat_heads jar loaded: no message has a Lads head");
                return;
            }
            String own = texture(mc.getConnection().getPlayerInfo(mc.player.getUUID())::getSkin);
            String tester = texture(mc.getConnection().getPlayerInfo(TESTER.id())::getSkin);
            check(!own.equals(tester), "own and second player have different skins (" + own + ", " + tester + ")");
            check(messages.stream().anyMatch(m -> m.content().getString().contains("Testy§7: ")), "the rank line shows the Lads nickname Testy");
            expect(messages, "my own chat", own);
            expect(messages, "second player's signed chat", tester);
            expect(messages, "plugin chat with a rank", tester); // "Lads_Tester" was renamed "Testy" before its head was found
            expect(messages, "a long plugin line from me", own);
            expect(messages, "Lads_Tester2 joined", null);
            expect(messages, "Server restarting", null);
        } catch (Exception failure) { fail("heads: " + failure); }
    }

    private static void expect(List<GuiMessage> messages, String text, String texture) {
        for (GuiMessage message : messages) {
            if (!message.content().getString().contains(text)) continue;
            Supplier<PlayerSkin> head = ((NativeChatHeads.Sender) (Object) message).lads$head();
            String actual = head == null ? null : texture(head);
            check(java.util.Objects.equals(actual, texture), "\"" + message.content().getString() + "\" has head " + actual + " (expected " + texture + ")");
            return;
        }
        fail("no chat message containing \"" + text + "\"");
    }

    private static String texture(Supplier<PlayerSkin> skin) { return skin.get().body().texturePath().toString(); }

    /** Adds (or removes) the second player in the client's own tab list; the server never hears of it. */
    @SuppressWarnings("unchecked")
    private static void tabList(ClientPacketListener connection, boolean add) throws ReflectiveOperationException {
        var map = ClientPacketListener.class.getDeclaredField("playerInfoMap");
        var listed = ClientPacketListener.class.getDeclaredField("listedPlayers");
        map.setAccessible(true);
        listed.setAccessible(true);
        Map<UUID, PlayerInfo> infos = (Map<UUID, PlayerInfo>) map.get(connection);
        Set<PlayerInfo> shown = (Set<PlayerInfo>) listed.get(connection);
        if (add) {
            PlayerInfo info = new PlayerInfo(TESTER, false);
            infos.put(TESTER.id(), info);
            shown.add(info);
        } else {
            PlayerInfo info = infos.remove(TESTER.id());
            if (info != null) shown.remove(info);
        }
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ChatScreen) mc.setScreenAndShow(null);
        try { if (mc.getConnection() != null) tabList(mc.getConnection(), false); } catch (Exception failure) { fail("tab list: " + failure); }
        OPTIONS.forEach(Option::load);
        ENABLED.forEach(Module::setEnabled);
        HudSettings.getInstance().setHudFpsCapEnabled(hudCap);
        HudSettings.getInstance().setHudFpsLimit(hudLimit);
        step = SHOTS.length;
        if (FAILURES.isEmpty()) LOGGER.info("Lads chat heads capture END: {} frames saved, 0 failed", saved);
        else LOGGER.error("Lads chat heads capture FAILED: {}", String.join(" | ", FAILURES));
    }

    private static void check(boolean result, String description) {
        if (result) LOGGER.info("Lads chat heads capture PASS: {}", description);
        else fail(description);
    }
    private static void fail(String failure) {
        FAILURES.add(failure);
        LOGGER.error("Lads chat heads capture check failed: {}", failure);
    }
}
