package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.client.CelestialOrbits;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hierarchical motion/continuity invariants, separate from framebuffer review. */
final class AstralOrbitChecks {
    private AstralOrbitChecks() { }

    static void run(){
        check(CelestialOrbits.PRIMARY_COUNT==6&&CelestialOrbits.MOONS_PER_PRIMARY==4
                &&CelestialOrbits.COUNT==30,"six primaries retain twenty-four local moons");
        int[] children=new int[CelestialOrbits.PRIMARY_COUNT];
        Vec3 normal=momentum(0,20,21).normalize();
        double primaryMu=CelestialOrbits.gravitationalParameter(0);
        int ringParents=0;
        for(int i=0;i<CelestialOrbits.COUNT;i++){
            int parent=CelestialOrbits.parentIndex(i),order=CelestialOrbits.moonOrder(i);
            double a=CelestialOrbits.semiMajor(i),e=CelestialOrbits.eccentricity(i);
            double n=CelestialOrbits.meanMotion(i),mu=CelestialOrbits.gravitationalParameter(i);
            check(Double.isFinite(a)&&Double.isFinite(e)&&Double.isFinite(n)&&Double.isFinite(mu)
                    &&a>0&&e>=0&&e<1&&n>0&&mu>0,"finite bound local orbit "+i);
            near(n*n*a*a*a,mu,mu*1E-10,"Kepler period-radius relation at immediate focus "+i);
            check(CelestialOrbits.radius(i)>0,"positive visible body radius "+i);
            if(i<CelestialOrbits.PRIMARY_COUNT){
                check(parent==-1&&order==-1&&CelestialOrbits.systemIndex(i)==i,"primary identity "+i);
                near(mu,primaryMu,primaryMu*1E-10,"primaries share central gravity "+i);
                check(Math.abs(CelestialOrbits.inclination(i))<.025,"nearly coplanar primary "+i);
                if(i>0)nested(i-1,i,"primary");
                if(CelestialOrbits.hasRings(i))ringParents++;
            }else{
                check(parent>=0&&parent<CelestialOrbits.PRIMARY_COUNT&&order>=0
                        &&order<CelestialOrbits.MOONS_PER_PRIMARY,"valid immediate parent and moon order "+i);
                check(CelestialOrbits.systemIndex(i)==parent,"moon belongs to its moving primary "+i);
                children[parent]++;
                check(Math.abs(CelestialOrbits.inclination(i))<.16,"coherent local moon plane "+i);
                int first=CelestialOrbits.PRIMARY_COUNT+parent;
                near(mu,CelestialOrbits.gravitationalParameter(first),mu*1E-10,"siblings share parent gravity "+i);
                check(n>CelestialOrbits.meanMotion(parent),"moon moves faster about parent than parent about centre "+i);
                if(order>0)nested(i-CelestialOrbits.PRIMARY_COUNT,i,"sibling");
                double parentEdge=CelestialOrbits.radius(parent)*(CelestialOrbits.hasRings(parent)?2.12:1.);
                check(a*(1-e)-CelestialOrbits.radius(i)>parentEdge,"whole moon clears parent surface and visible ring "+i);
            }
            check(momentum(i,20,21).normalize().dot(normal)>.98,"local angular momentum shares forward direction "+i);
            for(float age:new float[]{0,20,43,96,116,174,188,200}){
                Vec3 local=CelestialOrbits.localOffset(i,age,0),world=CelestialOrbits.offset(i,age,0);
                check(local.length()>=a*(1-e)-1E-8&&local.length()<=a*(1+e)+1E-8,
                        "local ellipse stays within apsides "+i+"/"+age);
                Vec3 focus=parent<0?Vec3.ZERO:CelestialOrbits.offset(parent,age,0);
                check(world.subtract(focus).distanceToSqr(local)<1E-20,
                        "rendered position composes with actual moving focus "+i+"/"+age);
            }
            check(CelestialOrbits.localOffset(i,96,0).distanceToSqr(CelestialOrbits.localOffset(i,116,0))<1E-20,
                    "local orbit stops exactly "+i);
            check(CelestialOrbits.offset(i,96,0).distanceToSqr(CelestialOrbits.offset(i,116,0))<1E-20,
                    "parent and child world positions stop exactly "+i);
            check(CelestialOrbits.localOffset(i,43,0).distanceToSqr(CelestialOrbits.localOffset(i,43,20))>.001,
                    "continued held charge advances each local orbit "+i);
            // A complete preview orbit must return geometrically, not merely report a plausible n.
            float previewPeriod=(float)(2*Math.PI/n/.018);
            check(CelestialOrbits.localOffset(i,43,0).distanceToSqr(CelestialOrbits.localOffset(i,43,previewPeriod))<1E-6,
                    "actual local motion completes the Kepler period "+i);
            check(momentum(i,188,189).dot(normal)<0,"parent and moon angular momenta reverse "+i);
            check(CelestialOrbits.localOffset(i,188,0).distanceToSqr(CelestialOrbits.localOffset(i,189,0))
                            >CelestialOrbits.localOffset(i,174,0).distanceToSqr(CelestialOrbits.localOffset(i,175,0)),
                    "reverse sweep accelerates for each local orbit "+i);
        }
        check(ringParents==2,"two visibly ringed primary systems");
        for(int i=0;i<children.length;i++)check(children[i]==4,"four distinct moons for primary "+i);
        qualityCoverage();
        LogUtils.getLogger().info("ASTRAL ORBIT INVARIANTS PASSED: 6 central Kepler primaries + 24 local moons, four per moving parent, nested local paths, common direction, ring clearance, complete lower-quality systems, exact freeze, held-preview periods and accelerating reversal; framebuffer reflection reviewed separately");
    }

