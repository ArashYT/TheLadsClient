package com.thelads.core.v26_2.feature.fastquit;

import com.thelads.core.client.FastQuit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

/**
 * FastQuit on 26.3 (common FastQuit has the rules). Minecraft.disconnect's wait for the integrated server is skipped
 * (MinecraftFastQuitMixin); world storage, new connections, Create World and closing the game wait for background saves
 * behind {@link SavingScreen}.
 */
public final class FastQuit26 {
    /** Minecraft (MinecraftFastQuitMixin): one frame of the current screen, with window events, as vanilla's own server wait. */
    public interface Pump {
        void lads$fastQuitFrame();
    }

    private FastQuit26() {}

    /**
     * Minecraft.disconnect's "while (!server.isShutdown())" loop: true ends it at once, the server saving in the background.
     * Only a plain IntegratedServer (not a replay server) while the game keeps running; closing the game waits as vanilla.
     */
    private static boolean broken;

    public static boolean skipServerWait(Minecraft minecraft, IntegratedServer server) {
        if (broken) return false;
        try {
            if (!minecraft.isRunning() || !minecraft.isSameThread() || server.getClass() != IntegratedServer.class || !FastQuit.enabled()) return false;
            Thread thread = server.getRunningThread();
            if (thread == null || !thread.isAlive()) return false;
            FastQuit.track(server.getWorldPath(LevelResource.ROOT), server.getWorldData().getLevelName(), thread);
            return true;
        } catch (Throwable failure) { // never break leaving a world: quits wait as vanilla does from here on
            broken = true;
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").error("Lads FastQuit: skipped for this quit (vanilla wait)", failure);
            return false;
        }
    }

    /**
     * Waits for every background save; on the render thread behind the Saving screen, restoring the screen it found
     * when {@code restore}. Free when nothing is saving.
     */
    public static void awaitSaves(boolean restore) {
        if (!FastQuit.anySaving()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !minecraft.isSameThread()) {
            FastQuit.awaitAll(Long.MAX_VALUE);
            return;
        }
        Screen previous = minecraft.gui.screen();
        minecraft.gui.setScreen(new SavingScreen());
        FastQuit.waitWhileSaving(((Pump) minecraft)::lads$fastQuitFrame);
        if (restore) minecraft.gui.setScreen(previous);
    }

    /** "Saving world" with each background save's name and seconds; no buttons, Esc does nothing. */
    public static final class SavingScreen extends Screen {
        public SavingScreen() {
            super(Component.translatable("menu.savingLevel"));
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            super.extractRenderState(g, mouseX, mouseY, delta);
            g.centeredText(font, title, width / 2, height / 2 - 14, 0xFFFFFFFF);
            g.centeredText(font, FastQuit.status(), width / 2, height / 2 + 2, 0xFFAAAAAA);
        }
    }
}
