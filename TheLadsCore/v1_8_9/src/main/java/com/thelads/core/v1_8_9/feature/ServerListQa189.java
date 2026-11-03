package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ViaForce;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v1_8_9.mixin.GuiScreenInputInvoker189;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA of the multiplayer and world lists: the script's "srv" steps. Everything goes through the open screen's own event methods, so the
 * player's real mouse is left alone. seed writes a hand-edited servers.dat (the network not first) for the game's own list to read.
 */
final class ServerListQa189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore");
    private static int lastX, lastY;

    private ServerListQa189() {}

    static void step(Minecraft mc, String[] s) throws Exception {
        GuiScreen screen = mc.currentScreen;
        GuiScreenInputInvoker189 input = (GuiScreenInputInvoker189) screen;
        switch (s[1]) {
            case "multiplayer":
                mc.displayGuiScreen(new GuiMultiplayer(screen));
                break;
            case "seed":
                seed(mc, s);
                break;
            case "columns":
                ((SliderOption) ModuleManager.getInstance().getModule(ServerListDrag.NAME).getOption(ServerListColumns.OPTION)).setValue(Integer.parseInt(s[2]));
                if (screen != null) screen.onResize(mc, screen.width, screen.height);
                break;
            case "scale":
                mc.gameSettings.guiScale = Integer.parseInt(s[2]);
                mc.displayGuiScreen(screen);
                break;
            case "hover":
                if (s.length < 3) ServerListDrag.qaMouseX = ServerListDrag.qaMouseY = -1;
                else {
                    int[] at = at(mc, s, 2);
                    ServerListDrag.qaMouseX = at[0];
                    ServerListDrag.qaMouseY = at[1];
                }
                break;
            case "click": {
                int[] at = at(mc, s, 2);
                input.ladsMouseClicked(at[0], at[1], 0);
                break;
            }
            case "rclick": {
                int[] at = at(mc, s, 2);
                lastX = at[0];
                lastY = at[1];
                input.ladsMouseClicked(at[0], at[1], 1);
                break;
            }
            case "pick":
                input.ladsMouseClicked(lastX + 8, lastY + 3 + 16 * Integer.parseInt(s[2]) + 8, 0);
                break;
            case "press": { // the left button down on a row, and held; the drag reads the drawn mouse
                ServerListDrag.heldForQa = true;
                int[] at = at(mc, s, 2);
                lastX = at[0];
                lastY = at[1];
                ServerListDrag.qaMouseX = at[0];
                ServerListDrag.qaMouseY = at[1];
                input.ladsMouseClicked(at[0], at[1], 0);
                break;
            }
            case "drag": {
                int[] at = at(mc, s, 2);
                lastX = at[0];
                lastY = at[1];
                ServerListDrag.qaMouseX = at[0];
                ServerListDrag.qaMouseY = at[1];
                break;
            }
            case "release":
                input.ladsMouseReleased(lastX, lastY, 0);
                ServerListDrag.heldForQa = false;
                ServerListDrag.qaMouseX = ServerListDrag.qaMouseY = -1;
                break;
            case "key":
                input.ladsKeyTyped((char) 0, s[2].equals("up") ? org.lwjgl.input.Keyboard.KEY_UP : s[2].equals("down") ? org.lwjgl.input.Keyboard.KEY_DOWN
                    : s[2].equals("left") ? org.lwjgl.input.Keyboard.KEY_LEFT : s[2].equals("right") ? org.lwjgl.input.Keyboard.KEY_RIGHT : org.lwjgl.input.Keyboard.KEY_ESCAPE);
                break;
            case "arrow": { // vanilla's move arrows on a row's icon: up or down
                ServerListColumns.Rows rows = (ServerListColumns.Rows) list(screen);
                int row = Integer.parseInt(s[2]);
                input.ladsMouseClicked(rows.lads$rowLeft(row) + 8, rows.lads$rowTop(row) + (s[3].equals("up") ? 8 : 24), 0);
                break;
            }
            case "worlds":
                mc.displayGuiScreen(new GuiSelectWorld(screen));
                break;
            case "log":
                log(mc);
                break;
            default:
                LOGGER.warn("Lads qa srv: unknown {}", s[1]);
        }
    }

    private static void seed(Minecraft mc, String[] s) throws Exception {
        File file = new File(mc.mcDataDir, "servers.dat");
        NBTTagList servers = new NBTTagList();
        for (int i = 2; i + 1 < s.length; i += 2) { // name|with|bars address ...
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("name", s[i].replace('|', ' '));
            entry.setString("ip", s[i + 1]);
            servers.appendTag(entry);
        }
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("servers", servers);
        CompressedStreamTools.safeWrite(root, file);
        LOGGER.info("Lads qa srv: seeded {} with {} servers", file, servers.tagCount());
    }

    private static void log(Minecraft mc) {
        GuiScreen screen = mc.currentScreen;
        List<String> rows = new ArrayList<>(), buttons = new ArrayList<>();
        if (screen instanceof GuiMultiplayer) {
            net.minecraft.client.multiplayer.ServerList servers = ((GuiMultiplayer) screen).getServerList();
            for (int i = 0; i < servers.countServers(); i++) {
                ServerData data = servers.getServerData(i);
                rows.add(i + ":" + data.serverName + "@" + data.serverIP + " ping=" + data.pingToServer + " protocol=" + data.version + " version=" + data.gameVersion);
            }
            ServerSelectionList list = list(screen);
            LOGGER.info("Lads qa srv: ViaForce.available={}, columns wanted={} shown={} most={} width={} selected={}", ViaForce.available(), ServerListColumns.wanted(),
                ServerListColumns.fit(ServerListColumns.wanted(), screen.width), ServerListColumns.most(screen.width), screen.width, list.func_148193_k());
            for (GuiButton button : ((com.thelads.core.v1_8_9.mixin.GuiScreenButtonsAccessor) screen).ladsButtons()) {
                buttons.add(button.displayString + (button.enabled ? "" : "(off)") + (button.visible ? "" : "(hidden)"));
            }
        } else if (screen != null) {
            for (GuiButton button : ((com.thelads.core.v1_8_9.mixin.GuiScreenButtonsAccessor) screen).ladsButtons()) {
                buttons.add(button.displayString + (button.enabled ? "" : "(off)"));
            }
        }
        LOGGER.info("Lads qa srv: screen={} buttons={} rows={} clipboard={}", screen == null ? "none" : screen.getClass().getSimpleName(), buttons, rows, GuiScreen.getClipboardString());
    }

    private static ServerSelectionList list(GuiScreen screen) {
        return ((com.thelads.core.v1_8_9.mixin.GuiMultiplayerListAccessor) screen).ladsList();
    }

    /** "x y" in GUI units, "row i" for a row, or "slot i" for a slot of the current columns; "wrow i" a world row. */
    private static int[] at(Minecraft mc, String[] s, int from) {
        GuiScreen screen = mc.currentScreen;
        if (s[from].equals("wrow")) return new int[] {screen.width / 2, 32 + 4 + Integer.parseInt(s[from + 1]) * 36 + 16};
        if (s[from].equals("row")) {
            ServerListColumns.Rows rows = (ServerListColumns.Rows) list(screen);
            int row = Integer.parseInt(s[from + 1]);
            return new int[] {rows.lads$rowLeft(row) + 90, rows.lads$rowTop(row) + 16};
        }
        if (s[from].equals("slot")) {
            ServerSelectionList list = list(screen);
            int columns = ServerListColumns.fit(ServerListColumns.wanted(), screen.width), slot = Integer.parseInt(s[from + 1]);
            int left = screen.width / 2 - (columns == 1 ? list.getListWidth() : ServerListColumns.total(columns, screen.width)) / 2;
            return new int[] {ServerListColumns.x(slot, columns, left, list.getListWidth()) + 90, ServerListColumns.y(slot, columns, list.top + 4 - list.getAmountScrolled(), list.slotHeight) + 16};
        }
        return new int[] {Integer.parseInt(s[from]), Integer.parseInt(s[from + 1])};
    }
}
