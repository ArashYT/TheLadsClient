package com.thelads.core.v26_2.feature;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (LADS_QA_SOUNDBREAK=1): block break and hit sounds in the QA world, each followed until it ends. A sound is cut when
 * OpenAL stops it, or its gain drops to 0, well before its .ogg file ends.
 */
final class SoundBreakProbe26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final class Watch {
        final String name; long lengthMs = -1; int activeTicks; volatile float maxOffset, minGain = Float.MAX_VALUE, maxGain; volatile int lastState;
        Watch(String name) { this.name = name; }
    }
    private static final Map<SoundInstance, Watch> watched = new LinkedHashMap<>();
    private static int tick = -1, failed;
    private static boolean done;
    private SoundBreakProbe26() {}

    static void tick(boolean ready) {
        if (done || System.getenv("LADS_QA_SOUNDBREAK") == null || (tick < 0 && !ready)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            if (++tick == 0) mc.getSoundManager().addListener((sound, set, range) -> {
                String id = sound.getIdentifier().getPath();
                if (!done && (id.endsWith(".break") || id.endsWith(".hit") || id.endsWith(".growl")))
                    watched.putIfAbsent(sound, new Watch(sound.getIdentifier() + " " + sound.getSource()));
            });
            BlockPos pos = mc.player.blockPosition().relative(mc.player.getDirection(), 2);
            RandomSource random = SoundInstance.createUnseededRandom();
            switch (tick) {
                case 5 -> play(mc, new SimpleSoundInstance(SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1, 0.8f, random, pos));
                case 30 -> mc.level.levelEvent(mc.player, 2001, pos, Block.getId(Blocks.STONE.defaultBlockState()));
                case 55 -> play(mc, new SimpleSoundInstance(SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.25f, 0.5f, random, pos));
                case 150 -> mc.level.levelEvent(mc.player, 2001, pos, Block.getId(Blocks.OAK_LOG.defaultBlockState()));
                case 6 -> play(mc, new SimpleSoundInstance(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1, 1, random, pos));
                case 70 -> {
                    BlockPos target = pos.above();
                    var server = mc.getSingleplayerServer();
                    if (server != null) server.execute(() -> server.overworld().setBlock(target, Blocks.DIRT.defaultBlockState(), 3));
                }
                default -> {}
            }
            // Real mining, as Minecraft.continueAttack does: start, then continue and swing every tick until the dirt breaks.
            if (tick >= 80 && tick < 140 && !mc.level.getBlockState(pos.above()).isAir()) {
                if (tick == 80 || !mc.gameMode.isDestroying()) mc.gameMode.startDestroyBlock(pos.above(), net.minecraft.core.Direction.UP);
                else mc.gameMode.continueDestroyBlock(pos.above(), net.minecraft.core.Direction.UP);
            }
            follow(mc);
            if (tick < 200) return;
            done = true;
            for (Watch w : watched.values()) {
                // Cut: OpenAL stopped it (or it went silent) before half its file played, with a little slack.
                boolean cut = w.lengthMs > 0 && (w.maxOffset * 1000 + 100 < w.lengthMs / 2 || w.maxGain <= 0);
                if (cut) failed++;
                LOGGER.info("Lads sound break probe {} {}: file {} ms, active {} ms, played to {} ms, gain {}..{}, last AL state {}",
                    cut ? "CUT" : "OK", w.name, w.lengthMs, w.activeTicks * 50, Math.round(w.maxOffset * 1000), w.minGain, w.maxGain, w.lastState);
            }
            LOGGER.info("Lads sound break probe END: {} sounds, {} cut", watched.size(), failed);
        } catch (Throwable failure) {
            done = true;
            LOGGER.error("Lads sound break probe END: failed", failure);
        }
    }

    private static void play(Minecraft mc, SimpleSoundInstance sound) {
        LOGGER.info("Lads sound break probe play {}: {}", sound.getIdentifier(), mc.getSoundManager().play(sound));
    }

    private static void follow(Minecraft mc) throws ReflectiveOperationException {
        Object engine = field(mc.getSoundManager().getClass(), "soundEngine").get(mc.getSoundManager());
        @SuppressWarnings("unchecked")
        Map<SoundInstance, net.minecraft.client.sounds.ChannelAccess.ChannelHandle> channels =
            (Map<SoundInstance, net.minecraft.client.sounds.ChannelAccess.ChannelHandle>) field(engine.getClass(), "instanceToChannel").get(engine);
        for (var entry : watched.entrySet()) {
            Watch w = entry.getValue();
            if (w.lengthMs < 0 && entry.getKey().getSound() != null) w.lengthMs = oggMs(mc, entry.getKey());
            var handle = channels.get(entry.getKey());
            if (handle == null || !mc.getSoundManager().isActive(entry.getKey())) continue;
            w.activeTicks++;
            handle.execute(channel -> {
                try {
                    int source = field(channel.getClass(), "source").getInt(channel);
                    w.lastState = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
                    float gain = AL10.alGetSourcef(source, AL10.AL_GAIN);
                    w.minGain = Math.min(w.minGain, gain);
                    w.maxGain = Math.max(w.maxGain, gain);
                    w.maxOffset = Math.max(w.maxOffset, AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET));
                } catch (ReflectiveOperationException ignored) {}
            });
        }
    }

    /** The .ogg's length: the last page's granule position over the identification header's sample rate. */
    private static long oggMs(Minecraft mc, SoundInstance sound) {
        try (var in = mc.getResourceManager().open(sound.getSound().getPath())) {
            byte[] b = in.readAllBytes();
            long rate = le(b, 40, 4);
            for (int i = b.length - 14; i >= 0; i--)
                if (b[i] == 'O' && b[i + 1] == 'g' && b[i + 2] == 'g' && b[i + 3] == 'S') return rate <= 0 ? -1 : le(b, i + 6, 8) * 1000 / rate;
        } catch (Exception ignored) {}
        return -1;
    }

    private static long le(byte[] b, int at, int n) {
        long v = 0;
        for (int i = n - 1; i >= 0; i--) v = v << 8 | (b[at + i] & 0xFF);
        return v;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
}
