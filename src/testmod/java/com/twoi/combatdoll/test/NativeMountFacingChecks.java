package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.*;
import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Natural server ticks with the real bundled XiaoXiao and a leased mount.
 * The caller drives tick() once per server Post and may keep the pair alive
 * after PASS for real client renderer inspection; cleanup() is explicit. */
public final class NativeMountFacingChecks {
    private static ServerPlayer owner;
    private static com.customdoll.xiaoxiao.content.entity.DollEntity rider;
    private static CangshengAxolotlEntity mount;
    private static Mob attention;
    private static int phase,elapsed,total;
    private static boolean finished,passed;
    private static float directorYaw=-123,directorPitch=17;
    private static Vec3 directorPosition;
    private static Throwable failure;
    private NativeMountFacingChecks(){}

    public static CangshengAxolotlEntity mount(){return mount;}
    public static Mob rider(){return rider;}
    public static Mob attention(){return attention;}
    public static boolean finished(){return finished;}
    public static void assertPassed(){if(!finished||!passed)throw new AssertionError("Native mount facing regression incomplete/failed",failure);}

    public static void begin(ServerPlayer player){
        if(owner!=null)throw new IllegalStateException("Mount regression already started; cleanup first");
        owner=player;phase=elapsed=total=0;finished=passed=false;failure=null;
        ServerLevel level=player.serverLevel();Vec3 origin=player.position().add(0,0,-8);
        // A private flat patch avoids obstacle/path noise in the facing cases.
        int floor=BlockPos.containing(origin).getY()-1;
        for(int x=-13;x<=13;x++)for(int z=-13;z<=13;z++)
            level.setBlock(new BlockPos(BlockPos.containing(origin).getX()+x,floor,BlockPos.containing(origin).getZ()+z),Blocks.QUARTZ_BLOCK.defaultBlockState(),2);
        rider=com.customdoll.xiaoxiao.init.ModEntities.TWO_I_DOLL.get().create(level);
        require(rider!=null,"bundled XiaoXiao exists");
        rider.initialize(player.getUUID(),new ItemStack(com.customdoll.xiaoxiao.init.ModItems.TWO_I_DOLL.get()));
        rider.setPos(origin);require(level.addFreshEntity(rider),"actual rider enters level");
        require(CombatActors.state(rider).equip(rider,new ItemStack(TwoICombatDollMod.AXOLOTL.get())),"actual pet equip/deploy succeeds");
        require(rider.getVehicle() instanceof CangshengAxolotlEntity,"real passenger relation exists");
        mount=(CangshengAxolotlEntity)rider.getVehicle();
        attention=EntityType.COW.create(level);require(attention!=null,"passive attention fixture exists");
        attention.setNoAi(true);attention.setPos(origin.add(10,0,-5));require(level.addFreshEntity(attention),"attention fixture enters level");
        cardinalRendererConvention();
        require(new CombatStopGoal(rider).getFlags().contains(net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK),"riding/combat goal owns LOOK");
        require(new CombatStopGoal(rider).canUse(),"passenger suppresses independent native follow/look goals");
    }

