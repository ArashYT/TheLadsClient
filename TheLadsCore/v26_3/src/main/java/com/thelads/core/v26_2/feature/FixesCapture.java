package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.FixesQa;
import com.thelads.core.client.util.FileDialogsQa;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world): the 1.9.1 fixes lane's common routines with 26.3's world, screens and screenshots
 * (screenshots/fixes/&lt;name&gt;.png). ".lads-qa-capture-fixes" (the harness's LADS_VERIFY_CAPTURE_FIXES): FixesQa; it leaves the QA
 * world at its end. ".lads-qa-capture-dialogs" (LADS_VERIFY_CAPTURE_DIALOGS, a machine nobody watches): FileDialogsQa.
 */
final class FixesCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static FixesQa fixes;
    private static FileDialogsQa dialogs;
    private static boolean running;
    private static String pendingShot;
    private static Path gameDir;

    private FixesCapture() {}

    static boolean busy() { return running; }

    static void tick(Path game, boolean ready) {
        if (!running) {
            if (!ready) return;
            Path fixesRequest = game.resolve(".lads-qa-capture-fixes"), dialogsRequest = game.resolve(".lads-qa-capture-dialogs");
            boolean wantDialogs = Files.isRegularFile(dialogsRequest, LinkOption.NOFOLLOW_LINKS);
            if (!wantDialogs && !Files.isRegularFile(fixesRequest, LinkOption.NOFOLLOW_LINKS)) return;
            gameDir = game;
            try { Files.delete(wantDialogs ? dialogsRequest : fixesRequest); } catch (Exception ignored) {}
            if (wantDialogs) dialogs = new FileDialogsQa(game.resolve("screenshots").resolve("fixes"));
            else fixes = new FixesQa();
            running = true;
        }
        if (pendingShot != null) return; // the next frame takes it
        if (dialogs != null ? dialogs.tick() : fixes.tick(GAME)) {
            running = false;
            dialogs = null;
            fixes = null;
        }
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): a requested screenshot. */
    static void frame(RenderTarget frame) {
        String name = pendingShot;
        if (name == null) return;
        try {
            Path output = gameDir.resolve("screenshots").resolve("fixes").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(frame, image -> {
                try { image.writeToFile(output); LOGGER.info("Lads fixes QA frame {}", output); }
                catch (Exception failure) { LOGGER.warn("Lads fixes QA frame {} not saved", name, failure); }
                finally { image.close(); }
            });
        } catch (Exception failure) {
            LOGGER.warn("Lads fixes QA frame {} not saved", name, failure);
        }
        pendingShot = null;
    }

    /** QA rule: the server list pings every entry, so it opens only on local ones; an empty list gets two nobody listens on. */
    static void localServersOnly() {
        Minecraft.getInstance().options.skipMultiplayerWarning = true; // QA: straight to the list (players see the warning once, then the same list)
        ServerList list = new ServerList(Minecraft.getInstance());
        list.load();
        for (int i = 0; i < list.size(); i++) {
            String ip = list.get(i).ip.toLowerCase(Locale.ROOT);
            if (!ip.startsWith("localhost") && !ip.startsWith("127.0.0.1")) throw new IllegalStateException("the QA server list has a non-local entry " + ip);
        }
        if (list.size() == 0) {
            list.add(new ServerData("QA Local 1", "127.0.0.1:25599", ServerData.Type.OTHER), false);
            list.add(new ServerData("QA Local 2", "localhost:25598", ServerData.Type.OTHER), false);
            list.save();
        }
    }

    private static Screen current() { return Minecraft.getInstance().gui.screen(); }

    private static final FixesQa.Game GAME = new FixesQa.Game() {
        @Override public double[] feet() {
            var player = Minecraft.getInstance().player;
            return new double[] {player.getX(), player.getY(), player.getZ()};
        }

        @Override public void command(String command) {
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
        }

        @Override public void fill(int x1, int y1, int z1, int x2, int y2, int z2, boolean stone) {
            command("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + (stone ? " minecraft:stone hollow" : " minecraft:air"));
        }

        @Override public void summonTag(String name, double x, double y, double z) {
            command(String.format(Locale.ROOT, "summon minecraft:armor_stand %.2f %.2f %.2f {CustomName:\"%s\",CustomNameVisible:1b,NoGravity:1b}", x, y, z, name));
        }

        @Override public void removeTags() { command("kill @e[type=minecraft:armor_stand]"); }

        @Override public void look(float yaw, float pitch) {
            var player = Minecraft.getInstance().player;
            player.setYRot(yaw);
            player.yRotO = yaw;
            player.setXRot(pitch);
            player.xRotO = pitch;
        }

        @Override public Path screenshot(String name) {
            pendingShot = name;
            return gameDir.resolve("screenshots").resolve("fixes").resolve(name + ".png");
        }

        @Override public boolean inWorld() { return Minecraft.getInstance().level != null; }

        @Override public void openPause() { Minecraft.getInstance().setScreenAndShow(new PauseScreen(true)); }

        @Override public void press(String label) {
            for (var child : current().children())
                if (child instanceof Button button && button.visible && button.getMessage().getString().equalsIgnoreCase(label)) { button.onPress(null); return; }
            throw new IllegalStateException("no button '" + label + "' on " + current().getClass().getName());
        }

        @Override public void back() { // the screen's Back / Cancel / "Back to ..." button, as a player leaves it
            Screen screen = current();
            for (String wanted : new String[] {"back", "cancel"})
                for (var child : screen.children())
                    if (child instanceof Button button && button.visible && button.getMessage().getString().toLowerCase(Locale.ROOT).contains(wanted)) { button.onPress(null); return; }
            throw new IllegalStateException("no Back or Cancel button on " + screen.getClass().getName());
        }

        @Override public String screen() {
            Screen screen = current();
            if (screen == null) return "none";
            if (screen instanceof PauseScreen) return "pause";
            if (screen instanceof JoinMultiplayerScreen) return "servers";
            if (screen instanceof ConnectScreen) return "connect";
            if (screen instanceof DisconnectedScreen) return "failed";
            if (screen instanceof TitleScreen || screen.getClass().getSimpleName().equals("LadsTitleScreen26")) return "title";
            return screen.getClass().getName();
        }

        @Override public void localServersOnly() { FixesCapture.localServersOnly(); }

        @Override public void joinFirstServer() {
            ServerList list = new ServerList(Minecraft.getInstance());
            list.load();
            ((JoinMultiplayerScreen) current()).join(list.get(0));
        }

        @Override public Path worldFolder() {
            return Minecraft.getInstance().getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize();
        }
    };
}
