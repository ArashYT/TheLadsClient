package com.thelads.core.v26_2.ladsapi.ui;

import com.thelads.core.client.ladsapi.minigame.AudioState;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Lads API audio cues (section 17) on the game's own sound manager: only sound events the game already has. Categories music,
 * ambient and sfx map to the player's own Music, Ambient and Master sliders, which the engine multiplies in, so a cue is never
 * louder than the player set. Fades and ends come from {@code AudioState} (it calls {@link #volume} and {@link #stop}).
 */
public final class Audio26 implements Hooks.SoundPlayer {
    private static final Map<String, Cue> cues = new ConcurrentHashMap<>();
    private volatile boolean hold;

    private static final class Cue extends AbstractTickableSoundInstance {
        final long started = System.nanoTime();
        volatile boolean stop;

        Cue(SoundEvent event, SoundSource source, AudioState.Voice v, double volume) {
            super(event, source, RandomSource.create());
            this.volume = (float) volume;
            this.pitch = (float) v.pitch;
            this.looping = v.loop;
            if (v.positional) {
                this.x = v.x;
                this.y = v.y;
                this.z = v.z;
                this.attenuation = SoundInstance.Attenuation.LINEAR;
                this.relative = false;
            } else {
                this.attenuation = SoundInstance.Attenuation.NONE;
                this.relative = true;
            }
        }

        /** A fade-in cue starts at volume 0; the engine drops a silent instance unless it may start silent. */
        @Override
        public boolean canStartSilent() {
            return true;
        }

        void level(float v) {
            this.volume = v;
        }

        @Override
        public void tick() {
            if (stop) stop();
        }
    }

    private static SoundSource source(String category) {
        return switch (category) {
            case "music" -> SoundSource.MUSIC;
            case "ambient" -> SoundSource.AMBIENT;
            default -> SoundSource.MASTER;
        };
    }

    @Override
    public boolean play(AudioState.Voice voice, double volume) {
        Identifier id = Identifier.tryParse(voice.sound);
        if (id == null) return false;
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (event == null) return false;
        Cue cue = new Cue(event, source(voice.category), voice, volume);
        Cue old = cues.put(voice.id, cue);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (old != null) mc.getSoundManager().stop(old);
            mc.getSoundManager().play(cue);
        });
        return true;
    }

    @Override
    public void volume(String id, double volume) {
        Cue c = cues.get(id);
        if (c != null) c.level((float) Math.max(0, Math.min(1, volume)));
    }

    @Override
    public void stop(String id) {
        Cue c = cues.remove(id);
        if (c == null) return;
        c.stop = true;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getSoundManager().stop(c));
    }

    @Override
    public boolean playing(String id) {
        Cue c = cues.get(id);
        if (c == null) return false;
        if (System.nanoTime() - c.started < 1_000_000_000L) return true; // queued to the client thread, not started yet
        boolean active = !c.isStopped() && Minecraft.getInstance().getSoundManager().isActive(c);
        if (!active) cues.remove(id, c);
        return active;
    }

    @Override
    public void musicHold(boolean hold) {
        this.hold = hold;
    }

    /** QA only: what the sound engine holds for the cues, the F3 "Sounds" line, the sliders and the volume the engine really uses. */
    public static String qa() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Cue> e : cues.entrySet()) {
            Cue c = e.getValue();
            float engine = -1;
            try {
                var sm = mc.getSoundManager();
                var f = sm.getClass().getDeclaredField("soundEngine");
                f.setAccessible(true);
                Object se = f.get(sm);
                var m = se.getClass().getDeclaredMethod("calculateVolume", SoundInstance.class);
                m.setAccessible(true);
                engine = (Float) m.invoke(se, c);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // engine internals not reachable: the slider numbers below still tell
            }
            sb.append(e.getKey()).append("{active=").append(mc.getSoundManager().isActive(c)).append(" stopped=").append(c.isStopped())
                .append(" cueVol=").append(c.getVolume()).append(" source=").append(c.getSource()).append(" engineVol=").append(engine)
                .append(" slider=").append(mc.options.getFinalSoundSourceVolume(c.getSource())).append("} ");
        }
        return "cues=" + cues.size() + " [" + sb.toString().trim() + "] f3=\"" + mc.getSoundManager().getChannelDebugString() + "\" master="
            + mc.options.getSoundSourceVolume(SoundSource.MASTER) + " music=" + mc.options.getSoundSourceVolume(SoundSource.MUSIC)
            + " ambient=" + mc.options.getSoundSourceVolume(SoundSource.AMBIENT);
    }

    /** Client tick: vanilla background music stays stopped while a cue asked for it. */
    public void tick() {
        if (hold) Minecraft.getInstance().getMusicManager().stopPlaying();
    }
}
