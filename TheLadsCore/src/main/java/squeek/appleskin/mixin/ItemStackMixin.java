/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item$TooltipContext
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.TooltipFlag
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package squeek.appleskin.mixin;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import squeek.appleskin.client.TooltipOverlayHandler;

@Mixin(value={ItemStack.class})
public class ItemStackMixin {
    @Inject(at={@At(value="RETURN")}, method={"getTooltipLines"})
    private void getTooltipFromItem(Item.TooltipContext context, Player player, TooltipFlag type, CallbackInfoReturnable<List<Component>> info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("AppleSkin").isEnabled()) {
            return;
        }
        if (TooltipOverlayHandler.INSTANCE != null) {
            TooltipOverlayHandler.INSTANCE.onItemTooltip((ItemStack)(Object)this, player, context, type, (List)info.getReturnValue());
        }
    }
}

