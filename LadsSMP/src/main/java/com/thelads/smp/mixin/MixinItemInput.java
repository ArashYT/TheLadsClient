package com.thelads.smp.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pathway 6: Prevents disabled items from being spawned via /give, /item, or other commands using ItemInput.
 */
@Mixin(ItemInput.class)
public abstract class MixinItemInput {
    @Shadow
    @Final
    private Holder<Item> item;

    @Unique
    private static final DynamicCommandExceptionType ERROR_ITEM_DISABLED = new DynamicCommandExceptionType(
            id -> Component.literal("[Lads SMP] Item '" + id + "' is disabled on this server and cannot be spawned.")
    );

    @Inject(method = "createItemStack", at = @At("HEAD"), cancellable = true)
    private void ladssmp$blockDisabledCommandSpawn(int count, CallbackInfoReturnable<ItemStack> cir) throws CommandSyntaxException {
        if (!ItemDisablerSystem.getConfig().allowAdminBypass && this.item != null) {
            Item rawItem = this.item.value();
            if (ItemDisablerSystem.isDisabled(rawItem)) {
                String idStr = this.item.unwrapKey().map(k -> k.identifier().toString()).orElse(rawItem.toString());
                ItemDisablerSystem.writeAuditLog("Blocked command creation of disabled item " + idStr + " (count=" + count + ")");
                throw ERROR_ITEM_DISABLED.create(idStr);
            }
        }
    }
}
