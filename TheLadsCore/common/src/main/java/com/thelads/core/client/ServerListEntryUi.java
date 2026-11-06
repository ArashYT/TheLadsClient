package com.thelads.core.client;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.ListContextMenu.Item;
import com.thelads.core.client.title.TitleScreenTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * What a row of the multiplayer list adds to vanilla's: the server's version left of the player count (red when it differs from the
 * client's), the "change version" button, and the right-click menu's items. Each version's entry mixin draws with these rules.
 */
public final class ServerListEntryUi {
    public static final String CHANGE_VERSION = "Change version for this server and reconnect";
    private static final String[] BUTTON_LABELS = {CHANGE_VERSION, "Change version & reconnect", "Change version"};
    public static final int VERSION_MUTED = 0xFF8A8A8A, VERSION_RED = 0xFFFF5555;
    /** Flat yellow star icon shown beside the pinned server's name. */
    public static final int PIN_STAR_COLOR = 0xFFFFD43B;
    public static final int PIN_STAR_SIZE = 9, PIN_STAR_GAP = 4, PIN_STAR_ADVANCE = PIN_STAR_SIZE + PIN_STAR_GAP;
    /** A row's icon and the space after it. */
    public static final int TEXT_X = 35;
    /** Ping bars: the icon and the space around it, at the row's right edge. */
    private static final int PING = 20;
    private static final int SPACE = 6;
    /** The button sits in the MOTD's second line, right aligned: it costs the MOTD that line. */
    public static final int BUTTON_Y = 20, BUTTON_HEIGHT = 12, BUTTON_RIGHT = 5;
    private static final int BUTTON_ICON = 10, BUTTON_PAD = 3;
    private static final int BUTTON_COLOR = 0xD05A1E24, BUTTON_HOVER = 0xFF8C2F36;

    public enum Version { NONE, MUTED, RED, RED_WITH_BUTTON }

    private ServerListEntryUi() {}

    /** What the row shows: nothing until pinged; a mismatch offers the button only when ViaFabricPlus can force what the ping reported. */
    public static Version version(boolean pinged, boolean mismatched, boolean viaSupports) {
        if (!pinged) return Version.NONE;
        if (!mismatched) return Version.MUTED;
        return viaSupports ? Version.RED_WITH_BUTTON : Version.RED;
    }

    /**
     * The widest name and version on a row's first line, {name, version}, right of the icon and left of the player count ({@code statusWidth})
     * and ping. When both do not fit the version gets what the name leaves, at least 2/5 of the room, so a long name cannot hide it.
     */
    public static int[] firstLine(int contentWidth, int nameWidth, int versionWidth, int statusWidth) {
        return firstLine(contentWidth, nameWidth, versionWidth, statusWidth, false);
    }

    /** Same as {@link #firstLine(int, int, int, int)}, reserving {@link #PIN_STAR_ADVANCE} beside the name when {@code pinned} is true. */
    public static int[] firstLine(int contentWidth, int nameWidth, int versionWidth, int statusWidth, boolean pinned) {
        int star = pinned ? PIN_STAR_ADVANCE : 0;
        int room = Math.max(0, contentWidth - TEXT_X - PING - statusWidth - 2 * SPACE - star);
        if (nameWidth + versionWidth <= room) return new int[] {nameWidth, versionWidth};
        int version = Math.min(room, Math.min(versionWidth, Math.max(room - nameWidth, (room + star) * 2 / 5)));
        return new int[] {Math.max(0, room - version), version};
    }

    /** Left X of the pinned server's star icon right after the drawn name. */
    public static int pinStarX(int rowLeft, int clippedNameWidth) {
        return rowLeft + TEXT_X + clippedNameWidth + PIN_STAR_GAP;
    }

    /** Draws the flat yellow Flaticon star beside a pinned server's name. */
    public static void drawPinStar(LadsGraphics g, int x, int y) {
        drawPinStar(g, x, y, 0xFF);
    }

    public static void drawPinStar(LadsGraphics g, int x, int y, int alpha) {
        int color = ((alpha & 0xFF) << 24) | (PIN_STAR_COLOR & 0xFFFFFF);
        TitleScreenTheme.icon(g, "star", x, y, PIN_STAR_SIZE, color);
    }

    /** The right edge of the version, relative to the row's content: left of the player count. */
    public static int versionRight(int contentWidth, int statusWidth) {
        return contentWidth - PING - statusWidth - SPACE;
    }

    private static final int MAX_CLIPPED = 128;

    /** {@code text} cut with ".." to fit, unchanged when it fits; "" when not even that does. */
    public static String clip(String text, int maxWidth, ToIntFunction<String> width) {
        if (text.length() > MAX_CLIPPED) text = text.substring(0, MAX_CLIPPED); // a server names its own version: this runs every frame
        if (width.applyAsInt(text) <= maxWidth) return text;
        for (int end = text.length() - 1; end > 0; end--) {
            String cut = text.substring(0, end) + "..";
            if (width.applyAsInt(cut) <= maxWidth) return cut;
        }
        return "";
    }

    public static int buttonWidth(String label, ToIntFunction<String> width) {
        return BUTTON_ICON + 2 * BUTTON_PAD + width.applyAsInt(label);
    }

    /** The longest button label that fits a row this wide, null when none does. */
    public static String buttonLabel(int contentWidth, ToIntFunction<String> width) {
        for (String label : BUTTON_LABELS) {
            if (buttonWidth(label, width) <= contentWidth - TEXT_X - BUTTON_RIGHT) return label;
        }
        return null;
    }

    public static void drawButton(LadsGraphics g, int x, int y, int w, String label, boolean hovered) {
        g.fill(x, y, x + w, y + BUTTON_HEIGHT, hovered ? BUTTON_HOVER : BUTTON_COLOR);
        TitleScreenTheme.icon(g, "switch", x + 2, y + 1, BUTTON_ICON, 0xFFFFFFFF);
        g.drawText(label, x + BUTTON_ICON + BUTTON_PAD + 1, y + 2, 0xFFFFFFFF, false);
    }

    /** A saved server's menu. The pinned network keeps what cannot move or remove it. */
    public static List<Item> serverMenu(boolean pinned, boolean changeVersion) {
        List<Item> items = new ArrayList<>();
        items.add(new Item("join", "Join", "play", true));
        if (!pinned) {
            items.add(new Item("edit", "Edit", "edit", true));
            items.add(new Item("delete", "Delete", "delete", true));
        }
        items.add(new Item("copy", "Copy address", "copy", true));
        items.add(new Item("refresh", "Refresh", "refresh", true));
        if (changeVersion) items.add(new Item("version", "Change version and reconnect", "switch", true));
        return items;
    }

    /** A LAN game's menu: it is not saved, so it has nothing to edit. */
    public static List<Item> lanMenu() {
        return List.of(new Item("join", "Join", "play", true));
    }

    /** A world's menu; each flag is whether vanilla's own button for that action is active for this world. */
    public static List<Item> worldMenu(boolean play, boolean edit, boolean delete, boolean recreate) {
        return List.of(new Item("play", "Play", "play", play), new Item("edit", "Edit", "edit", edit),
            new Item("delete", "Delete", "delete", delete), new Item("recreate", "Re-create", "refresh", recreate),
            new Item("folder", "Open folder", "folder", true), new Item("copy", "Copy name", "copy", true));
    }
}
