package com.thelads.core.client.hud;

import com.thelads.core.client.ClientTools;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ModuleSupport;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Native, opt-in utility HUDs using the same editor, colours and scaling as existing HUDs. */
public final class ToolsHudElement extends TextHudElement {
    private static final DateTimeFormatter CLOCK_24 = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter CLOCK_12 = DateTimeFormatter.ofPattern("h:mm:ss a", java.util.Locale.ROOT);
    private String clockText = "";
    private long clockSecond = Long.MIN_VALUE;
    private boolean clock12;
    private long timerSecond = Long.MIN_VALUE;
    private boolean timerRunning;
    private String timerText = "";
    public ToolsHudElement() { super(70); }
    @Override public boolean isAvailable() { return ModuleSupport.isBuiltIn(moduleName); }
    @Override protected String updateText(LadsGraphics g) {
        var game = g.getGame();
        return switch (moduleName) {
            case "Clock" -> {
                long second = System.currentTimeMillis() / 1000;
                boolean twelve = optBool("12-hour clock", false);
                if (second != clockSecond || twelve != clock12) {
                    clockSecond = second; clock12 = twelve;
                    clockText = LocalTime.now().format(twelve ? CLOCK_12 : CLOCK_24);
                }
                yield clockText;
            }
            case "Stopwatch" -> {
                long second = ClientTools.STOPWATCH.millis() / 1000;
                boolean running = ClientTools.STOPWATCH.running();
                if (second != timerSecond || running != timerRunning) {
                    timerSecond = second; timerRunning = running;
                    timerText = (running ? "Timer " : "Paused ") + ClientTools.duration(second * 1000);
                }
                yield timerText;
            }
            case "ItemCounter" -> {
                String s = game.getItemCountText(optCycle("Item", 0));
                yield (editor && (s == null || s.contains(": 0"))) ? "Totems: 2" : s;
            }
            case "ReachDisplay" -> {
                String s = game.getRecentReachText();
                yield (editor && (s == null || s.contains("--"))) ? "Reach: 3.42m" : s;
            }
            case "ServerAddress" -> optBool("Hide address", false) ? "Server hidden" : (editor && ("Singleplayer".equals(game.getServerAddress()) || game.getServerAddress().isEmpty())) ? "mc.thelads.net" : game.getServerAddress();
            case "PortalCoordinates" -> {
                String target = ClientTools.portal(game.getDimensionId(), game.getPlayerX(), game.getPlayerZ());
                yield target.isEmpty() ? (editor ? "Portal: X: 120, Z: -85" : "Portal: unavailable here") : target;
            }
            default -> "";
        };
    }
}
