package com.twoi.combatdoll.combat;

import com.customdoll.longhuan.content.entity.DollEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import java.util.function.Consumer;

/** The existing companion flies; the bound book remains its one saved weapon. */
public final class LonghuanBookFlightGoal extends Goal {
    private final PathfinderMob doll;
    private final Consumer<Boolean> flightControls;
    private boolean active, previousGravity, following, direct;
    private int repath, stationaryTicks, recoveryCooldown;
    private Vec3 hover, lastPosition, flightTarget;

    public LonghuanBookFlightGoal(PathfinderMob doll, Consumer<Boolean> flightControls) {
        this.doll=doll;this.flightControls=flightControls;
        setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP,Flag.LOOK));
    }

    /** Shared by server controls and client pose; busy companions still have support. */
    public static boolean riding(LivingEntity actor) {
        return actor instanceof DollEntity doll && !doll.isNoAi() && !doll.isPassenger()
                && actor.isAlive() && !actor.isRemoved()
                && CombatActors.state(actor).armed() && CombatActors.state(actor).kind()==WeaponKind.BOOK;
    }

    @Override public boolean canUse(){return riding(doll);}
    @Override public boolean canContinueToUse(){return active&&canUse();}
    @Override public boolean requiresUpdateEveryTick(){return true;}

    @Override public void start() {
        if(active)return;
        previousGravity=doll.isNoGravity();active=true;
        flightControls.accept(true);
        doll.setNoGravity(true);doll.fallDistance=0;
        hover=doll.position();
        for(int i=0;i<6;i++){
            Vec3 takeoff=doll.position().add(0,.65+i*.5,0);
            if(clear(takeoff)){hover=takeoff;break;}
        }
        lastPosition=doll.position();repath=0;stationaryTicks=0;recoveryCooldown=0;following=false;direct=false;flightTarget=null;
    }

    /** Goals do not tick under NoAI; also release transient controls on that path. */
    public void refreshLifecycle(){if(active&&!canUse())stop();}

    /** Entity.saveWithoutId wrote NoGravity before this callback; avoid orphan flight on load. */
    public void saveGravity(CompoundTag tag){if(active)tag.putBoolean("NoGravity",previousGravity);}

    @Override public void stop() {
        if(!active)return;
        hold();flightControls.accept(false);doll.setNoGravity(previousGravity);
        doll.fallDistance=0;active=false;hover=null;following=false;flightTarget=null;direct=false;
    }

    @Override public void tick() {
        if(!active)return;
        doll.setNoGravity(true);doll.fallDistance=0;
        if(recoveryCooldown>0)recoveryCooldown--;
        var id=((DollCombatAccess)doll).combatOwner();
        Player owner=id==null?null:doll.level().getPlayerByUUID(id);
        boolean unavailable=owner==null||!owner.isAlive()||owner.isSpectator();
        boolean paused=CombatActors.state(doll).age(doll,0)>=0||CombatActors.busy(doll)
                ||((DollEntity)doll).getPoseAction()==DollEntity.ACTION_PATTED;
        if(unavailable||paused){
            hold();
            // Receiving the book during a dance/pat must not cancel takeoff.
            // Store the lift target now; movement and safety queries wait until resume.
            hover=doll.position().add(0,doll.onGround()?.65:0,0);
            following=false;repath=0;stationaryTicks=0;flightTarget=null;direct=false;return;
        }
        doll.getLookControl().setLookAt(owner,40,40);

        double distance=doll.distanceToSqr(owner);
        if(distance>36||Math.abs(doll.getY()-owner.getY()-1.1)>1.8)following=true;
        if(following&&distance<12.25&&Math.abs(doll.getY()-owner.getY()-1.1)<.7){following=false;hover=doll.position();repath=0;}
        if(following&&recoveryCooldown==0&&(distance>32*32||stationaryTicks>=80)&&distance>36){
            recover(owner);recoveryCooldown=80;stationaryTicks=0;
        }
        if(--repath<=0){
            repath=10;
            if(following&&doll.position().distanceToSqr(lastPosition)<.04)stationaryTicks+=10;
            else stationaryTicks=0;
            lastPosition=doll.position();
            flightTarget=following?followPoint(owner):hover;
            direct=flightTarget!=null&&doll.position().distanceToSqr(flightTarget)<=9
                    &&clearBox(doll.getBoundingBox().expandTowards(flightTarget.subtract(doll.position())));
            if(flightTarget==null){hold();return;}
            if(direct)doll.getNavigation().stop();
            // Long paths use flying navigation around solids. Short clear paths
            // use exact doubles: vanilla path nodes round fractional Y down.
            else if(clear(flightTarget))doll.getNavigation().moveTo(flightTarget.x,flightTarget.y,flightTarget.z,1.0);
            else{hold();flightTarget=null;}
        }
        if(direct&&flightTarget!=null){
            if(doll.position().distanceToSqr(flightTarget)<.04)hold();
            else doll.getMoveControl().setWantedPosition(flightTarget.x,flightTarget.y,flightTarget.z,1.0);
        }
    }

    private Vec3 followPoint(Player owner) {
        Vec3 radial=doll.position().subtract(owner.position()).multiply(1,0,1);
        if(radial.lengthSqr()<.01)radial=owner.getLookAngle().multiply(-1,0,-1);
        if(radial.lengthSqr()<.01)radial=new Vec3(0,0,1);
        Vec3 target=owner.position().add(radial.normalize().scale(2.8)).add(0,1.1,0);
        if(clear(target))return target;
        // Bounded nearby alternatives for walls, low ceilings and narrow rooms.
        for(int i=0;i<6;i++){
            double angle=i*Math.PI/3;
            Vec3 candidate=owner.position().add(Math.cos(angle)*2.8,.4+(i%2)*.6,Math.sin(angle)*2.8);
            if(clear(candidate))return candidate;
        }
        return null;
    }

    private void recover(Player owner) {
        for(int i=0;i<12;i++){
            double angle=(doll.getId()%12+i)*Math.PI/6;
            Vec3 candidate=owner.position().add(Math.cos(angle)*3.2,.65+(i%3)*.45,Math.sin(angle)*3.2);
            if(!clear(candidate))continue;
            hold();doll.teleportTo(candidate.x,candidate.y,candidate.z);
            hover=candidate;lastPosition=candidate;flightTarget=null;direct=false;repath=0;return;
        }
    }

    private boolean clear(Vec3 at) {
        return clearBox(doll.getBoundingBox().move(at.subtract(doll.position())));
    }

    private boolean clearBox(AABB box) {
        if(box.minY<doll.level().getMinBuildHeight()||box.maxY>doll.level().getMaxBuildHeight()
                ||!doll.level().getWorldBorder().isWithinBounds(box))return false;
        // Check every intersected chunk before any block/collision/fluid query.
        int minX=BlockPos.containing(box.minX,box.minY,box.minZ).getX()>>4;
        int maxX=BlockPos.containing(box.maxX,box.minY,box.maxZ).getX()>>4;
        int minZ=BlockPos.containing(box.minX,box.minY,box.minZ).getZ()>>4;
        int maxZ=BlockPos.containing(box.maxX,box.minY,box.maxZ).getZ()>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
            if(!doll.level().hasChunkAt(new BlockPos(x<<4,(int)box.minY,z<<4)))return false;
        return !doll.level().containsAnyLiquid(box)&&doll.level().noCollision(doll,box);
    }

    private void hold() {
        doll.getNavigation().stop();
        doll.getMoveControl().setWantedPosition(doll.getX(),doll.getY(),doll.getZ(),0);
        doll.setSpeed(0);doll.setXxa(0);doll.setYya(0);doll.setZza(0);
        doll.setDeltaMovement(Vec3.ZERO);
    }
}
