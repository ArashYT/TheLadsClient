package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge.VoiceChatState;
import com.thelads.core.client.bridge.LadsGameBridge.VoiceMember;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-hud170" from the harness's LADS_VERIFY_CAPTURE_HUD170): the 1.7.0 HUD lane in the QA
 * world. hud170-hud: Hotbar Slots armour (damaged helmet, no chestplate, leggings, damaged boots), the scoreboard in its own
 * colours with a red team, one boss bar. hud170-chat-scrolled: 150 lines, scrolled 130 up. hud170-cap-1..3: the minimap under a
 * 30 FPS HUD cap. hud170-autohide-*: hidden and half faded, capped and not. hud170-voice-*: Simple Voice Chat's real state, then a
 * sample. Server-side changes (armour, scoreboard, team, boss bar) and every setting are put back.
 */
final class Hud170Capture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] MODULES = {"ArmorHUD", "Scoreboard", "BossBar", "Autohide", "Chat", "Voice Chat", "Voice Chat Group", "Minimap"};
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<Module, Boolean> ENABLED = new LinkedHashMap<>();
    private static final Map<EquipmentSlot, ItemStack> WORN = new LinkedHashMap<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static int step = -1, passed, saved;
    private static long due;
    private static String shot;
    private static boolean capturing, capWas;
    private static int limitWas;
    private static Object svcStates, svcOnboarded;

    private Hud170Capture() {}

    static boolean busy() { return step >= 0 && step < 99; }

    static void tick(Path game, boolean ready) {
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-hud170");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads HUD 1.7.0 capture FAILED: request", failure); return; }
        Minecraft mc = Minecraft.getInstance();
        for (String name : MODULES) {
            Module module = NativeQualityOfLife.module(name);
            ENABLED.put(module, module.isEnabled());
            for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
            module.getOptions().forEach(Option::reset);
            module.setEnabled(!name.equals("Autohide"));
        }
        capWas = HudSettings.getInstance().isHudFpsCapEnabled();
        limitWas = HudSettings.getInstance().getHudFpsLimit();
        for (EquipmentSlot slot : ARMOUR) WORN.put(slot, mc.player.getItemBySlot(slot).copy());
        ItemStack helmet = new ItemStack(Items.TURTLE_HELMET), boots = new ItemStack(Items.GOLDEN_BOOTS);
        helmet.setDamageValue(helmet.getMaxDamage() * 9 / 10);
        boots.setDamageValue(boots.getMaxDamage() / 5);
        wear(helmet, ItemStack.EMPTY, new ItemStack(Items.IRON_LEGGINGS), boots);
        for (String command : new String[] {"scoreboard objectives add lads dummy {\"text\":\"Lads QA\",\"color\":\"gold\"}",
            "scoreboard objectives setdisplay sidebar lads", "team add qared", "team modify qared color red", "team join qared Redstone",
            "scoreboard players set Redstone lads 3", "scoreboard players set Plain lads 2", "bossbar add lads:qa \"QA Boss\"",
            "bossbar set lads:qa players @a", "bossbar set lads:qa value 60"}) command(command);
        LOGGER.info("Lads HUD 1.7.0 capture BEGIN: armour, scoreboard, boss bar, chat, HUD cap, Autohide, Voice Chat");
        step = 0;
        at(2000, "hud170-hud");
    }

    static void frame(RenderTarget target, Path game) {
        if (!busy() || capturing || System.nanoTime() < due) return;
        if (shot == null) { next(); return; }
        capturing = true;
        String name = shot;
        try {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); saved++; LOGGER.info("Lads HUD 1.7.0 frame {}", output); }
                catch (Exception failure) { fail(name + ": " + failure); }
                finally { image.close(); Minecraft.getInstance().execute(Hud170Capture::next); }
            });
        } catch (Exception failure) {
            fail(name + ": " + failure);
            next();
        }
    }

    private static void next() {
        capturing = false;
        Minecraft mc = Minecraft.getInstance();
        try {
            switch (step++) {
                case 0 -> {
                    LadsGameBridge.ScoreboardSnapshot board = LadsGameBridge.get().getScoreboard();
                    check(board != null && board.title().contains("§6") && board.lines().get(0).name().contains("§c"),
                        "Scoreboard keeps the gold title and the red team colour: " + board);
                    check(LadsGameBridge.get().getArmor().size() == 3, "Armor HUD: the server's helmet, leggings and boots reached the client");
                    for (int i = 1; i <= 150; i++) mc.gui.hud.getChat().addClientSystemMessage(Component.literal("QA chat line " + i));
                    mc.setScreenAndShow(new net.minecraft.client.gui.screens.ChatScreen("", false));
                    mc.gui.hud.getChat().scrollChat(130);
                    at(800, "hud170-chat-scrolled");
                }
                case 1 -> {
                    var chat = mc.gui.hud.getChat();
                    int scrolled = field(chat, "chatScrollbarPos");
                    check(scrolled == 130, "Infinite History: the chat scrolls 130 lines up, past the old 100-line cap (" + scrolled + ")");
                    chat.rescaleChat(); // as a window resize: lays out only the lines near the newest message
                    int laidOut = lines(chat, "trimmedMessages"), kept = lines(chat, "allMessages");
                    check(laidOut < kept && laidOut >= chat.getLinesPerPage(), "Infinite History: a rescale lays out " + laidOut + " of " + kept + " messages' lines");
                    chat.scrollChat(140);
                    check(field(chat, "chatScrollbarPos") > laidOut - chat.getLinesPerPage(), "Infinite History: scrolling up lays out older messages as it goes ("
                        + field(chat, "chatScrollbarPos") + ", " + lines(chat, "trimmedMessages") + " lines)");
                    chat.resetChatScroll();
                    mc.setScreenAndShow(null);
                    HudSettings.getInstance().setHudFpsCapEnabled(true);
                    HudSettings.getInstance().setHudFpsLimit(30);
                    HudCapture.replayBlits = 0;
                    at(1500, "hud170-cap-1");
                }
                case 2 -> at(0, "hud170-cap-2");
                case 3 -> at(0, "hud170-cap-3");
                case 4 -> {
                    boolean minimap = MinimapIntegration.available();
                    check(!minimap || HudCapture.replayBlits > 20, "HUD cap: the minimap is blitted on replayed frames ("
                        + HudCapture.replayBlits + " replayed picture blits, minimap " + (minimap ? "loaded" : "absent") + ")");
                    Module autohide = NativeQualityOfLife.module("Autohide");
                    autohide.setEnabled(true);
                    ((BoolOption) autohide.getOption("Show when hurt or hungry")).set(false);
                    ((BoolOption) autohide.getOption("Show while moving")).set(false);
                    ((SliderOption) autohide.getOption("Fade milliseconds")).setValue(0);
                    at(400, null);
                }
                case 5 -> { NativeAutohide.idle(0); at(600, "hud170-autohide-hidden-capped"); }
                case 6 -> {
                    check(NativeAutohide.level() == 0, "Autohide: idle and hidden under the HUD cap");
                    ((SliderOption) NativeQualityOfLife.module("Autohide").getOption("Fade milliseconds")).setValue(1500);
                    NativeAutohide.idle(0.6f);
                    at(80, "hud170-autohide-fading-capped");
                }
                case 7 -> {
                    HudSettings.getInstance().setHudFpsCapEnabled(false);
                    ((SliderOption) NativeQualityOfLife.module("Autohide").getOption("Fade milliseconds")).setValue(0);
                    NativeAutohide.idle(0);
                    at(600, "hud170-autohide-hidden");
                }
                case 8 -> {
                    NativeQualityOfLife.module("Autohide").setEnabled(false);
                    VoiceChatState real = VoiceChatIntegration.state();
                    LOGGER.info("Lads HUD 1.7.0 capture: Simple Voice Chat loaded {}, state {}", VoiceChatIntegration.loaded(), real);
                    check(!VoiceChatIntegration.loaded() || real != null, "Voice Chat: Simple Voice Chat's HUD state reads through reflection");
                    // Simple Voice Chat shows no HUD icons (its own or, mirroring it, ours) until its onboarding is done: done, in memory.
                    svcOnboarded = svcSetting("onboardingFinished", true);
                    svcStates = svc("de.maxhenkel.voicechat.voice.client.ClientManager", "getPlayerStateManager");
                    if (svcStates != null) svcStates.getClass().getMethod("setDisabled", boolean.class).invoke(svcStates, true);
                    VoiceChatIntegration.suppressed = 0;
                    at(1500, "hud170-voice-svc");
                }
                case 9 -> {
                    VoiceChatState deafened = VoiceChatIntegration.state();
                    check(!VoiceChatIntegration.loaded() || deafened != null && "voicechat:icons/speaker_off".equals(deafened.icon())
                        && VoiceChatIntegration.suppressed > 0, "Voice Chat: deafened, the Lads element shows " + deafened
                        + " and Simple Voice Chat's own icon is held back (" + VoiceChatIntegration.suppressed + " times)");
                    restoreOnboarding(); // before setDisabled saves Simple Voice Chat's config
                    if (svcStates != null) svcStates.getClass().getMethod("setDisabled", boolean.class).invoke(svcStates, false);
                    VoiceChatIntegration.qa = new VoiceChatState("voicechat:icons/microphone",
                        List.of(new VoiceMember("Steve", "8667ba71-b85a-4004-af54-457a9734eed7", true, false),
                            new VoiceMember("Alex", "ec561538-f3fd-461d-aff5-086b22154bce", false, true)));
                    at(800, "hud170-voice-sample");
                }
                default -> finish();
            }
        } catch (Throwable failure) {
            fail("step " + step + ": " + failure);
            finish();
        }
    }

    private static void finish() {
        step = 99;
        VoiceChatIntegration.qa = null;
        restoreOnboarding();
        OPTIONS.forEach(Option::load);
        ENABLED.forEach(Module::setEnabled);
        HudSettings.getInstance().setHudFpsCapEnabled(capWas);
        HudSettings.getInstance().setHudFpsLimit(limitWas);
        if (Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) Minecraft.getInstance().setScreenAndShow(null);
        wear(WORN.get(EquipmentSlot.HEAD), WORN.get(EquipmentSlot.CHEST), WORN.get(EquipmentSlot.LEGS), WORN.get(EquipmentSlot.FEET));
        for (String command : new String[] {"bossbar remove lads:qa", "scoreboard objectives remove lads", "team remove qared"}) command(command);
        LOGGER.info("Lads HUD 1.7.0 capture END: {} passed, {} failed, {} frames saved{}", passed, FAILURES.size(), saved,
            FAILURES.isEmpty() ? "" : "; " + FAILURES);
    }

    private static void at(long ms, String name) {
        due = System.nanoTime() + ms * 1_000_000L;
        shot = name;
    }

    private static void wear(ItemStack... pieces) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        var id = Minecraft.getInstance().player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) for (int i = 0; i < ARMOUR.length; i++) player.setItemSlot(ARMOUR[i], pieces[i]);
        });
    }

    private static void command(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static int field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(owner);
    }

    private static int lines(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return ((java.util.List<?>) field.get(owner)).size();
    }

    /** Sets a Simple Voice Chat client setting in memory; its previous value, or null without Simple Voice Chat. */
    private static Object svcSetting(String name, Object value) {
        try {
            Object config = Class.forName("de.maxhenkel.voicechat.VoicechatClient").getField("CLIENT_CONFIG").get(null);
            Object entry = config.getClass().getField(name).get(config);
            Class<?> type = Class.forName("de.maxhenkel.voicechat.configbuilder.entry.ConfigEntry");
            Object previous = type.getMethod("get").invoke(entry);
            type.getMethod("set", Object.class).invoke(entry, value);
            return previous;
        } catch (ReflectiveOperationException | RuntimeException absent) { return null; }
    }

    private static void restoreOnboarding() {
        if (svcOnboarded != null) svcSetting("onboardingFinished", svcOnboarded);
        svcOnboarded = null;
    }

    private static Object svc(String type, String method) {
        try { return Class.forName(type).getMethod(method).invoke(null); } catch (ReflectiveOperationException | RuntimeException absent) { return null; }
    }

    private static void check(boolean ok, String what) {
        if (ok) { passed++; LOGGER.info("Lads HUD 1.7.0 capture PASS: {}", what); }
        else fail(what);
    }

    private static void fail(String what) {
        FAILURES.add(what);
        LOGGER.error("Lads HUD 1.7.0 capture FAILED: {}", what);
    }
}
