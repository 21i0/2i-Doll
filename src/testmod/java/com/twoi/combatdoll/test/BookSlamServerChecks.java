package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.scores.PlayerTeam;
import java.util.*;

/** Natural server ticks, actual execution/death/drop events, never a simulated clock. */
final class BookSlamServerChecks {
    private static ServerLevel level;
    private static ServerPlayer owner,other;
    private static Map<UUID,ServerPlayer> players;
    private static Map<UUID,Integer> deaths,drops;
    private static List<Mob> fullTargets,cancelTargets,afterCancel;
    private static final List<Mob> protectedMobs=new ArrayList<>();
    private static PlayerTeam team;
    private static long fullStart,fullDeadline,caseStart,caseDeadline;
    private static UUID originalId;
    private static CompoundTag interruptedBook;
    private static int verifiedWaves,nextSpawn=1,mode;
    private static boolean cancelled;
    private static LivingEntity caster;
    private static TwoIGrimoireCombat state;
    private static float ownerHealth,otherHealth;

    static void begin(ServerLevel world,ServerPlayer player,ServerPlayer bystander,Map<UUID,ServerPlayer> online,
                      List<Mob> initial,ItemStack book,Mob passive,Mob pet,Map<UUID,Integer> deathEvents,Map<UUID,Integer> dropEvents){
        level=world;owner=player;other=bystander;players=online;fullTargets=initial;deaths=deathEvents;drops=dropEvents;
        state=CombatActors.state(owner);fullStart=state.start();originalId=WeaponData.identity(book);
        fullDeadline=CombatWorldData.get(level.getServer()).clock()+CombatWorldData.get(level.getServer()).remaining(originalId);
        ownerHealth=owner.getHealth();otherHealth=other.getHealth();protectedMobs.add(passive);protectedMobs.add(pet);
        team=level.getScoreboard().addPlayerTeam("book_slam_allies");
        level.getScoreboard().addPlayerToTeam(owner.getScoreboardName(),team);
        Mob ally=spawn(1).getFirst();level.getScoreboard().addPlayerToTeam(ally.getScoreboardName(),team);protectedMobs.add(ally);
        Mob prank=spawn(1).getFirst();prank.getPersistentData().putUUID("useless_stretcher_2i_prank_owner",owner.getUUID());protectedMobs.add(prank);
        WeaponServerChecks.check(!TwoIGrimoireCombat.eligible(ally,owner)&&!TwoIGrimoireCombat.eligible(prank,owner),"friendly hostile and prank actors are excluded");
        WeaponServerChecks.check(TwoIArcanaTimeline.bookSlamCount()==8,"eight book slams");
        WeaponServerChecks.check(TwoIArcanaTimeline.bookSlamAt(Float.NaN)==-1&&TwoIArcanaTimeline.bookSlamAt(-1)==-1
                &&TwoIArcanaTimeline.bookSlamAt(TwoIArcanaTimeline.DURATION)==-1,"no book slam outside active presentation");
        for(int i=0;i<8;i++){
            int tick=TwoIArcanaTimeline.bookSlamTick(i);
            WeaponServerChecks.check(tick==104+i*12&&TwoIArcanaTimeline.bookSlamAt(tick)==i
                    &&TwoIArcanaTimeline.bookSlamAt(tick-.01F)==i-1,"shared visual/server slam boundary "+i);
        }
    }

