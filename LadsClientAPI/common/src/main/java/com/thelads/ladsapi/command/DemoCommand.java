package com.thelads.ladsapi.command;

import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.api.CameraPoint;
import com.thelads.ladsapi.api.Dialog;
import com.thelads.ladsapi.api.Hud;
import com.thelads.ladsapi.api.InputLock;
import com.thelads.ladsapi.api.LadsPlayer;
import com.thelads.ladsapi.api.ScreenFx;
import com.thelads.ladsapi.api.ServerAction;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import com.thelads.ladsapi.session.LadsSession;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /ladsapi demo <player> <feature> [args]}: plays every Minigame API feature (PROTOCOL.md sections 9-21) on one player so
 * a server owner can see it work. Each line of the reply says whether the call was sent; "not sent" means the client did not name
 * the cap or a value was bad. While a demo is on for a player, what that client sends back (action, dialog, input) is logged.
 */
final class DemoCommand {
    private DemoCommand() {}

    static final List<String> FEATURES = List.of("rumble", "camera", "fx", "lock", "waypoint", "telegraph", "hud", "audio",
            "profile", "run", "actions", "dialog", "input", "off");

    static final List<String> HELP = List.of(
            "§e/ladsapi demo <player> rumble [low] [high] [ms]§7, §ecamera spline|orbit|off§7, §efx blur|grayscale|sepia|invert|vignette|tint|flash|clear",
            "§e/ladsapi demo <player> lock§7 (5 s), §ewaypoint§7, §etelegraph circle|ring|cone|line|rect§7, §ehud cooldown|pips|meter|text|timer|off",
            "§e/ladsapi demo <player> audio [sound|stop]§7, §eprofile <name>§7, §erun start|split|finish|ghost§7, §eactions§7, §edialog toast|vote|modal§7, §einput",
            "§e/ladsapi demo <player> off §7- Ends everything a demo or a game started for the player");

    static List<String> suggestFeature(String[] args, String cur) {
        String feature = args[2].toLowerCase();
        if (args.length == 4) {
            List<String> opts = switch (feature) {
                case "camera" -> List.of("spline", "orbit", "off");
                case "fx" -> List.of("blur", "grayscale", "sepia", "invert", "vignette", "tint", "flash", "clear");
                case "telegraph" -> List.of("circle", "ring", "cone", "line", "rect", "clear");
                case "hud" -> List.of("cooldown", "pips", "meter", "text", "timer", "off");
                case "audio" -> List.of("stop");
                case "run" -> List.of("start", "split", "finish", "ghost", "reset");
                case "dialog" -> List.of("toast", "vote", "modal", "close");
                default -> List.of();
            };
            return LadsCommandHandler.filterMatching(opts, cur);
        }
        return List.of();
    }

