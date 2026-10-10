package com.thelads.core.v1_20_1.feature.fastquit;

import com.thelads.core.client.FastQuit;
import com.thelads.core.v1_20_1.feature.fastquit.mixin.MinecraftServerFastQuitAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

/**
 * FastQuit on 1.20.1, plain Fabric and Forge + Connector (common FastQuit has the rules). Minecraft.clearLevel's wait for
 * the integrated server is skipped (MinecraftFastQuitMixin); world storage, new connections, Create World and closing the
 * game wait for background saves behind {@link SavingScreen}.
 */
public final class FastQuit1201 {
    /** Minecraft (MinecraftFastQuitMixin): one runTick(false), as vanilla's own wait for the server. */
    public interface Pump {
        void lads$fastQuitFrame();
    }

    private FastQuit1201() {}

    /**
     * Minecraft.clearLevel lets go of its integrated server: true when it stops in the background, tracked (vanilla's
     * "while (!server.isShutdown())" loop then ends at once). A Save and Quit has logged the player out, which stops the server;
     * clearLevel without a level (a server still starting, or whose login failed) and doWorldLoad's clearLevel while a world is
     * loaded don't, and the next world's server would run beside it (Xaero's mods: "Multiple servers running at once is not
     * supported!"): those are stopped here, as 26.3's own disconnect does.
     * Only a plain IntegratedServer (not a replay server) while the game keeps running; closing the game waits as vanilla.
     */
    private static boolean broken;

    public static boolean stopInBackground(Minecraft minecraft, IntegratedServer server) {
        if (broken) return false;
        try {
            if (!minecraft.isRunning() || !minecraft.isSameThread() || server.getClass() != IntegratedServer.class || !FastQuit.enabled()) return false;
            Thread thread = server.getRunningThread();
            if (thread == null || !thread.isAlive()) return false;
            ClientPacketListener connection = minecraft.getConnection();
            if (connection == null || connection.getConnection().isConnected()) ((MinecraftServerFastQuitAccessor) server).lads$setRunning(false);
            FastQuit.track(server.getWorldPath(LevelResource.ROOT), server.getWorldData().getLevelName(), thread);
            return true;
        } catch (Throwable failure) { // never break leaving a world: quits wait as vanilla does from here on
            broken = true;
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").error("Lads FastQuit: skipped for this quit (vanilla wait)", failure);
            return false;
        }
    }

    /** -Dlads.fastquit.dh=false: Distant Horizons closes its world on the render thread when leaving one, as without Lads. */
    private static final boolean DH = !"false".equalsIgnoreCase(System.getProperty("lads.fastquit.dh"));
    private static volatile Thread dhClose;

    /**
     * Distant Horizons' ClientApi.onClientOnlyDisconnected (DhClientApiFastQuitMixin), called from ClientPacketListener.close
     * when leaving any world: in singleplayer it closes DH's world (each level flushes its LOD data to the world's DH database,
     * 5-8 s on a big world) on the render thread, after which the title screen appears. Leaving a singleplayer world, true:
     * {@code close} runs on its own thread instead, tracked like the server's save, so that everything that opens world storage,
     * every connection and closing the game wait for it; DH's next world also waits ({@link #awaitDhClose}). DH already closes
     * its levels on threads of their own and hands GL work to the render thread, and its server side closes the same world from
     * the server thread; only the render thread's wait goes.
     */
    public static boolean closeDhInBackground(Runnable close) {
        if (!DH || broken) return false;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (!minecraft.isRunning() || !minecraft.isSameThread() || !FastQuit.enabled()) return false;
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server == null || server.getClass() != IntegratedServer.class) return false;
            Thread thread = new Thread(() -> {
                try {
                    close.run();
                } catch (Throwable failure) {
                    org.slf4j.LoggerFactory.getLogger("TheLadsCore").error("Lads FastQuit: Distant Horizons' background close failed", failure);
                }
            }, "Lads FastQuit Distant Horizons close");
            dhClose = thread;
            thread.start();
            FastQuit.track(server.getWorldPath(LevelResource.ROOT), server.getWorldData().getLevelName() + " (Distant Horizons)", thread);
            return true;
        } catch (Throwable failure) {
            broken = true;
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").error("Lads FastQuit: Distant Horizons closes on the render thread from here on", failure);
            return false;
        }
    }

    /** DH's ServerApi.serverLoadEvent (DhServerApiFastQuitMixin): a new world's DH world starts after the last one closed. */
    public static void awaitDhClose() {
        Thread thread = dhClose;
        if (thread == null || thread == Thread.currentThread()) return;
        boolean interrupted = false;
        while (thread.isAlive()) {
            try { thread.join(); } catch (InterruptedException e) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
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
        Screen previous = minecraft.screen;
        minecraft.setScreen(new SavingScreen());
        FastQuit.waitWhileSaving(((Pump) minecraft)::lads$fastQuitFrame);
        if (restore) minecraft.setScreen(previous);
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
        public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            renderDirtBackground(g);
            g.drawCenteredString(font, title, width / 2, height / 2 - 14, 0xFFFFFF);
            g.drawCenteredString(font, FastQuit.status(), width / 2, height / 2 + 2, 0xAAAAAA);
            super.render(g, mouseX, mouseY, delta);
        }
    }
}
