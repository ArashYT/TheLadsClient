package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.SprintTrace;
import com.thelads.core.modules.ToggleSprintModule;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-sprint" from the harness's LADS_VERIFY_CAPTURE_SPRINT): Toggle Sprint &amp; Sneak in the
 * QA world, in survival on a stone corridor QA builds (and takes away again). The Sprint key is tapped through
 * KeyboardHandler.keyPress, W held, then a wall, three mob hits, hunger 6, eating, blindness, shallow water, sneaking, flying,
 * a death and respawn, and a separate Toggle Sprint key (J). Every tick's sprint state and the state last sent to the server
 * go to screenshots/sprint-trace.csv (SprintTrace: no start-stop flicker); frames sprint-hud, sprint-controls,
 * sneak-controls and sprint-module are saved. Module, options, keys, game mode, food, effects and blocks are put back.
 */
final class SprintCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final SprintTrace TRACE = new SprintTrace();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final Map<BlockPos, BlockState> BLOCKS = new LinkedHashMap<>();
    private static final int LAST = 60;
    private static int step = -1, wait, saved;
    private static boolean enabledBefore, capturing;
    private static long modifiedBefore;
    private static GameType modeBefore;
    private static BlockPos origin;
    private static String shot;
    private static LocalPlayer before;
    private SprintCapture() {}

    static boolean busy() { return step >= 0 && step <= LAST; }

    /** Each client tick (START) of the auto-world run: starts once the world is ready and the request exists, then one step. */
    static void tick(Path game, boolean ready) {
        Minecraft mc = Minecraft.getInstance();
        if (step < 0) {
            Path request = game.resolve(".lads-qa-capture-sprint");
            if (!ready || !Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads sprint capture FAILED: request", failure); return; }
            step = 0;
        }
        if (step > LAST) return;
        if (mc.player != null && mc.level != null) TRACE.tick(mc.player.isSprinting(), sent(mc.player), detail(mc.player));
        if (--wait > 0 || shot != null) return;
        try {
            run(mc, game, step++);
        } catch (Exception | Error failure) {
            fail(failure.toString());
            LOGGER.error("Lads sprint capture step {} failed", step - 1, failure);
            step = LAST;
            wait = 1;
        }
    }

    private static void run(Minecraft mc, Path game, int at) throws Exception {
        ToggleSprintModule toggles = NativeFeatures.toggles();
        LocalPlayer player = mc.player;
        switch (at) {
            case 0 -> {
                enabledBefore = toggles.isEnabled();
                modifiedBefore = toggles.getLastModified();
                for (Option option : toggles.getOptions()) OPTIONS.put(option, option.save().deepCopy());
                LOGGER.info("Lads sprint capture BEGIN: at world load Toggle Sprint & Sneak is {}, sprint toggled={}, sneak toggled={} (thelads_config.json)",
                    enabledBefore ? "on" : "off", toggles.isSprintToggled(), toggles.isSneakToggled());
                toggles.getOptions().forEach(Option::reset); // Sprint Toggle, Sneak Vanilla, untoggled
                toggles.setEnabled(true);
                NativeWorldVerification.syntheticInput(true);
                origin = new BlockPos(player.getBlockX(), 200, player.getBlockZ()); // open sky: no terrain, water or mobs in the way
                server(sp -> {
                    modeBefore = sp.gameMode.getGameModeForPlayer();
                    sp.setGameMode(GameType.SURVIVAL);
                    sp.getFoodData().setFoodLevel(20);
                    sp.setHealth(sp.getMaxHealth());
                    ServerLevel level = sp.level();
                    for (int x = -3; x <= 3; x++) for (int z = -3; z <= 40; z++) for (int y = -1; y <= 3; y++)
                        set(level, origin.offset(x, y, z), y < 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                });
                wait = 20;
            }
            case 1 -> {
                reposition(player);
                check(!toggles.isSprintToggled(), "the QA run starts untoggled");
                TRACE.phase("toggle");
                tap(mc.options.keySprint);
                check(toggles.isSprintToggled(), "a tap of the Sprint key (Toggle Sprint unbound) toggles sprint on");
                String config = Files.readString(ClientPaths.getConfigFile().toPath(), StandardCharsets.UTF_8);
                check(config.contains("\"Sprint toggled\": true"), "the toggle is saved in thelads_config.json at once");
                wait = 5;
            }
            case 2 -> { TRACE.phase("run"); mc.options.keyUp.setDown(true); wait = 30; }
            case 3 -> {
                check(player.isSprinting() && TRACE.sent(), "walking forward with the toggle sprints, and the server was told");
                TRACE.phase("wall");
                int wallZ = (int) Math.floor(player.getZ()) + 4;
                server(sp -> { for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) set(sp.level(), new BlockPos(origin.getX() + x, origin.getY() + y, wallZ), Blocks.STONE.defaultBlockState()); });
                wait = 40;
            }
            case 4 -> {
                check(!player.isSprinting() && !TRACE.sent() && TRACE.phaseChanges() <= 1, "running into a wall stops the sprint once ("
                    + TRACE.phaseChanges() + " sprint packets)");
                restoreBlocks(true);
                reposition(player);
                TRACE.phase("after-wall");
                wait = 30;
            }
            case 5 -> { resumed("the wall gone"); TRACE.phase("hit"); wait = 1; }
            case 6, 7, 8 -> { hit(); wait = 15; }
            case 9 -> {
                check(player.isSprinting() && TRACE.sent(), "three mob hits (knockback) leave the toggled sprint going");
                reposition(player);
                TRACE.phase("hunger");
                server(sp -> sp.getFoodData().setFoodLevel(6));
                wait = 30;
            }
            case 10 -> {
                check(!player.isSprinting() && !TRACE.sent() && TRACE.phaseChanges() <= 1, "food 6 stops the sprint once (" + TRACE.phaseChanges() + " sprint packets)");
                server(sp -> sp.getFoodData().setFoodLevel(20));
                TRACE.phase("fed");
                wait = 30;
            }
            case 11 -> {
                resumed("food 20 again");
                reposition(player);
                server(sp -> { sp.getFoodData().setFoodLevel(10); sp.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BREAD, 4)); });
                wait = 10;
            }
            case 12 -> {
                TRACE.phase("eating");
                mc.options.keyUse.setDown(true);
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                wait = 25;
            }
            case 13 -> {
                check(player.isUsingItem(), "QA is eating while walking (" + TRACE.phaseChanges() + " sprint packets)");
                mc.options.keyUse.setDown(false);
                server(sp -> { sp.getFoodData().setFoodLevel(20); sp.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); });
                TRACE.phase("after-eating");
                wait = 30;
            }
            case 14 -> {
                resumed("eating ended");
                reposition(player);
                TRACE.phase("blindness");
                server(sp -> sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 400)));
                wait = 30;
            }
            case 15 -> {
                check(!player.isSprinting() && !TRACE.sent() && TRACE.phaseChanges() <= 1, "blindness stops the sprint once (" + TRACE.phaseChanges() + " sprint packets)");
                server(sp -> sp.removeEffect(MobEffects.BLINDNESS));
                TRACE.phase("sight");
                wait = 30;
            }
            case 16 -> {
                resumed("blindness gone");
                reposition(player);
                server(sp -> { for (int x = -2; x <= 2; x++) for (int z = 3; z <= 40; z++) set(sp.level(), origin.offset(x, 0, z), Blocks.WATER.defaultBlockState()); });
                TRACE.phase("water");
                wait = 40;
            }
            case 17 -> {
                check(!player.isSprinting() && !TRACE.sent() && TRACE.phaseChanges() <= 1, "shallow water stops the sprint once (" + TRACE.phaseChanges() + " sprint packets)");
                restoreBlocks(true);
                reposition(player);
                TRACE.phase("dry");
                wait = 30;
            }
            case 18 -> {
                resumed("out of the water");
                TRACE.phase("sneaking");
                mc.options.keyShift.setDown(true);
                wait = 25;
            }
            case 19 -> {
                check(!player.isSprinting() && !TRACE.sent() && TRACE.phaseChanges() <= 1, "holding Sneak pauses the toggled sprint once (" + TRACE.phaseChanges() + " sprint packets)");
                check(toggles.status().contains("[Sneaking (Key Held)]") && toggles.isSprintToggled(), "the HUD line shows the held sneak: " + toggles.status());
                mc.options.keyShift.setDown(false);
                reposition(player);
                TRACE.phase("unsneak");
                wait = 30;
            }
            case 20 -> {
                resumed("Sneak released");
                TRACE.phase("flying");
                server(sp -> { sp.setGameMode(GameType.CREATIVE); sp.getAbilities().flying = true; sp.onUpdateAbilities(); });
                wait = 5;
            }
            case 21 -> { player.getAbilities().flying = true; reposition(player); wait = 30; }
            case 22 -> {
                server(sp -> { sp.getAbilities().flying = false; sp.setGameMode(GameType.SURVIVAL); sp.onUpdateAbilities(); });
                player.getAbilities().flying = false;
                reposition(player);
                TRACE.phase("landed");
                wait = 30;
            }
            case 23 -> {
                resumed("flying ended");
                TRACE.phase("death");
                mc.options.keyUp.setDown(false);
                before = player;
                server(sp -> sp.kill(sp.level()));
                wait = 20;
            }
            case 24 -> {
                check(mc.gui.screen() instanceof DeathScreen, "QA died (death screen)");
                player.respawn();
                wait = 20;
            }
            case 25 -> {
                if (mc.gui.screen() instanceof DeathScreen) mc.gui.setScreen(null);
                check(mc.player != null && mc.player != before && mc.player.isAlive(), "QA respawned as a new player");
                check(toggles.isSprintToggled(), "the sprint toggle survived death and respawn (a new player, as a world, server or dimension change)");
                server(sp -> sp.setGameMode(GameType.SURVIVAL));
                reposition(mc.player);
                TRACE.phase("respawned");
                mc.options.keyUp.setDown(true);
                wait = 30;
            }
            case 26 -> {
                resumed("respawned");
                mc.options.keyUp.setDown(false);
                TRACE.phase("separate-key");
                NativeKeyBindings.TOGGLE_SPRINT.setKey(InputConstants.getKey("key.keyboard.j"));
                KeyMapping.resetMapping();
                tap(mc.options.keySprint);
                check(toggles.isSprintToggled(), "with Toggle Sprint on J, a Sprint tap no longer toggles");
                tap(NativeKeyBindings.TOGGLE_SPRINT);
                check(!toggles.isSprintToggled(), "J toggles sprint off");
                wait = 10;
            }
            case 27 -> {
                reposition(player);
                key(mc.options.keySprint, true);
                mc.options.keyUp.setDown(true);
                wait = 20;
            }
            case 28 -> {
                check(player.isSprinting() && !toggles.isSprintToggled(), "with the toggle off, holding Sprint (Left Control) sprints as in vanilla");
                key(mc.options.keySprint, false);
                mc.options.keyUp.setDown(false);
                tap(NativeKeyBindings.TOGGLE_SPRINT);
                check(toggles.isSprintToggled(), "J toggles sprint on again");
                wait = 10;
            }
            case 29 -> {
                screen(mc, new KeyBindsScreen(null, mc.options), "sprint");
                wait = 15;
                shot = "sprint-controls-bound";
            }
            case 30 -> {
                NativeKeyBindings.TOGGLE_SPRINT.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
                screen(mc, new KeyBindsScreen(null, mc.options), "sprint");
                wait = 15;
                shot = "sprint-controls";
            }
            case 31 -> {
                screen(mc, new KeyBindsScreen(null, mc.options), "sneak");
                wait = 15;
                shot = "sneak-controls";
            }
            case 32 -> {
                var menu = new com.thelads.core.v26_2.gui.LadsSettingsScreen26(null);
                mc.gui.setScreen(menu);
                menu.openModule(ToggleSprintModule.NAME);
                wait = 20;
                shot = "sprint-module";
            }
            case 33 -> {
                mc.gui.setScreen(null);
                reposition(player);
                mc.options.keyUp.setDown(true);
                TRACE.phase("hud");
                wait = 30;
            }
            case 34 -> {
                check(toggles.status().equals("[Sprinting (Toggled)]"), "the HUD line reads " + toggles.status());
                shot = "sprint-hud";
                wait = 2;
            }
            default -> finish(game);
        }
    }

    /** Each completed game frame (NativeWorldVerification.renderedFrame): saves the requested shot. */
    static void frame(RenderTarget target, Path game) {
        if (shot == null || capturing || wait > 0) return;
        capturing = true;
        String name = shot;
        try {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); saved++; LOGGER.info("Lads sprint frame {}", output); }
                catch (Exception failure) { fail(name + ": " + failure); }
                finally { image.close(); Minecraft.getInstance().execute(() -> { shot = null; capturing = false; }); }
            });
        } catch (Exception failure) {
            fail(name + ": " + failure);
            shot = null;
            capturing = false;
        }
    }

    private static void finish(Path game) {
        Minecraft mc = Minecraft.getInstance();
        TRACE.finish();
        mc.options.keyUp.setDown(false);
        mc.options.keyShift.setDown(false);
        mc.options.keyUse.setDown(false);
        mc.options.keySprint.setDown(false);
        NativeKeyBindings.TOGGLE_SPRINT.setKey(InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        if (mc.gui.screen() != null) mc.gui.setScreen(null);
        restoreBlocks(false);
        server(sp -> {
            sp.removeEffect(MobEffects.BLINDNESS);
            sp.getFoodData().setFoodLevel(20);
            sp.setHealth(sp.getMaxHealth());
            if (modeBefore != null) sp.setGameMode(modeBefore);
        });
        ToggleSprintModule toggles = NativeFeatures.toggles();
        OPTIONS.forEach(Option::load);
        toggles.setEnabled(enabledBefore);
        toggles.setLastModified(modifiedBefore);
        ConfigManager.save();
        NativeWorldVerification.syntheticInput(false);
        FAILURES.addAll(TRACE.failures());
        try { Files.writeString(game.resolve("screenshots").resolve("sprint-trace.csv"), TRACE.csv(), StandardCharsets.UTF_8); }
        catch (Exception failure) { FAILURES.add("sprint-trace.csv: " + failure); }
        step = LAST + 1;
        if (FAILURES.isEmpty()) LOGGER.info("Lads sprint capture END: {} frames saved, 0 failed; {}", saved, TRACE.summary());
        else LOGGER.error("Lads sprint capture FAILED: {} | {}", String.join(" | ", FAILURES), TRACE.summary());
    }

    private static void resumed(String why) {
        LocalPlayer player = Minecraft.getInstance().player;
        check(player.isSprinting() && TRACE.sent() && TRACE.phaseChanges() <= 1, why + ", the toggled sprint starts again by itself ("
            + TRACE.phaseChanges() + " sprint packets)");
    }

    /** A zombie's blow from in front: the real damage and knockback path. */
    private static void hit() {
        server(sp -> {
            var zombie = EntityTypes.ZOMBIE.create(sp.level(), EntitySpawnReason.COMMAND);
            zombie.setPos(sp.getX(), sp.getY(), sp.getZ() + 1.5);
            sp.hurtServer(sp.level(), sp.level().damageSources().mobAttack(zombie), 1.0f);
            sp.setHealth(sp.getMaxHealth());
        });
    }

    /** Back to the corridor's start, facing along it (+Z, yaw 0), still: the server moves the player. */
    private static void reposition(LocalPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.setYRot(0);
        player.setXRot(0);
        server(sp -> sp.teleportTo(sp.level(), origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, java.util.Set.of(), 0, 0, false));
    }

    /** Server thread: one block, remembering what was there first (flag 2: no neighbour updates, so water stays put). */
    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        synchronized (BLOCKS) { BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos)); }
        level.setBlock(pos, state, 2);
    }

    /** Corridor only: QA's wall and water go back to air; everything: the world as QA found it. */
    private static void restoreBlocks(boolean corridor) {
        server(sp -> {
            synchronized (BLOCKS) {
                for (var entry : BLOCKS.entrySet()) {
                    BlockPos pos = entry.getKey();
                    boolean floor = pos.getY() < origin.getY();
                    if (!corridor) sp.level().setBlock(pos, entry.getValue(), 2);
                    else if (!floor) sp.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                if (!corridor) BLOCKS.clear();
            }
        });
    }

    private static void server(Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) action.accept(sp);
        });
    }

    private static void screen(Minecraft mc, KeyBindsScreen screen, String search) {
        mc.gui.setScreen(screen);
        var current = mc.gui.screen();
        current.children().stream().filter(c -> c instanceof EditBox).map(c -> (EditBox) c).findFirst()
            .orElseThrow(() -> new IllegalStateException("no Controls search box")).setValue(search);
    }

    private static void tap(KeyMapping mapping) throws ReflectiveOperationException {
        key(mapping, true);
        key(mapping, false);
    }

    /** One key event through the game's own KeyboardHandler.keyPress (NativeFeatures.key sees it as a typed one). */
    private static void key(KeyMapping mapping, boolean down) throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        int key = InputConstants.getKey(mapping.saveString()).getValue();
        Method press = KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
        press.setAccessible(true);
        press.invoke(mc.keyboardHandler, mc.getWindow().handle(), down ? InputConstants.PRESS : InputConstants.RELEASE,
            new KeyEvent(key, 0, 0)); // 26.3 (SDL): the physical key and no character
    }

    /** The sprint state LocalPlayer last sent (its START/STOP_SPRINTING packets). */
    private static boolean sent(LocalPlayer player) {
        return ((com.thelads.core.v26_2.mixin.LocalPlayerAccessor) player).ladsSentSprint();
    }

    private static String detail(LocalPlayer player) {
        return "collision=" + player.horizontalCollision + " food=" + player.getFoodData().getFoodLevel() + " using=" + player.isUsingItem()
            + " sneak=" + player.isShiftKeyDown() + " water=" + player.isInWater() + " flying=" + player.getAbilities().flying + " z=" + (float) player.getZ()
            + " toggled=" + NativeFeatures.toggles().isSprintToggled();
    }

    private static void check(boolean result, String description) {
        if (result) LOGGER.info("Lads sprint capture PASS: {}", description);
        else fail(description);
    }

    private static void fail(String failure) {
        FAILURES.add(failure);
        LOGGER.error("Lads sprint capture check failed: {}", failure);
    }
}
