package com.thelads.core.v1_21_11.ladsapi.ui;

import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v1_21_11.ladsapi.ui.mixin.KeyMappingAccess12111;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** QA only (harness step {@code ladsui}): drives the minigame API UI state and logs what the client holds. */
public final class LadsApiUiQa12111 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-LadsApiUi");

    private LadsApiUiQa12111() {}

    /** ladsui state | key &lt;up|down|left|right|jump|sneak|sprint|attack|use|drop&gt; on|off | srvkey &lt;id&gt; on|off | choose &lt;n&gt; | saveopts | dismiss */
    public static void step(Minecraft mc, String[] s) {
        String op = s.length > 1 ? s[1] : "state";
        long now = Minigame.now();
        switch (op) {
            case "key" -> {
                KeyMapping k = switch (s[2]) {
                    case "up" -> mc.options.keyUp;
                    case "down" -> mc.options.keyDown;
                    case "left" -> mc.options.keyLeft;
                    case "right" -> mc.options.keyRight;
                    case "jump" -> mc.options.keyJump;
                    case "sneak" -> mc.options.keyShift;
                    case "sprint" -> mc.options.keySprint;
                    case "attack" -> mc.options.keyAttack;
                    case "use" -> mc.options.keyUse;
                    default -> mc.options.keyDrop;
                };
                k.setDown("on".equals(s[3]));
                LOGGER.info("Lads qa ladsui key {} {}: isDown={} locked={}", s[2], s[3], k.isDown(), Minigame.lock.any(now));
            }
            case "srvkey" -> {
                for (KeyMapping k : KeyMappingAccess12111.lads$all().values()) {
                    if (k.getName().equals("lads.server." + s[2])) {
                        k.setDown("on".equals(s[3]));
                        LOGGER.info("Lads qa ladsui srvkey {} {}", s[2], s[3]);
                    }
                }
            }
            case "choose" -> LOGGER.info("Lads qa ladsui choose {}: {}", s[2], DialogOverlay.choose(Integer.parseInt(s[2])));
            case "dismiss" -> DialogOverlay.dismissModals();
            case "keybinds" -> { // ladsui keybinds [filter]: opens the Controls screen; a filter goes into its search box, none scrolls to the bottom
                mc.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(mc.screen, mc.options));
                var scr = mc.screen;
                String filter = s.length > 2 ? s[2] : null;
                for (Class<?> c = scr.getClass(); c != null; c = c.getSuperclass())
                    for (var f : c.getDeclaredFields()) {
                        try {
                            f.setAccessible(true);
                            Object v = f.get(scr);
                            if (filter != null && v instanceof net.minecraft.client.gui.components.EditBox eb) eb.setValue(filter);
                            else if (filter == null && v instanceof net.minecraft.client.gui.components.AbstractSelectionList<?> l) l.setScrollAmount(l.maxScrollAmount());
                        } catch (ReflectiveOperationException | RuntimeException ignored) {}
                    }
                LOGGER.info("Lads qa ladsui keybinds: screen={} filter={}", scr.getClass().getName(), filter);
            }
            case "rebind" -> { // ladsui rebind <actionId> <glfwKey>: selects that action's entry in the open Controls screen and presses the key
                var scr = (net.minecraft.client.gui.screens.options.controls.KeyBindsScreen) mc.screen;
                for (KeyMapping k : mc.options.keyMappings)
                    if (k.getName().equals("lads.server." + s[2])) scr.selectedKey = k;
                boolean ok = scr.keyPressed(new net.minecraft.client.input.KeyEvent(Integer.parseInt(s[3]), 0, 0));
                LOGGER.info("Lads qa ladsui rebind {} -> {}: handled={} now={}", s[2], s[3], ok,
                        mc.options.keyMappings.length == 0 ? "" : java.util.Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals("lads.server." + s[2])).map(KeyMapping::saveString).findFirst().orElse("missing"));
            }
            case "press" -> { // ladsui press <glfwKey> on|off: a real key through the key mapping table (what the keyboard does)
                KeyMapping.set(com.mojang.blaze3d.platform.InputConstants.getKey(new net.minecraft.client.input.KeyEvent(Integer.parseInt(s[2]), 0, 0)), "on".equals(s[3]));
                LOGGER.info("Lads qa ladsui press {} {}", s[2], s[3]);
            }
            case "vol" -> { // ladsui vol <master|music|ambient|...> <0..1>: moves a vanilla sound slider
                var src = net.minecraft.sounds.SoundSource.valueOf(s[2].toUpperCase());
                mc.options.getSoundSourceOptionInstance(src).set(Double.parseDouble(s[3]));
                LOGGER.info("Lads qa ladsui vol {}={}", s[2], mc.options.getSoundSourceVolume(src));
            }
            case "caps" -> LOGGER.info("Lads qa ladsui caps: {}", com.thelads.core.client.ladsapi.minigame.Caps.declared());
            case "hashcfg" -> { // ladsui hashcfg: sha-256 of the config and profiles files on disk
                for (java.io.File cf : new java.io.File[] {com.thelads.core.client.util.ClientPaths.getConfigFile(), com.thelads.core.client.util.ClientPaths.getProfilesFile()})
                    try { LOGGER.info("Lads qa ladsui hashcfg {}: {}", cf.getName(), java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(cf.toPath())))); } catch (Exception e) { LOGGER.info("Lads qa ladsui hashcfg {}: {}", cf.getName(), e.toString()); }
            }
            case "optscheck" -> { // ladsui optscheck: how many lines of options.txt on disk name a lads.server key
                long n = 0;
                try { n = java.nio.file.Files.readAllLines(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("options.txt")).stream().filter(l -> l.contains("lads.server")).count(); } catch (java.io.IOException e) { n = -1; }
                LOGGER.info("Lads qa ladsui optscheck: lads.server lines in options.txt = {}", n);
            }
            case "pause" -> mc.pauseGame(false);
            case "audio" -> LOGGER.info("Lads qa ladsui audio: {}", Audio12111.qa());
            case "pad" -> { // ladsui pad none|xbox|ps|switch|generic|press|kbm
                int t = switch (s[2]) {
                    case "xbox" -> 1;
                    case "ps" -> 2;
                    case "switch" -> 3;
                    case "generic" -> 0;
                    case "none" -> -1;
                    default -> -2;
                };
                if ("kbm".equals(s[2])) Pad12111.kbm();
                LOGGER.info("Lads qa ladsui pad {}: {} device={} pad={}", s[2], Pad12111.qa(t, "press".equals(s[2])),
                        Pad12111.get().device(), Pad12111.get().pad());
            }
            case "mkprofile" -> { // ladsui mkprofile <name> <module>: saves a profile with that module flipped, then flips it back
                var m = com.thelads.core.config.ModuleManager.getInstance().getModule(s[3]);
                boolean was = m.isEnabled();
                m.setEnabled(!was);
                com.thelads.core.config.ProfileManager.get().saveAs(s[2]);
                m.setEnabled(was);
                LOGGER.info("Lads qa ladsui profile {} saved with {}={}; now {}={}", s[2], s[3], !was, s[3], m.isEnabled());
            }
            case "module" -> {
                var mod = com.thelads.core.config.ModuleManager.getInstance().getModule(s[2].replace('_', ' '));
                if (s.length > 3) mod.setEnabled("on".equals(s[3]));
                LOGGER.info("Lads qa ladsui module {}={}", s[2], mod.isEnabled());
            }
            case "saveopts" -> {
                mc.options.save();
                LOGGER.info("Lads qa ladsui options saved");
            }
            default -> {
                StringBuilder keys = new StringBuilder();
                KeyMappingAccess12111.lads$all().values().forEach(k -> {
                    if (k.getName().startsWith("lads.server.")) keys.append(k.getName()).append('=').append(k.saveString()).append(' ');
                });
                LOGGER.info("Lads qa ladsui state: lock={} actions={} dialogs={} hud={} audio={} profileSwitched={} run={} elapsed={} split={} finish={} screen={} serverKeys=[{}]",
                        Minigame.lock.any(now), Minigame.actions.list().size(), Minigame.dialogs.open(now).size(),
                        Minigame.hud.groups(), Minigame.audio.voices().size(), Minigame.profile.switched(),
                        Minigame.run.active(now), Minigame.run.elapsedMs(now), Minigame.run.split(now), Minigame.run.finish(now),
                        mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(), keys.toString().trim());
                LOGGER.info("Lads qa ladsui state keys: optionsServerKeys={} allServerKeys={} actions={}", java.util.Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().startsWith("lads.server.")).count(), KeyMappingAccess12111.lads$all().values().stream().filter(k -> k.getName().startsWith("lads.server.")).count(), Minigame.actions.list().size());
            }
        }
    }
}