    static boolean tick()throws Exception{
        protectedActors();
        if(mode==0){
            float age=CombatWorldData.get(level.getServer()).clock()-fullStart;
            while(nextSpawn<TwoIArcanaTimeline.bookSlamCount()&&age>=TwoIArcanaTimeline.bookSlamTick(nextSpawn)-2){
                fullTargets.addAll(spawn(70));nextSpawn++;
            }
            if(age<TwoIArcanaTimeline.bookSlamTick(0))
                WeaponServerChecks.check(fullTargets.stream().allMatch(Entity::isAlive),"book windup never executes early");
            while(verifiedWaves<TwoIArcanaTimeline.bookSlamCount()&&age>=TwoIArcanaTimeline.bookSlamTick(verifiedWaves)+1){
                long dead=fullTargets.stream().filter(m->!m.isAlive()).count();
                WeaponServerChecks.check(dead==(verifiedWaves+1L)*64,"book wave "+verifiedWaves+" executes exactly its own 64-target cap; dead="+dead);
                WeaponServerChecks.check(fullTargets.stream().mapToInt(m->deaths.getOrDefault(m.getUUID(),0)).sum()==dead,"one actual death event per executed target");
                uniqueLoot(fullTargets);
                if(verifiedWaves==0){interruptedBook=new CompoundTag();state.save(interruptedBook,0);}
                // Arrivals between hits survive duplicate callbacks at this same
                // real server age, then become eligible for the next scheduled hit.
                List<Mob> arrivals=spawn(3);fullTargets.addAll(arrivals);
                for(int repeat=0;repeat<3;repeat++)state.tick(owner,level);
                WeaponServerChecks.check(arrivals.stream().allMatch(Entity::isAlive),"spent book wave never replays against new arrivals "+verifiedWaves);
                LogUtils.getLogger().info("BOOK SLAM WAVE PASSED: wave={} tick={} dead={} alive={} oneCooldownDeadline={}",
                        verifiedWaves,TwoIArcanaTimeline.bookSlamTick(verifiedWaves),dead,fullTargets.stream().filter(Entity::isAlive).count(),fullDeadline);
                verifiedWaves++;
            }
            unchangedDeadline(originalId,fullDeadline);
            WeaponServerChecks.check(!level.getServer().tickRateManager().isFrozen(),"book slams never freeze the server");
            if(age<230)return false;
            WeaponServerChecks.check(verifiedWaves==8&&state.age(owner,0)<0,"eight-wave book presentation completed");
            WeaponServerChecks.check(fullTargets.stream().filter(m->!m.isAlive()).count()==512,"exactly eight bounded executions, no late repeated impact");
            fullTargets.forEach(Entity::discard);beginCancel(1);return false;
        }
        float age=CombatWorldData.get(level.getServer()).clock()-caseStart;
        if(!cancelled){
            unchangedDeadline(WeaponData.identity(state.weapon()),caseDeadline);
            if(age<105)return false;
            WeaponServerChecks.check(cancelTargets.stream().noneMatch(Entity::isAlive),"first wave ran before interrupt mode="+mode);
            uniqueLoot(cancelTargets);CompoundTag saved=new CompoundTag();state.save(saved,0);
            if(mode==1)state.reset();
            if(mode==2)state.load(saved,0);
            if(mode==3){
                players.remove(owner.getUUID());state.tick(caster,level);players.put(owner.getUUID(),owner);
            }
            if(mode==4){
                WeaponServerChecks.check((boolean)caster.getClass().getMethod("reclaim",net.minecraft.world.entity.player.Player.class).invoke(caster,owner)
                        &&caster.isRemoved(),"actual Longhuan reclaim interrupts the active book");
                state.tick(caster,level);
                ItemStack carrier=owner.getInventory().items.stream().filter(WeaponEquipment::hasStoredWeapon).findFirst().orElseThrow();
                var restored=longhuan(carrier.copyWithCount(1));carrier.shrink(1);caster=restored;state=CombatActors.state(restored);
                WeaponServerChecks.check(state.armed()&&state.age(caster,0)<0,"actual reclaimed carrier restores weapon, never pending waves");
            }
            WeaponServerChecks.check(state.age(caster,0)<0,"interrupt cancels remaining waves mode="+mode);
            WeaponServerChecks.check(!state.begin(caster,level,true),"interrupt/reload cannot reserve a second cooldown mode="+mode);
            afterCancel=spawn(3);cancelled=true;
        }
        WeaponServerChecks.check(afterCancel.stream().allMatch(Entity::isAlive),"pending/rejoined/reclaimed waves stay cancelled mode="+mode);
        unchangedDeadline(WeaponData.identity(state.weapon()),caseDeadline);
        uniqueLoot(cancelTargets);protectedActors();
        if(age<215)return false;
        if(mode==4){
            WeaponServerChecks.check((boolean)caster.getClass().getMethod("reclaim",net.minecraft.world.entity.player.Player.class).invoke(caster,owner),"restored Longhuan fixture reclaims cleanly");
        }
        cancelTargets.forEach(Entity::discard);afterCancel.forEach(Entity::discard);owner.getInventory().clearContent();
        LogUtils.getLogger().info("BOOK SLAM INTERRUPTION PASSED: mode={} first hit once, remaining waves cancelled, no repeated loot or cooldown reservation",mode);
        if(mode<3||mode==3&&Boolean.getBoolean("two_i_combat_doll.with_longhuan")){beginCancel(mode+1);return false;}
        level.getScoreboard().removePlayerFromTeam(owner.getScoreboardName(),team);level.getScoreboard().removePlayerTeam(team);
        protectedMobs.get(2).discard();protectedMobs.get(3).discard();
        LogUtils.getLogger().info("BOOK SLAM SERVER CHECKS PASSED: eight real scheduled waves, 512 bounded executions, new arrivals each wave, same-age duplicate callbacks, owner/player/pet/passive/team/prank protection, one ten-minute reservation, reset/load/disconnect/rejoin and real Longhuan reclaim cancellation; reclaim={}",Boolean.getBoolean("two_i_combat_doll.with_longhuan"));
        return true;
    }

