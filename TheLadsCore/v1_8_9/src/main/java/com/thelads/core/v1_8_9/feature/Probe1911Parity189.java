package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.check;

import com.thelads.core.client.BackgroundFps;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v1_8_9.feature.jade.Jade189;
import java.io.File;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only (LADS_VERIFY_189_ONLY=parity1911): 1.9.11's 1.8.9 parity with 26.3 in the QA world: Jade (a crafting table and a pig under
 * the crosshair, the panel drawn, its settings), Minimap (Xaero's Minimap from the pack, the Lads switch driving Xaero's own setting,
 * its settings screen) and Dynamic FPS (the background states' limits, render skip, presets, volume fade and profile editor).
 */
final class Probe1911Parity189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static int tick = -1, drawnBefore, mode;
    private static boolean bypassWas, dynamicWas, minimapWas;
    private static double unfocusedWas, hiddenWas;
    private static float masterWas;
    private static BlockPos table;
    private static volatile int pig = -1;

    private Probe1911Parity189() {}

    private static Module dynamic() { return DynamicFPS189.module(); }

    private static void mode(int index) { ((DropdownOption) dynamic().getOption("Mode")).setIndex(index); }

    private static double slider(String name) { return ((SliderOption) dynamic().getOption(name)).getValue(); }

    private static boolean has(List<String> lines, String text) {
        for (String line : lines) if (line.contains(text)) return true;
        return false;
    }

    static boolean run(final Minecraft mc) throws Exception {
        tick++;
        if (tick == 0) {
            for (String name : new String[] {"Jade", "Minimap", "DynamicFPS"})
                check("builtIn".equals(ModuleSupport.support(name)), name + " is built in on 1.8.9 (" + ModuleSupport.support(name) + ": " + ModuleSupport.get(name).label() + ")");
            check(com.thelads.core.config.ModuleManager.getInstance().getModule("UnfocusedFpsCap") == null, "the Unfocused FPS cap is folded into Dynamic FPS (no separate module)");
            // Minimap: Xaero's Minimap from the launcher's 1.8.9 pack, driven by the Lads switch.
            check(Loader.isModLoaded(Minimap189.MOD_ID) && Minimap189.integrated(), "Xaero's Minimap is loaded and the Lads Minimap drives it");
            minimapWas = Minimap189.module().isEnabled();
            Minimap189.module().setEnabled(true);
            // Dynamic FPS: the QA harness's bypass off for this probe; Aggressive's preset comes on the next tick.
            bypassWas = DynamicFPS189.bypass;
            dynamicWas = dynamic().isEnabled();
            mode = ((DropdownOption) dynamic().getOption("Mode")).getIndex();
            unfocusedWas = slider("Unfocused FPS");
            hiddenWas = slider("Hidden FPS");
            masterWas = mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
            DynamicFPS189.bypass = false;
            dynamic().setEnabled(true);
            mode(BackgroundFps.AGGRESSIVE);
            // Jade: a crafting table at eye level two blocks ahead, the player looking straight at it.
            EnumFacing facing = mc.thePlayer.getHorizontalFacing();
            mc.thePlayer.rotationYaw = facing.getHorizontalIndex() * 90F;
            mc.thePlayer.rotationPitch = 0;
            table = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 1, mc.thePlayer.posZ).offset(facing, 2);
            final BlockPos at = table;
            final java.util.UUID id = mc.thePlayer.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP player = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id);
                player.worldObj.setBlockToAir(at.offset(facing.getOpposite()));
                player.worldObj.setBlockState(at, Blocks.crafting_table.getDefaultState());
                EntityPig spawned = new EntityPig(player.worldObj);
                spawned.setPosition(player.posX - facing.getFrontOffsetX() * 3 + 0.5, player.posY, player.posZ - facing.getFrontOffsetZ() * 3 + 0.5);
                spawned.setNoAI(true);
                player.worldObj.spawnEntityInWorld(spawned);
                pig = spawned.getEntityId();
            });
        }
        if (tick == 2) {
            check(Boolean.TRUE.equals(Minimap189.xaeroShown()), "Minimap on: Xaero's own minimap setting is on (" + Minimap189.xaeroShown() + ")");
            Minimap189.module().setEnabled(false);
            check(slider("Unfocused FPS") == 15 && slider("Hidden FPS") == 5, "Dynamic FPS Aggressive sets Unfocused 15 and Hidden 5 (" + slider("Unfocused FPS") + ", " + slider("Hidden FPS") + ")");
            DynamicFPS189.force(true, false);
            check(DynamicFPS189.state() == BackgroundFps.State.UNFOCUSED && DynamicFPS189.limit(260) == 15 && DynamicFPS189.limit(10) == 10,
                "Aggressive, unfocused: 15 FPS at once, and a lower player limit is kept (" + DynamicFPS189.state() + ", " + DynamicFPS189.limit(260) + ")");
            check(!DynamicFPS189.shouldSkipWorldRender(false), "unfocused: the world still renders");
            DynamicFPS189.force(false, true);
            check(DynamicFPS189.state() == BackgroundFps.State.INVISIBLE && DynamicFPS189.limit(260) == 5 && DynamicFPS189.shouldSkipWorldRender(false),
                "Aggressive, minimized: 5 FPS and no world render (" + DynamicFPS189.state() + ", " + DynamicFPS189.limit(260) + ")");
            DynamicFPS189.force(false, false);
            mode(BackgroundFps.OFF);
        }
        if (tick == 4) {
            check(Boolean.FALSE.equals(Minimap189.xaeroShown()), "Minimap off: Xaero's own minimap setting follows (" + Minimap189.xaeroShown() + ")");
            Minimap189.module().setEnabled(true);
            DynamicFPS189.force(true, false);
            check(DynamicFPS189.state() == BackgroundFps.State.FOCUSED && DynamicFPS189.limit(260) == 260, "Mode Off: no background limit");
            DynamicFPS189.force(false, false);
            mode(BackgroundFps.BALANCED);
        }
        if (tick == 6) {
            check(slider("Unfocused FPS") == 60 && slider("Hidden FPS") == 30, "Balanced sets Unfocused 60 and Hidden 30");
            ((SliderOption) dynamic().getOption("Unfocused FPS")).setValue(0);
            DynamicFPS189.force(true, false); // from here on the window counts as unfocused: after Balanced's 3 s the volume fades to 25%
        }
        if (tick == 7) {
            check(DynamicFPS189.state() == BackgroundFps.State.FOCUSED, "Balanced: the first 3 s unfocused keep the focused profile");
        }
        if (tick == 80) {
            check(DynamicFPS189.state() == BackgroundFps.State.UNFOCUSED && DynamicFPS189.limit(260) == 10 && DynamicFPS189.shouldSkipWorldRender(false),
                "Balanced after 3 s, Unfocused FPS 0: rendering paused, the loop held at 10 for ticks (" + DynamicFPS189.state() + ", " + DynamicFPS189.limit(260) + ")");
        }
        if (tick == 120) {
            check(Math.abs(DynamicFPS189.volume() - DynamicFPS189.PROFILE.unfocusedVolume) < 1e-3, "unfocused: the volume faded to the 25% profile (" + DynamicFPS189.volume() + ")");
            check(mc.gameSettings.getSoundLevel(SoundCategory.MASTER) == masterWas, "the saved master volume option is untouched");
            ((SliderOption) dynamic().getOption("Unfocused FPS")).setValue(60);
            check(DynamicFPS189.limit(260) == 60 && !DynamicFPS189.shouldSkipWorldRender(false), "Unfocused FPS 60: capped at 60 and rendering again");
            DynamicFPS189.force(false, false);
            DynamicFPS189.bypass = true; // the real (unfocused, maybe minimized) QA window counts as focused again: full volume, Jade drawn
        }
        if (tick == 150) {
            check(Math.abs(DynamicFPS189.volume() - 1f) < 1e-3, "focused again: the volume faded back to full within a second (" + DynamicFPS189.volume() + ")");
            ((ActionOption) dynamic().getOption("Background Profiles")).run();
            check(mc.currentScreen instanceof com.thelads.core.v1_8_9.gui.DynamicFpsScreen189, "Background Profiles opens the 1.8.9 profile editor (" + mc.currentScreen + ")");
            mc.displayGuiScreen(null);
            check(new File(mc.mcDataDir, "config/theladscore-dynamicfps.properties").isFile(), "closing the editor saves config/theladscore-dynamicfps.properties");
            ((ActionOption) Minimap189.module().getOption("Map and waypoint settings")).run();
            check(mc.currentScreen != null && mc.currentScreen.getClass().getName().equals("xaero.minimap.gui.GuiMinimap"),
                "Map and waypoint settings opens Xaero's settings (" + mc.currentScreen + ")");
            mc.displayGuiScreen(null);
        }
        if (tick == 160) {
            check(mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && table.equals(mc.objectMouseOver.getBlockPos()), "the crosshair is on the crafting table (" + mc.objectMouseOver + ")");
            List<String> lines = Jade189.lines(mc);
            LOG.info("Lads parity 1.9.11 probe: Jade on the crafting table: {}", lines);
            check(has(lines, "Crafting Table") && has(lines, "Minecraft"), "Jade names the crafting table and its mod (" + lines + ")");
            drawnBefore = Jade189.drawnFrames();
        }
        if (tick == 200) {
            check(Jade189.drawnFrames() > drawnBefore, "Jade drew its panel while the crafting table was looked at (" + (Jade189.drawnFrames() - drawnBefore) + " frames)");
            Entity entity = mc.theWorld.getEntityByID(pig);
            check(entity instanceof EntityPig, "a pig for Jade (" + entity + ")");
            mc.objectMouseOver = new MovingObjectPosition(entity);
            List<String> lines = Jade189.lines(mc);
            LOG.info("Lads parity 1.9.11 probe: Jade on the pig: {}", lines);
            check(has(lines, "Pig") && has(lines, "10"), "Jade names the pig and its 10 health (" + lines + ")");
            ((ActionOption) com.thelads.core.config.ModuleManager.getInstance().getModule("Jade").getOption("Settings and addons")).run();
            check(mc.currentScreen != null && mc.currentScreen.getClass().getName().contains("Jade"), "Settings and addons opens the Jade settings (" + mc.currentScreen + ")");
            mc.displayGuiScreen(null);
        }
        if (tick < 210) return false;
        // Put everything back as the run found it.
        final BlockPos at = table;
        final int id = pig;
        mc.getIntegratedServer().addScheduledTask(() -> {
            net.minecraft.world.World world = mc.getIntegratedServer().worldServerForDimension(0);
            world.setBlockToAir(at);
            Entity spawned = world.getEntityByID(id);
            if (spawned != null) spawned.setDead();
        });
        ((SliderOption) dynamic().getOption("Unfocused FPS")).setValue(unfocusedWas);
        ((SliderOption) dynamic().getOption("Hidden FPS")).setValue(hiddenWas);
        mode(mode);
        dynamic().setEnabled(dynamicWas);
        DynamicFPS189.bypass = bypassWas;
        Minimap189.module().setEnabled(minimapWas);
        LOG.info("Lads parity 1.9.11 probe END");
        return true;
    }
}
