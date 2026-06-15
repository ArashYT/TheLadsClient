package com.thelads.core.mixin.cosmetics;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.UUID;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {

    @Unique
    private Identifier thelads$cachedSkinId = null;

    @Unique
    private Identifier thelads$cachedCapeId = null;

    @Unique
    private PlayerSkin thelads$cachedOriginalSkin = null;

    @Unique
    private PlayerSkin thelads$cachedModifiedSkin = null;

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void thelads$getSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        UUID uuid = player.getUUID();
        if (uuid == null) return;
        
        Identifier activeSkin = CosmeticsBackend.getActiveSkin(uuid);
        Identifier activeCape = CosmeticsBackend.getActiveCape(uuid);
        
        if (activeSkin == null && activeCape == null) {
            thelads$cachedSkinId = null;
            thelads$cachedCapeId = null;
            thelads$cachedOriginalSkin = null;
            thelads$cachedModifiedSkin = null;
            return;
        }

        PlayerSkin original = cir.getReturnValue();
        if (original != null) {
            if (Objects.equals(activeSkin, thelads$cachedSkinId) &&
                Objects.equals(activeCape, thelads$cachedCapeId) &&
                Objects.equals(original, thelads$cachedOriginalSkin) &&
                thelads$cachedModifiedSkin != null) {
                cir.setReturnValue(thelads$cachedModifiedSkin);
                return;
            }

            thelads$cachedSkinId = activeSkin;
            thelads$cachedCapeId = activeCape;
            thelads$cachedOriginalSkin = original;

            ClientAsset.Texture newSkin = original.body();
            if (activeSkin != null) {
                newSkin = new ClientAsset.Texture() {
                    @Override public Identifier texturePath() { return activeSkin; }
                    @Override public Identifier id() { return activeSkin; }
                };
            }

            ClientAsset.Texture newCape = original.cape();
            if (activeCape != null) {
                newCape = new ClientAsset.Texture() {
                    @Override public Identifier texturePath() { return activeCape; }
                    @Override public Identifier id() { return activeCape; }
                };
            }
            
            thelads$cachedModifiedSkin = new PlayerSkin(
                newSkin, 
                newCape, 
                original.elytra(), 
                original.model(), 
                original.secure()
            );
            cir.setReturnValue(thelads$cachedModifiedSkin);
        }
    }
}
