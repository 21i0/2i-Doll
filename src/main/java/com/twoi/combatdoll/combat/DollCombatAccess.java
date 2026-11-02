package com.twoi.combatdoll.combat;

import net.minecraft.world.entity.PathfinderMob;
import java.util.UUID;

/** Common contract for independent dolls and optional third-party companion integrations. */
public interface DollCombatAccess {
    TwoIGrimoireCombat combatState();
    UUID combatOwner();
    boolean combatBusy();
    default float combatDamage() { return 24; }
    default int combatTargetLimit() { return TwoIArcanaTimeline.MAX_TARGETS; }
    default WeaponKind combatKind() { return WeaponKind.STAFF; }
    static TwoIGrimoireCombat state(net.minecraft.world.entity.LivingEntity doll) { return CombatActors.state(doll); }
}
