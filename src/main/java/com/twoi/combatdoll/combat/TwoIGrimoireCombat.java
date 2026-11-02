package com.twoi.combatdoll.combat;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.item.ArcanaWeaponItem;
import com.twoi.combatdoll.network.CombatNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;

/** Shared authoritative spell state for players and companion bridges. */
public final class TwoIGrimoireCombat {
    private ItemStack weapon=ItemStack.EMPTY;
    private boolean struck;
    private boolean axolotlRoared;
    private int bookSlamMask;
    private long start=-1;
    private Vec3 targetDelta=Vec3.ZERO;
    private WeaponKind kind=WeaponKind.STAFF;
    private boolean charging;
    private int heldTicks;
    private Vec3 sceneOrigin,stageAnchor,blackHole,lastSentHole;
    private Vec3 previousHole;
    private long holeReceived;
    public float age(LivingEntity actor,float partial){
        if(start<0)return -1;
        long now=actor.level().isClientSide()?com.twoi.combatdoll.client.ClientCombatClock.now():CombatWorldData.get(actor.getServer()).clock();
        float elapsed=Math.max(0,now-start+partial);
        return charging?Math.min(TwoIArcanaTimeline.CHARGE-1,elapsed):elapsed;
    }
    public boolean armed(){return !weapon.isEmpty();}
    public ItemStack weapon(){return weapon;}
    public WeaponKind kind(){return kind;}
    public Vec3 target(LivingEntity actor){return actor.position().add(targetDelta);}
    public long start(){return start;}
    public Vec3 targetDelta(){return targetDelta;}
    public boolean charging(){return charging;}
    public int heldTicks(){return heldTicks;}
    private long now(LivingEntity actor){return actor.level().isClientSide()?com.twoi.combatdoll.client.ClientCombatClock.now():CombatWorldData.get(actor.getServer()).clock();}
    public float chargeTicks(LivingEntity actor,float partial){return charging?Math.max(0,now(actor)-start+partial):heldTicks;}
    public float chargeExtra(LivingEntity actor,float partial){return charging?Math.max(0,chargeTicks(actor,partial)-(TwoIArcanaTimeline.CHARGE-1)):Math.max(0,heldTicks-TwoIArcanaTimeline.CHARGE);}
    public Vec3 sceneOrigin(LivingEntity actor){return sceneOrigin==null?actor.position():sceneOrigin;}
    public Vec3 stageAnchor(LivingEntity actor){return stageAnchor==null?actor.position().add(0,18,-14):stageAnchor;}
    public Vec3 blackHole(LivingEntity actor){return blackHole==null?stageAnchor(actor):blackHole;}
    public Vec3 blackHole(LivingEntity actor,float partial){
        if(!actor.level().isClientSide()||previousHole==null)return blackHole(actor);
        double t=Math.clamp((now(actor)-holeReceived+partial)/4.0,0,1);
        return previousHole.lerp(blackHole(actor),t);
    }
    public Vec3 aimPoint(LivingEntity actor){
        Vec3 proposed=actor.getEyePosition().add(actor.getLookAngle().scale(32));
        Vec3 origin=sceneOrigin(actor),delta=proposed.subtract(origin);
        if(delta.lengthSqr()>64*64)proposed=origin.add(delta.normalize().scale(64));
        return new Vec3(proposed.x,Math.clamp(proposed.y,origin.y+5,origin.y+60),proposed.z);
    }
    /** Reads only vanilla server rotation; clients never supply a world position. */
    public boolean updateChargeAim(ServerPlayer actor){
        if(!charging||kind!=WeaponKind.STAFF||!actor.isUsingItem()||!(actor.getUseItem().getItem() instanceof ArcanaWeaponItem item)
                ||item.kind()!=WeaponKind.STAFF||!java.util.Objects.equals(WeaponData.identity(actor.getUseItem()),WeaponData.identity(weapon)))return false;
        Vec3 proposed=aimPoint(actor);
        if(!Double.isFinite(proposed.x)||!Double.isFinite(proposed.y)||!Double.isFinite(proposed.z))return false;
        blackHole=proposed;return true;
    }
    public void synchronize(long serverStart,long serverNow,Vec3 delta,WeaponKind kind,boolean armed){
        start=serverStart<0?-1:com.twoi.combatdoll.client.ClientCombatClock.now()-(serverNow-serverStart);
        targetDelta=delta;this.kind=kind;
        weapon=armed?new ItemStack(kind==WeaponKind.AXOLOTL?TwoICombatDollMod.AXOLOTL.get():kind==WeaponKind.BOOK?TwoICombatDollMod.BOOK.get():TwoICombatDollMod.STAFF.get()):ItemStack.EMPTY;
    }
    public void synchronize(long start,Vec3 delta){this.start=start;targetDelta=delta;}
    public void synchronizeScene(boolean charging,int heldTicks,Vec3 origin,Vec3 anchor,Vec3 hole){
        previousHole=blackHole==null?hole:blackHole;
        holeReceived=com.twoi.combatdoll.client.ClientCombatClock.now();
        this.charging=charging;this.heldTicks=heldTicks;sceneOrigin=origin;stageAnchor=anchor;blackHole=hole;
        if(!charging)previousHole=hole;
    }
    public void reset(){start=-1;struck=true;charging=false;bookSlamMask=(1<<TwoIArcanaTimeline.bookSlamCount())-1;}
    public void hold(ItemStack stack){weapon=stack;kind=((ArcanaWeaponItem)stack.getItem()).kind();}
    public void disarm(){reset();weapon=ItemStack.EMPTY;}
    public boolean equip(LivingEntity actor,ItemStack stack){
        if(com.twoi.combatdoll.duel.DollDuelArena.isDuelActor(actor))return false;
        if(armed()||!(stack.getItem() instanceof ArcanaWeaponItem item)||!(actor instanceof DollCombatAccess access)||item.kind()!=access.combatKind())return false;
        if(access.combatOwner()==null)return false;
        var given=WeaponData.tag(stack);if(given.hasUUID("owner")&&!given.getUUID("owner").equals(access.combatOwner()))return false;
        weapon=stack.copyWithCount(1);kind=item.kind();WeaponData.ensure(weapon);
        var own=WeaponData.tag(weapon);own.putUUID("owner",access.combatOwner());WeaponData.write(weapon,own);
        if(kind==WeaponKind.AXOLOTL){
            var owner=actor.getServer().getPlayerList().getPlayer(access.combatOwner());
            if(owner==null||com.twoi.combatdoll.entity.CangshengAxolotlEntity.deploy(owner,weapon,actor.position(),actor)==null){disarm();return false;}
        }
        CombatNetwork.send(actor);return true;
    }
    private void end(LivingEntity actor){if(start<0)return;reset();if(kind==WeaponKind.STAFF)Chronostasis.actorLeft(actor);CombatNetwork.send(actor);}
    public static boolean eligible(Mob mob,ServerPlayer owner){
        return mob.isAlive()&&!mob.isRemoved()&&!(mob instanceof DollCombatAccess)
                &&!(mob instanceof TamableAnimal pet&&pet.isTame())&&!mob.isAlliedTo(owner)&&!owner.isAlliedTo(mob)
                &&!mob.getPersistentData().contains("useless_stretcher_2i_prank_owner")&&(mob instanceof Enemy||mob.getTarget()==owner);
    }
    public boolean begin(LivingEntity actor,ServerLevel level,boolean manual){
        return begin(actor,level,manual,false);
    }
    public boolean beginCharge(ServerPlayer actor,ServerLevel level){
        if(kind!=WeaponKind.STAFF||!available(actor,level))return false;
        start=CombatWorldData.get(level.getServer()).clock();charging=true;heldTicks=0;struck=true;
        targetDelta=actor.getLookAngle().scale(18).add(0,1,0);placeScene(actor);
        blackHole=aimPoint(actor);lastSentHole=blackHole;
        CombatRuntime.register(actor);CombatNetwork.send(actor);return true;
    }
    private boolean available(LivingEntity actor,ServerLevel level){
        if(!armed()||age(actor,0)>=0||Chronostasis.active(level.getServer())||CombatActors.busy(actor))return false;
        var owner=level.getServer().getPlayerList().getPlayer(CombatActors.owner(actor));
        if(owner==null||owner.level()!=level||owner.isSpectator()||!owner.isAlive())return false;
        var data=CombatWorldData.get(level.getServer());var previous=WeaponData.tag(weapon);var id=WeaponData.ensure(weapon);
        if(previous.hasUUID("owner")&&!previous.getUUID("owner").equals(owner.getUUID()))return false;
        data.restoreDeadline(id,previous.getLong("ready_at"));return data.remaining(id)<=0;
    }
    private void placeScene(LivingEntity actor){
        sceneOrigin=actor.position();Vec3 direction=targetDelta.multiply(1,0,1).normalize();
        if(direction.lengthSqr()<.1)direction=new Vec3(0,0,-1);
        stageAnchor=sceneOrigin.add(direction.scale(14)).add(0,18,0);blackHole=stageAnchor;
    }
    public boolean releaseCharge(ServerPlayer actor,ServerLevel level,ItemStack used){
        if(!charging||kind!=WeaponKind.STAFF)return false;
        ItemStack current=actor.getItemInHand(actor.getUsedItemHand());
        if(!current.is(TwoICombatDollMod.STAFF.get())
                ||!java.util.Objects.equals(WeaponData.identity(current),WeaponData.identity(weapon))
                ||!java.util.Objects.equals(WeaponData.identity(used),WeaponData.identity(weapon))||chargeTicks(actor,0)<TwoIArcanaTimeline.CHARGE){end(actor);return false;}
        updateChargeAim(actor);heldTicks=(int)Math.min(72_000,chargeTicks(actor,0));
        if(!begin(actor,level,true,true)){end(actor);return false;}
        return true;
    }
    private boolean begin(LivingEntity actor,ServerLevel level,boolean manual,boolean fromCharge){
        if(com.twoi.combatdoll.duel.DollDuelArena.isDuelActor(actor))return false;
        if(kind==WeaponKind.AXOLOTL&&(!(actor instanceof com.twoi.combatdoll.entity.CangshengAxolotlEntity pet)
                ||!AxolotlLeaseData.get(level.getServer()).valid(weapon,pet.ownerId(),actor.getUUID())))return false;
        if(!armed()||(!fromCharge&&age(actor,0)>=0)||Chronostasis.active(level.getServer())||CombatActors.busy(actor))return false;
        var owner=level.getServer().getPlayerList().getPlayer(CombatActors.owner(actor));
        if(owner==null||owner.level()!=level||owner.isSpectator()||!owner.isAlive())return false;
        var data=CombatWorldData.get(level.getServer());
        var identity=WeaponData.ensure(weapon);var previous=WeaponData.tag(weapon);
        if(previous.hasUUID("owner")&&!previous.getUUID("owner").equals(owner.getUUID()))return false;
        data.restoreDeadline(identity,previous.getLong("ready_at"));
        if(data.remaining(identity)>0)return false;
        var targets=level.getEntitiesOfClass(Mob.class,actor.getBoundingBox().inflate(24),m->eligible(m,owner)&&actor.hasLineOfSight(m));
        var target=targets.stream().min(Comparator.comparingDouble(m->m.distanceToSqr(actor))).orElse(null);
        if(target==null&&!manual)return false;
        if(!data.reserve(identity))return false;
        var own=WeaponData.tag(weapon);own.putLong("ready_at",data.clock()+CombatWorldData.COOLDOWN);WeaponData.write(weapon,own);
        start=data.clock()-(fromCharge?TwoIArcanaTimeline.CHARGE:0);struck=false;axolotlRoared=false;charging=false;bookSlamMask=0;
        targetDelta=target==null?actor.getLookAngle().scale(18).add(0,1,0):target.getBoundingBox().getCenter().subtract(actor.position());
        if(!fromCharge){heldTicks=0;placeScene(actor);}
        CombatNetwork.send(actor);
        if(fromCharge)Chronostasis.start(actor,TwoIArcanaTimeline.FREEZE_END-TwoIArcanaTimeline.CHARGE);
        if(kind==WeaponKind.BOOK)level.playSound(null,actor.blockPosition(),TwoICombatDollMod.OPEN.get(),SoundSource.PLAYERS,1,1);
        return true;
    }
    public void tick(LivingEntity actor,ServerLevel level){
        if(!armed())return;
        if(actor.isRemoved()||!actor.isAlive()){end(actor);return;}
        var owner=level.getServer().getPlayerList().getPlayer(CombatActors.owner(actor));
        if(owner==null||owner.level()!=level||!owner.isAlive()||owner.isSpectator()||actor.distanceToSqr(owner)>48*48||CombatActors.busy(actor)){end(actor);return;}
        if(Chronostasis.frozen(actor))return;
        if(charging){
            if(!(actor instanceof ServerPlayer player)||!player.isUsingItem()||!(player.getUseItem().getItem() instanceof ArcanaWeaponItem item)
                    ||item.kind()!=WeaponKind.STAFF||!java.util.Objects.equals(WeaponData.identity(player.getUseItem()),WeaponData.identity(weapon))){end(actor);return;}
            if(CombatWorldData.get(level.getServer()).clock()%4==0&&updateChargeAim(player)
                    &&(lastSentHole==null||lastSentHole.distanceToSqr(blackHole)>.0025)){
                lastSentHole=blackHole;CombatNetwork.send(actor);
            }
            return;
        }
        float age=age(actor,0);
        if(age<0){
            if(actor instanceof DollCombatAccess&&Math.floorMod(CombatWorldData.get(level.getServer()).clock()+actor.getId(),10)==0)begin(actor,level,false);
            return;
        }
        if(age>=(kind==WeaponKind.AXOLOTL?220:TwoIArcanaTimeline.DURATION)){end(actor);return;}
        if(actor instanceof PathfinderMob mob){mob.getNavigation().stop();mob.getLookControl().setLookAt(target(actor));mob.setDeltaMovement(0,mob.isNoGravity()?0:mob.getDeltaMovement().y,0);}
        if(kind==WeaponKind.STAFF&&age>=TwoIArcanaTimeline.CHARGE&&age<TwoIArcanaTimeline.FREEZE_END&&!Chronostasis.active(level.getServer()))Chronostasis.start(actor,(int)(TwoIArcanaTimeline.FREEZE_END-age));
        if(kind==WeaponKind.BOOK){
            int wave=TwoIArcanaTimeline.bookSlamAt(age);
            if(wave>=0&&(bookSlamMask&(1<<wave))==0){
                // Mark before external execution/death callbacks. If a frozen
                // actor missed prior waves, consume them without replaying them.
                bookSlamMask|=(1<<(wave+1))-1;
                executeImpact(actor,owner,level);
            }
        }else if(kind==WeaponKind.AXOLOTL){
            if(!axolotlRoared&&age>=98){axolotlRoared=true;level.playSound(null,actor.blockPosition(),TwoICombatDollMod.AXOLOTL_ROAR.get(),SoundSource.PLAYERS,2F,1);}
            if(!struck&&age>=160){struck=true;executeImpact(actor,owner,level);}
        }else if(!struck&&age>=TwoIArcanaTimeline.IMPACT){
            struck=true;executeImpact(actor,owner,level);
        }
    }
    private void executeImpact(LivingEntity actor,ServerPlayer owner,ServerLevel level){
        var targets=level.getEntitiesOfClass(Mob.class,actor.getBoundingBox().inflate(28),m->eligible(m,owner)&&m.distanceToSqr(actor)<=28*28)
                .stream().sorted(Comparator.comparingDouble(m->m.position().distanceToSqr(target(actor)))).limit(TwoIArcanaTimeline.MAX_TARGETS).toList();
        for(var mob:targets)AuraExecution.execute(owner,mob,weapon);
        level.playSound(null,actor.blockPosition(),kind==WeaponKind.AXOLOTL?TwoICombatDollMod.AXOLOTL_IMPACT.get():kind==WeaponKind.STAFF?TwoICombatDollMod.STAR_VERDICT.get():TwoICombatDollMod.IMPACT.get(),SoundSource.PLAYERS,1,1);
    }
    public void save(CompoundTag tag,long ignored){
        // Only the bound weapon is durable. Reloading/reclaiming cancels the
        // presentation rather than persisting/replaying partially spent waves.
        var own=new CompoundTag();var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if(armed()&&server!=null)own.put("weapon",weapon.save(server.registryAccess()));
        tag.put("two_i_combat_doll",own);
    }
    public void load(CompoundTag tag,long ignored){
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();var own=tag.getCompound("two_i_combat_doll");
        weapon=server!=null&&own.contains("weapon")?ItemStack.parseOptional(server.registryAccess(),own.getCompound("weapon")):ItemStack.EMPTY;
        if(weapon.getItem() instanceof ArcanaWeaponItem item)kind=item.kind();else weapon=ItemStack.EMPTY;
        reset();
    }
}
