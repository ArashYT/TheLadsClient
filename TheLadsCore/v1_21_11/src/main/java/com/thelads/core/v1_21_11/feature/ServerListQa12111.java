package com.thelads.core.v1_21_11.feature;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
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
final class ServerListQa12111 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static int lastX, lastY;

    private ServerListQa12111() {}

    static void step(Minecraft mc, String[] s) throws Exception {
        switch (s[1]) {
            case "multiplayer" -> mc.setScreen(new JoinMultiplayerScreen(mc.screen));
            case "seed" -> seed(mc, s);
            case "columns" -> {
                ((SliderOption) ModuleManager.getInstance().getModule(ServerListDrag.NAME).getOption(ServerListColumns.OPTION)).setValue(Integer.parseInt(s[2]));
                // The control and the rows show the choice after a relayout.
                if (mc.screen != null) mc.screen.resize(mc.screen.width, mc.screen.height);
            }
            case "scale" -> {
                mc.options.guiScale().set(Integer.parseInt(s[2]));
                mc.resizeDisplay();
            }
            case "hover" -> { // a mouse position for the screens' drawing only, off with no argument; -1 for a row's centre by name
                if (s.length < 3) ServerListDrag.qaMouseX = ServerListDrag.qaMouseY = -1;
                else {
                    int[] at = at(mc, s, 2);
                    ServerListDrag.qaMouseX = at[0];
                    ServerListDrag.qaMouseY = at[1];
                }
            }
            case "click" -> {
                int[] at = at(mc, s, 2);
                click(mc, at[0], at[1], InputConstants.MOUSE_BUTTON_LEFT);
            }
            case "rclick" -> {
                int[] at = at(mc, s, 2);
                lastX = at[0];
                lastY = at[1];
                click(mc, at[0], at[1], InputConstants.MOUSE_BUTTON_RIGHT);
            }
            case "pick" -> click(mc, lastX + 8, lastY + 3 + 16 * Integer.parseInt(s[2]) + 8, InputConstants.MOUSE_BUTTON_LEFT); // item of the menu just opened
            case "press" -> { // the left button down on a row, and held
                ServerListDrag.heldForQa = true;
                int[] at = at(mc, s, 2);
                lastX = at[0];
                lastY = at[1];
                click(mc, at[0], at[1], InputConstants.MOUSE_BUTTON_LEFT);
            }
            case "drag" -> { // to x y, in two moves past the threshold
                int[] at = at(mc, s, 2);
                screen(mc).mouseDragged(event(at[0], at[1], InputConstants.MOUSE_BUTTON_LEFT), at[0] - lastX, at[1] - lastY);
                lastX = at[0];
                lastY = at[1];
            }
            case "release" -> {
                screen(mc).mouseReleased(event(lastX, lastY, InputConstants.MOUSE_BUTTON_LEFT));
                ServerListDrag.heldForQa = false;
            }
            case "key" -> { // up, down, left, right or esc, with "shift" after it for Shift
                int key = switch (s[2]) {
                    case "up" -> InputConstants.KEY_UP;
                    case "down" -> InputConstants.KEY_DOWN;
                    case "left" -> InputConstants.KEY_LEFT;
                    case "right" -> InputConstants.KEY_RIGHT;
                    default -> InputConstants.KEY_ESCAPE;
                };
                screen(mc).keyPressed(new KeyEvent(key, 0, s.length > 3 ? InputConstants.MOD_SHIFT : 0));
            }
            case "button" -> { // its rect by the entry mixin's rules: the row's right end, the button's line
                ServerSelectionList.Entry row = servers(mc).children().get(Integer.parseInt(s[2]));
                click(mc, row.getContentRight() - 12, row.getContentY() + 26, InputConstants.MOUSE_BUTTON_LEFT);
            }
            case "wheel" -> { // wheel steps over the list: negative scrolls down
                int[] at = at(mc, new String[] {"row", "0"}, 0);
                screen(mc).mouseScrolled(at[0], list(mc).getY() + 20, 0, Double.parseDouble(s[2]));
            }
            case "dbl" -> {
                int[] at = at(mc, s, 2);
                screen(mc).mouseClicked(event(at[0], at[1], InputConstants.MOUSE_BUTTON_LEFT), true);
            }
            case "hoverbutton" -> {
                ServerSelectionList.Entry row = servers(mc).children().get(Integer.parseInt(s[2]));
                ServerListDrag.qaMouseX = row.getContentRight() - 12;
                ServerListDrag.qaMouseY = row.getContentY() + 26;
            }
            case "arrow" -> { // vanilla's move arrows on a row's icon: up or down
                ServerSelectionList.Entry row = servers(mc).children().get(Integer.parseInt(s[2]));
                click(mc, row.getContentX() + 8, row.getContentY() + (s[3].equals("up") ? 8 : 24), InputConstants.MOUSE_BUTTON_LEFT);
            }
            case "worlds" -> mc.setScreen(new SelectWorldScreen(screen(mc)));
            case "seedworlds" -> com.thelads.core.client.qa.QaWorldFixtures.seed(
                mc.getLevelSource().getBaseDir(), SharedConstants.getCurrentVersion().dataVersion().version(), SharedConstants.getCurrentVersion().name());
            case "addnewworld" -> com.thelads.core.client.qa.QaWorldFixtures.addExtraUnsavedWorld(
                mc.getLevelSource().getBaseDir(), SharedConstants.getCurrentVersion().dataVersion().version(), SharedConstants.getCurrentVersion().name());
            case "rmnewworld" -> com.thelads.core.client.qa.QaWorldFixtures.removeExtraUnsavedWorld(mc.getLevelSource().getBaseDir());
            case "restoreworlds" -> com.thelads.core.client.qa.QaWorldFixtures.restore(mc.getLevelSource().getBaseDir());
            case "assertcols" -> {
                int shown = ServerListColumns.fit(ServerListColumns.wanted(), screen(mc).width);
                int expected = Integer.parseInt(s[2]);
                if (shown != expected) throw new IllegalStateException("Expected columns=" + expected + " got " + shown);
            }
            case "assertworlds" -> {
                WorldSelectionList wlist = (WorldSelectionList) list(mc);
                List<String> ids = new ArrayList<>();
                for (var row : wlist.children()) {
                    if (row instanceof WorldSelectionList.WorldListEntry we) {
                        ids.add(we.getLevelSummary().getLevelId());
                    }
                }
                com.thelads.core.client.qa.QaWorldFixtures.assertOrder(ids, java.util.Arrays.copyOfRange(s, 2, s.length), mc.getLevelSource().getBaseDir());
                LOGGER.info("Lads 1.21.11 qa worlds order verified: {}", ids);
            }
            case "assertsavedworlds" -> com.thelads.core.client.qa.QaWorldFixtures.assertSavedFileEquals(java.util.Arrays.copyOfRange(s, 2, s.length));
            case "worldfilter" -> {
                String q = s.length > 2 ? s[2] : "";
                for (var child : screen(mc).children()) {
                    if (child instanceof net.minecraft.client.gui.components.EditBox box) {
                        box.setValue(q);
                        break;
                    }
                }
            }
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
        NbtIo.write(root, file);
        LOGGER.info("Lads qa srv: seeded {} with {} servers", file, servers.size());
    }

    private static void log(Minecraft mc) {
        Screen screen = screen(mc);
        List<String> rows = new ArrayList<>();
        if (screen instanceof JoinMultiplayerScreen multi) {
            var servers = multi.getServers();
            for (int i = 0; i < servers.size(); i++) {
                ServerData data = servers.get(i);
                ServerSelectionList.Entry row = servers(mc).children().get(i);
                rows.add(i + ":" + data.name + "@" + data.ip + " state=" + data.state() + " protocol=" + data.protocol + " version=" + data.version.getString()
                    + " forced=" + ViaForce.forced(data) + " tag=" + data.write().get(ViaForce.VIA_TAG) + "/" + data.write().get(ViaForce.TAG)
                    + " rect=" + row.getX() + "," + row.getY() + "," + row.getWidth());
            }
            LOGGER.info("Lads qa srv: client protocol {}, ViaForce.available={}, columns wanted={} shown={} most={} width={}", SharedConstants.getProtocolVersion(), ViaForce.available(),
                ServerListColumns.wanted(), ServerListColumns.fit(ServerListColumns.wanted(), screen.width), ServerListColumns.most(screen.width), screen.width);
            LOGGER.info("Lads qa srv: selected={} buttons={}", servers(mc).getSelected() == null ? "none" : servers(mc).children().indexOf(servers(mc).getSelected()),
                screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button).map(c -> (net.minecraft.client.gui.components.Button) c)
                    .map(b -> b.getMessage().getString() + (b.active ? "" : "(off)") + (b.visible ? "" : "(hidden)")).toList());
        } else if (screen instanceof SelectWorldScreen) {
            WorldSelectionList worlds = (WorldSelectionList) list(mc);
            for (var row : worlds.children()) rows.add(row instanceof WorldSelectionList.WorldListEntry world ? world.getLevelName() : row.getClass().getSimpleName());
            LOGGER.info("Lads qa srv: buttons={}", screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button).map(c -> (net.minecraft.client.gui.components.Button) c)
                .map(b -> b.getMessage().getString() + (b.active ? "" : "(off)")).toList());
        }
        LOGGER.info("Lads qa srv: screen={} rows={} clipboard={}", screen == null ? "none" : screen.getClass().getSimpleName(), rows, mc.keyboardHandler.getClipboard());
        try {
            Class<?> api = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
            LOGGER.info("Lads qa srv: ViaFabricPlus global target {}", api.getMethod("getImpl").invoke(null).getClass().getMethod("getTargetVersion").invoke(api.getMethod("getImpl").invoke(null)));
        } catch (ReflectiveOperationException | LinkageError none) {
            LOGGER.info("Lads qa srv: no ViaFabricPlus API");
        }
    }

    private static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    private static ServerSelectionList servers(Minecraft mc) {
        return (ServerSelectionList) list(mc);
    }

    private static AbstractSelectionList<?> list(Minecraft mc) {
        for (var child : screen(mc).children()) if (child instanceof AbstractSelectionList<?> list) return list;
        throw new IllegalStateException("no list on " + screen(mc));
    }

    /** "x y" in GUI units, or "row i" for the centre of a row, or "slot i" for a slot of the current columns. */
    private static int[] at(Minecraft mc, String[] s, int from) {
        if (s[from].equals("row")) {
            var row = (net.minecraft.client.gui.layouts.LayoutElement) list(mc).children().get(Integer.parseInt(s[from + 1]));
            return new int[] {row.getX() + row.getWidth() / 2, row.getY() + row.getHeight() / 2};
        }
        if (s[from].equals("slot")) {
            AbstractSelectionList<?> list = list(mc);
            int columns = ServerListColumns.fit(ServerListColumns.wanted(), screen(mc).width), slot = Integer.parseInt(s[from + 1]);
            return new int[] {ServerListColumns.x(slot, columns, list.getRowLeft(), ServerListColumns.width(columns, screen(mc).width)) + 90,
                ServerListColumns.y(slot, columns, list.getY() + 2 - (int) list.scrollAmount(), 36) + 18};
        }
        return new int[] {Integer.parseInt(s[from]), Integer.parseInt(s[from + 1])};
    }

    private static MouseButtonEvent event(int x, int y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    private static void click(Minecraft mc, int x, int y, int button) {
        boolean used = screen(mc).mouseClicked(event(x, y, button), false);
        LOGGER.info("Lads qa srv: click {},{} button {} used={}", x, y, button, used);
    }
}
