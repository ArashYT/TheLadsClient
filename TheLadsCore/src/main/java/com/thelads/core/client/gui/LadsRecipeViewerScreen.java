package com.thelads.core.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.fabricmc.fabric.api.recipe.v1.FabricRecipeAccess;
import net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes;
import java.util.ArrayList;
import java.util.List;

public class LadsRecipeViewerScreen extends Screen {
    private final Screen parent;
    private final ItemStack targetStack;
    private final List<RecipeHolder<?>> matchingRecipes = new ArrayList<>();
    private int recipeIndex = 0;

    public LadsRecipeViewerScreen(Screen parent, ItemStack targetStack) {
        super(Component.literal("Recipe Viewer"));
        this.parent = parent;
        this.targetStack = targetStack;
    }

    @Override
    protected void init() {
        super.init();
        matchingRecipes.clear();
        recipeIndex = 0;
        if (this.minecraft != null && this.minecraft.getConnection() != null && this.minecraft.level != null) {
            net.minecraft.world.item.crafting.RecipeAccess recipes = this.minecraft.getConnection().recipes();
            if (recipes instanceof FabricRecipeAccess fra) {
                SynchronizedRecipes sr = fra.getSynchronizedRecipes();
                if (sr != null) {
                    ContextMap context = SlotDisplayContext.fromLevel(this.minecraft.level);
                    for (RecipeHolder<?> holder : sr.recipes()) {
                        Recipe<?> recipe = holder.value();
                        List<RecipeDisplay> displays = recipe.display();
                        if (displays != null && !displays.isEmpty()) {
                            SlotDisplay resultSlot = displays.get(0).result();
                            if (resultSlot != null) {
                                ItemStack result = resultSlot.resolveForFirstStack(context);
                                if (result != null && result.getItem() == targetStack.getItem()) {
                                    matchingRecipes.add(holder);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void drawLadsButton(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        int bg = hover ? 0xCC2A1010 : 0xCC180A0A;
        int border = hover ? 0xFFFF5252 : 0x22FF5555;
        
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        
        int textColor = hover ? 0xFFFFFFFF : 0xFFCCCCCC;
        g.centeredText(this.font, label, x + w / 2, y + h / 2 - 4, textColor);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Background is drawn in extractRenderState
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xEE050508);
        g.fill(0, 0, this.width, 2, 0xFFD32F2F);

        int cx = this.width / 2;
        g.centeredText(this.font, "Recipe Viewer", cx, 15, 0xFFFF5252);

        // Draw target item details
        g.item(targetStack, cx - 8, 30);
        g.centeredText(this.font, targetStack.getHoverName().getString(), cx, 50, 0xFFFFFFFF);

        ItemStack hoveredStack = null;

        if (!matchingRecipes.isEmpty()) {
            RecipeHolder<?> holder = matchingRecipes.get(recipeIndex);
            Recipe<?> recipe = holder.value();
            
            List<RecipeDisplay> displays = recipe.display();
            if (displays != null && !displays.isEmpty()) {
                RecipeDisplay display = displays.get(0);
                ContextMap context = SlotDisplayContext.fromLevel(this.minecraft.level);
                ItemStack result = display.result().resolveForFirstStack(context);

                int gridX = cx - 54;
                int gridY = this.height / 2 - 30;

                // Draw 3x3 background slots
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        int slotX = gridX + c * 18;
                        int slotY = gridY + r * 18;
                        g.fill(slotX, slotY, slotX + 16, slotY + 16, 0x22FFFFFF);
                    }
                }

                // Draw ingredients
                if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                    int w = shaped.width();
                    int h = shaped.height();
                    List<SlotDisplay> ingredients = shaped.ingredients();
                    for (int row = 0; row < h; row++) {
                        for (int col = 0; col < w; col++) {
                            int idx = row * w + col;
                            if (idx < ingredients.size()) {
                                SlotDisplay ing = ingredients.get(idx);
                                if (ing != null) {
                                    ItemStack item = ing.resolveForFirstStack(context);
                                    if (item != null && !item.isEmpty()) {
                                        int slotX = gridX + col * 18;
                                        int slotY = gridY + row * 18;
                                        g.item(item, slotX, slotY);
                                        if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                                            hoveredStack = item;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    List<SlotDisplay> ingredients = null;
                    if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                        ingredients = shapeless.ingredients();
                    } else if (display instanceof FurnaceRecipeDisplay cooking) {
                        ingredients = List.of(cooking.ingredient(), cooking.fuel());
                    } else if (display instanceof StonecutterRecipeDisplay cutting) {
                        ingredients = List.of(cutting.input());
                    }

                    if (ingredients != null) {
                        for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
                            SlotDisplay ing = ingredients.get(i);
                            if (ing != null) {
                                ItemStack item = ing.resolveForFirstStack(context);
                                if (item != null && !item.isEmpty()) {
                                    int col = i % 3;
                                    int row = i / 3;
                                    int slotX = gridX + col * 18;
                                    int slotY = gridY + row * 18;
                                    g.item(item, slotX, slotY);
                                    if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                                        hoveredStack = item;
                                    }
                                }
                            }
                        }
                    }
                }

                // Draw Arrow and Output Slot
                g.centeredText(this.font, "➔", cx + 22, gridY + 22, 0xFFFFFFFF);

                int outX = cx + 45;
                int outY = gridY + 18;
                g.fill(outX - 1, outY - 1, outX + 17, outY + 17, 0x44FFFFFF);
                g.item(result, outX, outY);
                if (mouseX >= outX && mouseX < outX + 16 && mouseY >= outY && mouseY < outY + 16) {
                    hoveredStack = result;
                }

                // Draw recipe type/ID
                String typeName = recipe.getType().toString();
                g.centeredText(this.font, "Type: " + typeName.substring(typeName.indexOf(':') + 1), cx, gridY + 60, 0xFFAAAAAA);

                // Pagination
                if (matchingRecipes.size() > 1) {
                    g.centeredText(this.font, (recipeIndex + 1) + " / " + matchingRecipes.size(), cx, gridY + 75, 0xFFCCCCCC);
                    
                    boolean canPrev = recipeIndex > 0;
                    boolean canNext = recipeIndex < matchingRecipes.size() - 1;
                    
                    g.text(this.font, "◀", cx - 40, gridY + 75, canPrev ? 0xFFFFFFFF : 0x55FFFFFF);
                    g.text(this.font, "▶", cx + 30, gridY + 75, canNext ? 0xFFFFFFFF : 0x55FFFFFF);
                }
            } else {
                g.centeredText(this.font, "No recipes found for this item.", cx, this.height / 2, 0xFFAAAAAA);
            }
        } else {
            g.centeredText(this.font, "No recipes found for this item.", cx, this.height / 2, 0xFFAAAAAA);
        }

        // Draw Back Button
        drawLadsButton(g, "Back", cx - 40, this.height / 2 + 55, 80, 20, mouseX, mouseY);

        // Tooltip for hovered item
        if (hoveredStack != null && !hoveredStack.isEmpty()) {
            g.setTooltipForNextFrame(this.font, hoveredStack, mouseX, mouseY);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        double mx = event.x(), my = event.y();
        if (event.button() != 0) return super.mouseClicked(event, isDouble);

        int cx = this.width / 2;
        int gridY = this.height / 2 - 30;

        // Back button
        int backX = cx - 40, backY = this.height / 2 + 55, backW = 80, backH = 20;
        if (mx >= backX && mx < backX + backW && my >= backY && my < backY + backH) {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(parent);
            }
            return true;
        }

        // Pagination buttons
        if (matchingRecipes.size() > 1) {
            // Left Arrow: cx - 40
            if (mx >= cx - 45 && mx < cx - 25 && my >= gridY + 70 && my < gridY + 85) {
                if (recipeIndex > 0) {
                    recipeIndex--;
                    return true;
                }
            }
            // Right Arrow: cx + 30
            if (mx >= cx + 25 && mx < cx + 45 && my >= gridY + 70 && my < gridY + 85) {
                if (recipeIndex < matchingRecipes.size() - 1) {
                    recipeIndex++;
                    return true;
                }
            }
        }

        // Click on ingredients to view recipes recursively
        if (!matchingRecipes.isEmpty()) {
            RecipeHolder<?> holder = matchingRecipes.get(recipeIndex);
            Recipe<?> recipe = holder.value();

            List<RecipeDisplay> displays = recipe.display();
            if (displays != null && !displays.isEmpty()) {
                RecipeDisplay display = displays.get(0);
                ContextMap context = SlotDisplayContext.fromLevel(this.minecraft.level);

                // Output slot click
                int outX = cx + 45;
                int outY = gridY + 18;
                if (mx >= outX && mx < outX + 16 && my >= outY && my < outY + 16) {
                    ItemStack result = display.result().resolveForFirstStack(context);
                    if (result != null && !result.isEmpty() && this.minecraft != null) {
                        this.minecraft.setScreenAndShow(new LadsRecipeViewerScreen(this, result));
                        return true;
                    }
                }

                // Input slots click
                int gridX = cx - 54;
                if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                    int w = shaped.width();
                    int h = shaped.height();
                    List<SlotDisplay> ingredients = shaped.ingredients();
                    for (int row = 0; row < h; row++) {
                        for (int col = 0; col < w; col++) {
                            int idx = row * w + col;
                            if (idx < ingredients.size()) {
                                SlotDisplay ing = ingredients.get(idx);
                                if (ing != null) {
                                    ItemStack item = ing.resolveForFirstStack(context);
                                    if (item != null && !item.isEmpty() && this.minecraft != null) {
                                        int slotX = gridX + col * 18;
                                        int slotY = gridY + row * 18;
                                        if (mx >= slotX && mx < slotX + 16 && my >= slotY && my < slotY + 16) {
                                            this.minecraft.setScreenAndShow(new LadsRecipeViewerScreen(this, item));
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    List<SlotDisplay> ingredients = null;
                    if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                        ingredients = shapeless.ingredients();
                    } else if (display instanceof FurnaceRecipeDisplay cooking) {
                        ingredients = List.of(cooking.ingredient(), cooking.fuel());
                    } else if (display instanceof StonecutterRecipeDisplay cutting) {
                        ingredients = List.of(cutting.input());
                    }

                    if (ingredients != null) {
                        for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
                            SlotDisplay ing = ingredients.get(i);
                            if (ing != null) {
                                ItemStack item = ing.resolveForFirstStack(context);
                                if (item != null && !item.isEmpty() && this.minecraft != null) {
                                    int col = i % 3;
                                    int row = i / 3;
                                    int slotX = gridX + col * 18;
                                    int slotY = gridY + row * 18;
                                    if (mx >= slotX && mx < slotX + 16 && my >= slotY && my < slotY + 16) {
                                        this.minecraft.setScreenAndShow(new LadsRecipeViewerScreen(this, item));
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return super.mouseClicked(event, isDouble);
    }
}