    static List<String> run(LadsCore core, LadsPlayerHandle handle, String[] args) {
        LadsSession s = core.sessionManager().getSessions().get(handle.uuid());
        if (s == null || !s.isLads()) {
            return List.of("§7Player §f" + handle.name() + " §7is §cnot §7running Lads Client.");
        }
        LadsPlayer lp = s.getLadsPlayer();
        String feature = args[2].toLowerCase();
        String a1 = args.length > 3 ? args[3].toLowerCase() : "";
        s.setDemo(true);
        double[] pos = handle.position();
        boolean haveBase = pos != null;
        if (pos == null) pos = new double[] {0, 70, 0, 0, 0};
        double yawRad = Math.toRadians(pos[3]);
        double fx = -Math.sin(yawRad), fz = Math.cos(yawRad); // yaw 0 looks along +Z
        double ax = pos[0] + 5 * fx, az = pos[2] + 5 * fz, ay = pos[1];
        List<String> out = new ArrayList<>();
        switch (feature) {
            case "rumble" -> out.add(report("rumble", lp.rumble(num(args, 3, 0.8), num(args, 4, 0.4), (int) num(args, 5, 400))));
            case "camera" -> {
                switch (a1) {
                    case "off" -> out.add(report("camera off", lp.camera().off()));
                    case "orbit" -> out.add(report("camera orbit", lp.camera().orbit(pos[0], pos[1] + 1, pos[2])
                            .radius(6).height(2).periodMs(6000).ms(6000).fov(70).bars().hideHud().play()));
                    default -> out.add(report("camera spline", lp.camera().spline(List.of(
                            new CameraPoint(pos[0] + 6, pos[1] + 4, pos[2] + 6, 135, 25),
                            new CameraPoint(pos[0] - 6, pos[1] + 6, pos[2] + 6, -135, 30),
                            new CameraPoint(pos[0] - 6, pos[1] + 3, pos[2] - 6, -45, 20),
                            new CameraPoint(pos[0], pos[1] + 1.6, pos[2], pos[3], pos[4])))
                            .ms(5000).smooth().bars().hideHud().play()));
                }
            }
            case "fx" -> {
                switch (a1) {
                    case "clear" -> out.add(report("fx clear", lp.clearScreenFx()));
                    case "vignette" -> out.add(report("fx vignette", lp.screenFx(ScreenFx.create().ms(4000).fadeMs(400)
                            .vignette("#FF0000", 0.6, 800))));
                    case "tint" -> out.add(report("fx tint", lp.screenFx(ScreenFx.create().ms(4000).fadeMs(400).tint("#2040FF", 0.35))));
                    case "flash" -> out.add(report("fx flash", lp.flashScreen("#FFFFFF", 400)));
                    case "blur", "grayscale", "sepia", "invert" -> out.add(report("fx " + a1,
                            lp.screenFx(ScreenFx.create().ms(4000).fadeMs(400).shader(a1))));
                    default -> out.add("§cUsage: /ladsapi demo <player> fx blur|grayscale|sepia|invert|vignette|tint|flash|clear");
                }
            }
            case "lock" -> out.add(report("lock 5 s (move, jump, attack, use)",
                    lp.lockInput(InputLock.create().move().jump().attack().use(), 5000)));
            case "waypoint" -> {
                out.add(report("waypoint ahead", lp.waypoints().set("demo-ahead", ax, ay, az, "Demo goal", "#FFD700", "diamond", true, true, false)));
                if (!haveBase) out.add("§7This platform gives no player position: the demo uses 0,70,0.");
            }
            case "telegraph" -> out.addAll(telegraph(lp, a1, ax, ay, az, pos[3]));
            case "hud" -> out.addAll(hud(lp, a1));
            case "audio" -> {
                if ("stop".equals(a1)) {
                    out.add(report("audio stop", lp.audio().stopAll()));
                } else {
                    String snd = args.length > 3 ? args[3] : "minecraft:entity.experience_orb.pickup";
                    out.add(report("audio " + snd, lp.audio().play("demo", snd, "sfx", 1, 1, false, 0)));
                }
            }
            case "profile" -> {
                if (args.length < 4) out.add(report("profile back", lp.gameProfile(null)));
                else out.add(report("profile " + args[3], lp.gameProfile(String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)))));
            }
            case "run" -> out.addAll(run(lp, a1, pos));
            case "actions" -> {
                out.add(report("actions (R / Y)", lp.actions(List.of(
                        new ServerAction("demo-ult", "Demo Ultimate", "key.keyboard.r", "y"),
                        new ServerAction("demo-dash", "Demo Dash", null, "rb")))));
                out.add("§7Press the keys in-world: presses are logged to the console.");
            }
            case "dialog" -> out.add(dialog(core, lp, a1));
            case "input" -> out.add("§7Device: §f" + lp.inputDevice() + " §7pad: §f" + lp.padType() + " §7glyph: §f"
                    + lp.glyph("[R]", "(Y)", "(Triangle)", "(X)"));
            case "off" -> {
                lp.clearMinigame();
                s.setDemo(false);
                out.add("§aEnded every minigame effect for " + handle.name() + ".");
            }
            default -> {
                out.add("§cUnknown demo feature: " + args[2] + ". One of: " + String.join(", ", FEATURES));
                s.setDemo(false);
            }
        }
        return out;
    }

    private static List<String> telegraph(LadsPlayer lp, String shape, double x, double y, double z, double yaw) {
        List<String> out = new ArrayList<>();
        var t = lp.telegraphs();
        switch (shape) {
            case "ring" -> out.add(report("telegraph ring", t.ring("demo-tg", x, y, z, 5, 2.5, "#FF8800", 3000, 2500)));
            case "cone" -> out.add(report("telegraph cone", t.cone("demo-tg", x, y, z, 6, 90, yaw, "#FF2020", 3000, 2500)));
            case "line" -> out.add(report("telegraph line", t.line("demo-tg", x, y, z, yaw, 10, 1.5, "#FF2020", 3000, 2500)));
            case "rect" -> out.add(report("telegraph rect", t.rect("demo-tg", x, y, z, yaw, 8, 4, "#FF2020", 3000, 2500)));
            case "clear" -> out.add(report("telegraph clear", t.clear()));
            default -> out.add(report("telegraph circle", t.circle("demo-tg", x, y, z, 4, "#FF2020", 3000, 2500)));
        }
        return out;
    }

    private static List<String> hud(LadsPlayer lp, String kind) {
        List<String> out = new ArrayList<>();
        Hud h = lp.hud().group("demo");
        switch (kind) {
            case "pips" -> out.add(report("hud pips", h.slot(1).pips("demo-pips", "Lives", List.of(
                    new Hud.Pip("#FF4040", true), new Hud.Pip("#FF4040", true), new Hud.Pip("#FF4040", false)))));
            case "meter" -> out.add(report("hud meter", h.slot(2).meter("demo-meter", "Charge", 0.65, "#40C0FF", "0", "100")));
            case "text" -> out.add(report("hud text", h.slot(3).text("demo-text", "Objective", List.of("Reach the flag", "Do not fall"))));
            case "timer" -> out.add(report("hud timer", h.slot(4).timer("demo-timer", "Round", 60000, false)));
            case "off" -> out.add(report("hud clear", lp.hud().clear()));
            default -> out.add(report("hud cooldown", h.slot(0).cooldown("demo-cd", "Dash", "minecraft:feather", 8000, 10000, 2)));
        }
        return out;
    }

    private static List<String> run(LadsPlayer lp, String op, double[] pos) {
        List<String> out = new ArrayList<>();
        var r = lp.run();
        switch (op) {
            case "split" -> out.add(report("run split", r.split("demo-course", 0)));
            case "finish" -> out.add(report("run finish", r.finish("demo-course")));
            case "reset" -> out.add(report("run reset", r.reset("demo-course")));
            case "ghost" -> {
                List<double[]> frames = new ArrayList<>();
                for (int i = 0; i <= 100; i++) {
                    double ang = i / 100.0 * Math.PI * 2;
                    frames.add(new double[] {i * 100, pos[0] + 4 * Math.cos(ang), pos[1], pos[2] + 4 * Math.sin(ang), 0, 0});
                }
                out.add(report("ghost", r.ghost("demo-ghost", "demo-course", "Best run", null, frames)));
            }
            default -> out.add(report("run start", r.start("demo-course", "Demo course", 3, List.of(12000L, 25000L, 40000L), 40000)));
        }
        return out;
    }

    private static String dialog(LadsCore core, LadsPlayer lp, String style) {
        String name = core.sessionManager().getSessions().get(lp.uuid()).getHandle().name();
        Dialog.Callback log = (p, choice) -> {
            if (core.platform().logger() != null) core.platform().logger().info("[LadsClientAPI] demo dialog answer from " + name + ": " + choice);
        };
        return switch (style) {
            case "toast" -> report("dialog toast", lp.dialog(Dialog.toast("demo-dialog", "Demo toast").body("Dismisses itself.").ms(5000), log));
            case "modal" -> report("dialog modal", lp.dialog(Dialog.modal("demo-dialog", "Demo modal").body("Pick one. Esc dismisses.")
                    .button("yes", "Yes", "#40C040", null).button("no", "No", "#C04040", null).ms(15000), log));
            case "close" -> report("dialog close", lp.closeDialog("demo-dialog"));
            default -> report("dialog vote", lp.dialog(Dialog.vote("demo-dialog", "Demo vote").body("Which map next?")
                    .button("a", "Map A").button("b", "Map B").button("c", "Map C").ms(15000), log));
        };
    }

    private static double num(String[] args, int i, double def) {
        if (i >= args.length) return def;
        try {
            return Double.parseDouble(args[i]);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String report(String what, boolean sent) {
        return sent ? "§a" + what + ": sent" : "§e" + what + ": not sent (the client did not name this cap, or a value was bad)";
    }
}
