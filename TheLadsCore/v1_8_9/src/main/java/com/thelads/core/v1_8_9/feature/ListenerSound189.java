package com.thelads.core.v1_8_9.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/**
 * A sound heard in both ears wherever the player stands (Kill Banner, Combo Sounds), as the newer versions' UI sounds are. 1.8.9
 * puts an unplaced sound at the world's origin, where OpenAL pans a mono sound to one side (or behind): all but Reaver's kill
 * sounds, and the chimes, played in one ear only. This one stays on the player's eyes (the listener), moved each tick.
 */
public final class ListenerSound189 extends MovingSound {
    public ListenerSound189(ResourceLocation sound, float volume, float pitch) {
        super(sound);
        this.volume = volume;
        this.pitch = pitch;
        this.attenuationType = ISound.AttenuationType.NONE;
        update();
    }

    @Override
    public void update() {
        Entity player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        xPosF = (float) player.posX;
        yPosF = (float) (player.posY + player.getEyeHeight());
        zPosF = (float) player.posZ;
    }
}
