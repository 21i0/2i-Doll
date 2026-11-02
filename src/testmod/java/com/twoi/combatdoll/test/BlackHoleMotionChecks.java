package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.client.BlackHoleMotion;
import com.twoi.combatdoll.client.ClientCombatClock;
import com.twoi.combatdoll.client.TwoIArcanaRenderer;
import com.twoi.combatdoll.combat.CombatActors;
import com.twoi.combatdoll.combat.TwoIGrimoireCombat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Isolated visual integrator checks on the real, naturally advancing client
 * clock. This unspawned actor never receives server gameplay or render updates;
 * the separate real held-item test verifies vanilla/server aim round trips.
 */
final class BlackHoleMotionChecks {
    private static LivingEntity actor;
    private static TwoIGrimoireCombat state;
    private static final Vec3 ORIGIN=new Vec3(.5,100,.5),ANCHOR=ORIGIN.add(0,18,-14);
    private static final Vec3 FIRST=ANCHOR.add(-6,2,0),SECOND=ANCHOR.add(12,5,-3),FINAL=ANCHOR.add(-8,9,4);
    private static long started,releaseAt;
    private static int phase;
    private static boolean done,trailVerified;
    private BlackHoleMotionChecks(){ }

    static boolean tick(Minecraft mc){
        if(done)return true;
        long now=ClientCombatClock.now();
        if(phase==0){
            if(now<116)return false; // Later historical milestones must keep start >= 0.
            check(BlackHoleMotion.trackedCount()==0,"no prior visual motion before isolated fixture");
            actor=EntityType.ZOMBIE.create(mc.level);check(actor!=null,"client-only motion fixture exists");
            actor.setPos(ORIGIN);state=CombatActors.state(actor);
            state.hold(new ItemStack(TwoICombatDollMod.STAFF.get()));state.synchronize(now,Vec3.ZERO);
            state.synchronizeScene(true,0,ORIGIN,ANCHOR,FIRST);
            check(BlackHoleMotion.sample(actor,state,0).equals(FIRST),"initial sample uses synchronized target");
            state.synchronizeScene(true,0,ORIGIN,ANCHOR,SECOND);
            Vec3 stepped=BlackHoleMotion.sample(actor,state,0);
            check(stepped.equals(FIRST),"target step has inertia instead of teleporting at equal timestamp");
            check(state.blackHole(actor).equals(SECOND),"visual sample never overwrites authoritative aim");
            started=now;phase=1;return false;
        }
        check(now-started<120,"bounded natural-clock motion fixture");
        if(phase==1){
            Vec3 position=BlackHoleMotion.sample(actor,state,0);
            var before=BlackHoleMotion.snapshot(actor);
            check(BlackHoleMotion.sample(actor,state,0).equals(position)
                    &&BlackHoleMotion.snapshot(actor).velocity().equals(before.velocity()),
                    "terrain/sky sampling at same timestamp does not integrate twice");
            check(before.target().equals(SECOND)&&state.blackHole(actor).equals(SECOND),"presentation reads synchronized target unchanged");
            check(before.trailPoints()<=24,"motion trail history stays bounded");
            if(now-started>=3&&!trailVerified){
                check(BlackHoleMotion.visibleTrailPointCount(Vec3.ZERO,TwoIArcanaRenderer.quality())>=3,
                        "heavy wake uploads a visible multi-point path while the synchronized target is moving");
                trailVerified=true;
            }
            if(now-started==1){
                check(position.distanceTo(SECOND)>FIRST.distanceTo(SECOND)*.8,"first tick still trails a newly moved target");
                check(position.distanceTo(FIRST)>.01&&before.velocity().length()>.01,"inertia has actual nonzero movement and velocity");
            }
            if(now-started>=52){
                check(position.distanceTo(SECOND)<.02,"stable synchronized target converges without sustained jitter");
                state.synchronizeScene(true,0,ORIGIN,ANCHOR,FINAL);
                BlackHoleMotion.sample(actor,state,0);phase=2;return false;
            }
            return false;
        }
        if(phase==2){
            Vec3 moving=BlackHoleMotion.sample(actor,state,0);
            check(moving.distanceTo(FINAL)>1&&BlackHoleMotion.snapshot(actor).velocity().length()>.01,
                    "release fixture starts with an actually moving black hole");
            state.synchronizeScene(false,64,ORIGIN,ANCHOR,FINAL);
            check(BlackHoleMotion.sample(actor,state,0).equals(moving),"release begins continuously from moving position");
            check(BlackHoleMotion.snapshot(actor).settling(),"release has finite settling interval");
            releaseAt=now;phase=3;return false;
        }
        if(phase==3){
            Vec3 position=BlackHoleMotion.sample(actor,state,0);
            var snapshot=BlackHoleMotion.snapshot(actor);
            check(snapshot.target().equals(FINAL)&&state.blackHole(actor).equals(FINAL),"release never moves final authoritative aim");
            check(Double.isFinite(position.x)&&Double.isFinite(position.y)&&Double.isFinite(position.z),"finite release path");
            check(snapshot.trailPoints()<=24,"release trail remains bounded");
            if(now-releaseAt<BlackHoleMotion.RELEASE_TICKS)return false;
            check(!snapshot.settling()&&position.equals(FINAL)&&snapshot.velocity().equals(Vec3.ZERO),
                    "release settles exactly on fixed final target within twelve natural ticks");
            for(int age:new int[]{96,116}){
                state.synchronize(now-age,Vec3.ZERO);
                check(BlackHoleMotion.sample(actor,state,0).equals(FINAL),"frozen-stage black hole remains exactly fixed at "+age);
                check(!BlackHoleMotion.snapshot(actor).settling(),"no lingering spring at frozen milestone "+age);
            }
            state.reset();BlackHoleMotion.tick(null);
            check(BlackHoleMotion.snapshot(actor)==null&&BlackHoleMotion.trackedCount()==0,"cancellation removes cached visual state on client tick");
            state.synchronize(now,Vec3.ZERO);state.synchronizeScene(true,0,ORIGIN,ANCHOR,FIRST);
            BlackHoleMotion.sample(actor,state,0);check(BlackHoleMotion.trackedCount()==1,"fresh charge creates one new cache entry");
            actor.discard();BlackHoleMotion.tick(null);
            check(BlackHoleMotion.snapshot(actor)==null&&BlackHoleMotion.trackedCount()==0,"removed actor cleans its motion entry");
            actor=EntityType.ZOMBIE.create(mc.level);check(actor!=null,"replacement visual-only fixture exists");
            state=CombatActors.state(actor);state.hold(new ItemStack(TwoICombatDollMod.STAFF.get()));
            state.synchronize(now,Vec3.ZERO);state.synchronizeScene(true,0,ORIGIN,ANCHOR,FIRST);
            BlackHoleMotion.sample(actor,state,0);check(BlackHoleMotion.trackedCount()==1,"populated motion cache before explicit clear");
            BlackHoleMotion.clear();check(BlackHoleMotion.trackedCount()==0&&BlackHoleMotion.trailPointCount()==0,"explicit world-cache clear removes motion and uploaded trail");
            actor.discard();done=true;actor=null;state=null;
            check(trailVerified,"moving fixture verified the uploaded gravitational wake");
            LogUtils.getLogger().info("BLACK HOLE INERTIA CHECKS PASSED: real natural client clock, heavy synchronized target lag, visible multi-point gravitational wake, no authoritative writes, repeated same-time sample idempotent, moving release settles exactly within12ticks, 96/116 fixed, bounded trail history, cancellation/removal/explicit clear; world-switch handler audited separately");
            return true;
        }
        return false;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
