package com.thelads.core.v1_8_9.ladsapi.ui;

import com.thelads.core.client.ladsapi.minigame.AudioState;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.v1_8_9.ladsapi.ui.mixin.MusicTickerAccessor189;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.MusicTicker;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.ResourceLocation;

/**
 * Audio cues on 1.8.9 (PROTOCOL.md section 17): sounds the client already has, through the game's own sound handler, so they follow
 * the player's sliders (never louder) and the category comes from sounds.json. Modern ids are mapped to the 1.8.9 names by a small
 * table; an id with no 1.8.9 sound is ignored (play returns false). Nothing is loaded from outside the game.
 */
final class Audio189 implements Hooks.SoundPlayer {
    /** Starts at a tiny volume: the engine drops a sound that begins at exactly 0 (fade-in from silence). */
    private static final float MIN = 0.0001f;
    private static final Map<String, String> LEGACY = new HashMap<String, String>();
    static {
        LEGACY.put("entity.experience_orb.pickup", "random.orb");
        LEGACY.put("entity.player.levelup", "random.levelup");
        LEGACY.put("block.note_block.harp", "note.harp");
        LEGACY.put("block.note_block.pling", "note.pling");
        LEGACY.put("block.note_block.bass", "note.bass");
        LEGACY.put("block.note_block.basedrum", "note.bd");
        LEGACY.put("block.note_block.snare", "note.snare");
        LEGACY.put("block.note_block.hat", "note.hat");
        LEGACY.put("ui.button.click", "gui.button.press");
        LEGACY.put("entity.generic.explode", "random.explode");
        LEGACY.put("entity.arrow.hit_player", "random.successful_hit");
        LEGACY.put("entity.firework_rocket.blast", "fireworks.blast");
        LEGACY.put("block.anvil.land", "random.anvil_land");
        LEGACY.put("block.chest.open", "random.chestopen");
        LEGACY.put("block.chest.close", "random.chestclosed");
        LEGACY.put("entity.ender_dragon.growl", "mob.enderdragon.growl");
        LEGACY.put("entity.wither.spawn", "mob.wither.spawn");
        LEGACY.put("entity.lightning_bolt.thunder", "ambient.weather.thunder");
        LEGACY.put("block.portal.ambient", "portal.portal");
    }

    private final Map<String, Cue> cues = new HashMap<String, Cue>();
    private boolean held;

    private static final class Cue extends PositionedSound implements ITickableSound {
        volatile boolean stop;

        Cue(ResourceLocation loc, float vol, float pitch, boolean loop, boolean positional, double x, double y, double z) {
            super(loc);
            this.volume = Math.max(vol, MIN);
            this.pitch = pitch;
            this.repeat = loop;
            if (positional) {
                this.xPosF = (float) x;
                this.yPosF = (float) y;
                this.zPosF = (float) z;
            } else {
                this.attenuationType = ISound.AttenuationType.NONE;
            }
        }

        @Override
        public void update() {}

        @Override
        public boolean isDonePlaying() {
            return stop;
        }

        void level(float v) {
            this.volume = Math.max(v, MIN);
        }
    }

    private static ResourceLocation resolve(String id) {
        SoundHandler h = Minecraft.getMinecraft().getSoundHandler();
        if (id.contains(":") && !id.startsWith("minecraft:")) {
            ResourceLocation r = new ResourceLocation(id);
            return h.getSound(r) != null ? r : null;
        }
        String bare = id.startsWith("minecraft:") ? id.substring(10) : id;
        if (h.getSound(new ResourceLocation(bare)) != null) return new ResourceLocation(bare);
        String mapped = LEGACY.get(bare);
        if (mapped == null && bare.startsWith("music_disc.")) mapped = "records." + bare.substring(11);
        if (mapped != null && h.getSound(new ResourceLocation(mapped)) != null) return new ResourceLocation(mapped);
        return null;
    }

    @Override
    public boolean play(AudioState.Voice v, double volume) {
        Minecraft mc = Minecraft.getMinecraft();
        ResourceLocation loc = resolve(v.sound);
        if (loc == null) return false;
        Cue old = cues.remove(v.id);
        if (old != null) {
            old.stop = true;
            mc.getSoundHandler().stopSound(old);
        }
        Cue c = new Cue(loc, (float) volume, (float) v.pitch, v.loop, v.positional, v.x, v.y, v.z);
        cues.put(v.id, c);
        mc.getSoundHandler().playSound(c);
        return true;
    }

    @Override
    public void volume(String id, double volume) {
        Cue c = cues.get(id);
        if (c != null) c.level((float) volume);
    }

    @Override
    public void stop(String id) {
        Cue c = cues.remove(id);
        if (c == null) return;
        c.stop = true;
        Minecraft.getMinecraft().getSoundHandler().stopSound(c);
    }

    @Override
    public boolean playing(String id) {
        Cue c = cues.get(id);
        return c != null && Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(c);
    }

    @Override
    public void musicHold(boolean hold) {
        held = hold;
        holdTick();
    }

    /** Every client tick: while a cue asks for it, the vanilla background music is kept stopped and its next start pushed back. */
    void tick() {
        holdTick();
    }

    private void holdTick() {
        if (!held) return;
        MusicTicker t = Minecraft.getMinecraft().getMusicTicker();
        if (t == null) return;
        MusicTickerAccessor189 a = (MusicTickerAccessor189) t;
        ISound cur = a.ladsCurrentMusic();
        if (cur != null) Minecraft.getMinecraft().getSoundHandler().stopSound(cur);
        if (a.ladsTimeUntilNext() < 100) a.ladsSetTimeUntilNext(100);
    }

    /** QA text: what is playing in the engine. */
    String qa() {
        StringBuilder b = new StringBuilder("cues=").append(cues.size()).append(" held=").append(held);
        SoundHandler h = Minecraft.getMinecraft().getSoundHandler();
        for (Map.Entry<String, Cue> e : cues.entrySet()) {
            Cue c = e.getValue();
            b.append(' ').append(e.getKey()).append(':').append(c.getSoundLocation()).append(':')
                .append(h.isSoundPlaying(c) ? "playing" : "silent").append(":vol").append(String.format("%.2f", c.getVolume()));
            net.minecraft.client.audio.SoundEventAccessorComposite comp = h.getSound(c.getSoundLocation());
            net.minecraft.client.audio.SoundCategory sc = comp == null ? net.minecraft.client.audio.SoundCategory.MASTER : comp.getSoundCategory();
            net.minecraft.client.settings.GameSettings gs = Minecraft.getMinecraft().gameSettings;
            float slider = sc == net.minecraft.client.audio.SoundCategory.MASTER ? 1f : gs.getSoundLevel(sc);
            float master = gs.getSoundLevel(net.minecraft.client.audio.SoundCategory.MASTER);
            b.append(":cat=").append(sc.getCategoryName()).append(":slider=").append(String.format("%.2f", slider)).append(":master=")
                .append(String.format("%.2f", master)).append(":effective=").append(String.format("%.3f", Math.min(c.getVolume(), 1f) * slider * master));
        }
        return b.toString();
    }
}
