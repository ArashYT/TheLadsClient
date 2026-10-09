package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.client.ItemPhysics;
import com.thelads.core.modules.ItemPhysicsModule;
import com.thelads.core.v1_20_1.game.misc.NativeItemPhysics1201;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item Physics: each dropped item's tumble (client) and the singleplayer rules (NativeItemPhysics1201.rules decides where). */
@Mixin(ItemEntity.class)
public abstract class ItemPhysicsEntityMixin implements ItemPhysics.Holder {
    @Shadow private int age;
    @Unique private ItemPhysics.Tumble lads$tumble;

    @Override
    public ItemPhysics.Tumble lads$tumble() {
        if (lads$tumble == null) lads$tumble = new ItemPhysics.Tumble(lads$self().bobOffs * Mth.RAD_TO_DEG);
        return lads$tumble;
    }

    @Unique
    private ItemEntity lads$self() { return (ItemEntity) (Object) this; }

    /** Despawn time: a fresh drop starts older or younger than 0, so vanilla's 6000-tick despawn comes at the chosen minute. */
    @Inject(method = {"<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V",
        "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;DDD)V"}, at = @At("TAIL"), require = 2)
    private void lads$despawnTime(CallbackInfo callback) {
        ItemPhysicsModule module = lads$self().level() instanceof ServerLevel level ? NativeItemPhysics1201.rules(level) : null;
        if (module != null) age = 6000 - ItemPhysics.despawnTicks(module.despawn.getValue());
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 1)
    private void lads$tick(CallbackInfo callback) {
        if (lads$self().level().isClientSide()) NativeItemPhysics1201.clientTick(lads$self());
        else NativeItemPhysics1201.ignite(lads$self());
    }

    /** Only flammable items burn: the rest are fire-proof, so fire and lava neither light nor hurt them (they float on lava). */
    @Inject(method = "fireImmune", at = @At("RETURN"), cancellable = true, require = 1)
    private void lads$fireproof(CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) return;
        ItemPhysicsModule module = NativeItemPhysics1201.rules(lads$self().level());
        if (module != null && module.fireproof.get() && !NativeItemPhysics1201.burns(lads$self())) callback.setReturnValue(true);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$cactus(DamageSource source, float damage, CallbackInfoReturnable<Boolean> callback) {
        ItemPhysicsModule module = NativeItemPhysics1201.rules(lads$self().level());
        if (module != null && module.cactus.get() && source.is(DamageTypes.CACTUS)) callback.setReturnValue(false);
    }

    /** Vanilla lifts every item in water, slowly; light ones rise to the surface and heavy ones sink instead. */
    @Inject(method = "setUnderwaterMovement", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$buoyancy(CallbackInfo callback) {
        ItemPhysicsModule module = NativeItemPhysics1201.rules(lads$self().level());
        if (module == null || !module.floating.get()) return;
        Vec3 movement = lads$self().getDeltaMovement();
        lads$self().setDeltaMovement(movement.x * 0.99, ItemPhysics.buoyancy(movement.y, NativeItemPhysics1201.floats(lads$self())), movement.z * 0.99);
        callback.cancel();
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$rightClickPickup(Player player, CallbackInfo callback) {
        if (NativeItemPhysics1201.noAutoPickup(lads$self(), player)) callback.cancel();
    }
}
