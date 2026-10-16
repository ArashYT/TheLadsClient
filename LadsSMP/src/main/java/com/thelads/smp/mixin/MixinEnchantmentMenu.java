package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.features.EnchantmentMenuSlotsAccessor;
import com.thelads.smp.features.StickyLapisManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consolidates 'stickyenchantinglapis': Restores and persists Lapis Lazuli in EnchantmentMenu.
 */
@Mixin(EnchantmentMenu.class)
public abstract class MixinEnchantmentMenu implements EnchantmentMenuSlotsAccessor {

    @Shadow
    @Final
    private Container enchantSlots;

    @Unique
    @Nullable
    private Player ladssmp$player;

    @Override
    public Container ladssmp$getEnchantSlots() {
        return this.enchantSlots;
    }

    @Inject(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("TAIL")
    )
    private void ladssmp$onConstructMenu(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        if (!LadsConfig.get().features.stickyEnchantingLapis) return;
        this.ladssmp$player = inventory.player;
        EnchantingTableBlockEntity be = StickyLapisManager.getEnchantingTableBlockEntity(this.ladssmp$player);
        if (be != null) {
            int lapisCount = StickyLapisManager.getLapisCount(be);
            if (lapisCount > 0) {
                this.enchantSlots.setItem(1, new ItemStack(Items.LAPIS_LAZULI, lapisCount));
            }
        }
    }

    @Inject(method = "slotsChanged(Lnet/minecraft/world/Container;)V", at = @At("TAIL"))
    private void ladssmp$onSlotsChanged(Container container, CallbackInfo ci) {
        if (!LadsConfig.get().features.stickyEnchantingLapis || this.ladssmp$player == null) return;

        int lapisCount = 0;
        ItemStack stack = this.enchantSlots.getItem(1);
        if (stack.is(Items.LAPIS_LAZULI)) {
            lapisCount = stack.getCount();
        }

        EnchantingTableBlockEntity be = StickyLapisManager.getEnchantingTableBlockEntity(this.ladssmp$player);
        if (be != null) {
            Level level = this.ladssmp$player.level();
            if (lapisCount == StickyLapisManager.getLapisCount(be)) {
                return;
            }
            StickyLapisManager.saveLapisCount(level, be, lapisCount);

            if (!level.isClientSide() && level.getServer() != null) {
                BlockPos tablePos = be.getBlockPos();
                for (Player other : level.getServer().getPlayerList().getPlayers()) {
                    if (other.getUUID().equals(this.ladssmp$player.getUUID())) continue;
                    BlockPos otherPos = StickyLapisManager.getLastInteraction(other.getUUID());
                    if (tablePos.equals(otherPos) && other.containerMenu instanceof EnchantmentMenu) {
                        other.containerMenu.getSlot(1).set(
                                lapisCount > 0 ? new ItemStack(Items.LAPIS_LAZULI, lapisCount) : ItemStack.EMPTY
                        );
                    }
                }
            }
        }
    }

    @Inject(method = "removed(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"))
    private void ladssmp$onRemoved(Player player, CallbackInfo ci) {
        this.ladssmp$player = null;
    }
}
