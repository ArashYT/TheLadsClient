package com.thelads.smp.features.arise.nb1;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Useful Clipboard item: stores notes, opens an editable UI, and synchronizes with OS clipboard.
 */
public class ClipboardItem extends Item {

    public ClipboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() && UsefulClipboardFeature.CLIENT_OPEN_SCREEN != null) {
            UsefulClipboardFeature.CLIENT_OPEN_SCREEN.accept(hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipOutput, TooltipFlag flag) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        String text = tag.getStringOr("ClipboardText", "");
        if (text.isEmpty()) {
            tooltipOutput.accept(Component.literal("Empty clipboard (Right-click to write notes)").withStyle(ChatFormatting.GRAY));
        } else {
            String preview = text.length() > 40 ? text.substring(0, 37) + "..." : text;
            tooltipOutput.accept(Component.literal("Notes: " + preview).withStyle(ChatFormatting.AQUA));
            int lines = text.split("\r\n|\r|\n").length;
            tooltipOutput.accept(Component.literal(lines + " line(s), " + text.length() + " char(s)").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
