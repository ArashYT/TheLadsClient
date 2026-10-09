package com.thelads.core.v1_8_9.feature.fastquit;

import com.thelads.core.client.FastQuit;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.server.integrated.IntegratedServer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * FastQuit on 1.8.9 Forge (common FastQuit has the rules). Minecraft.loadWorld's wait for the integrated server is skipped
 * (MinecraftFastQuitMixin). Opening a world, a server connection, the world list's delete, rename and re-create (each opens
 * or flushes the world's files; 1.8.9's session.lock is a timestamp the saving server checks before every write, and the
 * region file cache is global) and closing the game wait for background saves behind a "Saving world" loading screen.
 */
public final class FastQuit189 {
    private FastQuit189() {}

    static File worldDir(Minecraft mc, String folder) {
        return new File(new File(mc.mcDataDir, "saves"), folder);
    }

    /**
     * Minecraft.loadWorld's "while (!server.isServerStopped()) sleep" loop: true ends it at once, the server saving in the
     * background. Only a plain IntegratedServer while the game keeps running; closing the game waits as vanilla.
     */
    private static boolean broken;

    public static boolean skipServerWait(Minecraft mc, IntegratedServer server, boolean running) {
        if (broken) return false;
        try {
            if (!running || !mc.isCallingFromMinecraftThread() || server.getClass() != IntegratedServer.class || !FastQuit.enabled()) return false;
            Thread thread = ((com.thelads.core.v1_8_9.feature.fastquit.mixin.MinecraftServerFastQuitAccessor) server).lads$serverThread();
            if (thread == null || !thread.isAlive()) return false;
            FastQuit.track(worldDir(mc, server.getFolderName()).toPath(), server.getWorldName(), thread);
            return true;
        } catch (Throwable failure) { // never break leaving a world: quits wait as vanilla does from here on
            broken = true;
            org.apache.logging.log4j.LogManager.getLogger("TheLadsCore").error("Lads FastQuit: skipped for this quit (vanilla wait)", failure);
            return false;
        }
    }

    /**
     * Minecraft.loadWorld(null) ends by flushing the region file cache (closing every cached region file), which the world's server
     * must not see while it is still saving: with a save running the flush waits until the next hook that waits for saves
     * (opening or deleting a world, a connection, closing the game), which runs it right after the saves; else it runs at once.
     */
    private static volatile net.minecraft.world.storage.ISaveFormat pendingFlush;

    public static void flushCache(net.minecraft.world.storage.ISaveFormat format) {
        if (FastQuit.anySaving()) pendingFlush = format;
        else format.flushCache();
    }

    private static void flushIfPending() {
        net.minecraft.world.storage.ISaveFormat format = pendingFlush;
        if (format == null || FastQuit.anySaving()) return;
        pendingFlush = null;
        format.flushCache();
    }

    /** Waits for every background save; on the client thread behind the "Saving world" loading screen. Free when nothing is saving. */
    public static void awaitSaves() {
        if (!FastQuit.anySaving()) {
            flushIfPending();
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !mc.isCallingFromMinecraftThread() || mc.loadingScreen == null) {
            FastQuit.awaitAll(Long.MAX_VALUE);
            flushIfPending();
            return;
        }
        mc.loadingScreen.resetProgressAndMessage(I18n.format("menu.savingLevel"));
        FastQuit.waitWhileSaving(new Runnable() {
            @Override public void run() {
                mc.loadingScreen.displayLoadingString(FastQuit.status());
                sleep(40);
            }
        });
        flushIfPending();
        // Clicks and keys pressed meanwhile were for the loading screen, not the screen that comes back.
        while (Mouse.isCreated() && Mouse.next()) {}
        while (Keyboard.isCreated() && Keyboard.next()) {}
    }

    /** Minecraft.shutdownMinecraftApplet: the game loop has ended (nothing can be drawn); the window keeps answering. */
    public static void awaitSavesAtExit() {
        while (!FastQuit.awaitAll(50)) {
            if (Display.isCreated()) Display.processMessages();
        }
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
