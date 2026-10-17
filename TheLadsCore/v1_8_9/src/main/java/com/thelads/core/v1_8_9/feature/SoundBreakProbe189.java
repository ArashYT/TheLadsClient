package com.thelads.core.v1_8_9.feature;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.Blocks;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only (LADS_VERIFY_189_ONLY=soundbreak), as 26.3's SoundBreakProbe26: block break and hit sounds in the QA world (direct,
 * playAuxSFX 2001, real mining), each followed while paulscode still plays it. 1.8.9's dig and step files run 0.2 s and more, so
 * one that plays for 100 ms or less was cut.
 */
public final class SoundBreakProbe189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    /** SoundHandlerQaMixin hands every sound played meanwhile to {@link #played}. */
    public static volatile boolean active;
    private static final Map<ISound, int[]> watched = new LinkedHashMap<>();
    private static int tick = -1;
    private static BlockPos pos;
    private SoundBreakProbe189() {}

    public static void played(ISound sound) {
        String id = sound.getSoundLocation().getResourcePath();
        if (id.startsWith("dig.") || id.startsWith("step.") || id.equals("mob.enderdragon.growl")) watched.put(sound, new int[1]);
    }

    static boolean run(Minecraft mc) {
        if (++tick == 0) {
            active = true;
            pos = new BlockPos(mc.thePlayer).offset(mc.thePlayer.getHorizontalFacing(), 2);
        }
        final BlockPos dirt = pos.up();
        if (tick == 5) play(mc, "dig.stone", 1, 0.8f);
        if (tick == 6) play(mc, "mob.enderdragon.growl", 1, 1);
        if (tick == 30) mc.theWorld.playAuxSFX(2001, pos, Block.getStateId(Blocks.stone.getDefaultState()));
        if (tick == 55) play(mc, "step.stone", 0.25f, 0.5f);
        if (tick == 70) {
            final IntegratedServer server = mc.getIntegratedServer();
            if (server != null) server.addScheduledTask(() -> server.worldServerForDimension(0).setBlockState(dirt, Blocks.dirt.getDefaultState()));
        }
        // Real mining, as Minecraft.sendClickBlockToController does: click, then damage the block every tick until it breaks.
        if (tick >= 80 && tick < 140 && !mc.theWorld.isAirBlock(dirt)) {
            if (tick == 80 || !mc.playerController.getIsHittingBlock()) mc.playerController.clickBlock(dirt, EnumFacing.UP);
            else mc.playerController.onPlayerDamageBlock(dirt, EnumFacing.UP);
        }
        if (tick == 150) mc.theWorld.playAuxSFX(2001, pos, Block.getStateId(Blocks.log.getDefaultState()));
        for (Map.Entry<ISound, int[]> entry : watched.entrySet())
            if (mc.getSoundHandler().isSoundPlaying(entry.getKey())) entry.getValue()[0]++;
        if (tick < 200) return false;
        active = false;
        int cut = 0;
        for (Map.Entry<ISound, int[]> entry : watched.entrySet()) {
            int ms = entry.getValue()[0] * 50;
            if (ms <= 100) cut++;
            LOG.info("Lads sound break probe {} {}: played {} ms (volume {}, pitch {})", ms <= 100 ? "CUT" : "OK",
                entry.getKey().getSoundLocation(), ms, entry.getKey().getVolume(), entry.getKey().getPitch());
        }
        LOG.info("Lads sound break probe END: {} sounds, {} cut", watched.size(), cut);
        return true;
    }

    private static void play(Minecraft mc, String sound, float volume, float pitch) {
        mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(sound), volume, pitch,
            pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f));
    }
}