    private static void beginCancel(int next)throws Exception{
        mode=next;cancelled=false;cancelTargets=spawn(3);ItemStack fresh=new ItemStack(TwoICombatDollMod.BOOK.get());
        if(mode==4){
            caster=longhuan(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("longhuan:longhuan_doll"))));
            var drop=new ItemEntity(level,caster.getX(),caster.getY(),caster.getZ(),fresh);drop.setThrower(owner);level.addFreshEntity(drop);WeaponEquipment.collect(caster);
            state=CombatActors.state(caster);WeaponServerChecks.check(state.armed()&&drop.isRemoved(),"real Longhuan collects interruption-test book");
            WeaponServerChecks.check(state.begin(caster,level,true),"Longhuan book begins before actual reclaim");
        }else{
            caster=owner;owner.setItemInHand(InteractionHand.MAIN_HAND,fresh);
            WeaponServerChecks.check(fresh.use(level,owner,InteractionHand.MAIN_HAND).getResult().consumesAction(),"real player book begins interrupt mode="+mode);
            state=CombatActors.state(owner);
        }
        caseStart=state.start();var data=CombatWorldData.get(level.getServer());caseDeadline=data.clock()+data.remaining(WeaponData.identity(state.weapon()));
    }
    private static LivingEntity longhuan(ItemStack carrier)throws Exception{
        var actor=(PathfinderMob)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("longhuan:longhuan_doll")).create(level);
        actor.getClass().getMethod("initialize",UUID.class,ItemStack.class).invoke(actor,owner.getUUID(),carrier);
        actor.setNoAi(true);actor.moveTo(1,160,3,0,0);WeaponServerChecks.check(level.addFreshEntity(actor),"Longhuan joins real server");return actor;
    }
    private static List<Mob> spawn(int count){
        var result=new ArrayList<Mob>();
        for(int i=0;i<count;i++){
            var mob=EntityType.ZOMBIE.create(level);mob.setNoAi(true);mob.setNoGravity(true);
            mob.setPos(-9+(i%10)*2,160,-9+(i/10)*2);mob.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
            WeaponServerChecks.check(level.addFreshEntity(mob),"new-wave enemy joins actual server");
            WeaponServerChecks.check(mob.distanceToSqr(owner)<24*24,"new-wave target lies inside real execution radius");result.add(mob);
        }
        return result;
    }
    private static void unchangedDeadline(UUID id,long expected){
        var data=CombatWorldData.get(level.getServer());WeaponServerChecks.check(data.clock()+data.remaining(id)==expected,"all waves share the one initial ten-minute cooldown deadline");
    }
    private static void protectedActors(){
        WeaponServerChecks.check(owner.isAlive()&&other.isAlive()&&owner.getHealth()==ownerHealth&&other.getHealth()==otherHealth
                &&protectedMobs.stream().allMatch(Entity::isAlive),"all waves preserve owner, other player, passive, pet, allied hostile and prank actors");
    }
    private static void uniqueLoot(List<Mob> targets){
        WeaponServerChecks.check(targets.stream().allMatch(m->deaths.getOrDefault(m.getUUID(),0)<=1&&drops.getOrDefault(m.getUUID(),0)<=1),"no victim receives a duplicate death/drop event");
    }
    static UUID originalBookIdentity(){return originalId;}
    static CompoundTag interruptedBookSave(){return interruptedBook.copy();}
}
