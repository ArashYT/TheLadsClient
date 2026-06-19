package com.thelads.core.mixin.auto.shulkerboxutils130;

import com.thelads.core.features.auto.shulkerboxutils.ShulkerBoxUtilsCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public class ShulkerInventoryMixin {
    @Unique
    private static final ThreadLocal<Boolean> thelads$spRendering = ThreadLocal.withInitial(() -> false);

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("TAIL"), require = 0)
    private void thelads$renderShulkerDecorations(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (thelads$spRendering.get()) {
            return;
        }
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (!(item instanceof BlockItem bi) || !(bi.getBlock() instanceof ShulkerBoxBlock)) {
            return;
        }

        GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        int filledSlots = 0;
        ItemStack firstItem = ItemStack.EMPTY;
        boolean uniform = true;
        Item singleType = null;

        if (contents != null) {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            contents.copyInto(items);
            for (ItemStack item2 : items) {
                if (item2.isEmpty()) continue;
                filledSlots++;
                if (firstItem.isEmpty()) {
                    firstItem = item2;
                }
                if (singleType == null) {
                    singleType = item2.getItem();
                } else if (singleType != item2.getItem()) {
                    uniform = false;
                }
            }
        }

        if (!firstItem.isEmpty()) {
            Matrix3x2fStack pose = self.pose();
            pose.pushMatrix();
            pose.translate(x + 4, y + 4);
            pose.scale(0.5f, 0.5f);
            thelads$spRendering.set(true);
            try {
                self.item(firstItem, 0, 0);
            } finally {
                thelads$spRendering.set(false);
                pose.popMatrix();
            }
        }

        if (filledSlots > 0) {
            float fillFraction = (float) filledSlots / 27.0f;
            float displayFraction = 1.0f - fillFraction;
            int barWidth = Math.max(1, Math.round(13.0f * displayFraction));
            int rgb = Mth.hsvToRgb(displayFraction / 3.0f, 1.0f, 1.0f);
            self.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
            self.fill(x + 2, y + 13, x + 2 + barWidth, y + 14, 0xFF000000 | rgb);
        }
    }
}
