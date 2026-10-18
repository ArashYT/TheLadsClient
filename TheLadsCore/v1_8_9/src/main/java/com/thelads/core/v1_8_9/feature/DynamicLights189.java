package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ExplosionFlashes;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.DynamicLightsModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityMinecartTNT;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;

/**
 * Dynamic Lights on 1.8.9: the Lads module switches OptiFine's own Dynamic Lights (Off, Fast or Fancy) through the field OptiFine
 * adds to GameSettings, and Minecraft's options save writes it to OptiFine's optionsof.txt, as OptiFine's Video Settings do. A
 * change made there flows back into the module; at start-up the module takes on OptiFine's saved setting, so nobody's 1.8.9 lights
 * change by themselves. Without OptiFine the module is unavailable.
 *
 * Missing vanilla entities (primed TNT, TNT minecarts, end crystals, lightning bolts, XP orbs) and explosion flashes are
 * registered into OptiFine's mapEntityLightLevels and tracked per client tick.
 */
public final class DynamicLights189 {
    private static Field setting;
    private static Method removeLights;
    private static Map<Class<?>, Integer> entityLightLevels;
    private static int applied = -1;
    private static World lastWorld;

    private DynamicLights189() {}

    /** After the other 1.8.9 module statuses: without OptiFine the module is unavailable. */
    @SuppressWarnings("unchecked")
    public static void register() {
        try {
            setting = GameSettings.class.getField("ofDynamicLights");
        } catch (ReflectiveOperationException | LinkageError absent) {
            ModuleSupport.registerUnavailable(DynamicLightsModule.NAME, "On Minecraft 1.8.9 it switches OptiFine's Dynamic Lights, and OptiFine is not loaded.");
            return;
        }
        try { // what OptiFine's own menu calls when the setting changes, so lights that went out leave no lit blocks behind
            removeLights = Class.forName("net.optifine.DynamicLights").getMethod("removeLights", RenderGlobal.class);
        } catch (ReflectiveOperationException | LinkageError absent) {
            LogManager.getLogger("TheLadsCore").warn("OptiFine's DynamicLights.removeLights is missing; lights turned off may linger until blocks update");
        }

        // Register missing vanilla glowing entities into OptiFine's entity light table
        try {
            Class<?> dlClass = Class.forName("net.optifine.DynamicLights");
            Field mapField = dlClass.getDeclaredField("mapEntityLightLevels");
            mapField.setAccessible(true);
            entityLightLevels = (Map<Class<?>, Integer>) mapField.get(null);
            if (entityLightLevels != null) {
                entityLightLevels.put(EntityTNTPrimed.class, 15);
                entityLightLevels.put(EntityMinecartTNT.class, 15);
                entityLightLevels.put(EntityEnderCrystal.class, 15);
                entityLightLevels.put(EntityLightningBolt.class, 15);
                entityLightLevels.put(EntityXPOrb.class, 6);
            }
        } catch (Throwable failure) {
            LogManager.getLogger("TheLadsCore").warn("Could not populate OptiFine mapEntityLightLevels", failure);
        }

        module().drivesOptiFine(); // built in (TheLadsCore189.LIMITED says what OptiFine's lights lack)
    }

    /** Triggered client-side when an explosion packet is received. */
    public static void onExplosion(double x, double y, double z, float strength) {
        if (setting == null) return;
        DynamicLightsModule module = module();
        if (module != null && module.isEnabled() && module.explosionsAndTnt()) {
            ExplosionFlashes.WORLD.add(x, y, z, strength);
        }
    }

