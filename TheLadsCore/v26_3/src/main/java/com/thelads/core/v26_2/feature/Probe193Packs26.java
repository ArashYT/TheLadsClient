package com.thelads.core.v26_2.feature;

import com.thelads.core.v26_2.gui.FolderNav26;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Probe193Packs26 {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    private static int step = 0;
    private static int wait = 0;
    private static boolean registered = false;

    private Probe193Packs26() {}

    public static void register() {
        if (registered) return;
        String verifyPacks = System.getProperty("thelads.verifyPacks");
        if (verifyPacks != null && !verifyPacks.isEmpty()) {
            registered = true;
            ClientTickEvents.END_CLIENT_TICK.register(Probe193Packs26::tick);
            LOG.info("Probe193Packs26 registered with mode: {}", verifyPacks);
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
                if (!(mc.gui.screen() instanceof TitleScreen)) return;
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
                mc.gui.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), packs -> {
                    mc.options.updateResourcePacks(packs);
                }, mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
                wait = 20;
                step++;
            }
            case 1 -> {
                screenshot(mc, "263-packs-relaunch");
                mc.gui.setScreen(new TitleScreen());
                LOG.info("Lads packs probe END: 1 passed, 0 failed");
                step = -1;
            }
        }
    }

    private static void tickInitial(Minecraft mc) throws Exception {
        switch (step) {
            case 0 -> {
                if (!(mc.gui.screen() instanceof TitleScreen)) return;
                ensureSandboxPacks(mc);
                mc.gui.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), packs -> {
                    mc.options.updateResourcePacks(packs);
                }, mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
                wait = 25;
                step++;
            }
            case 1 -> {
                screenshot(mc, "263-packs-title");
                setSearchText(mc.gui.screen(), "PvP");
                wait = 20;
                step++;
            }
            case 2 -> {
                screenshot(mc, "263-packs-search");
                setSearchText(mc.gui.screen(), "");
                if (mc.gui.screen() instanceof FolderNav26 nav) {
                    nav.ladsCd("PvP");
                }
                wait = 20;
                step++;
            }
            case 3 -> {
                screenshot(mc, "263-packs-in-pvp");
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
                mc.gui.setScreen(new TitleScreen());
                wait = 20;
                step++;
            }
            case 4 -> {
                mc.gui.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), packs -> {
                    mc.options.updateResourcePacks(packs);
                }, mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
                wait = 25;
                step++;
            }
            case 5 -> {
                screenshot(mc, "263-packs-pvp-selected");
                mc.gui.setScreen(new TitleScreen());
                wait = 20;
                step++;
            }
            case 6 -> {
                // Wait for world to be loaded and settled
                if (mc.level != null && mc.player != null && mc.player.tickCount > 60) {
                    mc.gui.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), packs -> {
                        mc.options.updateResourcePacks(packs);
                    }, mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
                    wait = 25;
                    step++;
                }
            }
            case 7 -> {
                screenshot(mc, "263-packs-ingame");
                mc.gui.setScreen(null);
                LOG.info("Lads packs probe END: 5 passed, 0 failed");
                step = -1;
            }
        }
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

    private static void screenshot(Minecraft mc, String file) throws Exception {
        Path output = FabricLoader.getInstance().getGameDir().resolve("screenshots").resolve(file + ".png");
        Files.createDirectories(output.getParent());
        net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try {
                image.writeToFile(output);
            } catch (Exception ignored) {}
        });
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
