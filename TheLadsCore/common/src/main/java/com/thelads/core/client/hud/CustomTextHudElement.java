package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.StringOption;
import java.util.Locale;

/**
 * Custom Text (OneClient's EvergreenHUD Custom Text): your own text with placeholders filled in, once a game tick. "\n" in the text
 * starts a new line.
 */
public class CustomTextHudElement extends TextHudElement {
    public static final String DEFAULT = "{player} | {fps} FPS | {ping} ms";
    private String template, text = "";
    private int tick = Integer.MIN_VALUE;

    public CustomTextHudElement() {
        super(40);
    }

    @Override
    protected String updateText(LadsGraphics g) {
        String t = option("Text") instanceof StringOption s ? s.get() : DEFAULT;
        LadsGameBridge game = g.getGame();
        if (game == null) return t;
        int now = game.getClientTicks();
        if (now != tick || !t.equals(template)) {
            text = resolve(t, game);
            tick = now;
            template = t;
        }
        return text;
    }

    /** The text with {player} {fps} {ping} {x} {y} {z} {biome} {direction} {server} {time} {day} {speed} {memory} filled in. */
    static String resolve(String template, LadsGameBridge game) {
        if (template == null) return "";
        StringBuilder out = new StringBuilder(template.length() + 16);
        int at = 0;
        while (at < template.length()) {
            int open = template.indexOf('{', at), close = open < 0 ? -1 : template.indexOf('}', open);
            if (open < 0 || close < 0) break;
            out.append(template, at, open);
            String value = value(template.substring(open + 1, close), game);
            out.append(value != null ? value : template.substring(open, close + 1));
            at = close + 1;
        }
        out.append(template, at, template.length());
        return out.toString().replace("\\n", "\n");
    }

    private static String value(String key, LadsGameBridge game) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "player" -> game.getPlayerName();
            case "fps" -> Integer.toString(game.getFps());
            case "ping" -> Integer.toString(game.getPing());
            case "x" -> Integer.toString(game.getPlayerX());
            case "y" -> Integer.toString(game.getPlayerY());
            case "z" -> Integer.toString(game.getPlayerZ());
            case "biome" -> game.getBiomeName();
            case "direction" -> game.getPlayerDirection();
            case "server" -> game.getServerAddress();
            case "time" -> game.getGameTime();
            case "day" -> Long.toString(game.getDayCount());
            case "speed" -> String.format(Locale.ROOT, "%.1f", game.getSpeed());
            case "memory" -> game.getUsedMemoryMb() + " MB";
            default -> null; // not a placeholder: shown as written
        };
    }
}
