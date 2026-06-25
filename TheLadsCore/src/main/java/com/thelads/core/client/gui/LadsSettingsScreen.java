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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Arrays;

public class LadsSettingsScreen extends Screen {
    private final Screen parent;
    private static String currentTab = "MODS";
    private static String currentCategory = "ALL";
    private static String currentSort = "Alphabetical";
    private static String currentFilter = "All";
    private static boolean sortAscending = true;

    public static double scrollOffset = 0;
    private static double targetScrollOffset = 0;

    private EditBox searchBox;
    private int winX, winY, winW, winH;

    // Premium Red and Black color palette
    private static final int BG = 0xEE050505; 
    private static final int SIDEBAR = 0xEE0A0A0A;
    private static final int CARD = 0xAA111111; 
    private static final int CARD_HOVER = 0xAA2B1111;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int ACCENT = 0xFFD32F2F; 
    private static final int TOGGLE_ON = 0xFF2E7D32;
    private static final int TOGGLE_OFF = 0xFFB71C1C;

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
                
        int tabX = winX + (winW / 2) - 220;
        this.addRenderableWidget(Button.builder(Component.literal("MODS"), b -> { currentTab = "MODS"; targetScrollOffset = 0; })
                .bounds(tabX, winY + 5, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("PERFORMANCE"), b -> { currentTab = "PERFORMANCE"; targetScrollOffset = 0; })
                .bounds(tabX + 110, winY + 5, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("PACKS"), b -> { currentTab = "PACKS"; targetScrollOffset = 0; })
                .bounds(tabX + 220, winY + 5, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("HUD PRESETS"), b -> { currentTab = "HUD PRESETS"; targetScrollOffset = 0; })
                .bounds(tabX + 330, winY + 5, 100, 20).build());
                
