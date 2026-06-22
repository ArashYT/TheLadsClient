/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.mc.ComponentProvider
 *  dev.tr7zw.trender.gui.GuiDescription
 *  dev.tr7zw.trender.gui.client.AbstractConfigScreen
 *  dev.tr7zw.trender.gui.client.BackgroundPainter
 *  dev.tr7zw.trender.gui.widget.WButton
 *  dev.tr7zw.trender.gui.widget.WGridPanel
 *  dev.tr7zw.trender.gui.widget.WListPanel
 *  dev.tr7zw.trender.gui.widget.WPanel
 *  dev.tr7zw.trender.gui.widget.WTabPanel
 *  dev.tr7zw.trender.gui.widget.WWidget
 *  dev.tr7zw.trender.gui.widget.data.Insets
 *  dev.tr7zw.trender.gui.widget.icon.Icon
 *  dev.tr7zw.trender.gui.widget.icon.ItemIcon
 *  net.minecraft.client.Minecraft
 *  net.minecraft.network.chat.CommonComponents
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.world.item.Items
 */
package dev.tr7zw.notenoughanimations.config;

import dev.tr7zw.notenoughanimations.NEAnimationsLoader;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.RotationLock;
import dev.tr7zw.notenoughanimations.versionless.animations.BowAnimation;
import dev.tr7zw.notenoughanimations.versionless.animations.HoldUpModes;
import dev.tr7zw.notenoughanimations.versionless.animations.HoldUpTarget;
import dev.tr7zw.notenoughanimations.versionless.config.Config;
import dev.tr7zw.transition.mc.ComponentProvider;
import dev.tr7zw.trender.gui.GuiDescription;
import dev.tr7zw.trender.gui.client.AbstractConfigScreen;
import dev.tr7zw.trender.gui.client.BackgroundPainter;
import dev.tr7zw.trender.gui.widget.WButton;
import dev.tr7zw.trender.gui.widget.WGridPanel;
import dev.tr7zw.trender.gui.widget.WListPanel;
import dev.tr7zw.trender.gui.widget.WPanel;
import dev.tr7zw.trender.gui.widget.WTabPanel;
import dev.tr7zw.trender.gui.widget.WWidget;
import dev.tr7zw.trender.gui.widget.data.Insets;
import dev.tr7zw.trender.gui.widget.icon.Icon;
import dev.tr7zw.trender.gui.widget.icon.ItemIcon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;

public class ConfigScreenProvider {
    public static Screen createConfigScreen(Screen parent) {
        return new CustomConfigScreen(parent).createScreen();
    }

