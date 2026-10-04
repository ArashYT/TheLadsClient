package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ItemPhysics;
import com.thelads.core.modules.ItemPhysicsModule;
import com.thelads.core.v1_8_9.feature.ItemPhysics189;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item Physics: each dropped item's tumble (client) and the singleplayer rules (ItemPhysics189.rules decides where). */
@Mixin(EntityItem.class)
public abstract class EntityItemMixin extends Entity implements ItemPhysics.Holder {
    @Shadow public float hoverStart;
    @Shadow public abstract ItemStack getEntityItem();
    @Unique private ItemPhysics.Tumble ladsTumble;
    /** Only flammable items burn: this one is fire-proof (Entity.isImmuneToFire) under the rule. */
    @Unique private boolean ladsFireproof;

    public EntityItemMixin(World world) {
        super(world);
    }

    @Override
    public ItemPhysics.Tumble lads$tumble() {
        if (ladsTumble == null) ladsTumble = new ItemPhysics.Tumble((float) Math.toDegrees(hoverStart));
        return ladsTumble;
    }

    /** At HEAD (Mixin 0.7: onUpdate returns twice): last tick's movement for the tumble, then the rules for this tick. */
    @Inject(method = "onUpdate", at = @At("HEAD"), require = 1)
    private void ladsItemPhysics(CallbackInfo ci) {
        if (worldObj.isRemote && ItemPhysics189.renders()) {
            double dx = posX - prevPosX, dy = posY - prevPosY, dz = posZ - prevPosZ;
            lads$tumble().tick(Math.sqrt(dx * dx + dy * dy + dz * dz), onGround, Math.max(ItemPhysics189.depth((EntityItem) (Object) this, Material.water),
                ItemPhysics189.depth((EntityItem) (Object) this, Material.lava)));
        }
        ItemPhysicsModule module = ItemPhysics189.rules(worldObj);
        ItemStack stack = getEntityItem();
        boolean fireproof = module != null && module.fireproof.get() && !ItemPhysics189.burns(stack);
        if (fireproof != ladsFireproof) isImmuneToFire = ladsFireproof = fireproof;
        if (module == null) return;
        // 1.8.9 has no item buoyancy: light items rise in water and heavy ones sink slowly, fire-proof ones float on lava. The
        // tick's gravity (0.04) follows, so it is added back.
        // Like 26.x, an item floats about 0.1 deep.
        if (module.floating.get() && ItemPhysics189.depth((EntityItem) (Object) this, Material.water) > 0.1)
            motionY = ItemPhysics.buoyancy(motionY, ItemPhysics189.floats(stack)) + 0.04;
        else if (fireproof && ItemPhysics189.depth((EntityItem) (Object) this, Material.lava) > 0.1)
            motionY = ItemPhysics.buoyancy(motionY, true) + 0.04;
        if (!worldObj.isRemote) ItemPhysics189.ignite((EntityItem) (Object) this);
    }

    /** A fire-proof item on lava floats instead of being thrown up with a fizz every second. */
    @Redirect(method = "onUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getMaterial()Lnet/minecraft/block/material/Material;"),
        require = 1, allow = 1)
    private Material ladsNoLavaPop(Block block) {
        return ladsFireproof ? Material.air : block.getMaterial();
    }

    /**
     * Cactus spares items; a fire-proof item takes no fire damage (EntityItem.dealFireDamage, which lava and fire in its box call
     * every tick, skips the isImmuneToFire test that Entity's own has). On both sides: 1.8.9 hurts the client's copy too, which
     * would vanish while the server's lives on.
     */
    @Inject(method = "attackEntityFrom", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsSpared(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (ladsFireproof && source.isFireDamage()) { cir.setReturnValue(false); return; }
        ItemPhysicsModule module = source == DamageSource.cactus ? ItemPhysics189.rules(worldObj) : null;
        if (module != null && module.cactus.get()) cir.setReturnValue(false);
    }
}
