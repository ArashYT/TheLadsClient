package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.hud.PaperDoll;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-raised" from the harness's LADS_VERIFY_CAPTURE_RAISED): Raised and the paper doll in the
 * QA world. Survival for the hearts and hunger: raised-1-default, raised-2-chat-open, raised-3-changed (hotbar 12, chat 24, an
 * action bar message). Creative for the doll: doll-1-sprint, doll-2-sneak, doll-3-fly, doll-4-eat, doll-5-opacity-50 and
 * doll-6-editor (moved and scaled in the HUD editor). Keys, settings, game mode and the held item are put back.
 */
final class RaisedDollCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String[] SHOTS = {"raised-1-default", "raised-2-chat-open", "raised-3-changed", "doll-1-sprint", "doll-2-sneak",
        "doll-3-fly", "doll-4-eat", "doll-5-opacity-50", "doll-6-editor"};
    private static final long[] WAIT_MS = {2500, 1200, 1500, 700, 900, 2600, 800, 800, 1500};
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static int step = -1, saved;
    private static long due;
    private static boolean capturing, raisedWas, dollWas;
    private static int[] positionWas;
    private static String gameModeWas;
    private static ItemStack heldWas = ItemStack.EMPTY;
    private RaisedDollCapture() {}

    static boolean busy() { return step >= 0 && step < SHOTS.length; }

    private static Module raised() { return NativeQualityOfLife.module("Raised"); }
    private static Module doll() { return NativeQualityOfLife.module("Paperdoll"); }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists. */
    static void tick(Path game, boolean ready) {
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-raised");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads raised capture FAILED: request", failure); return; }
        Minecraft mc = Minecraft.getInstance();
        for (Module module : new Module[] {raised(), doll()}) for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
        raisedWas = raised().isEnabled();
        dollWas = doll().isEnabled();
        positionWas = HudSettings.getInstance().getPosition("Paperdoll");
        gameModeWas = mc.gameMode.getPlayerMode().getName();
        heldWas = mc.player.getMainHandItem().copy();
        raised().getOptions().forEach(Option::reset);
        raised().setEnabled(true);
        doll().setEnabled(false);
        command("gamemode survival @a"); // hearts, hunger and air sit on the hotbar
        LOGGER.info("Lads raised capture BEGIN: {} frames", SHOTS.length);
        step = 0;
        due = System.nanoTime() + WAIT_MS[0] * 1_000_000L;
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame). */
    static void frame(RenderTarget target, Path game) {
        if (!busy() || capturing) return;
        // Kept up every frame: sprinting ends at a wall or the water's surface, flying when the player touches the ground.
        if (step == 3) Minecraft.getInstance().player.setSprinting(true);
        if (step == 5) Minecraft.getInstance().player.getAbilities().flying = true;
        if (System.nanoTime() < due) return;
        capturing = true;
        String name = SHOTS[step];
        try {
            verify(Minecraft.getInstance());
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); saved++; LOGGER.info("Lads raised frame {}", output); }
                catch (Exception failure) { FAILURES.add(name + ": " + failure); }
                finally { image.close(); Minecraft.getInstance().execute(RaisedDollCapture::next); }
            });
        } catch (Exception failure) {
            FAILURES.add(name + ": " + failure);
            next();
        }
    }

    /** What the frame about to be saved must show. */
    private static void verify(Minecraft mc) {
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        switch (step) {
            case 0 -> check(Raised26.hotbar() == 2 && Raised26.chat() == 0, "Raised: the hotbar group sits 2 px off the bottom, chat where vanilla draws it");
            case 1 -> check(mc.gui.screen() instanceof ChatScreen && Raised26.hotbar() == 16, "Raised: with chat open the hotbar group clears the chat box (2 + Distance 14)");
            case 2 -> check(Raised26.hotbar() == 12 && Raised26.chat() == 24, "Raised: changed sliders reach the game (hotbar 12, chat 24)");
            case 3 -> check(mc.player.isSprinting() && PaperDoll.INSTANCE.visible(firstPerson), "Paper doll: sprinting brings it up");
            case 4 -> check(mc.player.isCrouching() && PaperDoll.INSTANCE.visible(firstPerson), "Paper doll: crouching keeps it up, crouched");
            case 5 -> check(mc.player.getAbilities().flying && PaperDoll.INSTANCE.visible(firstPerson), "Paper doll: creative flying (switched on) keeps it up");
            case 6 -> check(mc.player.isUsingItem() && PaperDoll.INSTANCE.visible(firstPerson), "Paper doll: eating (using items switched on) keeps it up");
            case 7 -> check(PaperDoll.INSTANCE.opacity() == 0.5f, "Paper doll: Model Opacity 50");
            case 8 -> check(mc.gui.screen() instanceof com.thelads.core.v26_2.gui.DraggableHudScreen26, "the HUD editor shows the doll where it was moved, scaled up");
            default -> { }
        }
    }

    /** The frame of this step is saved: set up the next one. */
    private static void next() {
        capturing = false;
        Minecraft mc = Minecraft.getInstance();
        var options = mc.options;
        try {
            switch (step) {
                case 0 -> mc.setScreenAndShow(new ChatScreen("", false));
                case 1 -> {
                    mc.setScreenAndShow(null);
                    ((SliderOption) raised().getOption("Hotbar")).setValue(12);
                    ((SliderOption) raised().getOption("Chat")).setValue(24);
                    mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Lads Raised QA: chat 24 px up"));
                    mc.gui.hud.setOverlayMessage(Component.literal("Lads Raised QA: action bar 12 px up"), false);
                }
                case 2 -> {
                    raised().getOptions().forEach(Option::reset);
                    doll().getOptions().forEach(Option::reset);
                    doll().setEnabled(true);
                    command("gamemode creative @a");
                    // Sprinting lasts while moving forward: forward held, sprint started as the sprint key would.
                    options.keyUp.setDown(true);
                    mc.player.setSprinting(true);
                }
                case 3 -> {
                    options.keyUp.setDown(false);
                    mc.player.setSprinting(false);
                    options.keyShift.setDown(true);
                }
                case 4 -> {
                    options.keyShift.setDown(false);
                    ((BoolOption) doll().getOption("Creative Flying")).set(true);
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                }
                case 5 -> {
                    mc.player.getAbilities().flying = false;
                    mc.player.onUpdateAbilities();
                    ((BoolOption) doll().getOption("Using Items")).set(true);
                    hold(new ItemStack(Items.GOLDEN_APPLE));
                    options.keyUse.setDown(true);
                }
                case 6 -> {
                    options.keyUse.setDown(false);
                    hold(heldWas);
                    ((SliderOption) doll().getOption("Model Opacity")).setValue(50);
                    ((BoolOption) doll().getOption("Always Display")).set(true);
                }
                case 7 -> {
                    ((SliderOption) doll().getOption("Model Opacity")).setValue(100);
                    ((SliderOption) doll().getOption("Model Scale")).setValue(6);
                    HudSettings.getInstance().setPosition("Paperdoll", 40, 40);
                    mc.setScreenAndShow(new com.thelads.core.v26_2.gui.DraggableHudScreen26(null, new DraggableHudScreen(() -> { })));
                }
                default -> {
                    finish();
                    return;
                }
            }
        } catch (Exception failure) {
            FAILURES.add(SHOTS[step] + " setup: " + failure);
        }
        step++;
        due = System.nanoTime() + WAIT_MS[step] * 1_000_000L;
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping key : new KeyMapping[] {mc.options.keyUp, mc.options.keySprint, mc.options.keyShift, mc.options.keyUse}) key.setDown(false);
        if (mc.gui.screen() != null) mc.setScreenAndShow(null);
        OPTIONS.forEach(Option::load);
        raised().setEnabled(raisedWas);
        doll().setEnabled(dollWas);
        if (positionWas == null) HudSettings.getInstance().getPositions().remove("Paperdoll");
        else HudSettings.getInstance().setPosition("Paperdoll", positionWas[0], positionWas[1]);
        hold(heldWas);
        command("gamemode " + gameModeWas + " @a");
        step = SHOTS.length;
        if (FAILURES.isEmpty()) LOGGER.info("Lads raised capture END: {} frames saved, 0 failed", saved);
        else LOGGER.error("Lads raised capture FAILED: {}", String.join(" | ", FAILURES));
    }

    private static void hold(ItemStack stack) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        var id = Minecraft.getInstance().player.getUUID();
        var copy = stack.copy();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) player.setItemInHand(InteractionHand.MAIN_HAND, copy);
        });
    }

    private static void command(String command) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void check(boolean result, String description) {
        if (result) LOGGER.info("Lads raised capture PASS: {}", description);
        else {
            FAILURES.add(description);
            LOGGER.error("Lads raised capture check failed: {}", description);
        }
    }
}
