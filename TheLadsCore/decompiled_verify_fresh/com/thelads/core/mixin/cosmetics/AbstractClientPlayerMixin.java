/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.core.ClientAsset$Texture
 *  net.minecraft.world.entity.player.PlayerSkin
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin.cosmetics;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={AbstractClientPlayer.class})
public class AbstractClientPlayerMixin {
    @Unique
    private Identifier thelads$cachedSkinId = null;
    @Unique
    private Identifier thelads$cachedCapeId = null;
    @Unique
    private PlayerSkin thelads$cachedOriginalSkin = null;
    @Unique
    private PlayerSkin thelads$cachedModifiedSkin = null;

    @Inject(method={"getSkin"}, at={@At(value="RETURN")}, cancellable=true, require=0)
    private void thelads$getSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer)this;
        UUID uuid = player.getUUID();
        if (uuid == null) {
            return;
        }
        final Identifier activeSkin = CosmeticsBackend.getActiveSkin(uuid);
        final Identifier activeCape = CosmeticsBackend.getActiveCape(uuid);
        if (activeSkin == null && activeCape == null) {
            this.thelads$cachedSkinId = null;
            this.thelads$cachedCapeId = null;
            this.thelads$cachedOriginalSkin = null;
            this.thelads$cachedModifiedSkin = null;
            return;
        }
        PlayerSkin original = (PlayerSkin)cir.getReturnValue();
        if (original != null) {
            if (Objects.equals(activeSkin, this.thelads$cachedSkinId) && Objects.equals(activeCape, this.thelads$cachedCapeId) && Objects.equals(original, this.thelads$cachedOriginalSkin) && this.thelads$cachedModifiedSkin != null) {
                cir.setReturnValue((Object)this.thelads$cachedModifiedSkin);
                return;
            }
            this.thelads$cachedSkinId = activeSkin;
            this.thelads$cachedCapeId = activeCape;
            this.thelads$cachedOriginalSkin = original;
            ClientAsset.Texture newSkin = original.body();
            if (activeSkin != null) {
                newSkin = new ClientAsset.Texture(){
                    {
                        Objects.requireNonNull(this$0);
                    }

                    public Identifier texturePath() {
                        return activeSkin;
                    }

                    public Identifier id() {
                        return activeSkin;
                    }
                };
            }
            ClientAsset.Texture newCape = original.cape();
            if (activeCape != null) {
                newCape = new ClientAsset.Texture(){
                    {
                        Objects.requireNonNull(this$0);
                    }

                    public Identifier texturePath() {
                        return activeCape;
                    }

                    public Identifier id() {
                        return activeCape;
                    }
                };
            }
            this.thelads$cachedModifiedSkin = new PlayerSkin(newSkin, newCape, original.elytra(), original.model(), original.secure());
            cir.setReturnValue((Object)this.thelads$cachedModifiedSkin);
        }
    }
}

