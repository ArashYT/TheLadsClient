package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.thelads.core.client.WavyCape;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.modules.TntTimerModule;
import com.thelads.core.modules.WavyCapesModule;
import com.thelads.core.v1_8_9.world.mixin.NetworkPlayerInfoCapeAccessor;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecartTNT;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only (LADS_VERIFY_189_ONLY=world191): the 1.9.1 world lane in the QA world. The QA player gets a test cape (common
 * WavyCape.qaPixel) through its real NetworkPlayerInfo (OptiFine answers with it too when it has no cape of its own) and is
 * photographed in third person standing, sprinting, from the front, strafing, Blocky, sneaking and with Wavy Capes off; then primed
 * TNT lit 1.5 s apart and a TNT minecart lit by a powered activator rail are photographed with their timers, behind a stone wall
 * with Through blocks off and on, and removed before they explode (screenshots lads-qa/screenshots/world191-*.png). The 1.8
 * protocol does not send fuses, so every TNT counts from 80 ticks on the client, as on any 1.8.9 server.
 */
final class Probe191World {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final ResourceLocation CAPE = new ResourceLocation("theladscore", "qa_cape");
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<Option, JsonElement>();
    private static boolean capesOn, timerOn;
    private static int cameraWas;
    private static ResourceLocation capeWas;
    private static BlockPos base;

    private Probe191World() {}

    private static WavyCapesModule capes() { return (WavyCapesModule) Options189.module(WavyCapesModule.NAME); }

    private static TntTimerModule timer() { return (TntTimerModule) Options189.module(TntTimerModule.NAME); }

    private static void command(Minecraft mc, final String command) {
        final MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
    }

    private static String at(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", base.getX() + x, base.getY() + y, base.getZ() + z);
    }

