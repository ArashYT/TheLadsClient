package com.thelads.core.client.hud;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.*;
public class BossBarHudElement extends HudElement {
    private int maximum(){var m=ModuleManager.getInstance().getModule("BossBar");return m!=null&&m.getOption("Maximum bars") instanceof SliderOption s?(int)s.getValue():5;}
    @Override public void prepareRender(LadsGraphics g,boolean editor){width=182;height=Math.max(19,Math.min(maximum(),Math.max(editor?1:0,g.getGame().bossBarCount()))*19);}
    @Override public void render(LadsGraphics g){if(optBool("Show bars",true))g.drawBossBars(x,y,maximum(),optBool("Show names",true),false);}
    @Override public void renderEditor(LadsGraphics g){g.drawBossBars(x,y,maximum(),optBool("Show names",true),true);}
}
