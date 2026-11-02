package com.twoi.combatdoll.client;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.DollCombatAccess;
import com.twoi.combatdoll.combat.TwoIArcanaTimeline;
import com.twoi.combatdoll.combat.TwoIGrimoireCombat;
import com.twoi.combatdoll.combat.WeaponKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;

/** Local presentation only: no aim calculation, packets, collision or combat writes. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID,value=Dist.CLIENT)
public final class BlackHoleMotion {
    public static final int RELEASE_TICKS=12;
    // A low spring frequency gives the hole the heavy, reluctant pull the
    // spell calls for. It is presentation-only; the synchronized aim remains
    // authoritative and unchanged.
    private static final double OMEGA=.20,TRAIL_TICKS=24;
    private static final int MAX_TRACKED=8,MAX_HISTORY=24;
    private static final IdentityHashMap<LivingEntity,Motion> MOTIONS=new IdentityHashMap<>();
    private static final Matrix4f[] TRAIL={new Matrix4f(),new Matrix4f()};
    private static final Vector4f COLUMN=new Vector4f();
    private static ClientLevel lastLevel;
    private static Motion active;
    private static int uploadedPoints;
    private BlackHoleMotion() { }

    /** Immutable diagnostics; position and target are absolute world positions. */
    public record Snapshot(Vec3 position,Vec3 target,Vec3 velocity,boolean charging,
                           boolean settling,int trailPoints,double sampleTime) { }
    private record TrailPoint(Vec3 position,double time,double strength) { }
    private static final class Motion {
        final Vec3 anchor,origin;
        final ArrayDeque<TrailPoint> history=new ArrayDeque<>();
        Vec3 position,target,velocity=Vec3.ZERO,releasePosition,releaseVelocity;
        double time,releaseTime=Double.NaN,lastTrailTime,lastMoveTime;
        float lastAge;
        boolean charging;
        Motion(TwoIGrimoireCombat state,LivingEntity actor,double now,float age){
            anchor=state.stageAnchor(actor);origin=state.sceneOrigin(actor);
            position=target=state.blackHole(actor);time=lastTrailTime=lastMoveTime=now;
            lastAge=age;charging=state.charging();
        }
    }
    public static Snapshot snapshot(LivingEntity actor){
        Motion m=MOTIONS.get(actor);
        return m==null?null:new Snapshot(m.position,m.target,m.velocity,m.charging,
                !Double.isNaN(m.releaseTime)&&m.time-m.releaseTime<RELEASE_TICKS,m.history.size(),m.time);
    }
    public static int trackedCount(){return MOTIONS.size();}
    public static int trailPointCount(){return uploadedPoints;}
    /** Diagnostic count from the same bounded mesh upload used by the sky pass. */
    public static int visibleTrailPointCount(Vec3 camera,int quality){trailMatrices(camera,quality);return uploadedPoints;}
    public static void clear(){MOTIONS.clear();active=null;uploadedPoints=0;}
    private static void checkLevel(){
        ClientLevel level=Minecraft.getInstance().level;
        if(level!=lastLevel){clear();lastLevel=level;}
    }
    /** Called by the renderer only for a genuine, synchronized STAFF scene. */
    public static Vec3 sample(LivingEntity actor,TwoIGrimoireCombat state,float partial){
        checkLevel();
        float age=state.age(actor,partial);
        if(actor.level()!=lastLevel||actor.isRemoved()||state.kind()!=WeaponKind.STAFF||age<0||age>=TwoIArcanaTimeline.DURATION){
            Motion removed=MOTIONS.remove(actor);if(active==removed)active=null;
            return state.blackHole(actor);
        }
        double now=ClientCombatClock.now()+Math.clamp(partial,0,1);
        Motion m=MOTIONS.get(actor);
        if(m==null||age+4<m.lastAge||m.anchor.distanceToSqr(state.stageAnchor(actor))>.0001
                ||m.origin.distanceToSqr(state.sceneOrigin(actor))>.0001||now-m.time>20){
            if(m==null&&MOTIONS.size()>=MAX_TRACKED){
                var oldest=MOTIONS.entrySet().stream().min(java.util.Comparator.comparingDouble(e->e.getValue().time)).orElseThrow();
                MOTIONS.remove(oldest.getKey());
            }
            m=new Motion(state,actor,now,age);MOTIONS.put(actor,m);
        }
        active=m;
        // Both terrain and sky render stages call sample. Equal timestamps must
        // not advance the spring twice or add duplicate trail vertices.
        double dt=Math.max(0,now-m.time);
        Vec3 before=m.position;
        if(state.charging()){
            m.releaseTime=Double.NaN;
            m.target=state.blackHole(actor); // authoritative synchronized point only
            if(dt>0){
                // Exact critically damped solution for this bounded target step.
                Vec3 displacement=m.position.subtract(m.target);
                Vec3 c=m.velocity.add(displacement.scale(OMEGA));
                double decay=Math.exp(-OMEGA*dt);
                m.position=m.target.add(displacement.add(c.scale(dt)).scale(decay));
                m.velocity=m.velocity.subtract(c.scale(OMEGA*dt)).scale(decay);
            }
        }else if(m.charging){
            m.target=state.blackHole(actor);m.releaseTime=now;
            m.releasePosition=m.position;
            double maxTangent=m.position.distanceTo(m.target)*1.5/RELEASE_TICKS;
            m.releaseVelocity=m.velocity.length()>maxTangent?m.velocity.normalize().scale(maxTangent):m.velocity;
        }
        if(!state.charging()&&!Double.isNaN(m.releaseTime)){
            double t=Math.clamp((now-m.releaseTime)/RELEASE_TICKS,0,1),t2=t*t,t3=t2*t;
            if(t>=1){m.position=m.target;m.velocity=Vec3.ZERO;}
            else{
                m.position=m.releasePosition.scale(2*t3-3*t2+1)
                        .add(m.releaseVelocity.scale((t3-2*t2+t)*RELEASE_TICKS))
                        .add(m.target.scale(-2*t3+3*t2));
                m.velocity=m.releasePosition.scale((6*t2-6*t)/RELEASE_TICKS)
                        .add(m.releaseVelocity.scale(3*t2-4*t+1))
                        .add(m.target.scale((-6*t2+6*t)/RELEASE_TICKS));
            }
        }
        if(dt>0){
            double moved=before.distanceTo(m.position);
            if(moved>.025){
                m.lastMoveTime=now;
                if(now-m.lastTrailTime>=.45){
                    // Preserve a visible wake even during careful aim movement;
                    // faster pulls saturate naturally instead of making a
                    // one-frame needle.
                    double strength=Math.clamp(.30+moved/dt*.30,.30,1.0);
                    if(m.history.isEmpty())m.history.addLast(new TrailPoint(before,m.time,strength));
                    m.history.addLast(new TrailPoint(m.position,now,strength));
                    while(m.history.size()>MAX_HISTORY)m.history.removeFirst();
                    m.lastTrailTime=now;
                }
            }
            while(!m.history.isEmpty()&&now-m.history.peekFirst().time()>TRAIL_TICKS)m.history.removeFirst();
        }
        m.time=now;m.lastAge=age;m.charging=state.charging();
        return m.position;
    }
    /** At most 2/4/6 segments. Matrices are reused and camera-relative for sky and mirror. */
    static Matrix4f[] trailMatrices(Vec3 camera,int quality){
        for(Matrix4f matrix:TRAIL)matrix.zero();uploadedPoints=0;
        Motion m=active;
        if(m==null||m.history.size()<2||m.time-m.lastMoveTime>=TRAIL_TICKS)return TRAIL;
        int limit=Math.clamp(quality,1,3)*2+1;
        TrailPoint[] points=m.history.toArray(TrailPoint[]::new);
        int count=Math.min(limit,points.length+1);
        for(int i=0;i<count;i++){
            Vec3 p;double strength;
            if(i==0){p=m.position;strength=points[points.length-1].strength();}
            else{
                int index=points.length-1-(int)Math.round((double)(i-1)*(points.length-1)/Math.max(1,count-2));
                TrailPoint point=points[index];p=point.position();
                strength=point.strength()*Math.pow(Math.max(0,1-(m.time-point.time())/TRAIL_TICKS),.9);
            }
            strength*=Math.max(0,1-(m.time-m.lastMoveTime)/TRAIL_TICKS);
            p=p.subtract(camera);TRAIL[i/4].setColumn(i%4,COLUMN.set((float)p.x,(float)p.y,(float)p.z,(float)strength));
        }
        uploadedPoints=count;return TRAIL;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        checkLevel();
        Iterator<java.util.Map.Entry<LivingEntity,Motion>> entries=MOTIONS.entrySet().iterator();
        while(entries.hasNext()){
            var e=entries.next();var actor=e.getKey();var state=DollCombatAccess.state(actor);
            float age=state.age(actor,0);
            if(actor.level()!=lastLevel||actor.isRemoved()||!state.armed()||state.kind()!=WeaponKind.STAFF
                    ||age<0||age>=TwoIArcanaTimeline.DURATION||ClientCombatClock.now()-e.getValue().time>60){
                if(active==e.getValue())active=null;entries.remove();
            }
        }
    }
}
