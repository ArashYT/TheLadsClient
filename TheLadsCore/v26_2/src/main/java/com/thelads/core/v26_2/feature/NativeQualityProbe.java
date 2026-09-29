package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import com.thelads.core.v26_2.gui.LadsSettingsScreen26;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.TooltipDisplay;
import org.slf4j.LoggerFactory;

/** Opt-in isolated QA: exercise transformed Minecraft methods and restore every edited preference. */
final class NativeQualityProbe {
    private static boolean done;
    private static int passed;
    private NativeQualityProbe() {}

    static void tick() {
        if (done || !(Boolean.getBoolean("thelads.verifyNativeFeatures") || Boolean.getBoolean("thelads.verifyIntegrations"))
            || Minecraft.getInstance().level == null || Minecraft.getInstance().player == null
            || Minecraft.getInstance().getEntityRenderDispatcher().camera == null) return;
        if (Boolean.getBoolean("thelads.verifyInput") && !NativeFeatures.interactive()) return;
        done = true;
        Map<Module, Boolean> states = new LinkedHashMap<>();
        Map<Module, Long> modified = new LinkedHashMap<>();
        Map<Option, JsonElement> preferences = new LinkedHashMap<>();
        for (String name : List.of("EnhancedToolbars", "EnhancedTooltips", "HideChatIndicators")) {
            Module module = ModuleManager.getInstance().getModule(name);
            states.put(module, module.isEnabled());
            modified.put(module, module.getLastModified());
            for (Option option : module.getOptions()) preferences.put(option, option.save().deepCopy());
        }
        try {
            if(Boolean.getBoolean("thelads.verifyRequestedFeaturesOnly")){
                passed+=NativeRequestProbe.run();
                LoggerFactory.getLogger("TheLadsCore").info("Lads native feature probe END: {} passed, 0 failed; requested features only",passed);
                return;
            }
            // Menu close persists configuration. Exercise it before changing any QA preferences.
            if (Boolean.getBoolean("thelads.verifyInput")) inputPipeline();
            Module bars = NativeQualityOfLife.module("EnhancedToolbars");
            Module tips = NativeQualityOfLife.module("EnhancedTooltips");
            bars.setEnabled(false);
            tips.setEnabled(false);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.setDamageValue(100);
            int expected = sword.getMaxDamage() - 100;
            require(!lines(sword, TooltipFlag.NORMAL).contains("Durability:"), "disabled durability preserves vanilla basic tooltip");
            bars.setEnabled(true);
            set(bars, "Detailed Durability", true);
            set(bars, "Show Max Durability", true);
            require(lines(sword, TooltipFlag.NORMAL).contains("Durability: " + expected + " / " + sword.getMaxDamage()), "remaining and maximum durability");
            List<Component> advanced = sword.getTooltipLines(Item.TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, TooltipFlag.ADVANCED);
            require(advanced.stream().filter(line -> line.getString().contains("Durability:")).count() == 1, "advanced durability has no duplicate line");
            sword.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.DAMAGE, true));
            require(!lines(sword, TooltipFlag.NORMAL).contains("Durability:"), "hidden durability component stays hidden");
            sword.remove(DataComponents.TOOLTIP_DISPLAY);
            set(bars, "Show Max Durability", false);
            require(lines(sword, TooltipFlag.NORMAL).contains("Durability: " + expected) && !lines(sword, TooltipFlag.NORMAL).contains("Durability: " + expected + " /"), "maximum toggle");
            set(bars, "Detailed Durability", false);
            require(!lines(sword, TooltipFlag.NORMAL).contains("Durability:"), "detail toggle");
            set(bars, "Show Item Attributes", true);
            int attributes = sword.getTooltipLines(Item.TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, TooltipFlag.NORMAL).size();
            set(bars, "Show Item Attributes", false);
            require(sword.getTooltipLines(Item.TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, TooltipFlag.NORMAL).size() < attributes, "native attribute lines obey toggle");
            tips.setEnabled(true);
            set(tips, "Show Item ID", true);
            set(tips, "Show Food Values", true);
            set(tips, "Show Component Count", true);
            require(lines(sword, TooltipFlag.NORMAL).contains("minecraft:diamond_sword"), "item identifier");
            require(lines(sword, TooltipFlag.NORMAL).contains("Components: "), "real component count");
            require(lines(new ItemStack(Items.APPLE), TooltipFlag.NORMAL).contains("Food: +4 hunger"), "real food values");
            sword.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
            require(sword.getTooltipLines(Item.TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, TooltipFlag.NORMAL).isEmpty(), "hidden tooltip remains empty");
            sword.remove(DataComponents.TOOLTIP_DISPLAY);
            Module chat = NativeQualityOfLife.module("HideChatIndicators");
            GuiMessageTag tag = GuiMessageTag.system();
            MessageSignature signature = new MessageSignature(new byte[256]);
            GuiMessage message = new GuiMessage(1, Component.literal("QA"), signature, GuiMessageSource.SYSTEM_SERVER, tag);
            GuiMessage.Line line = new GuiMessage.Line(message, Component.literal("QA").getVisualOrderText(), true);
            chat.setEnabled(false);
            require(line.tag() == tag, "chat tag available with feature disabled");
            chat.setEnabled(true);
            require(line.tag() == null, "chat line indicator hidden immediately");
            require(message.tag() == tag && message.signature() == signature, "chat parent tag and signature retained");
            chat.setEnabled(false);
            require(line.tag() == tag, "existing chat indicator restored immediately");
            passed += NativeNametagConnectionProbe.run();
            passed += NativeBackgroundFrameProbe.run();
            passed += NativeViewDistanceProbe.run();
            passed += NativeKillBannerProbe.run();
            passed += NativeShulkerProbe.run();
            passed += com.thelads.core.v26_2.feature.food.NativeFoodProbe.run();
            passed += NativeShulkerParityProbe.run();
            passed += NativeRequestProbe.run();
            LoggerFactory.getLogger("TheLadsCore").info("Lads native feature probe END: {} passed, 0 failed", passed);
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads native feature probe FAILED after {} checks", passed, failure);
        } finally {
            preferences.forEach(Option::load);
            states.forEach(Module::setEnabled);
            modified.forEach(Module::setLastModified);
        }
    }
    private static void inputPipeline() throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        Screen previousScreen = mc.gui.screen();
        boolean wasDown = NativeKeyBindings.MODULES.isDown();
        KeyEvent rightShift = new KeyEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, 54, GLFW.GLFW_MOD_SHIFT);
        var dispatch = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
        dispatch.setAccessible(true);
        int before = passed;
        try {
            require(NativeKeyBindings.MODULES.matches(rightShift), "isolated QA retains default Right Shift binding");
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_PRESS, rightShift);
            Screen menu = mc.gui.screen();
            require(menu instanceof LadsSettingsScreen26, "Right Shift opens mods through real keyboard handler");
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_REPEAT, rightShift);
            require(mc.gui.screen() == menu, "native repeated press does not close menu");
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_RELEASE, rightShift);
            require(mc.gui.screen() == menu, "native release retains menu");
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_PRESS, rightShift);
            require(mc.gui.screen() == null, "second Right Shift closes to gameplay");
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_RELEASE, rightShift);
            ChatScreen chat = new ChatScreen("QA draft preserved", false);
            mc.gui.setScreen(chat);
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_PRESS, rightShift);
            require(mc.gui.screen() == chat, "Right Shift preserves open chat");
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_RELEASE, rightShift);
            require(mc.gui.screen() == chat, "release preserves open chat");
            PauseScreen pause = new PauseScreen(true);
            mc.gui.setScreen(pause);
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_PRESS, rightShift);
            require(mc.gui.screen() instanceof LadsSettingsScreen26, "Right Shift opens mods from pause menu");
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_RELEASE, rightShift);
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_PRESS, rightShift);
            require(mc.gui.screen() == pause, "closing mods returns to original pause screen");
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_RELEASE, rightShift);
            require(!NativeKeyBindings.MODULES.isDown(), "menu press does not latch gameplay key state");
            LoggerFactory.getLogger("TheLadsCore").info("Lads native input pipeline END: {} passed, 0 failed (synthetic GLFW 344 through KeyboardHandler)", passed - before);
        } finally {
            dispatch.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_RELEASE, rightShift);
            mc.gui.setScreen(previousScreen);
            NativeKeyBindings.MODULES.setDown(wasDown);
        }
    }
    private static String lines(ItemStack item, TooltipFlag flag) {
        return item.getTooltipLines(Item.TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, flag)
            .stream().map(Component::getString).reduce("", (left, right) -> left + "\n" + right);
    }
    private static void set(Module module, String name, boolean enabled) { ((BoolOption) module.getOption(name)).set(enabled); }
    private static void require(boolean result, String name) {
        if (!result) throw new IllegalStateException(name);
        passed++;
    }
}
