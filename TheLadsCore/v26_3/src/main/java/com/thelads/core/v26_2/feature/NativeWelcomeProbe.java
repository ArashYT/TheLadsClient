package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.LoggerFactory;

/** Opt-in observation only: no screen dismissal or preference changes. */
public final class NativeWelcomeProbe {
    private static long started;
    private static boolean done;
    public static void renderedFrame(RenderTarget target) {
        if (!Boolean.getBoolean("thelads.verifyWelcome")) return;
        tick(Minecraft.getInstance(), target);
    }
    private static void tick(Minecraft mc, RenderTarget target) {
        if (done || !mc.isGameLoadFinished() || mc.gui.overlay() != null) return;
        if (started == 0) started = System.nanoTime();
        if (System.nanoTime() - started < 20_000_000_000L) return;
        done = true;
        try {
            var game = mc.gameDirectory.toPath().toRealPath();
            if (!game.getFileName().toString().equals("26.3-title")
                || !game.getParent().getFileName().toString().equals("verification"))
                throw new IllegalStateException("Not an isolated QA folder");
            Object options = Class.forName("de.keksuccino.fancymenu.FancyMenu").getMethod("getOptions").invoke(null);
            Object welcome = options.getClass().getField("showWelcomeScreen").get(options);
            if (!Boolean.FALSE.equals(welcome.getClass().getMethod("getValue").invoke(welcome)))
                throw new IllegalStateException("FancyMenu welcome option was not loaded as false");
            var configField = Class.forName("com.moonplay.mcessentials.client.ModpackCoreEssentialsClient").getDeclaredField("config");
            configField.setAccessible(true);
            Object config = configField.get(null);
            if (!"NEVER".equals(config.getClass().getField("welcomeMode").get(config).toString()))
                throw new IllegalStateException("MCE welcome mode was not loaded as NEVER");
            if (!(mc.gui.screen() instanceof TitleScreen))
                throw new IllegalStateException("Title screen is obstructed by " + mc.gui.screen());
            var output = game.resolve("welcome-startup-" + System.currentTimeMillis() + ".png");
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try {
                    image.writeToFile(output);
                    LoggerFactory.getLogger("TheLadsCore").info("Lads welcome probe END: 3 passed, 0 failed; actual title frame {}", output);
                } catch (Exception failure) {
                    LoggerFactory.getLogger("TheLadsCore").error("Lads welcome probe FAILED", failure);
                } finally { image.close(); mc.execute(mc::stop); }
            });
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads welcome probe FAILED", failure);
            mc.stop();
        }
    }
}
