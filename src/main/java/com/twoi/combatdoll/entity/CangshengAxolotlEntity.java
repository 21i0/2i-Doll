package com.twoi.combatdoll.entity;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A real, persistent living mount/titan. The save ledger owns its sole transferable carrier. */
public final class CangshengAxolotlEntity extends PathfinderMob implements DollCombatAccess {
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(CangshengAxolotlEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> RIDER=SynchedEntityData.defineId(CangshengAxolotlEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> MOUNT=SynchedEntityData.defineId(CangshengAxolotlEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RECALL=SynchedEntityData.defineId(CangshengAxolotlEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> PHASE_START=SynchedEntityData.defineId(CangshengAxolotlEntity.class,EntityDataSerializers.LONG);
    private final TwoIGrimoireCombat combat=new TwoIGrimoireCombat();
    private boolean retired;
    private boolean chasing;
    private Vec3 titanHome;
    private int nextTitanHop;
    public CangshengAxolotlEntity(EntityType<? extends CangshengAxolotlEntity> type,Level level){
        super(type,level);setPersistenceRequired();setInvulnerable(true);
        // TITAN is the synchronized default. Its false MOUNT value may never be
        // sent in a spawn packet, so both logical sides must size it here.
        refreshDimensions();
    }
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,40)
            .add(Attributes.MOVEMENT_SPEED,.32).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.FOLLOW_RANGE,48).add(Attributes.STEP_HEIGHT,1.5);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(OWNER,Optional.empty());
        b.define(RIDER,Optional.empty());b.define(MOUNT,false);b.define(RECALL,false);b.define(PHASE_START,0L);}
    public UUID ownerId(){return entityData.get(OWNER).orElse(null);}
    public UUID riderId(){return entityData.get(RIDER).orElse(null);}
    public boolean mountMode(){return entityData.get(MOUNT);}
    public boolean recalling(){return entityData.get(RECALL);}
    public boolean emerging(){return !recalling()&&phaseAge(0)<40;}
    private float phaseAge(float partial){return Math.max(0,level().getGameTime()-entityData.get(PHASE_START)+partial);}
    public float animationAge(float partial){return recalling()?Math.min(40,phaseAge(partial)):phaseAge(partial);}
    public boolean isAttacking(){return combat.age(this,0)>=0;}
    public float attackAge(float partial){return combat.age(this,partial);}
    public ItemStack sourceWeapon(){return combat.weapon();}
    public UUID leaseIdentity(){return WeaponData.identity(sourceWeapon());}
    @Override public TwoIGrimoireCombat combatState(){return combat;}
    @Override public UUID combatOwner(){return ownerId();}
    @Override public WeaponKind combatKind(){return WeaponKind.AXOLOTL;}
    @Override public boolean combatBusy(){return recalling()||emerging();}
    @Override public EntityDimensions getDefaultDimensions(Pose pose){return EntityDimensions.scalable(mountMode()?1.6F:12.8F,mountMode()?1F:7F);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(MOUNT.equals(key))refreshDimensions();}
    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger,EntityDimensions dimensions,float scale){
        return mountMode()?new Vec3(0,.64,-.43).yRot((float)Math.toRadians(180-getYRot())):new Vec3(0,5.7,0);
    }
    @Override protected boolean canAddPassenger(Entity entity){return mountMode()&&getPassengers().isEmpty()&&entity.getUUID().equals(riderId());}
    @Override public boolean canBeLeashed(){return false;}
    @Override public boolean hurt(DamageSource source,float amount){return false;}
    @Override public boolean isInvulnerableTo(DamageSource source){return true;}
    @Override public void setHealth(float value){super.setHealth(Math.max(value,getMaxHealth()));}
    @Override public void die(DamageSource source){}
    @Override public void kill(){}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean isPushable(){return false;}
    @Override protected void checkFallDamage(double y,boolean grounded,net.minecraft.world.level.block.state.BlockState state,net.minecraft.core.BlockPos pos){}
    @Override public ItemStack getPickResult(){return ItemStack.EMPTY;}
    public static CangshengAxolotlEntity deploy(ServerPlayer owner,ItemStack source,Vec3 position,LivingEntity rider){
        if(owner.isSpectator()||!owner.isAlive()||source.isEmpty()||!source.is(TwoICombatDollMod.AXOLOTL.get()))return null;
        if(rider!=null&&(!(rider instanceof DollCombatAccess access)||access.combatKind()!=WeaponKind.AXOLOTL||!owner.getUUID().equals(access.combatOwner())||rider.isPassenger()))return null;
        ServerLevel level=(ServerLevel)owner.level();var pet=TwoICombatDollMod.AXOLOTL_ENTITY.get().create(level);if(pet==null)return null;
        if(!AxolotlLeaseData.get(owner.getServer()).acquire(source,owner.getUUID(),pet.getUUID()))return null;
        pet.entityData.set(OWNER,Optional.of(owner.getUUID()));pet.entityData.set(MOUNT,rider!=null);
        pet.entityData.set(RIDER,Optional.ofNullable(rider==null?null:rider.getUUID()));pet.entityData.set(PHASE_START,level.getGameTime());
        pet.combat.hold(source.copyWithCount(1));pet.moveTo(position.x,position.y,position.z,owner.getYRot(),0);pet.refreshDimensions();
        if(rider==null)pet.titanHome=position;
        if(!level.addFreshEntity(pet)){
            var recovered=AxolotlLeaseData.get(owner.getServer()).release(pet.sourceWeapon(),pet.getUUID(),false);
            if(!recovered.isEmpty()){source.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,recovered.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));}
            return null;
        }
        if(rider!=null){rider.startRiding(pet,true);CombatActors.state(rider).hold(pet.sourceWeapon());}
        level.playSound(null,pet.blockPosition(),TwoICombatDollMod.AXOLOTL_AWAKEN.get(),net.minecraft.sounds.SoundSource.PLAYERS,1.2F,1);
        return pet;
    }
    public boolean requestRecall(ServerPlayer player){
        if(!player.getUUID().equals(ownerId())||player.level()!=level()||!isAlive())return false;
        if(!recalling()){entityData.set(RECALL,true);entityData.set(PHASE_START,level().getGameTime());combat.reset();com.twoi.combatdoll.network.CombatNetwork.send(this);}
        return true;
    }
    /** Transfer the live pet into the doll carrier before the doll itself is reclaimed. */
    public boolean retireIntoDoll(LivingEntity doll){
        if(!mountMode()||!doll.getUUID().equals(riderId())||!(level() instanceof ServerLevel server)||retired)return false;
        var stack=AxolotlLeaseData.get(server.getServer()).release(sourceWeapon(),getUUID(),false);if(stack.isEmpty())return false;
        CombatActors.state(doll).hold(stack);retired=true;combat.disarm();ejectPassengers();discard();return true;
    }
    private void clearRider(){
        if(level() instanceof ServerLevel server&&riderId()!=null&&server.getEntity(riderId()) instanceof LivingEntity doll&&doll instanceof DollCombatAccess){
            if(Objects.equals(WeaponData.identity(CombatActors.state(doll).weapon()),leaseIdentity())){
                CombatActors.state(doll).disarm();com.twoi.combatdoll.network.CombatNetwork.send(doll);
            }
            if(doll.getVehicle()==this)doll.stopRiding();
        }
    }
    private boolean deliver(ServerPlayer owner){
        int slot=owner.getInventory().getFreeSlot();if(slot<0)return false;
        var stack=AxolotlLeaseData.get(owner.getServer()).release(sourceWeapon(),getUUID(),false);if(stack.isEmpty()){retired=true;discard();return true;}
        clearRider();retired=true;combat.disarm();ejectPassengers();owner.getInventory().setItem(slot,stack);owner.getInventory().setChanged();discard();return true;
    }
    @Override public void tick(){
        float previousYaw=getYRot(),previousPitch=getXRot();
        super.tick();
        if(!(level() instanceof ServerLevel level)){
            alignPassengerFacing(getFirstPassenger());
            return;
        }
        if(retired)return;
        if(!AxolotlLeaseData.get(level.getServer()).valid(sourceWeapon(),ownerId(),getUUID())){retired=true;clearRider();ejectPassengers();discard();return;}
        var owner=level.getServer().getPlayerList().getPlayer(ownerId());
        if(recalling()){
            getNavigation().stop();setDeltaMovement(Vec3.ZERO);setNoGravity(true);
            if(phaseAge(0)>=40&&owner!=null&&owner.level()==level&&tickCount%10==0){
                if(!deliver(owner)&&tickCount%100==0)owner.displayClientMessage(Component.translatable("message.two_i_combat_doll.axolotl_pending"),true);
            }return;
        }
        LivingEntity passenger=null;
        if(mountMode()&&riderId()!=null){
            var rider=level.getEntity(riderId());
            if(rider instanceof LivingEntity doll&&doll.isAlive()){
                passenger=doll;
                // Restore the actual passenger relation after chunk/server reload.
                if(doll.getVehicle()!=this)doll.startRiding(this,true);
                if(doll instanceof DollCombatAccess)CombatActors.state(doll).hold(sourceWeapon());
            }else{
                // The passenger can be in the next entity section or still loading.
                // Missing loaded data is never evidence that its carrier was reclaimed.
                getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);return;
            }
        }
        // A cutscene owns NoAI actors' poses and movement. The custom follow
        // below must respect that contract too; NoAI only disables vanilla AI.
        if(isNoAi()||passenger instanceof Mob mob&&mob.isNoAi())return;
        if(owner==null||owner.level()!=level||!owner.isAlive()){setDeltaMovement(0,getDeltaMovement().y,0);return;}
        if(isAttacking()){
            getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);
            updateSharedFacing(passenger,owner,previousYaw,previousPitch);return;
        }
        if(emerging()){
            getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);
            alignPassengerFacing(passenger);return;
        }
        // The enormous deployed form is a resident, not the doll's tiny mount.
        // Moving the owner must never drag or teleport it through an observer.
        if(!mountMode()){
            roamTitan(level,owner);
            updateSharedFacing(null,owner,previousYaw,previousPitch);
            if(tickCount%100==0)AxolotlLeaseData.get(level.getServer()).snapshot(sourceWeapon(),getUUID());
            return;
        }
        Vec3 delta=owner.position().subtract(position());boolean flying=owner.getAbilities().flying||owner.isFallFlying();
        if(delta.lengthSqr()>64*64){teleportTo(owner.getX()+3,owner.getY(),owner.getZ()+3);delta=owner.position().subtract(position());}
        if(flying){
            setNoGravity(true);noPhysics=true;
            double distance=delta.length();double speed=Math.min(.90,Math.max(0,(distance-4.5)*.15));
            setDeltaMovement(distance>4.5?delta.normalize().scale(speed):Vec3.ZERO);
        }
        else{
            setNoGravity(false);noPhysics=false;
            followHopping(delta);
        }
        updateSharedFacing(passenger,owner,previousYaw,previousPitch);
        if(tickCount%100==0)AxolotlLeaseData.get(level.getServer()).snapshot(sourceWeapon(),getUUID());
    }
    /** One attention source for the whole pair, after vanilla look/body control.
     * LookControl queues its request for the next AI tick, so using it here
     * allowed the owner's stale request to overwrite the combat target later. */
    private void updateSharedFacing(LivingEntity passenger,ServerPlayer owner,float previousYaw,float previousPitch){
        Vec3 attention;
        boolean focused=isAttacking();
        if(focused)attention=combat.target(this);
        else if(passenger instanceof DollCombatAccess access&&access.combatState().age(passenger,0)>=0){
            attention=access.combatState().target(passenger);focused=true;
        }else if(passenger instanceof Mob rider&&rider.getTarget()!=null&&rider.getTarget().isAlive()){
            attention=rider.getTarget().getEyePosition();focused=true;
        }else if(getTarget()!=null&&getTarget().isAlive()){
            attention=getTarget().getEyePosition();focused=true;
        }else if(mountMode())attention=owner.getEyePosition();
        else{
            // An idle titan keeps its pose or looks along its own movement. In
            // particular, walking round it must not rotate it after the camera.
            Vec3 motion=getDeltaMovement();
            attention=motion.horizontalDistanceSqr()>.001?getEyePosition().add(motion.x*10,0,motion.z*10)
                    :getEyePosition().add(getLookAngle());
        }
        Vec3 delta=attention.subtract(getEyePosition());
        float wantedYaw=delta.horizontalDistanceSqr()<1.0E-8?previousYaw:(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float wantedPitch=Mth.clamp((float)-Math.toDegrees(Math.atan2(delta.y,Math.max(1.0E-4,delta.horizontalDistance()))),-35,35);
        float yaw=Mth.approachDegrees(previousYaw,wantedYaw,focused?30:8);
        float pitch=Mth.approachDegrees(previousPitch,wantedPitch,focused?15:5);
        setYRot(yaw);setXRot(pitch);yRotO=previousYaw;xRotO=previousPitch;
        yBodyRot=yHeadRot=yaw;yBodyRotO=yHeadRotO=previousYaw;
        alignPassengerFacing(passenger);
    }
    /** Also called after the passenger's tick, which runs after its mount and
     * would otherwise apply the passenger's independent vanilla look control.
     * Both interpolation endpoints must agree, including on the client. */
    public void alignPassengerFacing(Entity passenger){
        if(!mountMode()||!(passenger instanceof LivingEntity rider)||rider.getVehicle()!=this
                ||!rider.getUUID().equals(riderId())||isNoAi()||rider instanceof Mob mob&&mob.isNoAi())return;
        float yaw=getYRot(),previous=yRotO;
        yBodyRot=yHeadRot=yaw;yBodyRotO=yHeadRotO=previous;
        rider.setYRot(yaw);rider.yRotO=previous;
        rider.yBodyRot=rider.yHeadRot=yaw;rider.yBodyRotO=rider.yHeadRotO=previous;
        rider.setXRot(getXRot());rider.xRotO=xRotO;
    }
    /** Occasional small local hops, with a long rest when the owner is observing.
     * No path search, forced chunks or per-tick neighbourhood scan is needed. */
    private void roamTitan(ServerLevel level,ServerPlayer owner){
        setNoGravity(false);noPhysics=false;getNavigation().stop();
        if(titanHome==null)titanHome=position(); // Existing saves establish home in place.
        if(getBoundingBox().inflate(8).contains(owner.getEyePosition())){
            setDeltaMovement(0,getDeltaMovement().y,0);
            nextTitanHop=tickCount+120;
            return;
        }
        if(!onGround())return;
        setDeltaMovement(0,getDeltaMovement().y,0);
        if(nextTitanHop==0){nextTitanHop=tickCount+120+random.nextInt(161);return;}
        if(tickCount<nextTitanHop)return;
        nextTitanHop=tickCount+120+random.nextInt(161);
        Vec3 homeward=titanHome.subtract(position()).multiply(1,0,1);
        double angle=random.nextDouble()*Math.PI*2;
        Vec3 heading=homeward.lengthSqr()>20*20?homeward.normalize():new Vec3(Math.cos(angle),0,Math.sin(angle));
        Vec3 probe=position().add(heading.scale(2));
        // Keep the saved deployment area and avoid hopping into an unloaded
        // chunk or off a ledge. Ordinary collisions still apply to the titan.
        if(probe.subtract(titanHome).horizontalDistanceSqr()>24*24)return;
        var feet=net.minecraft.core.BlockPos.containing(probe);
        if(!level.hasChunkAt(feet)||level.getBlockState(feet.below()).getCollisionShape(level,feet.below()).isEmpty())return;
        var ahead=getBoundingBox().move(heading.scale(.6));
        if(!level.noCollision(this,ahead))return;
        setDeltaMovement(heading.x*.11,.28,heading.z*.11);hasImpulse=true;
    }
    /** The low hop has sustained airborne drive; repeated .34 impulses were lost to friction. */
    private void followHopping(Vec3 delta){
        Vec3 horizontal=delta.multiply(1,0,1);double distance=horizontal.length();
        if(distance>6)chasing=true;else if(distance<=4.5)chasing=false;
        if(!chasing){
            setDeltaMovement(0,getDeltaMovement().y,0);
            if(onGround()&&tickCount%35==0){setDeltaMovement(0,.26,0);hasImpulse=true;}
            return;
        }
        // Chase rapidly outside the owner's breathing space, then taper before
        // reaching the owner. Hysteresis avoids alternating forward/backward hops.
        double speed=Math.min(.64,Math.max(.08,(distance-4.5)*.32));
        Vec3 desired=horizontal.normalize().scale(speed),current=getDeltaMovement();
        Vec3 velocity=new Vec3(current.x,0,current.z).lerp(desired,onGround()?.70:.40);
        setDeltaMovement(velocity.x,onGround()?.36:current.y,velocity.z);hasImpulse=true;faceMovement(velocity);
    }
    private void faceMovement(Vec3 horizontal){
        if(horizontal.lengthSqr()>.001){setYRot((float)(Math.atan2(-horizontal.x,horizontal.z)*180/Math.PI));yBodyRot=getYRot();}
    }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){
        if(!level().isClientSide&&!player.getUUID().equals(ownerId()))player.displayClientMessage(Component.translatable("message.two_i_combat_doll.owner_only"),true);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public void remove(RemovalReason reason){
        if(!retired&&reason.shouldDestroy()&&level() instanceof ServerLevel server&&!sourceWeapon().isEmpty()){
            clearRider();AxolotlLeaseData.get(server.getServer()).release(sourceWeapon(),getUUID(),true);retired=true;
        }
        super.remove(reason);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);
        if(ownerId()!=null)tag.putUUID("Owner",ownerId());if(riderId()!=null)tag.putUUID("Rider",riderId());tag.putBoolean("Mount",mountMode());
        tag.putBoolean("Recalling",recalling());tag.putLong("PhaseStart",entityData.get(PHASE_START));combat.save(tag,0);
        if(titanHome!=null){tag.putDouble("TitanHomeX",titanHome.x);tag.putDouble("TitanHomeY",titanHome.y);tag.putDouble("TitanHomeZ",titanHome.z);}
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);
        entityData.set(OWNER,tag.hasUUID("Owner")?Optional.of(tag.getUUID("Owner")):Optional.empty());
        entityData.set(RIDER,tag.hasUUID("Rider")?Optional.of(tag.getUUID("Rider")):Optional.empty());entityData.set(MOUNT,tag.getBoolean("Mount"));
        entityData.set(RECALL,tag.getBoolean("Recalling"));entityData.set(PHASE_START,tag.getLong("PhaseStart"));combat.load(tag,0);refreshDimensions();
        titanHome=tag.contains("TitanHomeX",net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)
                &&tag.contains("TitanHomeY",net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)&&tag.contains("TitanHomeZ",net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)
                ?new Vec3(tag.getDouble("TitanHomeX"),tag.getDouble("TitanHomeY"),tag.getDouble("TitanHomeZ")):null;
        nextTitanHop=0;
    }
}
