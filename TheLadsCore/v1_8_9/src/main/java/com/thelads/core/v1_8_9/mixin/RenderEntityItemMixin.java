package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ItemPhysics;
import com.thelads.core.client.OldAnimations;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.v1_8_9.feature.ItemPhysics189;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.OldAnimations189.Hook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dropped items (OptiFine leaves RenderEntityItem alone). Item Physics, when on, owns the placement: func_177077_a's hover and spin
 * become the entity origin, and each copy doRender draws is laid down by ItemPhysics.place (its jitter and the flat stack's offset
 * go). Otherwise 1.7 Animations' 2D dropped items: a flat item's placement becomes 1.7's camera-facing icon, its spin is dropped
 * and doRender's GROUND display transform becomes none. Blocks and built-in models keep 1.8.9's.
 */
@Mixin(RenderEntityItem.class)
public abstract class RenderEntityItemMixin {
    /** The item being drawn is a flat icon placed by the recipe; set per entity in func_177077_a, which doRender calls first. */
    @Unique private boolean ladsFlat;
    /** Item Physics places this item; its pose, model extent and stack, set in func_177077_a. */
    @Unique private boolean ladsPhysics;
    @Unique private float ladsYaw, ladsPitch, ladsRaise, ladsCx, ladsCy, ladsCz, ladsHalfY, ladsHalfZ;
    @Unique private int ladsCopy, ladsCopies, ladsSeed;

    @Shadow protected abstract int func_177078_a(ItemStack stack);

    @Redirect(method = "func_177077_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V",
        ordinal = 0), require = 1, allow = 1)
    private void ladsPlace(float x, float y, float z, EntityItem item, double posX, double posY, double posZ, float partialTicks,
                           IBakedModel model) {
        ladsPhysics = ItemPhysics189.renders();
        if (ladsPhysics) {
            ladsFlat = false;
            GlStateManager.translate((float) posX, (float) posY, (float) posZ);
            physics(item, model, partialTicks);
            return;
        }
        ladsFlat = !model.isGui3d() && !model.isBuiltInRenderer() && OldAnimations189.active(Feature.DROPPED_2D);
        if (!ladsFlat) {
            GlStateManager.translate(x, y, z);
            return;
        }
        GlStateManager.translate((float) posX, (float) posY, (float) posZ);
        OldAnimations.droppedItem(OldAnimations189.GL, item.getAge() + partialTicks, item.hoverStart,
            Minecraft.getMinecraft().getRenderManager().playerViewY);
        OldAnimations189.fullSizeMesh();
        OldAnimations189.hit(Hook.DROP);
    }

    /**
     * The model's extent where doRender draws it: blocks are halved first, then the GROUND transform (translation, scale) and
     * RenderItem's half-size unit cube; a flat item's mesh is 1/16 deep.
     */
    @Unique
    private void physics(EntityItem item, IBakedModel model, float partialTicks) {
        ItemPhysics.Tumble tumble = ((ItemPhysics.Holder) item).lads$tumble();
        boolean flat = !model.isGui3d();
        tumble.rest = flat ? 180 : 90;
        ladsYaw = tumble.yaw(partialTicks);
        ladsPitch = tumble.pitch(partialTicks);
        ladsRaise = tumble.raise(partialTicks);
        ItemTransformVec3f ground = model.getItemCameraTransforms().getTransform(TransformType.GROUND);
        float k = flat ? 1 : 0.5f;
        ladsCx = k * ground.translation.x;
        ladsCy = k * ground.translation.y;
        ladsCz = k * ground.translation.z;
        ladsHalfY = k * 0.25f * ground.scale.y;
        ladsHalfZ = flat ? ground.scale.z / 64 : k * 0.25f * ground.scale.z;
        ItemStack stack = item.getEntityItem();
        ladsCopies = func_177078_a(stack);
        ladsCopy = 0;
        ladsSeed = Item.getIdFromItem(stack.getItem()) + stack.getMetadata();
    }

    @Redirect(method = "func_177077_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V"),
        require = 1, allow = 1)
    private void ladsSpin(float angle, float x, float y, float z) {
        if (!ladsFlat && !ladsPhysics) GlStateManager.rotate(angle, x, y, z);
    }

    /** A flat stack's centring offset: Item Physics stacks the copies itself. */
    @Redirect(method = "func_177077_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V",
        ordinal = 1), require = 1, allow = 1)
    private void ladsStackOffset(float x, float y, float z) {
        if (!ladsPhysics) GlStateManager.translate(x, y, z);
    }

    /** Each copy's own matrix (doRender's push inside its loop): Item Physics lays this copy down. */
    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlStateManager;pushMatrix()V", ordinal = 1, shift = At.Shift.AFTER), require = 1)
    private void ladsPlaceCopy(EntityItem item, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        if (ladsPhysics) ItemPhysics.place(OldAnimations189.GL, ladsYaw, ladsPitch, ladsRaise, ladsCx, ladsCy, ladsCz, ladsHalfY, ladsHalfZ,
            ladsCopy++, ladsCopies, ladsSeed);
    }

    /** vanilla's random jitter of copies 2 to 5: Item Physics spreads them itself. */
    @Redirect(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V"), require = 1, allow = 1)
    private void ladsCopyJitter(float x, float y, float z) {
        if (!ladsPhysics) GlStateManager.translate(x, y, z);
    }

    @ModifyArg(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At(value = "INVOKE", remap = false,
        target = "Lnet/minecraftforge/client/ForgeHooksClient;handleCameraTransforms(Lnet/minecraft/client/resources/model/IBakedModel;"
            + "Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)Lnet/minecraft/client/resources/model/IBakedModel;"),
        index = 1, require = 1, allow = 1)
    private TransformType ladsNoGroundTransform(TransformType type) {
        return ladsFlat ? TransformType.NONE : type;
    }
}
