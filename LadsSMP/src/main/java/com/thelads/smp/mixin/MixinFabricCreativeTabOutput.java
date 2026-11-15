package com.thelads.smp.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

/**
 * Fabric API's creative-tab output (fabric-creative-tab-api-v1) copies every tab into a LinkedList and then finds the anchor of
 * insertAfter/insertBefore with {@code list.get(i)} in a loop, which is O(n) per step on a LinkedList, so each call costs O(n^2).
 * Bits and Balance calls it thousands of times for its ~5700 slab/step/vertical variants, so building the creative tabs froze the
 * client for 20 s on a fast PC and 70 s on a slow one: TCD Commons rebuilds all tabs inside the login packet, JEI rebuilds them again
 * on start, and the player sat on a frozen loading screen right after the first chunk arrived. The same scan with a ListIterator is O(n)
 * per call and gives the same result.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput", remap = false)
public class MixinFabricCreativeTabOutput {
    @Inject(method = "insertAfter(Lnet/minecraft/world/level/ItemLike;Ljava/util/Collection;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$insertAfterItem(ItemLike anchor, Collection<ItemStack> add, List<ItemStack> list, CallbackInfo ci) {
        var item = anchor.asItem();
        after(s -> s.is(item), add, list);
        ci.cancel();
    }

    @Inject(method = "insertAfter(Lnet/minecraft/world/item/ItemStack;Ljava/util/Collection;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$insertAfterStack(ItemStack anchor, Collection<ItemStack> add, List<ItemStack> list, CallbackInfo ci) {
        after(s -> ItemStack.isSameItemSameComponents(anchor, s), add, list);
        ci.cancel();
    }

    @Inject(method = "insertAfter(Ljava/util/function/Predicate;Ljava/util/Collection;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$insertAfterPredicate(Predicate<ItemStack> anchor, Collection<ItemStack> add, List<ItemStack> list, CallbackInfo ci) {
        after(anchor, add, list);
        ci.cancel();
    }

    @Inject(method = "insertBefore(Lnet/minecraft/world/level/ItemLike;Ljava/util/Collection;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$insertBeforeItem(ItemLike anchor, Collection<ItemStack> add, List<ItemStack> list, CallbackInfo ci) {
        var item = anchor.asItem();
        before(s -> s.is(item), add, list);
        ci.cancel();
    }

    @Inject(method = "insertBefore(Lnet/minecraft/world/item/ItemStack;Ljava/util/Collection;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$insertBeforeStack(ItemStack anchor, Collection<ItemStack> add, List<ItemStack> list, CallbackInfo ci) {
        before(s -> ItemStack.isSameItemSameComponents(anchor, s), add, list);
        ci.cancel();
    }

    @Inject(method = "insertBefore(Ljava/util/function/Predicate;Ljava/util/Collection;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$insertBeforePredicate(Predicate<ItemStack> anchor, Collection<ItemStack> add, List<ItemStack> list, CallbackInfo ci) {
        before(anchor, add, list);
        ci.cancel();
    }

    private static void check(Collection<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) throw new IllegalArgumentException("Cannot add empty stack");
            if (stack.getCount() != 1) throw new IllegalArgumentException("Stack size must be exactly 1 for stack: " + stack);
        }
    }

    /** Insert behind the LAST stack that matches (scan from the end), or append. */
    private static void after(Predicate<ItemStack> anchor, Collection<ItemStack> add, List<ItemStack> list) {
        check(add);
        ListIterator<ItemStack> it = list.listIterator(list.size());
        while (it.hasPrevious()) {
            if (anchor.test(it.previous())) {
                it.next();
                for (ItemStack s : add) it.add(s);
                return;
            }
        }
        list.addAll(add);
    }

    /** Insert in front of the FIRST stack that matches, or append. */
    private static void before(Predicate<ItemStack> anchor, Collection<ItemStack> add, List<ItemStack> list) {
        check(add);
        ListIterator<ItemStack> it = list.listIterator();
        while (it.hasNext()) {
            if (anchor.test(it.next())) {
                it.previous();
                for (ItemStack s : add) it.add(s);
                return;
            }
        }
        list.addAll(add);
    }
}
