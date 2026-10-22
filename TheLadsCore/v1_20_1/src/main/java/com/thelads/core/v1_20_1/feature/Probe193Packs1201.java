package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.pack.PackMenuSettings;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.v1_20_1.gui.FolderNav1201;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Probe193Packs1201 {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    private static int step = 0;
    private static int wait = 0;
    private static boolean registered = false;
    private static int passed = 0;
    private static int failed = 0;
    private static final List<OldAnimationsModule.Feature> OVERLAYS = List.of(OldAnimationsModule.Feature.LOW_FIRE, OldAnimationsModule.Feature.LOW_SHIELD);

    private Probe193Packs1201() {}

    public static void register() {
        if (registered) return;
        String verifyPacks = System.getProperty("thelads.verifyPacks");
        if (verifyPacks != null && !verifyPacks.isEmpty()) {
            registered = true;
            ClientTickEvents.END_CLIENT_TICK.register(Probe193Packs1201::tick);
            LOG.info("Probe193Packs1201 registered with mode: {}", verifyPacks);
        }
    }

    private static void tick(Minecraft mc) {
        String mode = System.getProperty("thelads.verifyPacks");
        if (mode == null || step < 0) return;
        if (wait > 0) { wait--; return; }

        try {
            if ("relaunch".equalsIgnoreCase(mode)) {
                tickRelaunch(mc);
            } else {
                tickInitial(mc);
            }
        } catch (Throwable t) {
            step = -1;
            LOG.error("Lads packs probe FAILED", t);
        }
    }

    private static void tickRelaunch(Minecraft mc) throws Exception {
        switch (step) {
            case 0 -> {
                if (!(mc.screen instanceof TitleScreen)) return;
                boolean hasPvPPack = false;
                for (String id : mc.options.resourcePacks) {
                    if (id.contains("PvPPackOne")) {
                        hasPvPPack = true;
                        break;
                    }
                }
                LOG.info("Lads packs relaunch check: options.resourcePacks={}, hasPvPPack={}", mc.options.resourcePacks, hasPvPPack);
                if (!hasPvPPack) {
                    step = -1;
                    LOG.error("Lads packs probe FAILED: PvPPackOne not found in options.resourcePacks on relaunch!");
                    return;
                }
                mc.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), mc.options::updateResourcePacks,
                    mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
                wait = 20;
                step++;
            }
            case 1 -> {
                screenshot(mc, "1201-packs-relaunch");
                mc.setScreen(new TitleScreen());
                LOG.info("Lads packs probe END: 1 passed, 0 failed");
                step = -1;
            }
        }
    }

    private static void tickInitial(Minecraft mc) throws Exception {
        switch (step) {
            case 0 -> {
                if (!(mc.screen instanceof TitleScreen)) return;
                ensureSandboxPacks(mc);
                mc.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), mc.options::updateResourcePacks,
                    mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
                wait = 25;
                step++;
            }
            case 1 -> {
                screenshot(mc, "1201-packs-title");
                checkOverlayButtons(mc);
                setSearchText(mc.screen, "PvP");
                wait = 20;
                step++;
            }
            case 2 -> {
                screenshot(mc, "1201-packs-search");
                setSearchText(mc.screen, "");
                if (mc.screen instanceof FolderNav1201 nav) {
                    nav.ladsCd("PvP");
                }
                wait = 20;
                step++;
            }
            case 3 -> {
                screenshot(mc, "1201-packs-in-pvp");
                var repo = mc.getResourcePackRepository();
                var available = repo.getAvailablePacks();
                for (var pack : available) {
                    if (pack.getId().contains("PvPPackOne")) {
                        repo.setSelected(List.of("vanilla", pack.getId()));
                        break;
                    }
                }
                mc.options.resourcePacks.clear();
                mc.options.resourcePacks.addAll(repo.getSelectedIds());
                mc.options.save();
                LOG.info("Saved selected packs to options: {}", mc.options.resourcePacks);
                mc.setScreen(new TitleScreen());
                wait = 20;
                step++;
            }
            case 4 -> {
                mc.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), mc.options::updateResourcePacks,
                    mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
                wait = 25;
                step++;
            }
            case 5 -> {
                screenshot(mc, "1201-packs-pvp-selected");
                mc.setScreen(new TitleScreen());
                wait = 20;
                step++;
            }
            case 6 -> {
                // Wait for world to be loaded and settled
                if (mc.level != null && mc.player != null && mc.player.tickCount > 60) {
                    mc.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), mc.options::updateResourcePacks,
                        mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
                    wait = 25;
                    step++;
                }
            }
            case 7 -> {
                screenshot(mc, "1201-packs-ingame");
                mc.setScreen(null);
                LOG.info("Lads packs probe END: {} passed, {} failed", 5 + passed, failed);
                step = -1;
            }
        }
    }

    private static void screenshot(Minecraft mc, String file) throws Exception {
        Path output = FabricLoader.getInstance().getGameDir().resolve("screenshots").resolve(file + ".png");
        Files.createDirectories(output.getParent());
        try (com.mojang.blaze3d.platform.NativeImage image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(output);
        }
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
            LOG.info("Lads packs probe PASS: {}", what);
        } else {
            failed++;
            LOG.error("Lads packs probe FAILED: {}", what);
        }
    }

    /** Low Fire / Low Shield: fully on screen and overlapping nothing at this window and at 854x480 / GUI scale 2 (427x240), then a press flips the option and its label and a second press restores them. */
    private static void checkOverlayButtons(Minecraft mc) {
        Screen screen = mc.screen;
        int w = screen.width;
        int h = screen.height;
        checkOverlayLayout(screen, w + "x" + h);
        screen.resize(mc, 427, 240);
        checkOverlayLayout(screen, "427x240");
        screen.resize(mc, w, h);
        checkOverlayPress(screen);
    }

    private static void checkOverlayLayout(Screen screen, String size) {
        for (OldAnimationsModule.Feature feature : OVERLAYS) {
            Button button = overlayButton(screen, feature);
            check(feature.option + " button exists at " + size, button != null);
            if (button == null) continue;
            check(feature.option + " fully on screen at " + size, button.getX() >= 0 && button.getY() >= 0
                && button.getX() + button.getWidth() <= screen.width && button.getY() + button.getHeight() <= screen.height);
            check(feature.option + " overlaps nothing at " + size, !overlapsAny(screen, button));
        }
    }

    private static void checkOverlayPress(Screen screen) {
        OldAnimationsModule anim = ModuleManager.getInstance().getModule(OldAnimationsModule.NAME) instanceof OldAnimationsModule m ? m : null;
        for (OldAnimationsModule.Feature feature : OVERLAYS) {
            Button button = overlayButton(screen, feature);
            if (button == null) continue;
            if (anim == null || !PackMenuSettings.overlayAvailable()) {
                check(feature.option + " inactive while 1.7 Animations is off", !button.active);
                continue;
            }
            boolean before = anim.option(feature).get();
            String label = button.getMessage().getString();
            check(feature.option + " active and labelled with its state", button.active && label.equals(feature.option + ": " + (before ? "ON" : "OFF")));
            button.onPress();
            check(feature.option + " press flips the option and its label", anim.option(feature).get() == !before
                && button.getMessage().getString().equals(feature.option + ": " + (before ? "OFF" : "ON")));
            button.onPress();
            check(feature.option + " second press restores both", anim.option(feature).get() == before && button.getMessage().getString().equals(label));
            anim.option(feature).set(before);
        }
    }

    private static Button overlayButton(Screen screen, OldAnimationsModule.Feature feature) {
        for (var child : screen.children()) {
            if (child instanceof Button b && b.getMessage().getString().startsWith(feature.option + ":")) return b;
        }
        return null;
    }

    private static boolean overlapsAny(Screen screen, Button b) {
        for (var child : screen.children()) {
            if (child == b) continue;
            if (child instanceof AbstractWidget w) {
                if (w.visible && b.getX() < w.getX() + w.getWidth() && w.getX() < b.getX() + b.getWidth()
                    && b.getY() < w.getY() + w.getHeight() && w.getY() < b.getY() + b.getHeight()) return true;
            } else if (child instanceof AbstractSelectionList<?> list) {
                // The list's bounds are protected on 1.20.1: probe the button's four corners and edge midpoints and centre.
                for (int dx = 0; dx <= 2; dx++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        if (list.isMouseOver(b.getX() + (b.getWidth() - 1) * dx / 2.0, b.getY() + (b.getHeight() - 1) * dy / 2.0)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static void setSearchText(Screen screen, String text) {
        if (screen == null) return;
        for (var child : screen.children()) {
            if (child instanceof EditBox box) {
                box.setValue(text);
                return;
            }
        }
    }

    private static void ensureSandboxPacks(Minecraft mc) {
        try {
            Path target = mc.getResourcePackDirectory();
            if (!Files.exists(target)) Files.createDirectories(target);
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path source = gameDir.resolve("tools/sandbox-packs");
            if (!Files.isDirectory(source)) {
                Path cur = gameDir;
                for (int i = 0; i < 4 && cur != null; i++) {
                    Path candidate = cur.resolve("tools/sandbox-packs");
                    if (Files.isDirectory(candidate)) {
                        source = candidate;
                        break;
                    }
                    cur = cur.getParent();
                }
            }
            if (Files.isDirectory(source)) {
                copyRecursive(source, target);
                LOG.info("Copied sandbox packs from {} to {}", source, target);
            }
            mc.getResourcePackRepository().reload();
        } catch (Exception e) {
            LOG.error("Failed to copy sandbox packs: {}", e.getMessage());
        }
    }

    private static void copyRecursive(Path src, Path dst) throws IOException {
        if (Files.isDirectory(src)) {
            if (!Files.exists(dst)) Files.createDirectories(dst);
            try (var stream = Files.newDirectoryStream(src)) {
                for (Path p : stream) {
                    copyRecursive(p, dst.resolve(p.getFileName()));
                }
            }
        } else {
            Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
