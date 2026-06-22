package com.thelads.core.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LadsSettingsScreen extends Screen {
    private final Screen parent;
    private static String currentTab = "MODS";
    private static String currentCategory = "ALL";
    public static double scrollOffset = 0;
    private static double targetScrollOffset = 0;
    
    private EditBox searchBox;
    private int winX, winY, winW, winH;
    
    // Old color palette
    private static final int BG = 0xEE111111; // Darker, slightly transparent black
    private static final int CARD = 0xAA222222; 
    private static final int CARD_HOVER = 0xAA444444;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int ACCENT = 0xFFFF5555; // Red accent
    
    private long openTime;
    
    public LadsSettingsScreen(Screen parent) {
        super(Component.literal("Lads Settings"));
        this.parent = parent;
        this.openTime = System.currentTimeMillis();
    }
    
    @Override
    protected void init() {
        super.init();
        winW = Math.min(900, this.width - 40);
        winH = Math.min(500, this.height - 40);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
        
        this.searchBox = new EditBox(this.font, winX + winW - 160, winY + 45, 140, 20, Component.literal("Search..."));
        this.addRenderableWidget(this.searchBox);
        
        this.addRenderableWidget(Button.builder(Component.literal("X"), b -> this.minecraft.setScreenAndShow(parent))
                .bounds(winX + winW - 25, winY + 5, 20, 20).build());
                
        int tabX = winX + (winW / 2) - 105;
        this.addRenderableWidget(Button.builder(Component.literal("MODS"), b -> { currentTab = "MODS"; targetScrollOffset = 0; })
                .bounds(tabX, winY + 5, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("HUD PRESETS"), b -> { currentTab = "HUD PRESETS"; targetScrollOffset = 0; })
                .bounds(tabX + 110, winY + 5, 100, 20).build());
                
        this.addRenderableWidget(Button.builder(Component.literal("EDIT HUD LAYOUT"), b -> this.minecraft.setScreenAndShow(new DraggableHudScreen()))
                .bounds(winX + 10, winY + winH - 30, 120, 20).build());
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        long timeSinceOpen = System.currentTimeMillis() - this.openTime;
        float animProgress = Math.min(1.0f, timeSinceOpen / 200.0f);
        float easeOut = 1.0f - (float)Math.pow(1.0f - animProgress, 3);
        
        g.pose().pushMatrix();
        
        if (easeOut < 1.0f) {
            float cx = this.width / 2.0f;
            float cy = this.height / 2.0f;
            g.pose().translate(cx, cy);
            g.pose().scale(easeOut, easeOut);
            g.pose().translate(-cx, -cy);
        }

        // Smooth scroll interpolation
        scrollOffset += (targetScrollOffset - scrollOffset) * 0.3;

        g.fill(winX, winY, winX + winW, winY + winH, BG);
        g.fill(winX, winY, winX + winW, winY + 30, 0xEE0A0A0A);
        g.fill(winX, winY + 30, winX + 140, winY + winH, 0xEE111111);
        
        if (currentTab.equals("MODS")) {
            renderModsTab(g, mouseX, mouseY);
        } else if (currentTab.equals("HUD PRESETS")) {
            renderHudPresetsTab(g, mouseX, mouseY);
        }
        
        super.extractRenderState(g, mouseX, mouseY, delta);
        
        g.pose().popMatrix();
    }
    
    private void renderModsTab(GuiGraphicsExtractor g, int mx, int my) {
        String[] cats = {"ALL", "NEW", "HUD", "SERVER", "MECHANIC"};
        int cx = winX + 150;
        for (String c : cats) {
            int w = this.font.width(c) + 20;
            int col = currentCategory.equals(c) ? ACCENT : 0xAA333333;
            g.fill(cx, winY + 45, cx + w, winY + 65, col);
            g.text(this.font, c, cx + 10, winY + 51, TEXT, false);
            cx += w + 5;
        }
        
        g.enableScissor(winX + 150, winY + 80, winX + winW, winY + winH);
        
        int startX = winX + 150;
        int startY = winY + 80 - (int)scrollOffset;
        int cardW = 150;
        int cardH = 35;
        int x = startX;
        int y = startY;
        
        String query = searchBox.getValue() != null ? searchBox.getValue().toLowerCase() : "";
        List<Module> modules = new ArrayList<>(ModuleManager.getInstance().getRegisteredModules());
        modules.sort(Comparator.comparing(Module::getName));
        
        for (Module m : modules) {
            if (!currentCategory.equals("ALL") && !m.getCategory().name().equalsIgnoreCase(currentCategory)) {
                continue;
            }
            if (!query.isEmpty() && !m.getName().toLowerCase().contains(query)) {
                continue;
            }
            
            boolean hover = mx >= x && mx < x + cardW && my >= y && my < y + cardH && my >= winY + 80 && my <= winY + winH;
            g.fill(x, y, x + cardW, y + cardH, hover ? CARD_HOVER : CARD);
            
            int nameWidth = this.font.width(m.getName());
            g.text(this.font, m.getName(), x + (cardW / 2) - (nameWidth / 2), y + 14, TEXT, false);
            
            int togCol = m.isEnabled() ? 0xFF55FF55 : 0xFFFF5555;
            g.fill(x + cardW - 20, y + 10, x + cardW - 5, y + 25, togCol);
            
            x += cardW + 10;
            if (x + cardW > winX + winW - 10) {
                x = startX;
                y += cardH + 10;
            }
        }
        
        // Adjust max scroll
        int visibleH = winH - 80;
        int totalH = y - startY + cardH + 10;
        if (x != startX) totalH += cardH + 10;
        double maxScroll = Math.max(0, totalH - visibleH);
        if (targetScrollOffset > maxScroll) targetScrollOffset = maxScroll;
        
        g.disableScissor();
    }
    
    private void renderHudPresetsTab(GuiGraphicsExtractor g, int mx, int my) {
        int cx = winX + 150;
        int cy = winY + 45;
        
        g.text(this.font, "HUD Presets Management", cx, cy, TEXT, false);
        
        cy += 30;
        
        // Save Preset Button
        boolean hoverSave = mx >= cx && mx < cx + 150 && my >= cy && my < cy + 25;
        g.fill(cx, cy, cx + 150, cy + 25, hoverSave ? CARD_HOVER : CARD);
        g.text(this.font, "Save Custom Preset", cx + 10, cy + 8, TEXT, false);
        
        // Save Per Server Button
        boolean hoverServer = mx >= cx + 160 && mx < cx + 310 && my >= cy && my < cy + 25;
        g.fill(cx + 160, cy, cx + 310, cy + 25, hoverServer ? CARD_HOVER : CARD);
        g.text(this.font, "Save Per Server", cx + 170, cy + 8, TEXT, false);
        
        cy += 40;
        g.text(this.font, "Your Presets:", cx, cy, TEXT, false);
        
        cy += 20;
        g.fill(cx, cy, cx + 310, cy + 100, 0x44000000);
        g.text(this.font, "(No presets saved yet)", cx + 10, cy + 10, 0xFFAAAAAA, false);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        targetScrollOffset -= scrollY * 35;
        if (targetScrollOffset < 0) targetScrollOffset = 0;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (event.button() != 0) return super.mouseClicked(event, isDouble);
        double mx = event.x();
        double my = event.y();
        
        if (currentTab.equals("MODS")) {
            String[] cats = {"ALL", "NEW", "HUD", "SERVER", "MECHANIC"};
            int cx = winX + 150;
            for (String c : cats) {
                int w = this.font.width(c) + 20;
                if (mx >= cx && mx < cx + w && my >= winY + 45 && my < winY + 65) {
                    currentCategory = c;
                    targetScrollOffset = 0;
                    return true;
                }
                cx += w + 5;
            }
            
            if (my >= winY + 80 && my <= winY + winH) {
                int startX = winX + 150;
                int startY = winY + 80 - (int)scrollOffset;
                int cardW = 150;
                int cardH = 35;
                int x = startX;
                int y = startY;
                
                String query = searchBox.getValue() != null ? searchBox.getValue().toLowerCase() : "";
                List<Module> modules = new ArrayList<>(ModuleManager.getInstance().getRegisteredModules());
                modules.sort(Comparator.comparing(Module::getName));
                
                for (Module m : modules) {
                    if (!currentCategory.equals("ALL") && !m.getCategory().name().equalsIgnoreCase(currentCategory)) {
                        continue;
                    }
                    if (!query.isEmpty() && !m.getName().toLowerCase().contains(query)) {
                        continue;
                    }
                    
                    if (mx >= x && mx < x + cardW && my >= y && my < y + cardH) {
                        if (mx >= x + cardW - 30) {
                            m.toggle();
                            com.thelads.core.config.ConfigManager.save();
                        } else {
                            this.minecraft.setScreenAndShow(new ModuleOptionsScreen(this, m));
                        }
                        return true;
                    }
                    x += cardW + 10;
                    if (x + cardW > winX + winW - 10) {
                        x = startX;
                        y += cardH + 10;
                    }
                }
            }
        } else if (currentTab.equals("HUD PRESETS")) {
            int cx = winX + 150;
            int cy = winY + 75;
            if (mx >= cx && mx < cx + 150 && my >= cy && my < cy + 25) {
                // Save custom preset
                com.thelads.core.config.ConfigManager.save();
                return true;
            }
            if (mx >= cx + 160 && mx < cx + 310 && my >= cy && my < cy + 25) {
                // Save per server
                com.thelads.core.config.ConfigManager.save();
                return true;
            }
        }
        return super.mouseClicked(event, isDouble);
    }
}
