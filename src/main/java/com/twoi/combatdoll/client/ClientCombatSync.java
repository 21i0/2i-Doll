package com.twoi.combatdoll.client;
import com.twoi.combatdoll.network.CombatNetwork;
import com.twoi.combatdoll.combat.DollCombatAccess;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.client.Minecraft;

@net.neoforged.fml.common.EventBusSubscriber(modid=com.twoi.combatdoll.TwoICombatDollMod.MODID,value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientCombatSync {
    private record Pending(CombatNetwork.State state,int expires) { }
    private static final java.util.Map<java.util.UUID,Pending> PENDING=new java.util.LinkedHashMap<>();
    private static int ticks;
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    public static void receive(CombatNetwork.State state) {
        currentLevel();
        if(apply(state)) {PENDING.remove(state.uuid());return;}
        if(PENDING.size()>=32)PENDING.remove(PENDING.keySet().iterator().next());
        PENDING.put(state.uuid(),new Pending(state,ticks+100));
    }
    @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        ticks++;ClientCombatClock.tick();ClientTimeStop.tick();
        var level=currentLevel();
        if(level==null){PENDING.clear();return;}
        PENDING.values().removeIf(p->p.expires<ticks||apply(p.state));
    }
    private static net.minecraft.client.multiplayer.ClientLevel currentLevel(){
        var level=Minecraft.getInstance().level;
        if(lastLevel!=level){com.twoi.combatdoll.combat.CombatActors.clearClient();lastLevel=level;}
        return level;
    }
    private static boolean apply(CombatNetwork.State state){
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(state.dimension())) return false;
        var entity = level.getEntity(state.id());
        if (entity instanceof net.minecraft.world.entity.LivingEntity doll && (doll instanceof DollCombatAccess||doll instanceof net.minecraft.world.entity.player.Player) && doll.getUUID().equals(state.uuid())) {
            DollCombatAccess.state(doll).synchronize(state.start(),state.clock(),state.delta(),com.twoi.combatdoll.combat.WeaponKind.decode(state.kind()),state.armed());
            DollCombatAccess.state(doll).synchronizeScene(state.charging(),state.heldTicks(),state.origin(),state.anchor(),state.hole());
            ClientAstralAudio.observe(doll);
            return true;
        }
        return false;
    }
}
