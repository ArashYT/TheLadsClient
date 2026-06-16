package com.thelads.core.mixin.alwayson.skinlayers;

import com.thelads.core.features.alwayson.skinlayers.accessor.PlayerSettings;
import com.thelads.core.features.alwayson.skinlayers.api.Mesh;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AbstractClientPlayer.class)
public abstract class PlayerMixin implements PlayerSettings {
    @Unique
    private Mesh headMesh;
    @Unique
    private Mesh torsoMesh;
    @Unique
    private Mesh leftArmMesh;
    @Unique
    private Mesh rightArmMesh;
    @Unique
    private Mesh leftLegMesh;
    @Unique
    private Mesh rightLegMesh;
    @Unique
    private Identifier currentSkin = null;
    @Unique
    private boolean thinarms = false;

    @Override
    public Mesh getHeadMesh() {
        return this.headMesh;
    }

    @Override
    public void setHeadMesh(Mesh headMesh) {
        this.headMesh = headMesh;
    }

    @Override
    public Mesh getTorsoMesh() {
        return this.torsoMesh;
    }

    @Override
    public void setTorsoMesh(Mesh torsoMesh) {
        this.torsoMesh = torsoMesh;
    }

    @Override
    public Mesh getLeftArmMesh() {
        return this.leftArmMesh;
    }

    @Override
    public void setLeftArmMesh(Mesh leftArmMesh) {
        this.leftArmMesh = leftArmMesh;
    }

    @Override
    public Mesh getRightArmMesh() {
        return this.rightArmMesh;
    }

    @Override
    public void setRightArmMesh(Mesh rightArmMesh) {
        this.rightArmMesh = rightArmMesh;
    }

    @Override
    public Mesh getLeftLegMesh() {
        return this.leftLegMesh;
    }

    @Override
    public void setLeftLegMesh(Mesh leftLegMesh) {
        this.leftLegMesh = leftLegMesh;
    }

    @Override
    public Mesh getRightLegMesh() {
        return this.rightLegMesh;
    }

    @Override
    public void setRightLegMesh(Mesh rightLegMesh) {
        this.rightLegMesh = rightLegMesh;
    }

    @Override
    public Identifier getCurrentSkin() {
        return this.currentSkin;
    }

    @Override
    public void setCurrentSkin(Identifier skin) {
        this.currentSkin = skin;
    }

    @Override
    public boolean hasThinArms() {
        return this.thinarms;
    }

    @Override
    public void setThinArms(boolean thin) {
        this.thinarms = thin;
    }
}
