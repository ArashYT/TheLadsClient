package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.Option;
import com.thelads.core.modules.DynamicLightsModule;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;

/**
 * QA only: Dynamic Lights (DynamicLights189), run by CoreProbe in its QA world. At midnight, holding a torch: the module off, then
 * on with Fancy, must switch OptiFine's own Dynamic Lights the same way (its setting, its light count, optionsof.txt) and the frame
 * must get brighter; a change to OptiFine's setting (as its Video Settings make) must flow back into the module. The held item is
 * this client's only; time, module and OptiFine's setting are put back. Screenshots: 170-lights-off, 170-lights-on.
 */
final class LightsProbe189 {
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(LightsProbe189::start, LightsProbe189::off,
        LightsProbe189::on, LightsProbe189::fast, LightsProbe189::followed, LightsProbe189::restore);
    private static final Map<Option, JsonElement> optionsWere = new LinkedHashMap<>();
    private static boolean started, wasEnabled;
    private static long modifiedWas, timeWas;
    private static int optiFineWas;
    private static float pitchWas;
    private static ItemStack heldWas;
    private static double offBrightness;

    private LightsProbe189() {}

    private static boolean start(Minecraft mc) {
        DynamicLightsModule module = DynamicLights189.module();
        check(ModuleSupport.isBuiltIn(DynamicLightsModule.NAME) && module.hidden(module.getOption("Light Radius"))
            && !module.hidden(module.getOption("Quality")), "Dynamic Lights is built in with OptiFine and shows only Quality");
        wasEnabled = module.isEnabled();
        modifiedWas = module.getLastModified();
        for (Option option : module.getOptions()) optionsWere.put(option, option.save());
        heldWas = mc.thePlayer.inventory.getCurrentItem();
        try { optiFineWas = setting(mc); } catch (Exception failure) { throw new IllegalStateException(failure); }
        pitchWas = mc.thePlayer.rotationPitch;
        WorldServer world = mc.getIntegratedServer().worldServerForDimension(0);
        timeWas = world.getWorldTime();
        started = true;
        mc.getIntegratedServer().addScheduledTask(() -> world.setWorldTime(18000));
        mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem] = new ItemStack(Blocks.torch);
        mc.thePlayer.rotationPitch = 50;
        module.setEnabled(false);
        return after(60);
    }

    private static boolean off(Minecraft mc) throws Exception {
        check(setting(mc) == DynamicLightsModule.OPTIFINE_OFF && lights() == 0, "Dynamic Lights off: OptiFine's Dynamic Lights are Off, no lights");
        offBrightness = shot(mc, "170-lights-off");
        DynamicLightsModule module = DynamicLights189.module();
        module.getOption("Quality").reset(); // Fancy
        module.setEnabled(true);
        return after(60);
    }

    private static boolean on(Minecraft mc) throws Exception {
        check(setting(mc) == DynamicLightsModule.OPTIFINE_FANCY && lights() > 0,
            "Dynamic Lights on (Fancy): OptiFine's Dynamic Lights are Fancy and light the held torch (" + lights() + " lights)");
        String saved = new String(Files.readAllBytes(new File(mc.mcDataDir, "optionsof.txt").toPath()), StandardCharsets.UTF_8);
        check(saved.contains("ofDynamicLights:2"), "OptiFine's setting is saved to optionsof.txt as OptiFine saves it");
        double on = shot(mc, "170-lights-on");
        check(on > offBrightness + 1, String.format("the torch lights the world: mean brightness %.1f on, %.1f off", on, offBrightness));
        return after(2);
    }

    /** As OptiFine's Video Settings set it: Fast. */
    private static boolean fast(Minecraft mc) throws Exception {
        field().setInt(mc.gameSettings, DynamicLightsModule.OPTIFINE_FAST);
        return after(3);
    }

    private static boolean followed(Minecraft mc) throws Exception {
        DynamicLightsModule module = DynamicLights189.module();
        check(module.isEnabled() && !module.fancy(), "OptiFine's Video Settings set to Fast: the module follows (on, Fast)");
        field().setInt(mc.gameSettings, DynamicLightsModule.OPTIFINE_OFF);
        return after(3);
    }

    private static boolean restore(Minecraft mc) throws Exception {
        check(!DynamicLights189.module().isEnabled(), "OptiFine's Video Settings set to Off: the module follows (off)");
        stop();
        return after(5);
    }

    /** Puts the module and OptiFine's setting back and saves both, then the time, the held item and the view. */
    static void stop() {
        if (!started) return;
        started = false;
        Minecraft mc = Minecraft.getMinecraft();
        DynamicLightsModule module = DynamicLights189.module();
        for (Map.Entry<Option, JsonElement> entry : optionsWere.entrySet()) entry.getKey().load(entry.getValue());
        module.setEnabled(wasEnabled);
        module.setLastModified(modifiedWas);
        com.thelads.core.config.ConfigManager.save();
        try {
            field().setInt(mc.gameSettings, optiFineWas);
            mc.gameSettings.saveOptions();
        } catch (Exception failure) { org.apache.logging.log4j.LogManager.getLogger("TheLadsCore").error("OptiFine's Dynamic Lights setting was not put back", failure); }
        if (mc.thePlayer != null) {
            mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem] = heldWas;
            mc.thePlayer.rotationPitch = pitchWas;
        }
        if (mc.getIntegratedServer() != null) {
            WorldServer world = mc.getIntegratedServer().worldServerForDimension(0);
            long time = timeWas;
            mc.getIntegratedServer().addScheduledTask(() -> world.setWorldTime(time));
        }
    }

    private static double shot(Minecraft mc, String name) throws Exception {
        screenshot(mc, name);
        BufferedImage image = ImageIO.read(new File(mc.mcDataDir, "lads-qa/screenshots/" + name + ".png"));
        long sum = 0, count = 0;
        for (int y = image.getHeight() / 5; y < image.getHeight() * 4 / 5; y += 2)
            for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x += 2) {
                int c = image.getRGB(x, y);
                sum += (c & 0xFF) + (c >> 8 & 0xFF) + (c >> 16 & 0xFF);
                count += 3;
            }
        return (double) sum / count;
    }

    private static Field field() throws Exception { return GameSettings.class.getField("ofDynamicLights"); }
    private static int setting(Minecraft mc) throws Exception { return field().getInt(mc.gameSettings); }
    private static int lights() throws Exception { return (Integer) Class.forName("net.optifine.DynamicLights").getMethod("getCount").invoke(null); }
}
