package com.thelads.core.v1_20_1.ladsapi.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.ladsapi.minigame.ActionsState;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v1_20_1.ladsapi.ui.mixin.KeyMappingAccess1201;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Server actions (Lads API section 20) as temporary keys under a "Server" Controls category. The player may rebind them; nothing is
 * written to options.txt (see ServerKeysOptionsMixin) and they are removed when the server's list goes. A real press or release
 * goes out through {@link LadsApiClient#serverAction}, only in the world with no screen open (so never while typing).
 */
public final class ServerKeys1201 implements Hooks.ServerKeys {
    /** The Controls heading (language key). The game sorts keys by category with no default for an unknown one: see {@link #register()}. */
    public static final String CATEGORY = "key.categories.theladscore.server";
    private static final String PREFIX = "lads.server.";

    /** The keys of the current actions list, by action id. */
    private static final Map<String, ServerKey> KEYS = new LinkedHashMap<>();
    /** Display names by translation key, read by the language mixin from any thread. */
    private static volatile Map<String, String> labels = Map.of();
    private static int sweep;

    /** Gives the Server category a place in the Controls sort order (after the last one), once; call at startup. */
    public static void register() {
        Map<String, Integer> order = KeyMappingAccess1201.lads$sortOrder();
        if (!order.containsKey(CATEGORY)) order.put(CATEGORY, order.values().stream().max(Integer::compare).orElse(0) + 1);
    }

    private static final class ServerKey extends KeyMapping {
        final String id;
        final String pad;
        boolean wasDown, padDown;

        ServerKey(String id, String pad, InputConstants.Key key) {
            super(PREFIX + id, key.getType(), key.getValue(), CATEGORY);
            this.id = id;
            this.pad = pad;
        }
    }

    public static boolean isServerKey(KeyMapping k) {
        return k instanceof ServerKey;
    }

    /** The display name for a server key's translation key, or null for any other key. */
    public static String label(String key) {
        return key != null && key.startsWith(PREFIX) ? labels.get(key) : null;
    }

    @Override
    public boolean keyFree(String optionsKeyName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || optionsKeyName == null) return true;
        for (KeyMapping k : mc.options.keyMappings) {
            if (!(k instanceof ServerKey) && optionsKeyName.equals(k.saveString())) return false;
        }
        return true;
    }

    @Override
    public void changed(List<ActionsState.Action> actions) {
        List<ActionsState.Action> copy = List.copyOf(actions);
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) apply(copy); // a disconnect must not leave the keys to a task that may never run
        else mc.execute(() -> apply(copy));
    }

    private void apply(List<ActionsState.Action> actions) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        Map<String, ServerKey> next = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        for (ActionsState.Action a : actions) {
            ServerKey old = KEYS.get(a.id());
            ServerKey k;
            if (old != null && java.util.Objects.equals(old.pad, a.pad())) {
                k = old; // keeps the key the player chose
            } else {
                InputConstants.Key bound = InputConstants.UNKNOWN;
                if (a.key() != null && keyFree(a.key())) {
                    try {
                        bound = InputConstants.getKey(a.key());
                    } catch (RuntimeException ignored) {
                        bound = InputConstants.UNKNOWN;
                    }
                }
                k = new ServerKey(a.id(), a.pad(), bound);
            }
            next.put(a.id(), k);
            names.put(PREFIX + a.id(), a.name() == null || a.name().isEmpty() ? a.id() : a.name());
        }
        Map<String, KeyMapping> all = KeyMappingAccess1201.lads$all();
        List<KeyMapping> keep = new ArrayList<>();
        for (KeyMapping k : mc.options.keyMappings) if (!(k instanceof ServerKey)) keep.add(k);
        for (ServerKey old : KEYS.values()) {
            if (next.get(old.id) != old) {
                all.remove(old.getName());
                if (old.wasDown) LadsApiClient.serverAction(old.id, false);
            }
        }
        for (ServerKey k : next.values()) {
            all.put(k.getName(), k);
            keep.add(k);
        }
        KEYS.clear();
        KEYS.putAll(next);
        labels = names;
        replaceKeys(mc, keep.toArray(new KeyMapping[0]));
    }

    private static void replaceKeys(Minecraft mc, KeyMapping[] keys) {
        ((OptionsKeys) mc.options).lads$setKeys(keys);
        KeyMapping.resetMapping();
    }

    /** Implemented by the Options class through ServerKeysOptionsMixin: swaps the (final) key array. */
    public interface OptionsKeys {
        void lads$setKeys(KeyMapping[] keys);
    }

    /** The keys differ from the server's current list, or a server key is still in the game's key tables after its list went. */
    private static boolean stale() {
        List<ActionsState.Action> list = Minigame.actions.list();
        if (list.size() != KEYS.size()) return true;
        for (ActionsState.Action a : list) if (!KEYS.containsKey(a.id())) return true;
        int inAll = 0;
        for (KeyMapping k : KeyMappingAccess1201.lads$all().values()) if (k instanceof ServerKey) inAll++;
        int inOpts = 0;
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) if (k instanceof ServerKey) inOpts++;
        return inAll != KEYS.size() || inOpts != KEYS.size();
    }

    /** Client tick: real presses and releases (and the pad suggestion, with a pad in use) become server actions. */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && (!KEYS.isEmpty() || ++sweep % 40 == 0) && stale()) apply(Minigame.actions.list()); // no key may outlive the server's list
        if (KEYS.isEmpty()) return;
        boolean live = mc.player != null && mc.screen == null && (mc.isWindowActive() || com.thelads.core.v1_20_1.feature.QaProbe1201.active());
        boolean pad = Hooks.input != null && Hooks.input.device() == Hooks.Device.GAMEPAD;
        for (ServerKey k : KEYS.values()) {
            boolean down = live && k.isDown();
            if (down != k.wasDown) {
                k.wasDown = down;
                LadsApiClient.serverAction(k.id, down);
            }
            boolean pd = live && pad && k.pad != null && Pad1201.get().down(k.pad);
            if (pd != k.padDown) {
                k.padDown = pd;
                LadsApiClient.serverAction(k.id, pd);
            }
        }
    }
}
