package com.thelads.core.v26_2.gui;
import net.minecraft.client.gui.components.AbstractWidget;
import java.lang.reflect.Method;
/** Keeps the real optional Essential component/action while removing its menu-position proxy. */
public final class EssentialActions {
    private static boolean warned;
    private static final java.util.Set<Class<?>> WARNED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    /**
     * Essential's menu is an independent Elementa layer; removing a vanilla proxy cannot hide it. Essential adds the same
     * layer again whenever the screen is initialised again at the same width (back from More, Wardrobe or a resize), so
     * it is removed every frame; Essential's removeLayer does nothing for a layer that is not shown.
     */
    public static void suppressOverlay(net.minecraft.client.gui.screens.Screen screen){
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("essential"))return;
        try{
            Object handler=screen.getClass().getMethod("essential$getProxyHandler").invoke(screen);
            if(handler==null)return;
            Object layer=handler.getClass().getMethod("getLayer").invoke(handler);
            if(layer!=null){
                Class<?> util=Class.forName("gg.essential.util.GuiUtil");
                util.getMethod("removeLayer",Class.forName("gg.essential.gui.overlay.Layer")).invoke(util.getField("INSTANCE").get(null),layer);
            }
        }catch(NoSuchMethodException ignored){}
        catch(ReflectiveOperationException failure){if(!warned){warned=true;org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("Essential menu layer integration is unavailable",failure);}}
    }
    private static Object field(Object object,String name)throws ReflectiveOperationException{
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}
        return null;
    }
    private static Object findButton(Object component)throws ReflectiveOperationException{
        if(Class.forName("gg.essential.gui.common.MenuButton").isInstance(component))return component;
        Object children=component.getClass().getMethod("getChildren").invoke(component);
        for(Object child:(Iterable<?>)children){Object button=findButton(child);if(button!=null)return button;}
        return null;
    }
    public record Action(String label,Runnable press,boolean active) {}
    public static Action capture(net.minecraft.client.gui.screens.Screen screen,AbstractWidget widget){
        if(!widget.getClass().getName().startsWith("gg.essential."))return null;
        try {
            var type=widget.getClass();Object component=type.getMethod("getEssentialComponent").invoke(widget);
            String id=String.valueOf(type.getMethod("getEssentialId").invoke(widget));
            String key=id.toLowerCase(java.util.Locale.ROOT);
            if(component==null){
                Object container=field(widget,"essentialContainer");
                if(container!=null)component=findButton(container);
            }
            if(component==null||key.contains("badge")||key.contains("notification")||key.contains("player")||key.contains("new")||key.contains("count"))return null;
            Method action=null;
            for(Class<?> c=type;c!=null&&action==null;c=c.getSuperclass())for(var m:c.getDeclaredMethods())
                if(m.getName().equals("click")&&m.getParameterCount()==1&&!m.isBridge()&&m.getParameterTypes()[0].isInstance(component)){action=m;break;}
            if(action==null)return null;action.setAccessible(true);final Method click=action;final Object target=component;
            String label=key.contains("social")||key.contains("friend")?"Social":key.contains("wardrobe")||key.contains("cosmetic")?"Wardrobe":key.contains("picture")||key.contains("screenshot")?"Pictures":key.contains("host")||key.contains("invite")?"Host world":key.contains("setting")?"Essential":id.replaceAll("(?i)essential[._:-]?", "").replace('_',' ').replace('-',' ');
            return new Action(label,()->{try{click.invoke(widget,target);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Could not open Essential action "+id,failure);}},widget.active);
        }catch(ReflectiveOperationException failure){
            if(WARNED.add(widget.getClass()))org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("Essential menu integration unavailable for {}",widget.getClass().getName(),failure);
            return null;
        }
    }
}