        this.addRenderableWidget(Button.builder(Component.literal("EDIT HUD LAYOUT"), b -> this.minecraft.setScreenAndShow(new DraggableHudScreen()))
                .bounds(winX + 10, winY + winH - 30, 120, 20).build());
    }

    private boolean isPerformanceModule(Module m) {
        String name = m.getName().toLowerCase();
        return name.contains("exordium") || name.contains("render scale") || name.contains("renderscale") || name.contains("scalablelux") || name.contains("clumps") || name.contains("dynamic fps") || name.contains("dynamicfps") || name.contains("performance manager") || name.contains("performancemanager");
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

        scrollOffset += (targetScrollOffset - scrollOffset) * 0.3;

        g.fill(winX, winY, winX + winW, winY + winH, BG);
        g.fill(winX, winY, winX + winW, winY + 30, SIDEBAR);
        g.fill(winX, winY + 30, winX + 140, winY + winH, SIDEBAR);
        g.fill(winX + 140, winY + 30, winX + 141, winY + winH, ACCENT); // Divider line
        
        if (currentTab.equals("MODS") || currentTab.equals("PERFORMANCE")) {
            renderModulesList(g, mouseX, mouseY);
        } else if (currentTab.equals("PACKS")) {
            renderPacksTab(g, mouseX, mouseY);
        } else if (currentTab.equals("HUD PRESETS")) {
            renderHudPresetsTab(g, mouseX, mouseY);
        }
        
        super.extractRenderState(g, mouseX, mouseY, delta);
        
        g.pose().popMatrix();
    }
    
    private void renderModulesList(GuiGraphicsExtractor g, int mx, int my) {
        int listStartY = winY + 70;
        if (currentTab.equals("MODS")) {
            String[] cats = {"ALL", "NEW", "HUD", "SERVER", "MECHANIC"};
            int cx = winX + 150;
            for (String c : cats) {
                int w = this.font.width(c) + 20;
                int col = currentCategory.equals(c) ? ACCENT : 0xAA333333;
                g.fill(cx, winY + 45, cx + w, winY + 65, col);
                g.text(this.font, c, cx + 10, winY + 51, TEXT, false);
                cx += w + 5;
            }
            listStartY = winY + 95;
        }

        // Render Sort & Filter Bar
        int topY = currentTab.equals("MODS") ? winY + 75 : winY + 45;
        g.text(this.font, "Filter: " + currentFilter, winX + 155, topY, TEXT, false);
        g.text(this.font, "Sort: " + currentSort, winX + 270, topY, TEXT, false);
        g.text(this.font, sortAscending ? "[ASC]" : "[DESC]", winX + 410, topY, TEXT, false);

        g.enableScissor(winX + 150, listStartY, winX + winW, winY + winH);
        
        int startX = winX + 150;
        int startY = listStartY - (int)scrollOffset;
        int cardW = 160;
        int cardH = 50;
        int x = startX;
        int y = startY;
        
        String query = searchBox.getValue() != null ? searchBox.getValue().toLowerCase() : "";
        List<Module> modules = new ArrayList<>(ModuleManager.getInstance().getRegisteredModules());
        
        modules.removeIf(m -> currentTab.equals("PERFORMANCE") ? !isPerformanceModule(m) : isPerformanceModule(m));
        if (currentTab.equals("MODS") && !currentCategory.equals("ALL")) {
            modules.removeIf(m -> !m.getCategory().name().equalsIgnoreCase(currentCategory));
        }
        if (!query.isEmpty()) {
            modules.removeIf(m -> !m.getName().toLowerCase().contains(query));
        }
        if (currentFilter.equals("Enabled")) modules.removeIf(m -> !m.isEnabled());
        else if (currentFilter.equals("Disabled")) modules.removeIf(m -> m.isEnabled());
        else if (currentFilter.equals("Favorites")) modules.removeIf(m -> !m.isFavorite());

        Comparator<Module> comp;
        if (currentSort.equals("Recently Opened")) comp = Comparator.comparingLong(Module::getLastOpenedTime);
        else if (currentSort.equals("Recently Edited")) comp = Comparator.comparingLong(Module::getLastModified);
        else comp = Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER);
        
        if (!sortAscending) comp = comp.reversed();
        modules.sort(comp);
        
        for (Module m : modules) {
            boolean hover = mx >= x && mx < x + cardW && my >= y && my < y + cardH && my >= listStartY && my <= winY + winH;
            
            // Faux rounded rect
            int cBg = hover ? CARD_HOVER : CARD;
            g.fill(x + 3, y, x + cardW - 3, y + cardH, cBg);
            g.fill(x, y + 3, x + cardW, y + cardH - 3, cBg);
            g.fill(x + 1, y + 1, x + 3, y + 3, cBg);
            g.fill(x + cardW - 3, y + 1, x + cardW - 1, y + 3, cBg);
            g.fill(x + 1, y + cardH - 3, x + 3, y + cardH - 1, cBg);
            g.fill(x + cardW - 3, y + cardH - 3, x + cardW - 1, y + cardH - 1, cBg);
            
            int nameWidth = this.font.width(m.getName());
            g.text(this.font, m.getName(), x + (cardW / 2) - (nameWidth / 2), y + 10, TEXT, false);
            
            int starColor = m.isFavorite() ? 0xFFFFD700 : 0xFF555555;
            g.text(this.font, "★", x + cardW - 15, y + 5, starColor, false);
            
            int togCol = m.isEnabled() ? TOGGLE_ON : TOGGLE_OFF;
            if (hover) togCol = m.isEnabled() ? 0xFF388E3C : 0xFFD32F2F;
            String togText = m.isEnabled() ? "ENABLED" : "DISABLED";
            int tw = this.font.width(togText);
            
            int barY = y + cardH - 16;
            g.fill(x + 10, barY, x + cardW - 10, barY + 12, togCol);
            g.text(this.font, togText, x + (cardW / 2) - (tw / 2), barY + 2, TEXT, false);
            
            x += cardW + 15;
            if (x + cardW > winX + winW - 10) {
                x = startX;
                y += cardH + 15;
            }
        }
        
        int visibleH = winH - (listStartY - winY);
        int totalH = y - startY + cardH + 15;
        if (x != startX) totalH += cardH + 15;
        double maxScroll = Math.max(0, totalH - visibleH);
        if (targetScrollOffset > maxScroll) targetScrollOffset = maxScroll;
        
        g.disableScissor();
    }
    
    private void renderHudPresetsTab(GuiGraphicsExtractor g, int mx, int my) {
        int cx = winX + 150;
        int cy = winY + 45;
        g.text(this.font, "HUD Presets Management", cx, cy, TEXT, false);
        cy += 30;
        boolean hoverSave = mx >= cx && mx < cx + 150 && my >= cy && my < cy + 25;
        g.fill(cx, cy, cx + 150, cy + 25, hoverSave ? CARD_HOVER : CARD);
        g.text(this.font, "Save Custom Preset", cx + 10, cy + 8, TEXT, false);
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

    private void cycleFilter() {
        if (currentFilter.equals("All")) currentFilter = "Enabled";
        else if (currentFilter.equals("Enabled")) currentFilter = "Disabled";
        else if (currentFilter.equals("Disabled")) currentFilter = "Favorites";
        else currentFilter = "All";
    }

    private void cycleSort() {
        if (currentSort.equals("Alphabetical")) currentSort = "Recently Opened";
        else if (currentSort.equals("Recently Opened")) currentSort = "Recently Edited";
        else currentSort = "Alphabetical";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (event.button() != 0) return super.mouseClicked(event, isDouble);
        double mx = event.x();
        double my = event.y();
        
        if (currentTab.equals("MODS") || currentTab.equals("PERFORMANCE")) {
            int topY = currentTab.equals("MODS") ? winY + 75 : winY + 45;
            if (my >= topY - 5 && my <= topY + 15) {
                if (mx >= winX + 150 && mx <= winX + 260) {
                    cycleFilter();
                    return true;
                } else if (mx >= winX + 270 && mx <= winX + 400) {
                    cycleSort();
                    return true;
                } else if (mx >= winX + 410 && mx <= winX + 450) {
                    sortAscending = !sortAscending;
                    return true;
                }
            }

            int listStartY = currentTab.equals("MODS") ? winY + 95 : winY + 70;
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
            }
            
            if (my >= listStartY && my <= winY + winH) {
                int startX = winX + 150;
                int startY = listStartY - (int)scrollOffset;
                int cardW = 160;
                int cardH = 50;
                int x = startX;
                int y = startY;
                
                String query = searchBox.getValue() != null ? searchBox.getValue().toLowerCase() : "";
                List<Module> modules = new ArrayList<>(ModuleManager.getInstance().getRegisteredModules());
                
                modules.removeIf(m -> currentTab.equals("PERFORMANCE") ? !isPerformanceModule(m) : isPerformanceModule(m));
                if (currentTab.equals("MODS") && !currentCategory.equals("ALL")) {
                    modules.removeIf(m -> !m.getCategory().name().equalsIgnoreCase(currentCategory));
                }
                if (!query.isEmpty()) {
                    modules.removeIf(m -> !m.getName().toLowerCase().contains(query));
                }
                if (currentFilter.equals("Enabled")) modules.removeIf(m -> !m.isEnabled());
                else if (currentFilter.equals("Disabled")) modules.removeIf(m -> m.isEnabled());
                else if (currentFilter.equals("Favorites")) modules.removeIf(m -> !m.isFavorite());

                Comparator<Module> comp;
                if (currentSort.equals("Recently Opened")) comp = Comparator.comparingLong(Module::getLastOpenedTime);
                else if (currentSort.equals("Recently Edited")) comp = Comparator.comparingLong(Module::getLastModified);
                else comp = Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER);
                
                if (!sortAscending) comp = comp.reversed();
                modules.sort(comp);
                
                for (Module m : modules) {
                    if (mx >= x && mx < x + cardW && my >= y && my < y + cardH) {
                        if (mx >= x + cardW - 20 && my <= y + 20) {
                            m.setFavorite(!m.isFavorite());
                            com.thelads.core.config.ConfigManager.save();
                        } else if (my >= y + cardH - 20) {
                            m.toggle();
                            com.thelads.core.config.ConfigManager.save();
                        } else {
                            this.minecraft.setScreenAndShow(new ModuleOptionsScreen(this, m));
                        }
                        return true;
                    }
                    x += cardW + 15;
                    if (x + cardW > winX + winW - 10) {
                        x = startX;
                        y += cardH + 15;
                    }
                }
            }
        } else if (currentTab.equals("PACKS")) {
            int cx = winX + 150;
            int cardW = winW - 170;
            int cardH = 34;
            com.thelads.core.config.Module module = ModuleManager.getInstance().getModule("TexturePacks");
            if (module != null) {
                String[] optionNames = {
                    "Disable Hidden Overrides",
                    "Show Hidden",
                    "Show All",
                    "Max Packs"
                };
                for (int i = 0; i < 4; i++) {
                    int cy = winY + 75 + i * (cardH + 10);
                    if (mx >= cx && mx < cx + cardW && my >= cy && my < cy + cardH) {
                        com.thelads.core.config.Option opt = module.getOption(optionNames[i]);
                        if (opt instanceof com.thelads.core.config.BoolOption) {
                            ((com.thelads.core.config.BoolOption) opt).toggle();
                            com.thelads.core.config.ConfigManager.save();
                        } else if (opt instanceof com.thelads.core.config.DropdownOption) {
                            com.thelads.core.config.DropdownOption dropdown = (com.thelads.core.config.DropdownOption) opt;
                            int next = (dropdown.getIndex() + 1) % dropdown.getChoices().length;
                            dropdown.setIndex(next);
                            com.thelads.core.config.ConfigManager.save();
                        }
                        return true;
                    }
                }
            }
        } else if (currentTab.equals("HUD PRESETS")) {
            int cx = winX + 150;
            int cy = winY + 75;
            if (mx >= cx && mx < cx + 150 && my >= cy && my < cy + 25) {
                com.thelads.core.config.ConfigManager.save();
                return true;
            }
            if (mx >= cx + 160 && mx < cx + 310 && my >= cy && my < cy + 25) {
                com.thelads.core.config.ConfigManager.save();
                return true;
            }
        }
        return super.mouseClicked(event, isDouble);
    }

    private void renderPacksTab(GuiGraphicsExtractor g, int mx, int my) {
        int cx = winX + 150;
        int cy = winY + 45;
        g.text(this.font, "Resource Packs Settings", cx, cy, TEXT, false);
        cy += 30;

        int cardW = winW - 170;
        int cardH = 34;

        com.thelads.core.config.Module module = ModuleManager.getInstance().getModule("TexturePacks");
        if (module == null) return;

        String[] optionNames = {
            "Disable Hidden Overrides",
            "Show Hidden",
            "Show All",
            "Max Packs"
        };

        String[] optionLabels = {
            "Disable Hidden Packs Override",
            "Show Hidden Packs in HUD",
            "Show All Active Packs",
            "Max Packs to Display"
        };

        for (int i = 0; i < 4; i++) {
            String optName = optionNames[i];
            String label = optionLabels[i];

            boolean hover = mx >= cx && mx < cx + cardW && my >= cy && my < cy + cardH;
            g.fill(cx, cy, cx + cardW, cy + cardH, hover ? CARD_HOVER : CARD);
            g.fill(cx, cy + 3, cx + 3, cy + cardH - 3, ACCENT);

            g.text(this.font, label, cx + 15, cy + 12, TEXT, false);

            com.thelads.core.config.Option opt = module.getOption(optName);
            if (opt instanceof com.thelads.core.config.BoolOption) {
                boolean val = ((com.thelads.core.config.BoolOption) opt).get();
                int toggleX = cx + cardW - 55;
                int toggleY = cy + 9;
                drawToggle(g, toggleX, toggleY, val);
            } else if (opt instanceof com.thelads.core.config.DropdownOption) {
                com.thelads.core.config.DropdownOption c = (com.thelads.core.config.DropdownOption) opt;
                int bw = 120;
                int bx = cx + cardW - bw - 6;
                int by = cy + 8;
                g.fill(bx, by, bx + bw, by + 18, 0xFF2A0A0A);
                String valText = c.getValue() + " ▼";
                g.text(this.font, valText, bx + bw / 2 - this.font.width(valText) / 2, by + 5, TEXT, false);
            }

            cy += cardH + 10;
        }
    }

    private void drawToggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        g.fill(x, y, x + 42, y + 16, on ? ACCENT : 0xFF2A0A0A);
        int knob = on ? x + 27 : x + 3;
        g.fill(knob, y + 2, knob + 12, y + 14, TEXT);
        g.text(this.font, on ? "§aON" : "§cOFF", x + (on ? 5 : 15), y + 4, TEXT, false);
    }
}