    private static class CustomConfigScreen
    extends AbstractConfigScreen {
        public CustomConfigScreen(Screen previous) {
            super((Component)ComponentProvider.translatable((String)"text.nea.title"), previous);
            WGridPanel root = new WGridPanel(8);
            root.setInsets(Insets.ROOT_PANEL);
            this.setRootPanel((WPanel)root);
            WTabPanel wTabPanel = new WTabPanel();
            List<Object> options = new ArrayList<>();
            options.add(this.getSplitLine("text.nea.line.animations"));
            options.add(this.getOnOffOption("text.nea.enable.inworldmaprendering", () -> NEABaseMod.config.enableInWorldMapRendering, b -> {
                NEABaseMod.config.enableInWorldMapRendering = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.ladderanimation", () -> NEABaseMod.config.enableLadderAnimation, b -> {
                NEABaseMod.config.enableLadderAnimation = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.rotatetoladder", () -> NEABaseMod.config.enableRotateToLadder, b -> {
                NEABaseMod.config.enableRotateToLadder = b;
            }));
            options.add(this.getDoubleOption("text.nea.ladderAnimationAmplifier", 0.1f, 0.5, 0.01f, () -> NEABaseMod.config.ladderAnimationAmplifier, i -> {
                NEABaseMod.config.ladderAnimationAmplifier = (float)i;
            }));
            options.add(this.getDoubleOption("text.nea.ladderAnimationArmHeight", 1.0, 3.0, 0.1f, () -> NEABaseMod.config.ladderAnimationArmHeight, i -> {
                NEABaseMod.config.ladderAnimationArmHeight = (float)i;
            }));
            options.add(this.getDoubleOption("text.nea.ladderAnimationArmSpeed", 1.0, 4.0, 0.1f, () -> NEABaseMod.config.ladderAnimationArmSpeed, i -> {
                NEABaseMod.config.ladderAnimationArmSpeed = (float)i;
            }));
            options.add(this.getOnOffOption("text.nea.enable.crawling", () -> NEABaseMod.config.enableCrawlingAnimation, b -> {
                NEABaseMod.config.enableCrawlingAnimation = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.eatdrinkanimation", () -> NEABaseMod.config.enableEatDrinkAnimation, b -> {
                NEABaseMod.config.enableEatDrinkAnimation = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.rowboatanimation", () -> NEABaseMod.config.enableRowBoatAnimation, b -> {
                NEABaseMod.config.enableRowBoatAnimation = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.horseanimation", () -> NEABaseMod.config.enableHorseAnimation, b -> {
                NEABaseMod.config.enableHorseAnimation = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.enableHorseLegAnimation", () -> NEABaseMod.config.enableHorseLegAnimation, b -> {
                NEABaseMod.config.enableHorseLegAnimation = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.itemSwapAnimation", () -> NEABaseMod.config.itemSwapAnimation, b -> {
                NEABaseMod.config.itemSwapAnimation = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.petAnimation", () -> NEABaseMod.config.petAnimation, b -> {
                NEABaseMod.config.petAnimation = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.freezingAnimation", () -> NEABaseMod.config.freezingAnimation, b -> {
                NEABaseMod.config.freezingAnimation = b;
            }));
            options.add(this.getEnumOption("text.nea.enable.bowAnimation", BowAnimation.class, () -> NEABaseMod.config.bowAnimation, b -> {
                NEABaseMod.config.bowAnimation = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.customBowRotationLock", () -> NEABaseMod.config.customBowRotationLock, b -> {
                NEABaseMod.config.customBowRotationLock = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.clampCrossbowAnimations", () -> NEABaseMod.config.clampCrossbowAnimations, b -> {
                NEABaseMod.config.clampCrossbowAnimations = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.burningAnimation", () -> NEABaseMod.config.burningAnimation, b -> {
                NEABaseMod.config.burningAnimation = b;
            }));
            options.add(this.getSplitLine(""));
            options.add(this.getSplitLine("text.nea.line.smoothing"));
            options.add(this.getOnOffOption("text.nea.enable.animationsmoothing", () -> NEABaseMod.config.enableAnimationSmoothing, b -> {
                NEABaseMod.config.enableAnimationSmoothing = b;
            }));
            options.add(this.getOnOffOption("text.nea.disableLegSmoothing", () -> NEABaseMod.config.disableLegSmoothing, b -> {
                NEABaseMod.config.disableLegSmoothing = b;
            }));
            options.add(this.getDoubleOption("text.nea.smoothingSpeed", 0.01f, 1.0, 0.1f, () -> NEABaseMod.config.animationSmoothingSpeed, i -> {
                NEABaseMod.config.animationSmoothingSpeed = (float)i;
            }));
            options.add(this.getSplitLine(""));
            options.add(this.getSplitLine("text.nea.line.rotation"));
            options.add(this.getEnumOption("text.nea.rotationlock", RotationLock.class, () -> NEABaseMod.config.rotationLock, b -> {
                NEABaseMod.config.rotationLock = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.limitRotationLockToFP", () -> NEABaseMod.config.limitRotationLockToFP, b -> {
                NEABaseMod.config.limitRotationLockToFP = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.applyRotationLockToEveryone", () -> NEABaseMod.config.applyRotationLockToEveryone, b -> {
                NEABaseMod.config.applyRotationLockToEveryone = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.rotationlocking", () -> NEABaseMod.config.enableRotationLocking, b -> {
                NEABaseMod.config.enableRotationLocking = b;
            }));
            options.add(this.getSplitLine(""));
            options.add(this.getSplitLine("text.nea.line.rotationAngle"));
            options.add(this.getDoubleOption("text.nea.maxNormalAngle", 0.0, 50.0, 0.1f, () -> NEABaseMod.config.maxNormalAngle, i -> {
                NEABaseMod.config.maxNormalAngle = (float)i;
            }));
            options.add(this.getDoubleOption("text.nea.maxBlockingAngle", 0.0, 15.0, 0.1f, () -> NEABaseMod.config.maxBlockingAngle, i -> {
                NEABaseMod.config.maxBlockingAngle = (float)i;
            }));
            options.add(this.getSplitLine(""));
            options.add(this.getSplitLine("text.nea.line.holdup"));
            options.add(this.getEnumOption("text.nea.holdUpItemsMode", HoldUpModes.class, () -> NEABaseMod.config.holdUpItemsMode, b -> {
                NEABaseMod.config.holdUpItemsMode = b;
            }));
            options.add(this.getBooleanOption("text.nea.animateLanterns", () -> NEABaseMod.config.animateLanterns, b -> {
                NEABaseMod.config.animateLanterns = b;
            }));
            options.add(this.getDoubleOption("text.nea.holdUpItemOffset", -0.5, 0.4f, 0.1f, () -> NEABaseMod.config.holdUpItemOffset, i -> {
                NEABaseMod.config.holdUpItemOffset = (float)i;
            }));
            options.add(this.getEnumOption("text.nea.holdUpTarget", HoldUpTarget.class, () -> NEABaseMod.config.holdUpTarget, b -> {
                NEABaseMod.config.holdUpTarget = b;
            }));
            options.add(this.getDoubleOption("text.nea.holdUpCameraOffset", -0.3f, 0.6f, 0.1f, () -> NEABaseMod.config.holdUpCameraOffset, i -> {
                NEABaseMod.config.holdUpCameraOffset = (float)i;
            }));
            options.add(this.getOnOffOption("text.nea.enable.holdUpOnlySelf", () -> NEABaseMod.config.holdUpOnlySelf, b -> {
                NEABaseMod.config.holdUpOnlySelf = b;
            }));
            options.add(this.getSplitLine(""));
            options.add(this.getSplitLine("text.nea.line.fixes"));
            options.add(this.getOnOffOption("text.nea.enable.tweakElytraAnimation", () -> NEABaseMod.config.tweakElytraAnimation, b -> {
                NEABaseMod.config.tweakElytraAnimation = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.dontholditemsinbed", () -> NEABaseMod.config.dontHoldItemsInBed, b -> {
                NEABaseMod.config.dontHoldItemsInBed = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.freezearmsinbed", () -> NEABaseMod.config.freezeArmsInBed, b -> {
                NEABaseMod.config.freezeArmsInBed = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.offhandhiding", () -> NEABaseMod.config.enableOffhandHiding, b -> {
                NEABaseMod.config.enableOffhandHiding = b;
            }));
            options.add(this.getSplitLine(""));
            options.add(this.getSplitLine("text.nea.line.fun"));
            options.add(this.getOnOffOption("text.nea.enable.narutoRunning", () -> NEABaseMod.config.narutoRunning, b -> {
                NEABaseMod.config.narutoRunning = b;
            }));
            options.add(this.getOnOffOption("text.nea.enable.huggingAnimation", () -> NEABaseMod.config.huggingAnimation, b -> {
                NEABaseMod.config.huggingAnimation = b;
            }));
            options.add(this.getSplitLine(""));
            options.add(this.getSplitLine("text.nea.line.legacy"));
            options.add(this.getOnOffOption("text.nea.enable.fallingAnimation", () -> NEABaseMod.config.fallingAnimation, b -> {
                NEABaseMod.config.fallingAnimation = b;
            }));
            WListPanel optionList = this.createOptionList((List)options);
            optionList.setGap(-1);
            optionList.setSize(280, 180);
            wTabPanel.add((WWidget)optionList, b -> b.title((Component)ComponentProvider.translatable((String)"text.nea.tab.settings")).icon((Icon)new ItemIcon(Items.FILLED_MAP)));
            WGridPanel itemTab = this.createItemTab(key -> NEABaseMod.config.holdingItems.contains(this.getResourceString((ResourceKey)key.getKey())), (b, i) -> {
                String key = this.getResourceString((ResourceKey)i.getKey());
                if (b.booleanValue()) {
                    NEABaseMod.config.holdingItems.add(key);
                    NEAnimationsLoader.INSTANCE.animationProvider.refreshEnabledAnimations();
                } else {
                    NEABaseMod.config.holdingItems.remove(key);
                    NEAnimationsLoader.INSTANCE.animationProvider.refreshEnabledAnimations();
                }
                NEAnimationsLoader.INSTANCE.writeConfig();
            });
            wTabPanel.add((WWidget)itemTab, b -> b.title((Component)ComponentProvider.translatable((String)"text.nea.tab.holdup")).icon((Icon)new ItemIcon(Items.TORCH)));
            wTabPanel.layout();
            root.add((WWidget)wTabPanel, 0, 2);
            WGridPanel lanternTab = this.createItemTab(key -> NEABaseMod.config.lanternItems.contains(this.getResourceString((ResourceKey)key.getKey())), (b, i) -> {
                String key = this.getResourceString((ResourceKey)i.getKey());
                if (b.booleanValue()) {
                    NEABaseMod.config.lanternItems.add(key);
                    NEAnimationsLoader.INSTANCE.animationProvider.refreshEnabledAnimations();
                } else {
                    NEABaseMod.config.lanternItems.remove(key);
                    NEAnimationsLoader.INSTANCE.animationProvider.refreshEnabledAnimations();
                }
                NEAnimationsLoader.INSTANCE.writeConfig();
            });
            wTabPanel.add((WWidget)lanternTab, b -> b.title((Component)ComponentProvider.translatable((String)"text.nea.tab.lantern")).icon((Icon)new ItemIcon(Items.LANTERN)));
            wTabPanel.layout();
            root.add((WWidget)wTabPanel, 0, 2);
            WButton doneButton = new WButton(CommonComponents.GUI_DONE);
            doneButton.setOnClick(() -> {
                this.save();
                Minecraft.getInstance().setScreenAndShow(previous);
            });
            root.add((WWidget)doneButton, 0, 27, 6, 2);
            WButton resetButton = new WButton((Component)ComponentProvider.translatable((String)"controls.reset"));
            resetButton.setOnClick(() -> {
                this.reset();
                root.layout();
            });
            root.add((WWidget)resetButton, 37, 27, 6, 2);
            root.setBackgroundPainter(BackgroundPainter.VANILLA);
            root.validate((GuiDescription)this);
            root.setHost((GuiDescription)this);
        }

        private String getResourceString(ResourceKey key) {
            return key.identifier().toString();
        }

        public void reset() {
            NEABaseMod.config = new Config();
            NEAnimationsLoader.INSTANCE.writeConfig();
        }

        public void save() {
            NEAnimationsLoader.INSTANCE.writeConfig();
            NEAnimationsLoader.INSTANCE.animationProvider.refreshEnabledAnimations();
        }
    }
}

