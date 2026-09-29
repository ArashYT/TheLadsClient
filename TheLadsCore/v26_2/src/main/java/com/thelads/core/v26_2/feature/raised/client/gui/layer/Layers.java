// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.client.gui.layer;

import com.thelads.core.v26_2.feature.raised.registry.LayerRegistry;
import net.minecraft.resources.Identifier;

public class Layers {

    public static final Identifier HOTBAR = Identifier.withDefaultNamespace("hotbar");
    public static final Identifier CHAT = Identifier.withDefaultNamespace("chat");
    public static final Identifier ACTION_BAR = Identifier.withDefaultNamespace("action_bar");
    public static final Identifier BOSS_BAR = Identifier.withDefaultNamespace("boss_bar");
    public static final Identifier SCOREBOARD = Identifier.withDefaultNamespace("scoreboard");
    public static final Identifier EFFECTS = Identifier.withDefaultNamespace("effects");
    public static final Identifier PLAYER_LIST = Identifier.withDefaultNamespace("player_list");
    public static final Identifier TITLES = Identifier.withDefaultNamespace("titles");
    public static final Identifier SUBTITLES = Identifier.withDefaultNamespace("subtitles");
    public static final Identifier TOASTS = Identifier.withDefaultNamespace("toasts");
    public static final Identifier UNKNOWN = Identifier.withDefaultNamespace("unknown");

    public Layers() {}

    public static void boostrap() {
        LayerRegistry.register(HOTBAR, new Layer(Layer.Anchor.BOTTOM));
        LayerRegistry.register(CHAT, new Layer(Layer.Anchor.NONE));
        LayerRegistry.register(ACTION_BAR, new Layer(Layer.Anchor.BOTTOM));
        LayerRegistry.register(BOSS_BAR, new Layer(Layer.Anchor.TOP));
        LayerRegistry.register(SCOREBOARD, new Layer(Layer.Anchor.RIGHT));
        LayerRegistry.register(EFFECTS, new Layer(Layer.Anchor.TOP_RIGHT));
        LayerRegistry.register(PLAYER_LIST, new Layer(Layer.Anchor.TOP));
        LayerRegistry.register(TITLES, new Layer(Layer.Anchor.NONE));
        LayerRegistry.register(SUBTITLES, new Layer(Layer.Anchor.BOTTOM_RIGHT));
        LayerRegistry.register(TOASTS, new Layer(Layer.Anchor.TOP_RIGHT));
        LayerRegistry.register(UNKNOWN, new Layer(Layer.Anchor.NONE));
    }

}