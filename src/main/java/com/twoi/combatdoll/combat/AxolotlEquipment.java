package com.twoi.combatdoll.combat;

import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** The optional doll retains its own carrier; only this bridge owns the real passenger transition. */
public final class AxolotlEquipment {
    private AxolotlEquipment(){}
    public static void beforeReclaim(LivingEntity doll,Player player){
        if(doll.level().isClientSide||!(doll instanceof DollCombatAccess access)||!player.getUUID().equals(access.combatOwner())
                ||!player.isAlive()||player.isSpectator()||player.distanceToSqr(doll)>36||player.getInventory().getFreeSlot()<0)return;
        if(doll.getVehicle() instanceof CangshengAxolotlEntity pet)pet.retireIntoDoll(doll);
    }
    public static void restoreMount(LivingEntity doll){
        if(!(doll.level() instanceof ServerLevel level)||!(doll instanceof DollCombatAccess access)||doll.isPassenger()||!doll.isAlive())return;
        var state=CombatActors.state(doll);if(!state.armed()||state.kind()!=WeaponKind.AXOLOTL||access.combatOwner()==null)return;
        // An unloaded doll can retain the previous generation after its mount
        // was destroyed and returned safely through the save-owned pending row.
        // Clear that retired copy so it cannot block a future genuine pickup.
        if(!AxolotlLeaseData.get(level.getServer()).currentCarrier(state.weapon(),access.combatOwner())){
            state.disarm();com.twoi.combatdoll.network.CombatNetwork.send(doll);return;
        }
        var owner=level.getServer().getPlayerList().getPlayer(access.combatOwner());if(owner==null||owner.level()!=level)return;
        // acquire rejects old copies and an existing, temporarily unloaded mount.
        CangshengAxolotlEntity.deploy(owner,state.weapon(),doll.position(),doll);
    }
}
