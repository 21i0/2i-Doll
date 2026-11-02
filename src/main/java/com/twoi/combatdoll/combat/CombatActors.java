package com.twoi.combatdoll.combat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.*;

public final class CombatActors {
    // Entity.equals compares numeric IDs: integrated server and client players share an ID.
    // Each logical side owns its own weak cache and accesses it on its own game thread.
    private static final Map<LivingEntity,TwoIGrimoireCombat> SERVER_PLAYERS=new WeakHashMap<>();
    private static final Map<LivingEntity,TwoIGrimoireCombat> CLIENT_PLAYERS=new WeakHashMap<>();
    public static TwoIGrimoireCombat state(LivingEntity actor){
        return actor instanceof DollCombatAccess doll?doll.combatState():(actor.level().isClientSide()?CLIENT_PLAYERS:SERVER_PLAYERS).computeIfAbsent(actor,k->new TwoIGrimoireCombat());
    }
    public static UUID owner(LivingEntity actor){return actor instanceof Player?actor.getUUID():((DollCombatAccess)actor).combatOwner();}
    public static boolean busy(LivingEntity actor){return actor instanceof DollCombatAccess doll&&doll.combatBusy();}
    public static void clear(){SERVER_PLAYERS.clear();}
    public static void clearClient(){CLIENT_PLAYERS.clear();}
}
