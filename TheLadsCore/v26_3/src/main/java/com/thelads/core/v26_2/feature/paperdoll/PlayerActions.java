package com.thelads.core.v26_2.feature.paperdoll;
import com.thelads.core.config.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
/** Extensible state catalog. Public boolean is* accessors added by mods are discovered too. */
public final class PlayerActions {
    private static final Map<PlayerActionOption,Predicate<Player>> states=new LinkedHashMap<>();
    private static boolean initialized;
    private PlayerActions() {}
    public static void register(String name,Predicate<Player> detector) {
        var module=ModuleManager.getInstance().getModule("Paperdoll");
        var option=module.getOption(name);
        PlayerActionOption action=option instanceof PlayerActionOption a?a:module.addOption(new PlayerActionOption(name,false));
        states.put(action,detector);
    }
    public static void initialize() {
        if(initialized)return;initialized=true;
        for(Pose pose:Pose.values()) register("Pose: "+words(pose.name().toLowerCase(Locale.ROOT).replace('_',' ')),p->p.getPose()==pose);
        Arrays.stream(LocalPlayer.class.getMethods()).filter(m->!Modifier.isStatic(m.getModifiers())&&m.getParameterCount()==0&&m.getReturnType()==boolean.class&&m.getName().matches("is[A-Z].*"))
            .sorted(Comparator.comparing(java.lang.reflect.Method::getName)).forEach(m->register("State: "+words(m.getName().substring(2).replaceAll("([a-z])([A-Z])","$1 $2")),p->{try{return (boolean)m.invoke(p);}catch(ReflectiveOperationException|RuntimeException e){return false;}}));
    }
    private static String words(String s){return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    static boolean active(Player player,boolean recentlyRiding) {
        boolean any=false;
        var module=ModuleManager.getInstance().getModule("Paperdoll");
        for(var o:module.getOptions()) if(o instanceof PlayerActionOption action) {
            boolean active=switch(action.getName()) {
                case "Sprinting"->player.isSprinting();case "Swimming"->player.isSwimming();case "Crawling"->player.isVisuallyCrawling();
                case "Crouching"->!recentlyRiding&&player.isCrouching();case "Creative Flying"->player.getAbilities().flying;
                case "Elytra Gliding"->player.isFallFlying();case "Riding"->player.isPassenger()||player.getPose().name().equals("SITTING");
                case "Spin Attacking"->player.isAutoSpinAttack();case "Using Items"->player.isUsingItem();
                case "Walking"->player.getDeltaMovement().horizontalDistanceSqr()>.0001;
                case "Standing"->player.onGround()&&player.getDeltaMovement().horizontalDistanceSqr()<=.0001;
                case "Jumping"->!player.onGround()&&player.getDeltaMovement().y>0;case "Falling"->!player.onGround()&&player.getDeltaMovement().y<-.08;
                case "Sleeping"->player.isSleeping();case "Climbing"->player.onClimbable();case "In Water"->player.isInWater();case "On Fire"->player.isOnFire();
                case "Attacking"->player.isSwinging();case "Blocking"->player.isBlocking();case "Hurt"->player.hurtTime>0;case "Dead"->!player.isAlive();case "Spectating"->player.isSpectator();
                default->states.containsKey(action)&&states.get(action).test(player);
            };
            action.detect(active);any|=action.get()&&active;
        }
        return any;
    }
}
