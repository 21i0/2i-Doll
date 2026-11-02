package com.twoi.combatdoll.client;

import com.twoi.combatdoll.combat.TwoIArcanaTimeline;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Six visible parent/moon systems sharing a fixed central focus and one cinematic clock. */
public final class CelestialOrbits {
    public static final int COUNT=30,PRIMARY_COUNT=6,MOONS_PER_PRIMARY=4;
    private static final double[] PRIMARY_RADII={1.45,1.75,1.30,2.00,1.62,1.90};
    private static final double[] PRIMARY_PHASE={.20,2.10,.40,1.15,2.30,.90};
    private static final Vec3 U=new Vec3(.985,.09,.15).normalize();
    private static final Vec3 RAW_V=new Vec3(-.04,.98,-.21);
    private static final Vec3 V=RAW_V.subtract(U.scale(RAW_V.dot(U))).normalize();
    private static final Vec3 NORMAL=U.cross(V);
    private static final Matrix4f[] BATCH=new Matrix4f[8];
    private static final Matrix4f[] SYSTEMS={new Matrix4f(),new Matrix4f()};
    private static final Vector4f COLUMN=new Vector4f();
    static {for(int i=0;i<BATCH.length;i++)BATCH[i]=new Matrix4f();}
    private CelestialOrbits() { }
    public static int parentIndex(int index){return index<PRIMARY_COUNT?-1:(index-PRIMARY_COUNT)%PRIMARY_COUNT;}
    public static int systemIndex(int index){return index<PRIMARY_COUNT?index:parentIndex(index);}
    public static int moonOrder(int index){return index<PRIMARY_COUNT?-1:(index-PRIMARY_COUNT)/PRIMARY_COUNT;}
    public static boolean hasRings(int index){return index==1||index==4;}
    public static double semiMajor(int index){
        if(index<PRIMARY_COUNT)return 18.+index*13.2;
        int parent=parentIndex(index),order=moonOrder(index);
        double first=hasRings(parent)?PRIMARY_RADII[parent]*2.55:PRIMARY_RADII[parent]+.95;
        return first+order*(hasRings(parent)?.82:1.10);
    }
    public static double eccentricity(int index){return index<PRIMARY_COUNT?.035+index*.003:.018+moonOrder(index)*.006;}
    /** Each parent/child level obeys n squared a cubed equals its focus's mu. */
    public static double gravitationalParameter(int index){
        // Compressed cinematic systems keep even outer moons visibly quicker than
        // their parent while the shortest preview orbit remains close to one turn.
        return index<PRIMARY_COUNT?26.*26.*26.:240.*Math.pow(PRIMARY_RADII[parentIndex(index)],3.);
    }
    public static double meanMotion(int index){return Math.sqrt(gravitationalParameter(index)/Math.pow(semiMajor(index),3.));}
    public static double inclination(int index){
        return index<PRIMARY_COUNT?(index-2.5)*.006:
                .12*Math.sin(parentIndex(index)*1.1)+(moonOrder(index)-1.5)*.012;
    }
    public static float radius(int index){
        return (float)(index<PRIMARY_COUNT?PRIMARY_RADII[index]:.23+parentIndex(index)*.035+moonOrder(index)*.075);
    }
    private static double orbitTime(float age,float chargeExtra){
        // Fourfold faster preview makes independent motion legible. Rewind keeps
        // the existing cinematic acceleration instead of spinning into aliasing.
        return TwoIArcanaTimeline.celestialAge(age,chargeExtra)*.0045
                +(Math.min(age,TwoIArcanaTimeline.CHARGE)+chargeExtra)*.0135;
    }
    /** Orbit relative to this body's immediate focus; moons do not orbit the black hole directly. */
    public static Vec3 localOffset(int index,float age,float chargeExtra){
        int parent=parentIndex(index),order=moonOrder(index);
        double phase=parent<0?PRIMARY_PHASE[index]:parent*.57+order*1.63+.31;
        double mean=phase+orbitTime(age,chargeExtra)*meanMotion(index);
        double e=eccentricity(index),anomaly=mean;
        for(int i=0;i<3;i++)anomaly-=(anomaly-e*Math.sin(anomaly)-mean)/(1.-e*Math.cos(anomaly));
        double a=semiMajor(index),incline=inclination(index);
        double node=parent<0?0:parent*.31;
        Vec3 localU=U.scale(Math.cos(node)).add(V.scale(Math.sin(node)));
        Vec3 localV=V.scale(Math.cos(node)).subtract(U.scale(Math.sin(node)));
        Vec3 tiltedV=localV.scale(Math.cos(incline)).add(NORMAL.scale(Math.sin(incline)));
        return localU.scale(a*(Math.cos(anomaly)-e))
                .add(tiltedV.scale(a*Math.sqrt(1.-e*e)*Math.sin(anomaly)));
    }
    /** Positive motion is shared; every parent and child freezes and rewinds together. */
    public static Vec3 offset(int index,float age,float chargeExtra){
        int parent=parentIndex(index);
        Vec3 local=localOffset(index,age,chargeExtra);
        return parent<0?local:local.add(localOffset(parent,age,chargeExtra));
    }
    /** Eight reused matrices carry four centre/radius columns each. No per-pixel orbital trigonometry. */
    static Matrix4f[] matrices(Vec3 anchor,float age,float chargeExtra){
        for(Matrix4f matrix:BATCH)matrix.zero();
        for(int i=0;i<COUNT;i++){
            Vec3 p=anchor.add(offset(i,age,chargeExtra));
            BATCH[i/4].setColumn(i%4,COLUMN.set((float)p.x,(float)p.y,(float)p.z,radius(i)));
        }
        return BATCH;
    }
    /** Six actual moving parent centres/radii drive the local orbit paths in both sky and mirror. */
    static Matrix4f[] systemMatrices(Vec3 anchor,float age,float chargeExtra){
        for(Matrix4f matrix:SYSTEMS)matrix.zero();
        for(int i=0;i<PRIMARY_COUNT;i++){
            Vec3 p=anchor.add(offset(i,age,chargeExtra));
            SYSTEMS[i/4].setColumn(i%4,COLUMN.set((float)p.x,(float)p.y,(float)p.z,radius(i)));
        }
        return SYSTEMS;
    }
}
