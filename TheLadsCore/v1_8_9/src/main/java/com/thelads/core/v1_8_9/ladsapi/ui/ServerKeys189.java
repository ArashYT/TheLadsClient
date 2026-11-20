package com.thelads.core.v1_8_9.ladsapi.ui;

import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.ladsapi.minigame.ActionsState;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

/**
 * Server action keys on 1.8.9 (PROTOCOL.md section 16). Each action is a temporary key under the "Server" category of the Controls
 * screen. They are never written to options.txt (GameSettings.saveOptions is given an array without them), never fire while a
 * screen is open or the window is unfocused, and are fully removed (key list, key hash, category) when the server drops the
 * actions, on reset and on disconnect. A key the player rebound stays while its id stays.
 */
public final class ServerKeys189 implements Hooks.ServerKeys {
    static final String CATEGORY = "key.categories.theladscore.server";

    /** Lets the KeyBinding mixin take a key out of the game's private key list. */
    public interface Removable {
        void lads$unregister();
    }

    private static final class ServerKey extends KeyBinding {
        final String id;
        final String name;

        ServerKey(String id, String name, int code) {
            super(name.replace("%", "%%"), code, CATEGORY);
            this.id = id;
            this.name = name;
        }
    }

    private static final Map<String, Integer> MODERN = new HashMap<String, Integer>();
    static {
        String[][] t = {
            {"left.shift", "LSHIFT"}, {"right.shift", "RSHIFT"}, {"left.control", "LCONTROL"}, {"right.control", "RCONTROL"},
            {"left.alt", "LMENU"}, {"right.alt", "RMENU"}, {"enter", "RETURN"}, {"backspace", "BACK"}, {"grave.accent", "GRAVE"},
            {"page.up", "PRIOR"}, {"page.down", "NEXT"}, {"caps.lock", "CAPITAL"}, {"equal", "EQUALS"},
            {"left.bracket", "LBRACKET"}, {"right.bracket", "RBRACKET"}, {"keypad.enter", "NUMPADENTER"},
            {"keypad.add", "ADD"}, {"keypad.subtract", "SUBTRACT"}, {"keypad.multiply", "MULTIPLY"}, {"keypad.divide", "DIVIDE"},
            {"keypad.decimal", "DECIMAL"}, {"keypad.equal", "NUMPADEQUALS"}, {"left.win", "LMETA"}, {"right.win", "RMETA"},
            {"print.screen", "SYSRQ"}, {"scroll.lock", "SCROLL"}, {"num.lock", "NUMLOCK"}
        };
        for (String[] p : t) MODERN.put(p[0], Keyboard.getKeyIndex(p[1]));
        for (int i = 0; i <= 9; i++) MODERN.put("keypad." + i, Keyboard.getKeyIndex("NUMPAD" + i));
    }

