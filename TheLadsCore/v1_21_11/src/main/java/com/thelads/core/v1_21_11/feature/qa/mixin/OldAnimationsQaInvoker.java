package com.thelads.core.v1_21_11.feature.qa.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** One first-person hand through the real renderArmWithItem, with every mod's transformations (1.7 Animations probe). */
@Mixin(ItemInHandRenderer.class)
public interface OldAnimationsQaInvoker {
    @Invoker("renderArmWithItem") void ladsQaArm(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                                                 ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light);
}
