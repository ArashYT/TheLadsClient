package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.CustomFovModule;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-f3fov" from the harness's LADS_VERIFY_CAPTURE_F3FOV). Better F3: the debug screen with
 * the module off (the game's own), mid slide-in, with its defaults, with Hide Inessential off, and with System, World and the
 * background off (f3-*.png). Custom FOV: the player's FOV modifier from the game's own getFieldOfViewModifier (FOV Effects 100%)
 * standing, sprinting, flying, with Speed II (given by the integrated server) and with a fully drawn bow, at 0% and 100% of that
 * change, plus the world FOV the camera settled at with Speed II and the bow; screenshots/custom-fov.csv. All restored.
 */
final class F3FovCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private record Step(String shot, long waitMs, Runnable action) {}
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<Option, JsonElement> SAVED = new LinkedHashMap<>();
    private static final StringBuilder CSV = new StringBuilder("state,share,fov_modifier,expected_modifier,world_fov\n");
    private static List<Step> steps;
    private static int step = -1, saved, checks;
    private static long due;
    private static boolean shotPending, shooting, f3Enabled, fovEnabled;
    private static ItemStack heldBefore = ItemStack.EMPTY;
    private F3FovCapture() {}

    static boolean busy() { return step >= 0 && steps != null && step < steps.size(); }

    static void tick(Path game, boolean ready) {
        if (step < 0) {
            Path request = game.resolve(".lads-qa-capture-f3fov");
            if (!ready || !Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads F3/FOV capture FAILED: request", failure); return; }
            steps = plan(game);
            LOGGER.info("Lads F3/FOV capture BEGIN: {} steps", steps.size());
            step = -1;
            advance(game);
        } else if (busy() && !shotPending && System.nanoTime() >= due) {
            if (steps.get(step).shot() != null) shotPending = true;
            else advance(game);
        }
    }

    static void frame(RenderTarget target, Path game) {
        if (!shotPending || shooting) return;
        shooting = true;
        String name = steps.get(step).shot();
        try {
            Path folder = game.resolve("screenshots");
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(game)) throw new java.io.IOException("QA screenshot folder is outside the isolated game directory");
            Path output = folder.resolve(name + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try { image.writeToFile(output); saved++; LOGGER.info("Lads F3/FOV frame {}", output); }
                catch (Exception failure) { fail(name + ": " + failure); }
                finally { image.close(); Minecraft.getInstance().execute(() -> { shooting = shotPending = false; advance(game); }); }
            });
        } catch (Exception failure) {
            fail(name + ": " + failure);
            shooting = shotPending = false;
            advance(game);
        }
    }

    private static void advance(Path game) {
        if (++step >= steps.size()) return;
        try { steps.get(step).action().run(); } catch (Exception failure) { fail("step " + step + ": " + failure); }
        due = System.nanoTime() + steps.get(step).waitMs() * 1_000_000L;
    }

    private static List<Step> plan(Path game) {
        Minecraft mc = Minecraft.getInstance();
        Module f3 = module("BetterF3"), fov = module(CustomFovModule.NAME);
        return List.of(
            new Step("f3-1-vanilla", 1500, () -> {
                save(f3); save(fov);
                f3Enabled = f3.isEnabled(); fovEnabled = fov.isEnabled();
                f3.setEnabled(false);
                mc.debugEntries.setOverlayVisible(true);
            }),
            new Step(null, 400, () -> {
                mc.debugEntries.setOverlayVisible(false);
                f3.getOptions().forEach(Option::reset);
                f3.setEnabled(true);
            }),
            new Step("f3-2-slide-in", 90, () -> mc.debugEntries.setOverlayVisible(true)),
            new Step("f3-3-default", 1200, () -> {}),
            new Step("f3-4-hide-inessential-off", 600, () -> bool(f3, "Hide Inessential", false)),
            new Step("f3-5-system-world-background-off", 600, () -> {
                bool(f3, "Hide Inessential", true);
                bool(f3, "Show System", false);
                bool(f3, "Show World", false);
                bool(f3, "Background", false);
            }),
            new Step(null, 300, () -> {
                mc.debugEntries.setOverlayVisible(false);
                SAVED.forEach((option, value) -> { if (f3.getOptions().contains(option)) option.load(value); });
                f3.setEnabled(f3Enabled);
                fov.getOptions().forEach(Option::reset);
                fov.setEnabled(true);
                var player = mc.player;
                boolean flying = player.getAbilities().flying;
                player.getAbilities().flying = false;
                measure("standing", "Sprinting", 1);
                player.setSprinting(true);
                measure("sprinting", "Sprinting", 1.15);
                player.setSprinting(false);
                player.getAbilities().flying = true;
                measure("flying", "Flying", 1.1);
                player.getAbilities().flying = flying;
                heldBefore = player.getMainHandItem().copy();
                server(sp -> {
                    sp.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 60, 1));
                    sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
                });
            }),
            new Step(null, 1500, () -> {
                check(mc.player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Identifier.withDefaultNamespace("effect.speed")) != null,
                    "the server's Speed II reached the client's movement speed");
                measure("speed II", "Speed Effects", 1.2);
                share("Speed Effects", 100);
            }),
            new Step(null, 1000, () -> { world("speed II", "Speed Effects", 1.2); share("Speed Effects", 0); }),
            new Step(null, 1000, () -> {
                world("speed II", "Speed Effects", 1);
                share("Speed Effects", 100);
                server(sp -> sp.removeEffect(MobEffects.SPEED));
                check(mc.player.getMainHandItem().is(Items.BOW), "the bow reached the client's main hand");
                mc.player.startUsingItem(InteractionHand.MAIN_HAND);
            }),
            new Step(null, 1500, () -> {
                check(mc.player.getTicksUsingItem() >= 20, "the bow is fully drawn (" + mc.player.getTicksUsingItem() + " ticks)");
                measure("bow drawn", "Bow Aiming", 0.85);
                share("Bow Aiming", 100);
            }),
            new Step(null, 1000, () -> { world("bow drawn", "Bow Aiming", 0.85); share("Bow Aiming", 0); }),
            new Step(null, 1000, () -> {
                world("bow drawn", "Bow Aiming", 1);
                mc.player.stopUsingItem();
                ItemStack held = heldBefore;
                server(sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, held));
                SAVED.forEach((option, value) -> { if (fov.getOptions().contains(option)) option.load(value); });
                fov.setEnabled(fovEnabled);
                finish(game);
            }));
    }

    /** The modifier the game computes now (FOV Effects 100%) with {@code change} at 100% (expect {@code full}) and at 0% (expect 1). */
    private static void measure(String state, String change, double full) {
        for (int share : new int[] {100, 0}) {
            share(change, share);
            double expected = share == 100 ? full : 1;
            float modifier = Minecraft.getInstance().player.getFieldOfViewModifier(true, 1f);
            CSV.append(String.format(Locale.ROOT, "%s,%d%%,%.5f,%.5f,\n", state, share, modifier, expected));
            check(Math.abs(modifier - expected) < 1e-3, state + " at " + share + "%: FOV modifier " + modifier + " (expected " + expected + ")");
        }
        share(change, 100);
    }

    /** The world FOV the camera settled at: the FOV option times the modifier (Lads Zoom off, dry land). */
    private static void world(String state, String change, double modifier) {
        Minecraft mc = Minecraft.getInstance();
        double expected = mc.options.fov().get() * modifier, world = NativeFeatures.lastWorldFov;
        String share = modifier == 1 ? "0%" : "100%";
        CSV.append(String.format(Locale.ROOT, "%s (world),%s,,%.5f,%.3f\n", state, share, modifier, world));
        check(Math.abs(world / expected - 1) < 2e-3, state + " at " + share + ": world FOV " + world + " (expected " + expected + ")");
    }

    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            if (player != null) action.accept(player);
        });
    }

    private static void finish(Path game) {
        try { Files.writeString(game.resolve("screenshots").resolve("custom-fov.csv"), CSV.toString(), StandardCharsets.UTF_8); }
        catch (Exception failure) { fail("custom-fov.csv: " + failure); }
        if (FAILURES.isEmpty()) LOGGER.info("Lads F3/FOV capture END: {} frames saved, {} checks passed, 0 failed", saved, checks);
        else LOGGER.error("Lads F3/FOV capture FAILED: {}", String.join(" | ", FAILURES));
    }

    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }
    private static void save(Module module) { for (Option option : module.getOptions()) SAVED.put(option, option.save().deepCopy()); }
    private static void bool(Module module, String name, boolean value) { ((BoolOption) module.getOption(name)).set(value); }
    private static void share(String change, int percent) { ((SliderOption) module(CustomFovModule.NAME).getOption(change)).setValue(percent); }
    private static void check(boolean result, String description) {
        if (!result) { fail(description); return; }
        checks++;
        LOGGER.info("Lads F3/FOV capture PASS: {}", description);
    }
    private static void fail(String failure) {
        FAILURES.add(failure);
        LOGGER.error("Lads F3/FOV capture check failed: {}", failure);
    }
}