    /** Resets explosion flashes and removes dummy light entities from the world. */
    public static void reset() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            for (int i = 0; i < ExplosionFlashes.MAX_FLASHES; i++) {
                mc.theWorld.removeEntityFromWorld(ExplosionFlashes.FIRST_ID - i);
            }
        }
        ExplosionFlashes.WORLD.reset();
        if (entityLightLevels != null) {
            for (Class<?> clazz : FLASH_CLASSES) {
                entityLightLevels.remove(clazz);
            }
        }
    }

    /** Each client tick: a change on either side reaches the other, and explosion flashes are updated. */
    public static void tick(Minecraft mc) {
        if (setting == null || mc.gameSettings == null) return;
        DynamicLightsModule module = module();
        try {
            int optiFine = setting.getInt(mc.gameSettings);
            if (optiFine != applied) { // start-up, or OptiFine's Video Settings
                if (module.optiFineSetting() != optiFine) {
                    module.followOptiFine(optiFine);
                    ConfigManager.save();
                }
            } else if (module.optiFineSetting() != optiFine) { // the Lads menu
                setting.setInt(mc.gameSettings, module.optiFineSetting());
                if (removeLights != null && mc.renderGlobal != null) removeLights.invoke(null, mc.renderGlobal);
                mc.gameSettings.saveOptions();
            }
            applied = setting.getInt(mc.gameSettings);
        } catch (ReflectiveOperationException failure) {
            LogManager.getLogger("TheLadsCore").error("Dynamic Lights cannot reach OptiFine's setting; the module stops driving it", failure);
            setting = null;
            return;
        }

        // World change detection
        if (mc.theWorld != lastWorld) {
            reset();
            lastWorld = mc.theWorld;
        }

        // Tick explosion flashes
        ExplosionFlashes.WORLD.tick();
        if (mc.theWorld != null && module.isEnabled() && module.explosionsAndTnt() && entityLightLevels != null) {
            int count = ExplosionFlashes.WORLD.count();
            for (int i = 0; i < count; i++) {
                int id = ExplosionFlashes.FIRST_ID - i;
                double x = ExplosionFlashes.WORLD.x(i);
                double y = ExplosionFlashes.WORLD.y(i);
                double z = ExplosionFlashes.WORLD.z(i);
                int lum = ExplosionFlashes.WORLD.luminance(i);
                Entity existing = mc.theWorld.getEntityByID(id);
                if (existing == null) {
                    FlashEntity flash = createFlash(i, mc.theWorld, x, y, z);
                    mc.theWorld.addEntityToWorld(id, flash);
                } else {
                    existing.setPosition(x, y, z);
                }
                entityLightLevels.put(FLASH_CLASSES[i], lum);
            }
            // Remove any inactive slots
            for (int i = count; i < ExplosionFlashes.MAX_FLASHES; i++) {
                int id = ExplosionFlashes.FIRST_ID - i;
                if (mc.theWorld.getEntityByID(id) != null) {
                    mc.theWorld.removeEntityFromWorld(id);
                }
                entityLightLevels.remove(FLASH_CLASSES[i]);
            }
        } else if (mc.theWorld != null && entityLightLevels != null) {
            for (int i = 0; i < ExplosionFlashes.MAX_FLASHES; i++) {
                int id = ExplosionFlashes.FIRST_ID - i;
                if (mc.theWorld.getEntityByID(id) != null) {
                    mc.theWorld.removeEntityFromWorld(id);
                }
                entityLightLevels.remove(FLASH_CLASSES[i]);
            }
        }
    }

    /** Whether it registered as built in (OptiFine's dynamic lights setting found). */
    public static boolean available() { return setting != null; }

    static DynamicLightsModule module() { return (DynamicLightsModule) Options189.module(DynamicLightsModule.NAME); }

    private static FlashEntity createFlash(int slot, World world, double x, double y, double z) {
        FlashEntity flash;
        switch (slot) {
            case 0: flash = new Flash0(world); break;
            case 1: flash = new Flash1(world); break;
            case 2: flash = new Flash2(world); break;
            case 3: flash = new Flash3(world); break;
            case 4: flash = new Flash4(world); break;
            case 5: flash = new Flash5(world); break;
            case 6: flash = new Flash6(world); break;
            case 7: flash = new Flash7(world); break;
            case 8: flash = new Flash8(world); break;
            case 9: flash = new Flash9(world); break;
            case 10: flash = new Flash10(world); break;
            case 11: flash = new Flash11(world); break;
            case 12: flash = new Flash12(world); break;
            case 13: flash = new Flash13(world); break;
            case 14: flash = new Flash14(world); break;
            case 15: flash = new Flash15(world); break;
            default: flash = new Flash0(world); break;
        }
        flash.setPosition(x, y, z);
        return flash;
    }

    public static abstract class FlashEntity extends Entity {
        public FlashEntity(World world) {
            super(world);
            this.noClip = true;
            this.setSize(0.1F, 0.1F);
        }
        @Override protected void entityInit() {}
        @Override protected void readEntityFromNBT(NBTTagCompound tag) {}
        @Override protected void writeEntityToNBT(NBTTagCompound tag) {}
    }

    public static final class Flash0 extends FlashEntity { public Flash0(World w) { super(w); } }
    public static final class Flash1 extends FlashEntity { public Flash1(World w) { super(w); } }
    public static final class Flash2 extends FlashEntity { public Flash2(World w) { super(w); } }
    public static final class Flash3 extends FlashEntity { public Flash3(World w) { super(w); } }
    public static final class Flash4 extends FlashEntity { public Flash4(World w) { super(w); } }
    public static final class Flash5 extends FlashEntity { public Flash5(World w) { super(w); } }
    public static final class Flash6 extends FlashEntity { public Flash6(World w) { super(w); } }
    public static final class Flash7 extends FlashEntity { public Flash7(World w) { super(w); } }
    public static final class Flash8 extends FlashEntity { public Flash8(World w) { super(w); } }
    public static final class Flash9 extends FlashEntity { public Flash9(World w) { super(w); } }
    public static final class Flash10 extends FlashEntity { public Flash10(World w) { super(w); } }
    public static final class Flash11 extends FlashEntity { public Flash11(World w) { super(w); } }
    public static final class Flash12 extends FlashEntity { public Flash12(World w) { super(w); } }
    public static final class Flash13 extends FlashEntity { public Flash13(World w) { super(w); } }
    public static final class Flash14 extends FlashEntity { public Flash14(World w) { super(w); } }
    public static final class Flash15 extends FlashEntity { public Flash15(World w) { super(w); } }

    @SuppressWarnings("unchecked")
    private static final Class<? extends FlashEntity>[] FLASH_CLASSES = new Class[] {
        Flash0.class, Flash1.class, Flash2.class, Flash3.class,
        Flash4.class, Flash5.class, Flash6.class, Flash7.class,
        Flash8.class, Flash9.class, Flash10.class, Flash11.class,
        Flash12.class, Flash13.class, Flash14.class, Flash15.class
    };
}
