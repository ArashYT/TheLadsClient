package com.thelads.core.client.capes.menu;

import com.thelads.core.client.capes.CapeConfig;
import com.thelads.core.client.capes.Capes;
import com.thelads.core.client.capes.render.PlaceholderEntity;
import com.thelads.core.client.capes.render.PlaceholderEntityRenderState;
import com.thelads.core.client.capes.render.PlaceholderEntityRenderer;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SelectorMenu extends MainMenu {
    private long lastTime;
    private boolean dropdownOpen = false;
    private Button capeTypeButton;

    public SelectorMenu(Screen parent, Options gameOptions) {
        super(parent, gameOptions);
    }

    public long getLastTime() {
        return this.lastTime;
    }

    public void setLastTime(long lastTime) {
        this.lastTime = lastTime;
    }

    @Override
    protected void init() {
        super.init();
        CapeConfig config = Capes.INSTANCE.getCONFIG();

        this.capeTypeButton = Button.builder(config.getClientCapeType().getText(), button -> {
            this.dropdownOpen = !this.dropdownOpen;
        }).pos(this.width / 4 - 50, 60).size(100, 20).build();
        this.addRenderableWidget(this.capeTypeButton);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(this.lastScreen);
            }
        }).pos(this.width / 4 - 50, 220).size(100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("options.capes.selector.player"), button -> {
            PlaceholderEntity.INSTANCE.setShowBody(!PlaceholderEntity.INSTANCE.getShowBody());
        }).pos(this.width / 4 - 50, 145).size(100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("options.capes.selector.elytra"), button -> {
            PlaceholderEntity.INSTANCE.setShowElytra(!PlaceholderEntity.INSTANCE.getShowElytra());
        }).pos(this.width / 4 - 50, 120).size(100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int playerX1 = this.width * 3 / 4 - 50;
        int playerX2 = this.width * 3 / 4 + 50;
        int playerY1 = 65;
        int playerY2 = 365;
        long time = System.currentTimeMillis();
        PlaceholderEntity entity = PlaceholderEntity.INSTANCE;
        if (time > this.lastTime + 16) {
            this.lastTime = time;
            entity.setPrevX(entity.getX() + 0.025);
            entity.updateLimbs();
        }
        this.drawEntity(graphics, playerX1, playerY1, playerX2, playerY2, PlaceholderEntity.INSTANCE);

        if (this.dropdownOpen) {
            graphics.nextStratum();
            com.thelads.core.client.capes.CapeType[] types = com.thelads.core.client.capes.CapeType.values();
            int bw = 100;
            int bx = this.width / 4 - 50;
            int by = 80;
            int itemH = 20;
            int listH = types.length * itemH;
            
            // Draw background (dark color) and red outline matching client style
            graphics.fill(bx, by, bx + bw, by + listH, 0xCC180A0A);
            // Draw border
            graphics.fill(bx, by, bx + bw, by + 1, 0xFFD32F2F);
            graphics.fill(bx, by + listH - 1, bx + bw, by + listH, 0xFFD32F2F);
            graphics.fill(bx, by, bx + 1, by + listH, 0xFFD32F2F);
            graphics.fill(bx + bw - 1, by, bx + bw, by + listH, 0xFFD32F2F);
            
            for (int k = 0; k < types.length; k++) {
                int iy = by + k * itemH;
                boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= iy && mouseY < iy + itemH;
                if (hovered) {
                    graphics.fill(bx + 1, iy + 1, bx + bw - 1, iy + itemH - 1, 0xCC2A1010);
                }
                String text = types[k].getStylized();
                graphics.centeredText(this.font, text, bx + bw / 2, iy + 6, hovered ? 0xFFFF5252 : 0xFFFFFFFF);
            }
        }
    }

    public void drawEntity(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, PlaceholderEntity entity) {
        context.enableScissor(x1, y1, x2, y2);
        PlaceholderEntityRenderer entityRenderer = PlaceholderEntity.INSTANCE.getRenderer();
        PlaceholderEntityRenderState entityRenderState = entityRenderer.getAndUpdatePlaceholderRenderState(entity);
        context.entity(entityRenderState, 69.0f, new Vector3f(0.0f, 0.0f, 0.0f), new Quaternionf().rotateZ((float) Math.PI), null, x1, y1, x2, y2);
        context.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        if (this.dropdownOpen) {
            double mx = event.x();
            double my = event.y();
            com.thelads.core.client.capes.CapeType[] types = com.thelads.core.client.capes.CapeType.values();
            int bw = 100;
            int bx = this.width / 4 - 50;
            int by = 80;
            int itemH = 20;
            int listH = types.length * itemH;
            
            if (mx >= bx && mx < bx + bw && my >= by && my < by + listH) {
                int clickedIdx = (int) ((my - by) / itemH);
                if (clickedIdx >= 0 && clickedIdx < types.length) {
                    CapeConfig config = Capes.INSTANCE.getCONFIG();
                    config.setClientCapeType(types[clickedIdx]);
                    config.save();
                    this.capeTypeButton.setMessage(config.getClientCapeType().getText());
                    PlaceholderEntity.INSTANCE.setCapeLoaded(false);
                    com.thelads.core.client.capes.PlayerHandler.onLoadTexture(this.minecraft.getGameProfile());
                }
            }
            this.dropdownOpen = false;
            return true;
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (click.x() >= this.width / 2) {
            super.mouseDragged(click, offsetX, offsetY);
            PlaceholderEntity.INSTANCE.setPrevYaw(PlaceholderEntity.INSTANCE.getYaw());
            PlaceholderEntity.INSTANCE.setYaw(PlaceholderEntity.INSTANCE.getYaw() - (float) offsetX);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }
}
