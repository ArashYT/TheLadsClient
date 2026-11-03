package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ViaForce;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.shared.SharedContentPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA of the multiplayer and world lists: the script's "srv" steps. Everything goes through the open screen's own event methods, so the
 * player's real mouse is left alone. seed writes a hand-edited servers.dat (the network not first) for the game's own list to read.
 */
final class ServerListQa1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static int lastX, lastY;

    private ServerListQa1201() {}

    static void step(Minecraft mc, String[] s) throws Exception {
        switch (s[1]) {
            case "multiplayer" -> mc.setScreen(new JoinMultiplayerScreen(mc.screen));
            case "seed" -> seed(mc, s);
            case "columns" -> {
                ((SliderOption) ModuleManager.getInstance().getModule(ServerListDrag.NAME).getOption(ServerListColumns.OPTION)).setValue(Integer.parseInt(s[2]));
                if (mc.screen != null) mc.screen.resize(mc, mc.screen.width, mc.screen.height);
            }
            case "scale" -> {
                mc.options.guiScale().set(Integer.parseInt(s[2]));
                mc.resizeDisplay();
            }
            case "hover" -> {
                if (s.length < 3) ServerListDrag.qaMouseX = ServerListDrag.qaMouseY = -1;
                else {
                    int[] at = at(mc, s, 2);
                    ServerListDrag.qaMouseX = at[0];
                    ServerListDrag.qaMouseY = at[1];
                }
            }
            case "click" -> {
                int[] at = at(mc, s, 2);
                click(mc, at[0], at[1], 0);
            }
            case "rclick" -> {
                int[] at = at(mc, s, 2);
                lastX = at[0];
                lastY = at[1];
                click(mc, at[0], at[1], 1);
            }
            case "pick" -> click(mc, lastX + 8, lastY + 3 + 16 * Integer.parseInt(s[2]) + 8, 0); // item of the menu just opened
            case "press" -> { // the left button down on a row, and held
                ServerListDrag.heldForQa = true;
                int[] at = at(mc, s, 2);
                lastX = at[0];
                lastY = at[1];
                click(mc, at[0], at[1], 0);
            }
            case "drag" -> {
                int[] at = at(mc, s, 2);
                mc.screen.mouseDragged(at[0], at[1], 0, at[0] - lastX, at[1] - lastY);
                lastX = at[0];
                lastY = at[1];
            }
            case "release" -> {
                mc.screen.mouseReleased(lastX, lastY, 0);
                ServerListDrag.heldForQa = false;
            }
            case "key" -> mc.screen.keyPressed(switch (s[2]) { // up, down, left, right or esc
                case "up" -> 265;
                case "down" -> 264;
                case "left" -> 263;
                case "right" -> 262;
                default -> 256;
            }, 0, 0);
            case "button" -> { // the row's right end, the button's line
                ServerListColumns.Rows rows = rows(mc);
                int row = Integer.parseInt(s[2]);
                click(mc, rows.lads$rowLeft(row) + ServerListColumns.width(ServerListColumns.fit(ServerListColumns.wanted(), mc.screen.width), mc.screen.width) - 12, rows.lads$rowTop(row) + 26, 0);
            }
            case "wheel" -> mc.screen.mouseScrolled(mc.screen.width / 2, 50, Double.parseDouble(s[2])); // negative scrolls down
            case "dbl" -> {
                int[] at = at(mc, s, 2);
                mc.screen.mouseClicked(at[0], at[1], 0);
                if (mc.screen != null) mc.screen.mouseClicked(at[0], at[1], 0);
            }
            case "hoverbutton" -> {
                ServerListColumns.Rows rows = rows(mc);
                int row = Integer.parseInt(s[2]);
                ServerListDrag.qaMouseX = rows.lads$rowLeft(row) + ServerListColumns.width(ServerListColumns.fit(ServerListColumns.wanted(), mc.screen.width), mc.screen.width) - 12;
                ServerListDrag.qaMouseY = rows.lads$rowTop(row) + 26;
            }
            case "arrow" -> { // vanilla's move arrows on a row's icon: up or down
                ServerListColumns.Rows rows = rows(mc);
                int row = Integer.parseInt(s[2]);
                click(mc, rows.lads$rowLeft(row) + 8, rows.lads$rowTop(row) + (s[3].equals("up") ? 8 : 24), 0);
            }
            case "worlds" -> mc.setScreen(new SelectWorldScreen(mc.screen));
            case "badworld" -> { // a world vanilla disables: its level.dat can't be read; and a good copy of the QA world's beside it
                Path base = mc.getLevelSource().getBaseDir();
                Files.createDirectories(base.resolve("QA Broken"));
                Files.writeString(base.resolve("QA Broken").resolve("level.dat"), "not a level");
                try (var worlds = Files.list(base)) {
                    Path good = worlds.filter(p -> Files.exists(p.resolve("level.dat")) && !p.getFileName().toString().startsWith("QA ")).findFirst().orElse(null);
                    if (good != null) {
                        Files.createDirectories(base.resolve("QA Good"));
                        Files.copy(good.resolve("level.dat"), base.resolve("QA Good").resolve("level.dat"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            case "log" -> log(mc);
            default -> LOGGER.warn("Lads qa srv: unknown {}", s[1]);
        }
    }

    private static void seed(Minecraft mc, String[] s) throws Exception {
        Path file = SharedContentPaths.redirectEnabled() ? SharedContentPaths.serversFile() : mc.gameDirectory.toPath().resolve("servers.dat");
        ListTag servers = new ListTag();
        for (int i = 2; i + 1 < s.length; i += 2) { // name|with|bars address ...
            CompoundTag entry = new CompoundTag();
            entry.putString("name", s[i].replace('|', ' '));
            entry.putString("ip", s[i + 1]);
            servers.add(entry);
        }
        CompoundTag root = new CompoundTag();
        root.put("servers", servers);
        Files.deleteIfExists(file);
        NbtIo.write(root, file.toFile());
        LOGGER.info("Lads qa srv: seeded {} with {} servers", file, servers.size());
    }

    private static void log(Minecraft mc) {
        Screen screen = mc.screen;
        List<String> rows = new ArrayList<>();
        List<String> buttons = screen == null ? List.of() : screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button).map(c -> (net.minecraft.client.gui.components.Button) c)
            .map(b -> b.getMessage().getString() + (b.active ? "" : "(off)") + (b.visible ? "" : "(hidden)")).toList();
        if (screen instanceof JoinMultiplayerScreen multi) {
            var servers = multi.getServers();
            for (int i = 0; i < servers.size(); i++) {
                ServerData data = servers.get(i);
                rows.add(i + ":" + data.name + "@" + data.ip + " ping=" + data.ping + " protocol=" + data.protocol + " version=" + data.version.getString()
                    + " forced=" + ViaForce.forced(data) + " tag=" + data.write().get(ViaForce.VIA_TAG) + "/" + data.write().get(ViaForce.TAG));
            }
            ServerSelectionList list = list(screen);
            LOGGER.info("Lads qa srv: client protocol {}, ViaForce.available={}, columns wanted={} shown={} most={} width={}", SharedConstants.getCurrentVersion().getProtocolVersion(), ViaForce.available(),
                ServerListColumns.wanted(), ServerListColumns.fit(ServerListColumns.wanted(), screen.width), ServerListColumns.most(screen.width), screen.width);
            LOGGER.info("Lads qa srv: selected={} buttons={}", list.getSelected() == null ? "none" : list.children().indexOf(list.getSelected()), buttons);
        } else if (screen instanceof SelectWorldScreen) {
            for (var child : screen.children()) {
                if (child instanceof WorldSelectionList worlds) {
                    for (var row : worlds.children()) rows.add(row instanceof WorldSelectionList.WorldListEntry world ? world.getLevelName() : row.getClass().getSimpleName());
                }
            }
            LOGGER.info("Lads qa srv: buttons={}", buttons);
        }
        LOGGER.info("Lads qa srv: screen={} rows={} clipboard={}", screen == null ? "none" : screen.getClass().getSimpleName(), rows, mc.keyboardHandler.getClipboard());
    }

    private static ServerSelectionList list(Screen screen) {
        for (var child : screen.children()) if (child instanceof ServerSelectionList list) return list;
        throw new IllegalStateException("no server list on " + screen);
    }

    private static ServerListColumns.Rows rows(Minecraft mc) {
        return (ServerListColumns.Rows) list(mc.screen);
    }

    /** "x y" in GUI units, "row i" for the centre of a row, or "slot i" for a slot of the current columns; world rows by "wrow i" (one column). */
    private static int[] at(Minecraft mc, String[] s, int from) {
        int width = mc.screen.width;
        if (s[from].equals("wrow")) return new int[] {width / 2, 72 + 4 + Integer.parseInt(s[from + 1]) * 36 + 16}; // WorldFoldersMixin moved the list to 72
        if (s[from].equals("row")) {
            ServerListColumns.Rows rows = rows(mc);
            int row = Integer.parseInt(s[from + 1]);
            return new int[] {rows.lads$rowLeft(row) + 90, rows.lads$rowTop(row) + 16};
        }
        if (s[from].equals("slot")) {
            ServerSelectionList list = list(mc.screen);
            int columns = ServerListColumns.fit(ServerListColumns.wanted(), width), slot = Integer.parseInt(s[from + 1]);
            return new int[] {ServerListColumns.x(slot, columns, list.getRowLeft() - 2, ServerListColumns.width(columns, width)) + 90,
                ServerListColumns.y(slot, columns, 32 + 4 - (int) list.getScrollAmount(), 36) + 16};
        }
        return new int[] {Integer.parseInt(s[from]), Integer.parseInt(s[from + 1])};
    }

    private static void click(Minecraft mc, int x, int y, int button) {
        boolean used = mc.screen.mouseClicked(x, y, button);
        LOGGER.info("Lads qa srv: click {},{} button {} used={}", x, y, button, used);
    }
}
