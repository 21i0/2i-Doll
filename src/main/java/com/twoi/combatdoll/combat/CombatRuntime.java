package com.twoi.combatdoll.combat;

import com.twoi.combatdoll.TwoICombatDollMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Loaded companions only; no world/entity scan. Runs outside frozen level simulation. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID)
public final class CombatRuntime {
    private static final Map<MinecraftServer,Set<LivingEntity>> ACTORS=new WeakHashMap<>();
    public static void register(LivingEntity actor){if(actor.getServer()!=null)ACTORS.computeIfAbsent(actor.getServer(),s->new HashSet<>()).add(actor);}
    @SubscribeEvent public static void joined(EntityJoinLevelEvent e){if(!e.getLevel().isClientSide()&&e.getEntity() instanceof LivingEntity actor&&actor instanceof DollCombatAccess)register(actor);}
    @SubscribeEvent public static void left(EntityLeaveLevelEvent e){if(!e.getLevel().isClientSide()&&e.getEntity() instanceof LivingEntity actor){
        var set=ACTORS.get(actor.getServer());if(set!=null)set.remove(actor);Chronostasis.actorLeft(actor);
    }}
    @SubscribeEvent public static void pre(ServerTickEvent.Pre e){CombatWorldData.get(e.getServer()).tick();Chronostasis.tick(e.getServer());}
    @SubscribeEvent public static void post(ServerTickEvent.Post e){
        var set=ACTORS.get(e.getServer());if(set==null)return;
        for(var actor:List.copyOf(set)){
            if(actor.isRemoved()||!actor.isAlive()){set.remove(actor);continue;}
            if(com.twoi.combatdoll.duel.DollDuelArena.isDuelActor(actor))continue;
            CombatActors.state(actor).tick(actor,(net.minecraft.server.level.ServerLevel)actor.level());
            if(!(actor instanceof DollCombatAccess)&&CombatActors.state(actor).age(actor,0)<0)set.remove(actor);
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){Chronostasis.stop(e.getServer());ACTORS.remove(e.getServer());CombatActors.clear();}
}
