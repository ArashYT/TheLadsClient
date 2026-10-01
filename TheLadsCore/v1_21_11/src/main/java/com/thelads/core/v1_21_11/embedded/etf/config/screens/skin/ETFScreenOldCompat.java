package com.thelads.core.v1_21_11.embedded.etf.config.screens.skin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;


import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import com.thelads.core.v1_21_11.embedded.etf.ETF;
import com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.accessor.TooltipAccessor;
import com.thelads.core.v1_21_11.embedded.etf.tconfig.gui.TConfigScreen;

import java.util.ArrayList;
import java.util.List;

public abstract class ETFScreenOldCompat extends TConfigScreen {

    @SuppressWarnings("SameParameterValue")
    protected ETFScreenOldCompat(final String title, final Screen parent, @SuppressWarnings("SameParameterValue") final boolean showBackButton) {
        super(title, parent, showBackButton);
    }

    public static void renderGUITexture(GuiGraphics context, Identifier texture, double x1, double y1, double x2, double y2) {
        context.blit(RenderPipelines.GUI_TEXTURED, texture, (int) x1, (int) y1, 0, 0, (int) (x2-x1), (int) (y2-y1), 1, 1, 1, 1,
                net.minecraft.util.ARGB.color( 255, 255, 255, 255));
    }

    public static String booleanAsOnOff(boolean bool) {
        return CommonComponents.optionStatus(bool).getString();
    }

    public Button getETFButton(int x, int y, int width, @SuppressWarnings("SameParameterValue") int height, Component buttonText, Button.OnPress onPress) {
        return getETFButton(x, y, width, height, buttonText, onPress, Component.nullToEmpty(""));
    }

    public Button getETFButton(int x, int y, int width, int height, Component buttonText, Button.OnPress onPress, Component toolTipText) {
        int nudgeLeftEdge;
        if (width > 384) {
            nudgeLeftEdge = (width - 384) / 2;
            width = 384;
        } else {
            nudgeLeftEdge = 0;
        }
//        if (width > 800)
//            height=80;
//        if (width > 1600)
//            height=16;
        boolean tooltipIsEmpty = toolTipText.getString().isBlank();
//        String[] strings = toolTipText.getString().split("\n");
//        List<Text> lines = new ArrayList<>();
//        for (String str :
//                strings) {
//            lines.add(Text.of(str.strip()));
//        }

        if (tooltipIsEmpty) {
            //button with no tooltip
            return Button.builder(buttonText, onPress).bounds(x + nudgeLeftEdge, y, width, height).build();
        } else {
            //return ButtonWidget.builder(buttonText,onPress).dimensions(x+nudgeLeftEdge, y, width, height).tooltip(Tooltip.of(toolTipText)).build();
            //1.19.3 required only
            ///////////////////////////////////////

            Tooltip bob = Tooltip.create(toolTipText);
            if (!ETF.isThisModLoaded("adaptive-tooltips")) {
                //split tooltip by our rules
                String[] strings = toolTipText.getString().split("\n");
                List<FormattedCharSequence> texts = new ArrayList<>();
                for (String str :
                        strings) {
                    texts.add(Component.nullToEmpty(str).getVisualOrderText());
                }

                //apply to tooltip object

                ((TooltipAccessor) bob).setCachedTooltip(texts);
            }
            ////////////////////////////////////////
            //create button
            return Button.builder(buttonText, onPress).bounds(x + nudgeLeftEdge, y, width, height).tooltip(bob).build();


        }
    }
}