    /** An options.txt style key name ("key.keyboard.f", "key.mouse.left") as a 1.8.9 key code, 0 when there is none (or refused). */
    static int code(String name) {
        if (name == null) return 0;
        if (name.startsWith("key.mouse.")) {
            String m = name.substring(10);
            if (m.equals("left")) return -100;
            if (m.equals("right")) return -99;
            if (m.equals("middle")) return -98;
            try {
                int n = Integer.parseInt(m);
                return n >= 4 && n <= 8 ? -100 + (n - 1) : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (!name.startsWith("key.keyboard.")) return 0;
        String k = name.substring(13);
        if (k.equals("escape") || k.equals("unknown")) return 0;
        Integer sp = MODERN.get(k);
        if (sp != null) return sp;
        return Keyboard.getKeyIndex(k.toUpperCase(java.util.Locale.ROOT));
    }

    private final Map<String, ServerKey> keys = new HashMap<String, ServerKey>();
    private final Map<String, Boolean> down = new HashMap<String, Boolean>();

    @Override
    public boolean keyFree(String optionsKeyName) {
        int c = code(optionsKeyName);
        if (c == 0) return false;
        KeyBinding[] all = Minecraft.getMinecraft().gameSettings.keyBindings;
        for (KeyBinding k : all) if (!(k instanceof ServerKey) && k.getKeyCode() == c) return false;
        return true;
    }

    @Override
    public void changed(List<ActionsState.Action> actions) {
        Set<String> want = new HashSet<String>();
        for (ActionsState.Action a : actions) {
            if (a.key() == null && a.pad() == null) { /* unbound but still listed: the player can bind it */ }
            want.add(a.id());
        }
        boolean dirty = false;
        for (String id : new ArrayList<String>(keys.keySet())) {
            if (!want.contains(id)) {
                release(id);
                remove(keys.remove(id));
                dirty = true;
            }
        }
        for (ActionsState.Action a : actions) {
            ServerKey old = keys.get(a.id());
            if (old != null && old.name.equals(a.name())) continue;
            int code = old != null ? old.getKeyCode() : code(a.key());
            if (old != null) {
                release(a.id());
                remove(keys.remove(a.id()));
            }
            ServerKey k = new ServerKey(a.id(), a.name(), code);
            keys.put(a.id(), k);
            KeyBinding[] cur = Minecraft.getMinecraft().gameSettings.keyBindings;
            KeyBinding[] grown = new KeyBinding[cur.length + 1];
            System.arraycopy(cur, 0, grown, 0, cur.length);
            grown[cur.length] = k;
            Minecraft.getMinecraft().gameSettings.keyBindings = grown;
            dirty = true;
        }
        if (dirty) KeyBinding.resetKeyBindingArrayAndHash();
        if (keys.isEmpty()) dropCategory();
    }

    private void release(String id) {
        Boolean d = down.remove(id);
        if (d != null && d) LadsApiClient.serverAction(id, false);
    }

    private static void remove(ServerKey k) {
        if (k == null) return;
        ((Removable) (Object) k).lads$unregister();
        KeyBinding[] cur = Minecraft.getMinecraft().gameSettings.keyBindings;
        List<KeyBinding> out = new ArrayList<KeyBinding>();
        for (KeyBinding b : cur) if (b != k) out.add(b);
        Minecraft.getMinecraft().gameSettings.keyBindings = out.toArray(new KeyBinding[0]);
    }

    private static void dropCategory() {
        KeyBinding.getKeybinds().remove(CATEGORY);
    }

    /** Options saving: whether the list has a server key, and the list without them. */
    public static boolean any(KeyBinding[] all) {
        if (all == null) return false;
        for (KeyBinding k : all) if (k instanceof ServerKey) return true;
        return false;
    }

    public static KeyBinding[] without(KeyBinding[] all) {
        List<KeyBinding> out = new ArrayList<KeyBinding>();
        for (KeyBinding k : all) if (!(k instanceof ServerKey)) out.add(k);
        return out.toArray(new KeyBinding[0]);
    }

    /** QA: the player rebinds a server key (as the Controls screen would). */
    boolean rebind(String id, int code) {
        ServerKey k = keys.get(id);
        if (k == null) return false;
        k.setKeyCode(code);
        KeyBinding.resetKeyBindingArrayAndHash();
        return true;
    }

    int count() {
        return keys.size();
    }

    /** QA text: "n=1 a:key=17:down=false". */
    String qa() {
        StringBuilder b = new StringBuilder("n=").append(keys.size());
        for (ServerKey k : keys.values()) {
            b.append(' ').append(k.id).append(":code=").append(k.getKeyCode()).append(":down=").append(Boolean.TRUE.equals(down.get(k.id)));
        }
        return b.toString();
    }

    /** Every client tick: keep the keys equal to the actions list, and send the edges of a real press. */
    void tick(boolean qaActive) {
        Minecraft mc = Minecraft.getMinecraft();
        List<ActionsState.Action> list = Minigame.actions.list();
        boolean same = list.size() == keys.size();
        if (same) for (ActionsState.Action a : list) if (!keys.containsKey(a.id())) same = false;
        if (!same) changed(list);
        if (keys.isEmpty()) return;
        boolean gate = mc.thePlayer != null && mc.currentScreen == null && (Display.isActive() || qaActive);
        for (ServerKey k : keys.values()) {
            boolean now = gate && k.getKeyCode() != 0 && k.isKeyDown();
            while (k.isPressed()) { /* used up: only the held state is sent */ }
            boolean was = Boolean.TRUE.equals(down.get(k.id));
            if (now != was) {
                LadsApiClient.serverAction(k.id, now);
                down.put(k.id, now);
            }
        }
    }

    void clearAll() {
        changed(java.util.Collections.<ActionsState.Action>emptyList());
        down.clear();
    }
}
