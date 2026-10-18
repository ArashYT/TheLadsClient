package com.thelads.smp.features.arise.nb9.mixin;

import com.thelads.smp.features.arise.nb9.AriseNB9Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin on LecternBlock to display book title and author as action-bar feedback on interaction.
 */
@Mixin(LecternBlock.class)
public abstract class MixinLecternBlock {

    @Inject(method = "useWithoutItem", at = @At("HEAD"))
    private void ladssmp$displayBookTitle(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!AriseNB9Config.get().lecternBookTitle) return;

        if (level.getBlockEntity(pos) instanceof LecternBlockEntity lectern && lectern.hasBook()) {
            ItemStack book = lectern.getBook();
            if (book.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
                if (content != null) {
                    String title = content.title().raw();
                    String author = content.author();
                    player.sendOverlayMessage(Component.literal("📖 \"" + title + "\" by " + author));
                }
            } else if (book.is(Items.WRITABLE_BOOK)) {
                player.sendOverlayMessage(Component.literal("📖 Book & Quill"));
            }
        }
    }
}