    private static Vec3 momentum(int index,float from,float to){
        Vec3 point=CelestialOrbits.localOffset(index,from,0);
        return point.cross(CelestialOrbits.localOffset(index,to,0).subtract(point));
    }

    private static void nested(int inner,int outer,String kind){
        double a=CelestialOrbits.semiMajor(outer),e=CelestialOrbits.eccentricity(outer);
        double previousA=CelestialOrbits.semiMajor(inner),previousE=CelestialOrbits.eccentricity(inner);
        check(a>previousA&&CelestialOrbits.meanMotion(outer)<CelestialOrbits.meanMotion(inner),
                "inner "+kind+" moves faster than next outer body "+outer);
        for(int angle=0;angle<64;angle++){
            double cosine=Math.cos(angle*Math.PI/32);
            check(a*(1-e*e)/(1+e*cosine)>previousA*(1-previousE*previousE)/(1+previousE*cosine),
                    "aligned "+kind+" ellipses stay nested at common true anomaly "+outer+"/"+angle);
        }
    }

    private static void qualityCoverage(){
        // Read the shader actually loaded by the client so a cheaper tier cannot silently omit a system.
        String resource="/assets/two_i_combat_doll/shaders/program/arcana_world.fsh";
        try(var input=AstralOrbitChecks.class.getResourceAsStream(resource)){
            check(input!=null,"production celestial shader resource exists");
            String shader=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            Matcher tiers=Pattern.compile("VisualQuality\\s*<\\s*1\\.5\\s*\\?\\s*(\\d+)\\.\\s*:\\s*\\(VisualQuality\\s*<\\s*2\\.5\\s*\\?\\s*(\\d+)\\.\\s*:\\s*(\\d+)\\.").matcher(shader);
            check(tiers.find(),"shader exposes bounded low/medium/high celestial counts");
            int previous=0;
            for(int tier=1;tier<=3;tier++){
                int count=Integer.parseInt(tiers.group(tier));
                check(count>previous&&count<=CelestialOrbits.COUNT,"increasing bounded quality tier "+tier);
                int[] visibleMoons=new int[CelestialOrbits.PRIMARY_COUNT];
                for(int i=0;i<count;i++){
                    int parent=CelestialOrbits.parentIndex(i);
                    if(parent>=0){check(parent<count,"visible moon has visible primary "+i);visibleMoons[parent]++;}
                }
                for(int parent=0;parent<visibleMoons.length;parent++){
                    check(parent<count,"every quality tier retains every primary "+parent);
                    check(visibleMoons[parent]>=tier+1,"each quality tier retains local moons for every primary "+tier+"/"+parent);
                }
                previous=count;
            }
            check(previous==CelestialOrbits.COUNT,"highest tier displays all thirty bodies");
        }catch(IOException ex){throw new AssertionError("Unable to read production celestial shader",ex);}
    }

    private static void near(double actual,double expected,double tolerance,String message){
        check(Math.abs(actual-expected)<=tolerance,message);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
