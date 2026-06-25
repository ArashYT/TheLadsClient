package com.thelads.core.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class LadsJeiContainerScreenMixin extends Screen {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    protected int imageWidth;
    @Shadow
    protected int imageHeight;

    @Unique
    private EditBox ladsSearchBox;
    @Unique
    private int ladsSearchPage = 0;
    @Unique
    private final List<Item> ladsFilteredItems = new ArrayList<>();
    @Unique
    private String ladsLastSearchText = "";

    protected LadsJeiContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("JEI (Just Enough Items)").isEnabled()) {
            this.ladsSearchBox = null;
            return;
        }
        this.ladsSearchBox = new EditBox(this.font, leftPos + imageWidth + 10, topPos + 10, 80, 16, Component.literal("Search..."));
        this.addRenderableWidget(this.ladsSearchBox);
        ladsUpdateFilteredItems("");
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (this.ladsSearchBox != null) {
            String text = this.ladsSearchBox.getValue();
            if (!text.equals(ladsLastSearchText)) {
                ladsLastSearchText = text;
                ladsSearchPage = 0;
                ladsUpdateFilteredItems(text);
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("JEI (Just Enough Items)").isEnabled() || this.ladsSearchBox == null) {
            return;
        }

        var jei = com.thelads.core.config.ModuleManager.getInstance().getModule("JEI (Just Enough Items)");
        var centerOpt = jei != null ? jei.getOption("Center Search Bar") : null;
        boolean centerSearch = centerOpt instanceof com.thelads.core.config.BoolOption bo && bo.get();
        if (centerSearch) {
            this.ladsSearchBox.setX(this.width / 2 - 40);
            this.ladsSearchBox.setY(this.height - 24);
            g.text(this.font, "JEI Search", this.width / 2 - 40, this.height - 35, 0xFFFF5555);
        } else {
            this.ladsSearchBox.setX(leftPos + imageWidth + 10);
            this.ladsSearchBox.setY(topPos + 10);
            g.text(this.font, "JEI Search", leftPos + imageWidth + 10, topPos - 4, 0xFFFF5555);
        }

        int startX = leftPos + imageWidth + 10;
        int startY = topPos + 32;
        int columns = 5;
        int rows = 8;
        int itemSpacing = 18;

        int startIndex = ladsSearchPage * (columns * rows);
        int endIndex = Math.min(startIndex + (columns * rows), ladsFilteredItems.size());

        Item hoveredItem = null;

        for (int i = startIndex; i < endIndex; i++) {
            int index = i - startIndex;
            int col = index % columns;
            int row = index / columns;
            int x = startX + col * itemSpacing;
            int y = startY + row * itemSpacing;

            Item item = ladsFilteredItems.get(i);
            ItemStack stack = item.getDefaultInstance();

            g.fill(x, y, x + 16, y + 16, 0x22FFFFFF);
            g.item(stack, x, y);

            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                hoveredItem = item;
                g.fill(x, y, x + 16, y + 16, 0x44FFFFFF);
            }
        }

        int pageY = startY + rows * itemSpacing + 5;
        g.text(this.font, "< Prev", startX, pageY, ladsSearchPage > 0 ? 0xFFFFFFFF : 0x55FFFFFF);
        g.text(this.font, "Next >", startX + 50, pageY, (ladsSearchPage + 1) * (columns * rows) < ladsFilteredItems.size() ? 0xFFFFFFFF : 0x55FFFFFF);

        if (hoveredItem != null) {
            var showIdsOpt = jei != null ? jei.getOption("Show Item IDs") : null;
            boolean showIds = showIdsOpt instanceof com.thelads.core.config.BoolOption bo && bo.get();
            ItemStack stack = hoveredItem.getDefaultInstance();
            if (showIds) {
                var id = BuiltInRegistries.ITEM.getKey(hoveredItem);
                if (id != null) {
                    List<net.minecraft.util.FormattedCharSequence> tooltip = new ArrayList<>();
                    tooltip.add(stack.getHoverName().getVisualOrderText());
                    tooltip.add(Component.literal(id.toString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY).getVisualOrderText());
                    g.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
                } else {
                    g.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
                }
            } else {
                g.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean isDouble, CallbackInfoReturnable<Boolean> cir) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("JEI (Just Enough Items)").isEnabled() || this.ladsSearchBox == null) {
            return;
        }

        double mx = event.x();
        double my = event.y();

        int startX = leftPos + imageWidth + 10;
        int startY = topPos + 32;
        int columns = 5;
        int rows = 8;
        int itemSpacing = 18;
        int pageY = startY + rows * itemSpacing + 5;

        if (mx >= startX && mx < startX + 40 && my >= pageY && my < pageY + 12) {
            if (ladsSearchPage > 0) {
                ladsSearchPage--;
                cir.setReturnValue(true);
            }
            return;
        }

        if (mx >= startX + 50 && mx < startX + 90 && my >= pageY && my < pageY + 12) {
            if ((ladsSearchPage + 1) * (columns * rows) < ladsFilteredItems.size()) {
                ladsSearchPage++;
                cir.setReturnValue(true);
            }
            return;
        }

        int startIndex = ladsSearchPage * (columns * rows);
        int endIndex = Math.min(startIndex + (columns * rows), ladsFilteredItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            int index = i - startIndex;
            int col = index % columns;
            int row = index / columns;
            int x = startX + col * itemSpacing;
            int y = startY + row * itemSpacing;

            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                Item item = ladsFilteredItems.get(i);
                var jei = com.thelads.core.config.ModuleManager.getInstance().getModule("JEI (Just Enough Items)");
                var showRecipesOpt = jei != null ? jei.getOption("Show Recipes") : null;
                boolean showRecipes = showRecipesOpt instanceof com.thelads.core.config.BoolOption bo ? bo.get() : true;
                var cheatOpt = jei != null ? jei.getOption("Cheat Mode") : null;
                boolean cheatMode = cheatOpt instanceof com.thelads.core.config.BoolOption bo && bo.get();

                if (cheatMode && this.minecraft.player != null) {
                    var id = BuiltInRegistries.ITEM.getKey(item);
                    if (id != null) {
                        this.minecraft.player.connection.sendCommand("give @s " + id.toString() + " 64");
                    }
                    cir.setReturnValue(true);
                    return;
                } else if (showRecipes) {
                    this.minecraft.setScreenAndShow(new com.thelads.core.client.gui.LadsRecipeViewerScreen(this, item.getDefaultInstance()));
                    cir.setReturnValue(true);
                    return;
                } else {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.ladsSearchBox != null && this.ladsSearchBox.isFocused()) {
            int keyCode = getEventKeyCode(event);
            if (keyCode == 256) {
                this.ladsSearchBox.setFocused(false);
                cir.setReturnValue(true);
                return;
            }
            if (this.ladsSearchBox.keyPressed(event)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Unique
    private int getEventKeyCode(KeyEvent event) {
        try {
            int keyCode = 0;
            java.util.List<java.lang.reflect.Field> intFields = new java.util.ArrayList<>();
            for (java.lang.reflect.Field f : event.getClass().getDeclaredFields()) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    intFields.add(f);
                }
            }
            if (!intFields.isEmpty()) {
                keyCode = intFields.get(0).getInt(event);
            }
            for (java.lang.reflect.Field f : event.getClass().getDeclaredFields()) {
                if (f.getType() == int.class) {
                    String name = f.getName().toLowerCase();
                    if (name.contains("keycode") || name.equals("key")) {
                        keyCode = f.getInt(event);
                    }
                }
            }
            return keyCode;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Unique
    private void ladsUpdateFilteredItems(String query) {
        ladsFilteredItems.clear();
        String lowerQuery = query.toLowerCase();
        for (Item item : BuiltInRegistries.ITEM) {
            String name = item.getDefaultInstance().getHoverName().getString().toLowerCase();
            if (name.contains(lowerQuery)) {
                ladsFilteredItems.add(item);
            }
        }
    }
}
