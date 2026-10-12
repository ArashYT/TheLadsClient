package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.FixesQa;
import com.thelads.core.client.util.FileDialogsQa;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.world.level.storage.LevelResource;

/**
 * QA only: the 1.9.1 fixes lane's common routines as script verbs, plain Fabric and Zero SMP. {@code fixes}: FixesQa (Fullbright,
 * name tags, the pause menu's Multiplayer; it leaves the QA world at its end, so it goes last). {@code dialogs}: FileDialogsQa (opens
 * dialogs on the desktop: a machine nobody watches). Screenshots: screenshots/&lt;name&gt;.png. A failed check fails the step.
 */
final class FixesQa1201 {
    private static FixesQa fixes;
    private static FileDialogsQa dialogs;

    private FixesQa1201() {}

    static void register() {
        if (!Boolean.getBoolean("thelads.verifyAutoWorld") && System.getProperty("thelads.qaScript") == null) return;
        QaProbe1201.registerStep("fixes", args -> {
            if (fixes == null) fixes = new FixesQa();
            if (!fixes.tick(GAME)) return false;
            if (!fixes.failures().isEmpty()) throw new IllegalStateException("fixes QA: " + fixes.failures());
            return true;
        });
        QaProbe1201.registerStep("dialogs", args -> {
            if (dialogs == null) dialogs = new FileDialogsQa(FabricLoader.getInstance().getGameDir().resolve("screenshots"));
            if (!dialogs.tick()) return false;
            if (!dialogs.failures().isEmpty()) throw new IllegalStateException("dialogs QA: " + dialogs.failures());
            return true;
        });
    }

    private static Screen current() { return Minecraft.getInstance().screen; }

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
            command(String.format(Locale.ROOT, "summon minecraft:armor_stand %.2f %.2f %.2f {CustomName:'\"%s\"',CustomNameVisible:1b,NoGravity:1b}", x, y, z, name));
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
            try {
                QaProbe1201.shot(Minecraft.getInstance(), name);
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
            return FabricLoader.getInstance().getGameDir().resolve("screenshots").resolve(name + ".png");
        }

        @Override public boolean inWorld() { return Minecraft.getInstance().level != null; }

        @Override public void openPause() { Minecraft.getInstance().setScreen(new PauseScreen(true)); }

        @Override public void press(String label) {
            for (var child : current().children())
                if (child instanceof Button button && button.visible && button.getMessage().getString().equalsIgnoreCase(label)) { button.onPress(); return; }
            throw new IllegalStateException("no button '" + label + "' on " + current().getClass().getName());
        }

        @Override public void back() { // the screen's Back / Cancel / "Back to ..." button, as a player leaves it
            Screen screen = current();
            for (String wanted : new String[] {"back", "cancel"})
                for (var child : screen.children())
                    if (child instanceof Button button && button.visible && button.getMessage().getString().toLowerCase(Locale.ROOT).contains(wanted)) { button.onPress(); return; }
            throw new IllegalStateException("no Back or Cancel button on " + screen.getClass().getName());
        }

        @Override public String screen() {
            Screen screen = current();
            if (screen == null) return "none";
            if (screen instanceof PauseScreen) return "pause";
            if (screen instanceof JoinMultiplayerScreen) return "servers";
            if (screen instanceof ConnectScreen) return "connect";
            if (screen instanceof DisconnectedScreen) return "failed";
            if (screen instanceof TitleScreen) return "title";
            return screen.getClass().getName();
        }

        @Override public void localServersOnly() {
            Minecraft.getInstance().options.skipMultiplayerWarning = true; // QA: straight to the list (players see the warning once, then the same list)
            ServerList list = new ServerList(Minecraft.getInstance());
            list.load();
            for (int i = 0; i < list.size(); i++) {
                String ip = list.get(i).ip.toLowerCase(Locale.ROOT);
                if (!ip.startsWith("localhost") && !ip.startsWith("127.0.0.1")) throw new IllegalStateException("the QA server list has a non-local entry " + ip);
            }
            if (list.size() == 0) {
                list.add(new ServerData("QA Local 1", "127.0.0.1:25599", false), false);
                list.add(new ServerData("QA Local 2", "localhost:25598", false), false);
                list.save();
            }
        }

        @Override public void joinFirstServer() {
            JoinMultiplayerScreen servers = (JoinMultiplayerScreen) current();
            for (var child : servers.children())
                if (child instanceof ServerSelectionList list)
                    for (var entry : list.children())
                        if (entry instanceof ServerSelectionList.OnlineServerEntry) { servers.setSelected(entry); servers.joinSelectedServer(); return; }
            throw new IllegalStateException("no server entry on the list");
        }

        @Override public Path worldFolder() {
            return Minecraft.getInstance().getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize();
        }
    };
}
