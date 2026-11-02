package com.twoi.combatdoll.combat;

import com.twoi.combatdoll.network.CombatNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import java.util.*;

/** One server-wide lease. Restores the previous freeze state, never writes a persistent frozen flag. */
public final class Chronostasis {
    private record Lease(UUID caster,UUID owner,long end,boolean wasFrozen){}
    private static final Map<MinecraftServer,Lease> LEASES=new WeakHashMap<>();
    public static boolean active(MinecraftServer server){return LEASES.containsKey(server);}
    public static boolean start(Entity actor,int ticks){
        var server=actor.getServer();if(server==null||active(server))return false;
        var lease=new Lease(actor.getUUID(),CombatActors.owner((net.minecraft.world.entity.LivingEntity)actor),
                CombatWorldData.get(server).clock()+ticks,server.tickRateManager().isFrozen());
        LEASES.put(server,lease);server.tickRateManager().setFrozen(true);
        CombatNetwork.freeze(server,lease.caster,lease.owner,ticks);return true;
    }
    public static boolean frozen(Entity entity){
        if(entity.level().isClientSide())return com.twoi.combatdoll.client.ClientTimeStop.frozen(entity);
        var server=entity.getServer();var lease=LEASES.get(server);
        return lease!=null&&!entity.getUUID().equals(lease.caster)&&!entity.getUUID().equals(lease.owner);
    }
    public static boolean exempt(Entity entity){
        if(entity.level().isClientSide())return com.twoi.combatdoll.client.ClientTimeStop.exempt(entity);
        var lease=LEASES.get(entity.getServer());return lease!=null&&(entity.getUUID().equals(lease.caster)||entity.getUUID().equals(lease.owner));
    }
    public static void tick(MinecraftServer server){
        var lease=LEASES.get(server);if(lease==null)return;
        var owner=server.getPlayerList().getPlayer(lease.owner);
        if(CombatWorldData.get(server).clock()>=lease.end||owner==null||!owner.isAlive())stop(server);
    }
    public static void actorLeft(Entity actor){var lease=LEASES.get(actor.getServer());if(lease!=null&&lease.caster.equals(actor.getUUID()))stop(actor.getServer());}
    public static void stop(MinecraftServer server){
        var lease=LEASES.remove(server);if(lease==null)return;
        server.tickRateManager().setFrozen(lease.wasFrozen);CombatNetwork.freeze(server,null,null,0);
    }
    public static void joining(net.minecraft.server.level.ServerPlayer player){var lease=LEASES.get(player.getServer());
        if(lease!=null)CombatNetwork.freeze(player,lease.caster,lease.owner,(int)Math.max(0,lease.end-CombatWorldData.get(player.getServer()).clock()));}
}
