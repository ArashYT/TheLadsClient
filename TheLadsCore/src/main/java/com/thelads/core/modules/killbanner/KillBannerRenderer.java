package com.thelads.core.modules.killbanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.KillBannerModule;
import com.thelads.core.config.Module;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

public class KillBannerRenderer {
    private static final Identifier BASE_BANNER = Identifier.fromNamespaceAndPath("theladscore", "textures/gui/base_kill_banner.png");
    
    // Sprite sheets per kill level. Each sheet is 2048x2048 with 8x11 = 88 frames of 256x176.
    private static final Identifier[][] REAVER_KILL_SHEETS = new Identifier[][] {
        {
            Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_1_1.png"),
            Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_1_2.png")
        },
        {
            Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_2_1.png"),
            Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_2_2.png")
        },
        {
            Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_3_1.png"),
            Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_3_2.png")
        },
        {
            Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_4_1.png"),
            Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_4_2.png")
        },
        {
            Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_5_1.png"),
            Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_5_2.png"),
            Identifier.fromNamespaceAndPath("theladscore", "textures/gui/reaver_kill_5_3.png")
        }
    };

    private static final int[] REAVER_KILL_FRAMES = new int[] { 89, 100, 100, 100, 235 };

    private static int currentKillLevel = 1;
    private static int killCount = 0;
    private static long lastKillTime = -1;
    private static final long MULTIKILL_WINDOW = 10000; // 10 seconds

    private static long killTriggerTime = -1;

    public static void triggerKill() {
        long now = System.currentTimeMillis();
        if (lastKillTime != -1 && (now - lastKillTime) < MULTIKILL_WINDOW) {
            killCount = Math.min(5, killCount + 1);
        } else {
            killCount = 1;
        }
        lastKillTime = now;
        triggerKill(killCount);
    }

    public static void triggerKill(int level) {
        currentKillLevel = level;
        killTriggerTime = System.currentTimeMillis();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
            }
        } catch (Throwable t) {
            // Safe guard for headless tests
        }
    }

    public static void extract(GuiGraphicsExtractor extractor) {
        Module mod = ModuleManager.getInstance().getModule("KillBanner");
        if (mod == null || !mod.isEnabled()) return;
        if (killTriggerTime == -1) return;

        long elapsed = System.currentTimeMillis() - killTriggerTime;

        int style = 0;
        if (mod instanceof KillBannerModule) {
            style = ((KillBannerModule) mod).bannerStyle.getIndex();
        }

        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        int centerX = width / 2;
        int bottomY = height - 80;

        if (style == 0) { // Base style
            long elapsedBase = elapsed;
            long baseDuration = 2000;
            if (elapsedBase > baseDuration) {
                killTriggerTime = -1;
                return;
            }
            float progress = (float) elapsedBase / baseDuration;
            float scale = 1.0f;
            if (progress < 0.1f) {
                float p = progress / 0.1f;
                scale = 0.5f + 0.5f * p;
            } else if (progress > 0.8f) {
                float p = (progress - 0.8f) / 0.2f;
                scale = Math.max(0.0f, 1.0f - p);
            }
            if (scale <= 0.0f) return;
            int targetSize = (int)(80 * scale);
            int x = centerX - targetSize / 2;
            int y = bottomY - targetSize;
            extractor.blit(RenderPipelines.GUI_TEXTURED, BASE_BANNER, x, y, 0f, 0f, targetSize, targetSize, 312, 400, 312, 400);
        } else if (style == 1) { // Reaver style (animated sprite sheets)
            int activeLevel = Math.min(REAVER_KILL_SHEETS.length, Math.max(1, currentKillLevel));
            int totalFrames = REAVER_KILL_FRAMES[activeLevel - 1];

            // 60 FPS playback
            int frameIndex = (int) (elapsed * 60 / 1000);
            if (frameIndex >= totalFrames) {
                killTriggerTime = -1;
                return;
            }

            // Each sheet holds 88 frames (8 cols × 11 rows) of 256×176 in a 2048×2048 image
            int FRAMES_PER_SHEET = 88;
            int sheetIndex = frameIndex / FRAMES_PER_SHEET;
            int localFrame = frameIndex % FRAMES_PER_SHEET;
            int col = localFrame % 8;
            int row = localFrame / 8;

            Identifier[] sheets = REAVER_KILL_SHEETS[activeLevel - 1];
            if (sheetIndex >= sheets.length) {
                killTriggerTime = -1;
                return;
            }

            float u = col * 256f;
            float v = row * 176f;

            // Valorant-style entrance/exit: subtle pop-in, scale-fade at the end
            float progress = (float) frameIndex / totalFrames;
            float scale = 1.0f;
            if (progress < 0.08f) {
                float p = progress / 0.08f;
                scale = 0.85f + 0.15f * p; // gentle pop-in: 0.85 → 1.0
            } else if (progress > 0.85f) {
                float p = (progress - 0.85f) / 0.15f;
                scale = Math.max(0.0f, 1.0f - p); // fade-out: 1.0 → 0
            }
            if (scale <= 0.0f) return;

            // Native sprite frame is 256×176; render at that size scaled by animation
            int destW = (int) (256 * scale);
            int destH = (int) (176 * scale);

            // Position: horizontally centered, sits just above the hotbar (like Valorant)
            int reaverBottomY = height - 22;
            int x = centerX - destW / 2;
            int y = reaverBottomY - destH;

            extractor.blit(RenderPipelines.GUI_TEXTURED, sheets[sheetIndex], x, y, u, v, destW, destH, 2048, 2048, 256, 176);
        }
    }
}

