package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.hypixel.HypixelMods;
import com.thelads.core.client.hypixel.HypixelTabFill;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiChat.class)
public abstract class GuiChatMixin {
    @Shadow protected GuiTextField inputField;

    @org.spongepowered.asm.mixin.injection.Inject(method = "initGui", at = @At("RETURN"))
    private void ladsChatInit(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (inputField != null) {
            inputField.setMaxStringLength(com.thelads.core.modules.UnlimitedCommandLengthModule.getCommandMaxLength(100));
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void ladsKeyTyped(char typedChar, int keyCode, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (keyCode == org.lwjgl.input.Keyboard.KEY_F && (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LCONTROL) || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RCONTROL))) {
            com.thelads.core.client.ChatSearch.toggle();
            net.minecraft.client.Minecraft.getMinecraft().ingameGUI.getChatGUI().refreshChat();
            ci.cancel();
            return;
        }

        if (keyCode == org.lwjgl.input.Keyboard.KEY_RETURN || keyCode == org.lwjgl.input.Keyboard.KEY_NUMPADENTER) {
            if (inputField != null) {
                String text = inputField.getText();
                String expanded = com.thelads.core.client.ChatShortcuts.expand(text);
                if (!expanded.equals(text)) {
                    inputField.setText(expanded);
                }
                if ((org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT) || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT))
                        && com.thelads.core.client.ChatPeek.keepOpenEnabled()) {
                    String msg = inputField.getText().trim();
                    if (!msg.isEmpty() && net.minecraft.client.Minecraft.getMinecraft().thePlayer != null) {
                        net.minecraft.client.Minecraft.getMinecraft().thePlayer.sendChatMessage(msg);
                    }
                    inputField.setText("");
                    ci.cancel();
                }
            }
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ladsChatMouseClicked(int mouseX, int mouseY, int mouseButton, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (mouseButton == 0 && com.thelads.core.client.ChatTabs.enabled()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            int tabCount = com.thelads.core.client.ChatTabs.getTabCount();
            int x = 2;
            int y = mc.currentScreen.height - (int) com.thelads.core.v1_8_9.feature.Options189.number("Chat", "Chat Height", 180) - 24;
            for (int i = 0; i < tabCount; i++) {
                String name = com.thelads.core.client.ChatTabs.getTab(i).getName();
                int width = mc.fontRendererObj.getStringWidth(name) + 6;
                if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + mc.fontRendererObj.FONT_HEIGHT + 4) {
                    com.thelads.core.client.ChatTabs.setActiveTab(i);
                    mc.ingameGUI.getChatGUI().refreshChat();
                    ci.cancel();
                    return;
                }
                x += width + 4;
            }
        }
    }
    @ModifyVariable(method = "onAutocompleteResponse([Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true)
    private String[] ladsHypixelTabComplete(String[] completions) {
        if (!HypixelSafetyManager.isHypixel() || !HypixelMods.isTabFillEnabled()) {
            return completions;
        }
        if (inputField == null) return completions;
        String text = inputField.getText();
        if (text == null || !text.startsWith("/")) return completions;

        Set<String> knownNames = HypixelTabFill.collectKnownNames();
        List<String> clientCompletions = HypixelTabFill.complete(text, knownNames);
        if (clientCompletions.isEmpty()) {
            return completions;
        }

        return HypixelTabFill.mergeCompletions(completions, clientCompletions);
    }
}
