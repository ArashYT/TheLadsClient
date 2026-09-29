package com.thelads.core.v26_2.mixin;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.controls.*;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
@Mixin(KeyBindsScreen.class)
public abstract class KeySearchMixin extends OptionsSubScreen {
    @Shadow private KeyBindsList keyBindsList;
    @Unique private EditBox ladsSearch;
    protected KeySearchMixin(Screen parent,Options options,Component title){super(parent,options,title);}
    @Inject(method="addContents",at=@At("TAIL"),require=1)
    private void ladsSearchControls(CallbackInfo ci){
        var all=List.copyOf(keyBindsList.children());
        layout.setHeaderHeight(62);
        ladsSearch=new EditBox(font,width/2-110,33,220,20,Component.literal("Search controls"));
        ladsSearch.setHint(Component.literal("Search controls or assigned key..."));
        ladsSearch.setResponder(value->{
            String query=value.toLowerCase(Locale.ROOT).trim();
            keyBindsList.replaceEntries(query.isEmpty()?all:all.stream().filter(e->e instanceof KeyEntryAccessor).filter(e->{
                var key=((KeyEntryAccessor)e).ladsKey();
                return (I18n.get(key.getName())+" "+key.getTranslatedKeyMessage().getString()).toLowerCase(Locale.ROOT).contains(query);
            }).toList());
            keyBindsList.setScrollAmount(0);
        });
        addRenderableWidget(ladsSearch);
    }
    @Inject(method="repositionElements",at=@At("TAIL"),require=1)
    private void ladsPositionSearch(CallbackInfo ci){if(ladsSearch!=null){ladsSearch.setX(width/2-110);ladsSearch.setY(33);}}
}
