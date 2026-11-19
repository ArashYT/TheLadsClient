package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dialogs (PROTOCOL.md section 21): toast, vote and modal cards. A dialog sends one answer at most, and only for a button the
 * server named or "dismiss": {@link #answer}. Esc, chat, pause and disconnect are never blocked (the hook decides how a modal
 * grabs the mouse). Game thread only.
 */
public final class DialogState {
    public static final int MAX = 4;
    public static final String DISMISS = "dismiss";

    public enum Style { TOAST, VOTE, MODAL }

    /** {@code icon} is an item id or null. */
    public record Button(String id, String label, int color, String icon) {}

    public record Dialog(String id, Style style, String title, String body, List<Button> buttons, long openedAt, int ms) {
        public boolean expired(long now) {
            return now - openedAt >= ms;
        }
    }

    private final Map<String, Dialog> map = new LinkedHashMap<>();

    public void clear() {
        map.clear();
    }

    /** What to show now; expired ones are dropped (and then nothing is sent for them). */
    public List<Dialog> open(long now) {
        map.values().removeIf(d -> d.expired(now));
        return new ArrayList<>(map.values());
    }

    public void apply(JsonObject json, long now) {
        String op = Wire.str(json, "op");
        if (op == null) op = "open";
        String id = Wire.id(json, "id", 32);
        if (id == null) return;
        if (op.equals("close")) {
            map.remove(id);
            return;
        }
        if (!op.equals("open")) return;
        String st = Wire.str(json, "style");
        if (st == null) return;
        Style style = switch (st) {
            case "toast" -> Style.TOAST;
            case "vote" -> Style.VOTE;
            case "modal" -> Style.MODAL;
            default -> null;
        };
        if (style == null) return;
        map.values().removeIf(d -> d.expired(now));
        if (!map.containsKey(id) && map.size() >= MAX) return;
        List<Button> buttons = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonArray arr = Wire.arr(json, "buttons");
        if (arr != null) {
            for (JsonElement e : arr) {
                if (buttons.size() >= 6) break;
                if (!e.isJsonObject()) continue;
                JsonObject b = e.getAsJsonObject();
                String bid = Wire.id(b, "id", 24);
                if (bid == null || bid.equals(DISMISS) || !seen.add(bid)) continue;
                String label = Wire.text(b, "label", 24);
                buttons.add(new Button(bid, label == null ? bid : label, Wire.color(b, "color", 0xFFFFFF),
                    Wire.resource(Wire.str(b, "icon"), 64)));
            }
        }
        String title = Wire.text(json, "title", 64), body = Wire.text(json, "body", 256);
        map.put(id, new Dialog(id, style, title == null ? "" : title, body == null ? "" : body, List.copyOf(buttons), now,
            Wire.whole(json, "ms", 1000, 60000, 15000)));
    }

    /**
     * The player clicked a button, pressed its number key, or dismissed. Returns true once, only for a dialog that is open and
     * not expired and a choice that is one of its button ids or "dismiss"; the dialog is then gone.
     */
    public boolean answer(String id, String choice, long now) {
        Dialog d = id == null ? null : map.get(id);
        if (d == null || choice == null) return false;
        if (d.expired(now)) {
            map.remove(id);
            return false;
        }
        if (!choice.equals(DISMISS) && d.buttons().stream().noneMatch(b -> b.id().equals(choice))) return false;
        map.remove(id);
        return true;
    }
}
