package com.twoi.combatdoll.client;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import java.util.UUID;

public final class ClientTimeStop {
    private static UUID caster,owner;
    private static long end;
    private static net.minecraft.client.resources.sounds.SimpleSoundInstance declaration;
    private static net.minecraft.client.multiplayer.ClientLevel soundLevel;
    public static void receive(UUID actor,UUID player,int ticks){
        var mc=Minecraft.getInstance();
        if(actor==null||ticks<=0)stopDeclaration();
        else if(!active()||!actor.equals(caster)){
            stopDeclaration();
            declaration=net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.twoi.combatdoll.TwoICombatDollMod.TIME_STOP.get(),1,1);
            soundLevel=mc.level;mc.getSoundManager().play(declaration);
        }
        caster=actor;owner=player;end=ClientCombatClock.now()+Math.max(0,ticks);
    }
    public static boolean active(){return caster!=null&&ClientCombatClock.now()<end;}
    public static boolean exempt(Entity entity){return active()&&(entity.getUUID().equals(caster)||entity.getUUID().equals(owner));}
    public static boolean frozen(Entity entity){return active()&&!exempt(entity);}
    public static float age(){return active()?162-(end-ClientCombatClock.now()):-1;}
    private static void stopDeclaration(){
        if(declaration!=null)Minecraft.getInstance().getSoundManager().stop(declaration);
        declaration=null;soundLevel=null;
    }
    public static void tick(){
        var mc=Minecraft.getInstance();
        if(mc.level==null||declaration!=null&&soundLevel!=mc.level){stopDeclaration();caster=null;owner=null;}
        // The natural local countdown does not truncate the declaration's tail;
        // the authoritative clear packet, cancellation or world leave cleans it.
        else if(ClientCombatClock.now()>end+5){caster=null;owner=null;}
    }
}