    /** Recommended deadline: 240 real server ticks after begin. */
    public static void tick(){
        if(finished||owner==null)return;
        try{
            total++;elapsed++;
            require(total<240,"natural tick deadline");
            require(mount.isAlive()&&!mount.isRemoved()&&rider.getVehicle()==mount,"actual living pair persists");
            if(phase==0){
                if(elapsed<50)return;
                require(!mount.emerging(),"emergence finishes through natural world ticks");
                rider.setTarget(attention);phase=1;elapsed=0;return;
            }
            if(phase==1){
                pair("rider target");
                require(turn()<=30.01F,"focused yaw uses a bounded shortest turn");
                if(elapsed<20)return;
                require(error(attention.getEyePosition())<1,"mount faces rider's actual target rather than owner");
                rider.setTarget(null);owner.setPos(mount.position().add(4,0,0));phase=2;elapsed=0;return;
            }
            if(phase==2){
                pair("idle owner");require(turn()<=8.01F,"idle turn is gentle rather than an immediate180-degree snap");
                if(elapsed<35)return;
                require(error(owner.getEyePosition())<1,"idle pair settles toward owner");
                mount.setNoAi(true);rider.setNoAi(true);mount.setNoGravity(true);mount.noPhysics=true;mount.setDeltaMovement(Vec3.ZERO);
                pose(mount,directorYaw,directorPitch);pose(rider,directorYaw,directorPitch);
                // A director can use distinct interpolation endpoints. The
                // companion bridge itself must not overwrite either endpoint.
                mount.yRotO=mount.yBodyRotO=mount.yHeadRotO=-131;
                rider.yRotO=rider.yBodyRotO=rider.yHeadRotO=-137;
                mount.alignPassengerFacing(rider);
                require(mount.yRotO==-131&&rider.yRotO==-137,"NoAI director owns previous-frame values");
                pose(mount,directorYaw,directorPitch);pose(rider,directorYaw,directorPitch);
                directorPosition=mount.position();owner.setPos(mount.position().add(-4,0,0));
                phase=3;elapsed=0;return;
            }
            if(phase==3){
                require(Math.abs(Mth.wrapDegrees(mount.getYRot()-directorYaw))<.001&&Math.abs(mount.getXRot()-directorPitch)<.001,"NoAI director pose survives native follow tick");
                for(LivingEntity actor:new LivingEntity[]{mount,rider})
                    require(close(actor.getYRot(),directorYaw)&&close(actor.yBodyRot,directorYaw)&&close(actor.yHeadRot,directorYaw)&&Math.abs(actor.getXRot()-directorPitch)<.001,"NoAI director retains both actors' current head/body/pitch");
                require(mount.position().distanceToSqr(directorPosition)<1E-8,"NoAI director movement survives owner relocation");
                require(!mount.getLookControl().isLookingAtTarget(),"director receives no deferred native owner-look request");
                if(elapsed<15)return;
                mount.setNoAi(false);rider.setNoAi(false);mount.setNoGravity(false);mount.noPhysics=false;
                require(mount.combatState().begin(mount,owner.serverLevel(),true),"real mounted spell begins");
                Vec3 away=mount.combatState().target(mount).subtract(mount.position()).multiply(1,0,1).normalize().scale(-4);
                owner.setPos(mount.position().add(away));phase=4;elapsed=0;return;
            }
            if(phase==4){
                pair("active spell");
                if(elapsed<25)return;
                require(mount.isAttacking()&&error(mount.combatState().target(mount))<1,"spell target owns pair facing even with owner opposite it");
                mount.combatState().reset();
                com.twoi.combatdoll.network.CombatNetwork.send(mount);
                passed=finished=true;
                LogUtils.getLogger().info("NATIVE MOUNT FACING CHECKS PASSED: real XiaoXiao passenger, rider attention, gentle idle owner, bounded yaw, current/previous body-head alignment, both NoAI director poses and queued-look exclusion, actual spell target, four mathematical renderer cardinal conventions; natural_ticks={} mount={} rider={}",total,mount.getUUID(),rider.getUUID());
            }
        }catch(Throwable problem){failure=problem;finished=true;LogUtils.getLogger().error("NATIVE MOUNT FACING CHECKS FAILED phase="+phase+" elapsed="+elapsed,problem);}
    }

    private static void pair(String where){
        float yaw=mount.getYRot(),old=mount.yRotO;
        for(LivingEntity entity:new LivingEntity[]{mount,rider}){
            require(close(entity.getYRot(),yaw)&&close(entity.yBodyRot,yaw)&&close(entity.yHeadRot,yaw),where+": current yaw/body/head agree");
            require(close(entity.yRotO,old)&&close(entity.yBodyRotO,old)&&close(entity.yHeadRotO,old),where+": previous yaw/body/head agree");
        }
    }
    private static float error(Vec3 point){Vec3 delta=point.subtract(mount.getEyePosition());return Math.abs(Mth.wrapDegrees(mount.getYRot()-(float)Math.toDegrees(Math.atan2(-delta.x,delta.z))));}
    private static float turn(){return Math.abs(Mth.wrapDegrees(mount.getYRot()-mount.yRotO));}
    private static boolean close(float a,float b){return Math.abs(Mth.wrapDegrees(a-b))<.001F;}
    private static void pose(LivingEntity entity,float yaw,float pitch){entity.setYRot(yaw);entity.yRotO=yaw;entity.yBodyRot=entity.yBodyRotO=entity.yHeadRot=entity.yHeadRotO=yaw;entity.setXRot(pitch);entity.xRotO=pitch;}
    private static void cardinalRendererConvention(){
        for(float yaw:new float[]{0,90,180,-90}){
            // All three doll rigs have a -Z face. MobRenderer and the real
            // axolotl renderer both rotate that face by180-yaw; do not invert it.
            var actual=new org.joml.Vector3f(0,0,-1).rotateY((180-yaw)*Mth.DEG_TO_RAD);
            Vec3 expected=Vec3.directionFromRotation(0,yaw);
            require(actual.x*expected.x+actual.z*expected.z>.9999,"renderer front agrees with Minecraft yaw="+yaw);
        }
    }
    public static void cleanup(){
        if(mount!=null&&!mount.isRemoved()){
            if(rider!=null&&!rider.isRemoved())mount.retireIntoDoll(rider);
            if(!mount.isRemoved())mount.discard();
        }
        if(rider!=null&&!rider.isRemoved())rider.discard();
        if(attention!=null&&!attention.isRemoved())attention.discard();
        owner=null;rider=null;mount=null;attention=null;
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError("Native mount: "+message);}
}
