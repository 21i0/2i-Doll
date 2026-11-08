package com.twoi.combatdoll.combat;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

public final class CombatStopGoal extends Goal {
    private final PathfinderMob doll;
    public CombatStopGoal(PathfinderMob doll) { this.doll=doll;setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP,Flag.LOOK)); }
    @Override public boolean canUse() { return DollCombatAccess.state(doll).age(doll,0)>=0
            ||doll.getVehicle() instanceof com.twoi.combatdoll.entity.CangshengAxolotlEntity mount&&mount.mountMode(); }
    @Override public void tick() { doll.getNavigation().stop(); }
}
