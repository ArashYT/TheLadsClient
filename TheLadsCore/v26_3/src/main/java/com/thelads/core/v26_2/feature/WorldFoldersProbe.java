package com.thelads.core.v26_2.feature;

import com.thelads.core.shared.SharedContentQa;
import com.thelads.core.v26_2.gui.WorldSourcesScreen;
import com.thelads.core.v26_2.mixin.WorldStorageAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

public final class WorldFoldersProbe {
    public static void run(SharedContentQa qa) throws Exception {
        var mc=Minecraft.getInstance();
        if(mc.level!=null)return;
        var original=mc.gui.screen();var storage=mc.getLevelSource();
        try{
            var screen=new WorldSourcesScreen(original);mc.setScreenAndShow(screen);
            var search=screen.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();
            qa.check(screen.children().stream().anyMatch(c->c instanceof Button b&&b.getMessage().getString().startsWith("Global .minecraft:")),"World folders show canonical global storage");
            var categories=screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("All locations")).map(c->(Button)c).findFirst().orElseThrow();
            for(String label:java.util.List.of("Global .minecraft","Specific Version","Custom Instances","All locations")){
                categories.onPress(null);qa.check(categories.getMessage().getString().equals(label),"World folder category "+label);
            }
            search.setValue("no-such-instance-309124");
            qa.check(screen.children().stream().noneMatch(c->c instanceof Button b&&b.getMessage().getString().startsWith("Global .minecraft:")),"World folder search filters actual rows");
            search.setValue("");
            var global=screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith("Global .minecraft:")).map(c->(Button)c).findFirst().orElseThrow();
            global.onPress(null);
            qa.check(mc.getLevelSource().getBaseDir().toRealPath().equals(storage.getBaseDir().toRealPath()),"World selection retains canonical save path");
            qa.check(mc.gui.screen() instanceof SelectWorldScreen,"Folder choice opens vanilla world selection with version safeguards");
            qa.check(mc.gui.screen().children().stream().anyMatch(c->c instanceof Button b&&b.getMessage().getString().startsWith("World folders:")),"World list exposes folder categories");
            qa.check(mc.gui.screen().children().stream().anyMatch(c->c instanceof EditBox),"World-name search remains available");
        }finally{((WorldStorageAccessor)mc).ladsSetLevelSource(storage);mc.setScreenAndShow(original);}
    }
}
