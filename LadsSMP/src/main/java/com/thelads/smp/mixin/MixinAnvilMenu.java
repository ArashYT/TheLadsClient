package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consolidates 'one-level-renaming': Caps pure item renaming in an Anvil to 1 XP level.
 */
@Mixin(AnvilMenu.class)
public abstract class MixinAnvilMenu extends ItemCombinerMenu {

    @Shadow
    @Final
    private DataSlot cost;

    @Shadow
    @Nullable
    private String itemName;

    protected MixinAnvilMenu(@Nullable MenuType<?> menuType, int containerId, Inventory playerInventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slotDefinition) {
        super(menuType, containerId, playerInventory, access, slotDefinition);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void ladssmp$capRenameCostToOneLevel(CallbackInfo ci) {
        if (!LadsConfig.get().features.oneLevelRenaming) return;

        ItemStack input = this.inputSlots.getItem(0);
        ItemStack addition = this.inputSlots.getItem(1);
        ItemStack output = this.resultSlots.getItem(0);
        if (addition.isEmpty() && !input.isEmpty() && !output.isEmpty()) {
            boolean renamed = (!StringUtil.isBlank(this.itemName) && !this.itemName.equals(input.getHoverName().getString()))
                    || (StringUtil.isBlank(this.itemName) && input.has(DataComponents.CUSTOM_NAME));
            if (renamed && this.cost.get() > 0) {
                this.cost.set(1);
            }
        }
    }
}
