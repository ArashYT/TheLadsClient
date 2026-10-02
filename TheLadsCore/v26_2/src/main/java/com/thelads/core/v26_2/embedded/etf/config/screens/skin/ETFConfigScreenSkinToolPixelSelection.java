package com.thelads.core.v26_2.embedded.etf.config.screens.skin;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import com.thelads.core.v26_2.embedded.etf.ETF;
import com.thelads.core.v26_2.embedded.etf.utils.ETFUtils2;
import com.thelads.core.v26_2.embedded.etf.utils.UScreen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static com.thelads.core.v26_2.embedded.etf.ETF.MOD_ID;

//inspired by puzzles custom gui code
public class ETFConfigScreenSkinToolPixelSelection extends ETFScreenOldCompat {

    private final SelectionMode MODE;

    private final ETFConfigScreenSkinTool etfParent;
    Set<Integer> selectedPixels;
    Identifier currentSkinToRender = ETFUtils2.res(MOD_ID + ":textures/gui/icon.png");

    protected ETFConfigScreenSkinToolPixelSelection(ETFConfigScreenSkinTool parent, SelectionMode mode) {
        super("config." + ETF.MOD_ID + (mode == SelectionMode.EMISSIVE ? ".emissive_select" : ".enchanted_select") + ".title", parent, false);
        this.MODE = mode;
        etfParent = parent;


    }

    @Override
    protected void init() {
        super.init();

        Identifier randomID = ETFUtils2.res(MOD_ID + "_ignore", "gui_skin_" + System.currentTimeMillis() + ".png");
        if (ETFUtils2.registerNativeImageToIdentifier(etfParent.currentEditorSkin, randomID)) {
            currentSkinToRender = randomID;
        }

        selectedPixels = new HashSet<>();
        for (int x = MODE.startX; x < MODE.startX + 8; x++) {
            for (int y = MODE.startY; y < MODE.startY + 8; y++) {
                int color = ETFUtils2.getPixel(etfParent.currentEditorSkin, x, y);
                if (color != 0) {
                    selectedPixels.add(color);
                }
            }
        }

        this.addRenderableWidget(getETFButton((int) (this.width * 0.024), (int) (this.height * 0.2), 20, 20,
                Component.nullToEmpty("⟳"),
                (button) -> etfParent.flipView = !etfParent.flipView));


        this.addRenderableWidget(getETFButton((int) (this.width * 0.55), (int) (this.height * 0.9), (int) (this.width * 0.2), 20,
                CommonComponents.GUI_BACK,
                (button) -> UScreen.setScreen(parent)));

        int pixelSize = (int) (this.height * 0.7 / 64);

        //simple method to create 4096 buttons instead of extrapolating mouse position
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                Button butt = getButtonPixels(x, y, pixelSize);
//todo can do so much better than this
                this.addRenderableWidget(butt);
            }
        }

    }

    @NotNull
    private Button getButtonPixels(final int x, final int y, final int pixelSize) {

        return new
                Button.Plain
                ((int) ((ETFConfigScreenSkinToolPixelSelection.this.width * 0.35) + (x * pixelSize)), (int) ((ETFConfigScreenSkinToolPixelSelection.this.height * 0.2) + (y * pixelSize)), pixelSize, pixelSize,
                Component.nullToEmpty(""),
                (button) -> {
                    int colorAtPixel = ETFUtils2.getPixel(etfParent.currentEditorSkin, x, y);
                    if (selectedPixels.contains(colorAtPixel)) {
                        selectedPixels.remove(colorAtPixel);
                    } else {
                        selectedPixels.add(colorAtPixel);
                    }

                    applyCurrentSelectedPixels();
                    etfParent.thisETFPlayerTexture.changeSkinToThisForTool(etfParent.currentEditorSkin);
                    Identifier randomID2 = ETFUtils2.res(MOD_ID + "_ignore", "gui_skin_" + System.currentTimeMillis() + ".png");
                    if (ETFUtils2.registerNativeImageToIdentifier(etfParent.currentEditorSkin, randomID2)) {
                        currentSkinToRender = randomID2;
                    }
                }, Supplier::get) {

            @Override protected void extractContents(GuiGraphicsExtractor guiGraphics, int i, int j, float f) {}

        };
    }

    private void applyCurrentSelectedPixels() {
        ArrayList<Integer> integerSet = new ArrayList<>(selectedPixels);

        for (int x = MODE.startX; x < MODE.startX + 8; x++) {
            for (int y = MODE.startY; y < MODE.startY + 8; y++) {
                if (integerSet.isEmpty()) {
                    ETFUtils2.setPixel(etfParent.currentEditorSkin, x, y, 0);
                } else {
                    ETFUtils2.setPixel(etfParent.currentEditorSkin, x, y, integerSet.get(0));
                    integerSet.remove(0);
                }
            }
        }
    }

    @Override
    public void
        extractRenderState
    (GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.
                extractRenderState
                        (context, mouseX, mouseY, delta);

        int pixelSize = (int) (this.height * 0.7 / 64);

        renderGUITexture(context,currentSkinToRender, (int) ((this.width * 0.35)), (int) ((this.height * 0.2)), (int) ((this.width * 0.35) + (64 * pixelSize)), (int) ((this.height * 0.2) + (64 * pixelSize)));
        context.text(font, ETF.getTextFromTranslation("config." + MOD_ID + ".skin_select" + (selectedPixels.size() > 64 ? ".warn" : ".hint")), width / 7, (int) (this.height * 0.8), selectedPixels.size() > 64 ? 0xff1515 : 0xFFFFFF);


        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {

            int height = (int) (this.height * 0.75);
            int playerX = (int) (this.width * 0.14);
            drawEntity(context, playerX, height, (int) (this.height * 0.3),
                    mouseX, mouseY,

                    player);
        } else {
            context.text(font, Component.nullToEmpty("Player model only visible while in game!"), width / 7, (int) (this.height * 0.4), 0xFFFFFF);
            context.text(font, Component.nullToEmpty("load a single-player world and then open this menu."), width / 7, (int) (this.height * 0.45), 0xFFFFFF);
        }


//        if(MODE == SelectionMode.EMISSIVE && ETFVersionDifferenceHandler.isThisModLoaded("iris"))
//            drawTextWithShadow(matrices, textRenderer, ETFVersionDifferenceHandler.getTextFromTranslation("config." + MOD_ID + ".player_skin_editor.iris_message"), width / 8, (int) (this.height * 0.15), 0xFF5555);

    }

    public void drawEntity(GuiGraphicsExtractor context, int x, int y, int size, float mouseX, float mouseY, LivingEntity entity) {
        ETFConfigScreenSkinTool.renderEntityInInventoryFollowsMouse(etfParent.flipView,
                context,
                0, (int) (this.height * 0.15), x * 2, y,
                size, 0.0625F,
                mouseX + (int) (this.height * 0.15), mouseY,
                entity
        );
    }

    public enum SelectionMode {
        EMISSIVE(56, 16),
        ENCHANTED(56, 24);

        final int startX;
        final int startY;

        SelectionMode(@SuppressWarnings("SameParameterValue") int start_x, int start_y) {
            startX = start_x;
            startY = start_y;
        }
    }

}
