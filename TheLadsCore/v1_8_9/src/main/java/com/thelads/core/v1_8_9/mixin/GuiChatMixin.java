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