    private static void keys(Minecraft mc, boolean forward, boolean left, boolean sprint, boolean sneak) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), forward);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), left);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprint);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sneak);
        if (forward && sprint) mc.thePlayer.setSprinting(true);
    }

    private static NetworkPlayerInfoCapeAccessor info(Minecraft mc) {
        return (NetworkPlayerInfoCapeAccessor) mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
    }

    private static String links(Minecraft mc) {
        WavyCape cape = WavyCape.of(mc.thePlayer);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < cape.segments(); i++) text.append(i == 0 ? "" : " ").append(Math.round(cape.linkDegrees(i)));
        return "links(deg)=[" + text + "] sway=" + String.format(Locale.ROOT, "%.1f", cape.sway(1));
    }

    private static float maxLink(Minecraft mc) {
        WavyCape cape = WavyCape.of(mc.thePlayer);
        float max = -999;
        for (int i = 0; i < cape.segments(); i++) max = Math.max(max, cape.linkDegrees(i));
        return max;
    }

    /** Logs the primed TNT and lit TNT minecarts this client has, with the timer each shows; returns the texts. */
    private static List<String> timers(Minecraft mc) {
        List<String> labels = new ArrayList<String>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            float left = entity instanceof EntityTNTPrimed ? ((EntityTNTPrimed) entity).fuse + 1
                : entity instanceof EntityMinecartTNT && ((EntityMinecartTNT) entity).getFuseTicks() > -1 ? ((EntityMinecartTNT) entity).getFuseTicks() + 1 : Float.NaN;
            if (Float.isNaN(left)) continue;
            labels.add(TntTimerModule.label(left));
            LOG.info("Lads world191 tnt: {} fuse ticks left={} timer='{}' colour={}", entity.getName(), left, TntTimerModule.label(left),
                String.format("%08X", TntTimerModule.color(left)));
        }
        return labels;
    }

    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(
        mc -> { // modules on at their defaults, the test cape, third person, facing south
            OPTIONS.clear();
            for (Module module : new Module[] {capes(), timer()}) for (Option option : module.getOptions()) OPTIONS.put(option, option.save());
            capesOn = capes().isEnabled();
            timerOn = timer().isEnabled();
            for (Option option : capes().getOptions()) option.reset();
            for (Option option : timer().getOptions()) option.reset();
            capes().setEnabled(true);
            timer().setEnabled(true);
            BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < 64; x++) for (int y = 0; y < 32; y++) image.setRGB(x, y, WavyCape.qaPixel(x, y));
            mc.getTextureManager().loadTexture(CAPE, new DynamicTexture(image));
            capeWas = info(mc).ladsGetCape();
            info(mc).ladsSetCape(CAPE);
            cameraWas = mc.gameSettings.thirdPersonView;
            mc.gameSettings.thirdPersonView = 1;
            base = new BlockPos(mc.thePlayer);
            command(mc, "time set 6000");
            mc.thePlayer.rotationYaw = 0;
            mc.thePlayer.rotationPitch = 10;
            return after(40);
        },
        mc -> {
            check(CAPE.equals(mc.thePlayer.getLocationCape()), "the QA player wears the test cape (its cape is " + mc.thePlayer.getLocationCape() + ")");
            LOG.info("Lads world191 cape standing: {}", links(mc));
            check(WavyCape.of(mc.thePlayer).segments() == 16 && maxLink(mc) < 30, "Wavy Capes draws the cape, hanging (highest link " + maxLink(mc) + " degrees)");
            screenshot(mc, "world191-cape-standing");
            keys(mc, true, false, true, false);
            return after(30);
        },
        mc -> {
            LOG.info("Lads world191 cape sprinting: {} speed={}", links(mc), String.format(Locale.ROOT, "%.3f", Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ)));
            check(maxLink(mc) > 25 && maxLink(mc) <= 110, "sprinting lifts the cape (highest link " + maxLink(mc) + " degrees)");
            screenshot(mc, "world191-cape-sprinting");
            mc.gameSettings.thirdPersonView = 2;
            return after(3);
        },
        mc -> {
            screenshot(mc, "world191-cape-sprinting-front");
            mc.gameSettings.thirdPersonView = 1;
            keys(mc, false, true, false, false);
            return after(25);
        },
        mc -> {
            LOG.info("Lads world191 cape strafing left: {}", links(mc));
            screenshot(mc, "world191-cape-strafing");
            capes().style.setIndex(1);
            keys(mc, true, false, true, false);
            return after(25);
        },
        mc -> {
            screenshot(mc, "world191-cape-blocky-sprinting");
            capes().style.setIndex(0);
            keys(mc, false, false, false, true);
            return after(20);
        },
        mc -> {
            LOG.info("Lads world191 cape sneaking: {}", links(mc));
            screenshot(mc, "world191-cape-sneaking");
            capes().setEnabled(false);
            keys(mc, true, false, true, false);
            return after(25);
        },
        mc -> { // vanilla's rigid cape for comparison; then first person facing the TNT
            screenshot(mc, "world191-cape-module-off-sprinting");
            keys(mc, false, false, false, false);
            capes().setEnabled(true);
            mc.gameSettings.thirdPersonView = 0;
            base = new BlockPos(mc.thePlayer);
            mc.thePlayer.rotationYaw = 0;
            mc.thePlayer.rotationPitch = 5;
            command(mc, "setblock " + at(4, -1, 6) + " redstone_block");
            command(mc, "setblock " + at(4, 0, 6) + " activator_rail");
            command(mc, "summon PrimedTnt " + at(-2, 0, 6));
            return after(20);
        },
        mc -> { command(mc, "summon PrimedTnt " + at(0.5, 0, 6)); command(mc, "summon MinecartTNT " + at(4.5, 0, 6.5)); return after(20); },
        mc -> { command(mc, "summon PrimedTnt " + at(2.5, 0, 6)); return after(3); },
        mc -> {
            List<String> labels = timers(mc);
            LOG.info("Lads world191 timers a: {}", labels);
            check(labels.size() == 4, "three primed TNT and a lit minecart carry timers: " + labels);
            screenshot(mc, "world191-tnt-a");
            command(mc, "fill " + at(-4, 0, 3) + " " + at(6, 3, 3) + " stone");
            return after(3);
        },
        mc -> { screenshot(mc, "world191-tnt-wall-hidden"); timer().throughBlocks.set(true); return after(2); },
        mc -> {
            screenshot(mc, "world191-tnt-wall-through-blocks");
            timer().throughBlocks.set(false);
            command(mc, "fill " + at(-4, 0, 3) + " " + at(6, 3, 3) + " air");
            return after(20);
        },
        mc -> { // the first TNT a third of a second from 0; all of them go before it explodes
            LOG.info("Lads world191 timers b: {}", timers(mc));
            screenshot(mc, "world191-tnt-b");
            stop(mc);
            return after(5);
        });

    /** Before the first TNT explodes (80 ticks): TNT, cart, wall and rail go; modules, cape, camera and keys back. */
    private static void stop(Minecraft mc) {
        keys(mc, false, false, false, false);
        command(mc, "kill @e[type=PrimedTnt]");
        command(mc, "kill @e[type=MinecartTNT]");
        command(mc, "fill " + at(-4, 0, 3) + " " + at(6, 3, 3) + " air");
        command(mc, "setblock " + at(4, 0, 6) + " air");
        command(mc, "setblock " + at(4, -1, 6) + " grass");
        command(mc, "kill @e[type=Item]");
        mc.gameSettings.thirdPersonView = cameraWas;
        info(mc).ladsSetCape(capeWas);
        for (Map.Entry<Option, JsonElement> entry : OPTIONS.entrySet()) entry.getKey().load(entry.getValue());
        capes().setEnabled(capesOn);
        timer().setEnabled(timerOn);
    }
}
