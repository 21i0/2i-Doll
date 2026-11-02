package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.entity.*;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.*;
import com.twoi.combatdoll.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;

final class TwoIArcanaServerChecks {
    @SuppressWarnings("unchecked")
    static void run(ServerLevel level) throws Exception {
        long before=level.getGameTime();
        var owner=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"ArcanaOwner"));
        var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);
        var players=(Map<UUID,ServerPlayer>)field.get(level.getServer().getPlayerList());
        players.put(owner.getUUID(),owner);
        try {
            level.setChunkForced(0,0,true);level.setChunkForced(-1,-1,true);
            var managerField=ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);
            var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)managerField.get(level);
            for(int x=-1;x<=0;x++)for(int z=-1;z<=0;z++)
                manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(x,z),net.minecraft.world.level.entity.Visibility.TICKING);
            owner.setPos(1,160,5);
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)level.setBlock(new BlockPos(x,159,z),Blocks.QUARTZ_BLOCK.defaultBlockState(),2);
            var doll=TwoICombatDollMod.DOLL_ENTITY.get().create(level);check(doll!=null,"doll factory");
            doll.setOwnerId(owner.getUUID());doll.setNoAi(true);doll.setPos(1,160,1);check(level.addFreshEntity(doll),"doll joined");
            var hostiles=new ArrayList<Mob>();
            for(int i=0;i<10;i++) {
                var mob=EntityType.ZOMBIE.create(level);mob.setNoAi(true);mob.setPos(-4+i,160,-5);
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);mob.setHealth(100);
                check(level.addFreshEntity(mob),"monster joined");hostiles.add(mob);
            }
            var cow=EntityType.COW.create(level);cow.setPos(3,160,2);level.addFreshEntity(cow);
            var wolf=EntityType.WOLF.create(level);wolf.setTame(true,false);wolf.setOwnerUUID(owner.getUUID());
            wolf.setPos(4,160,2);level.addFreshEntity(wolf);
            check(!TwoIGrimoireCombat.eligible(cow,owner)&&!TwoIGrimoireCombat.eligible(wolf,owner),"passive and tamed are protected");
            float cowHealth=cow.getHealth(),wolfHealth=wolf.getHealth();
            long start=-1;
            for(int i=0;i<250;i++) {
                time(level,before+i);doll.tickCount++;doll.tick();
                if(DollCombatAccess.state(doll).age(doll,0)>=0&&start<0)start=before+i;
                if(start>=0&&before+i-start<TwoIArcanaTimeline.IMPACT)
                    check(hostiles.stream().allMatch(m->m.getHealth()==100),"wind-up never damages");
            }
            check(start>=0,"natural hostile scan started a cast");
            LogUtils.getLogger().info("ARCANA SERVER HEALTH: {}",hostiles.stream().map(Mob::getHealth).toList());
            check(hostiles.stream().filter(m->m.getHealth()==76).count()==8,"exactly eight targets take exactly one 24-point hit");
            check(hostiles.stream().filter(m->m.getHealth()==100).count()==2,"target cap leaves excess monsters intact");
            check(cow.getHealth()==cowHealth&&wolf.getHealth()==wolfHealth&&owner.getHealth()==20,"player, pet and passive remain unharmed");
            check(DollCombatAccess.state(doll).age(doll,0)<0,"cast finishes");
            check(level.getBlockState(new BlockPos(1,159,1)).is(Blocks.QUARTZ_BLOCK),"domain preserves terrain");
            // A second cast is interrupted by its owner's actual interaction lease.
            for(int i=250;i<430;i++){time(level,before+i);doll.tickCount++;doll.tick();if(DollCombatAccess.state(doll).age(doll,0)>12)break;}
            check(DollCombatAccess.state(doll).age(doll,0)>0,"second cast after cooldown");
            var item=doll.toDollItem();
            doll.keepInteraction(owner,true);doll.tick();check(DollCombatAccess.state(doll).age(doll,0)<0,"UI lease cancels pending cast");
            var restored=TwoICombatDollMod.DOLL_ENTITY.get().create(level);restored.setOwnerId(owner.getUUID());
            restored.setNoAi(true);restored.setPos(1,160,1);restored.restoreFromItem(item);
            for(int i=0;i<50;i++){time(level,level.getGameTime()+1);restored.tickCount++;restored.tick();check(DollCombatAccess.state(restored).age(restored,0)<0,"item reload retains cooldown, never repeats pending damage");}
            CompoundTag saved=new CompoundTag();doll.addAdditionalSaveData(saved);
            restored.readAdditionalSaveData(saved);check(DollCombatAccess.state(restored).age(restored,0)<0,"NBT reload never resumes an attack");
            var ledger=new com.twoi.combatdoll.entity.DollAcquisitionData();
            check(ledger.begin(owner.getUUID())&&!ledger.begin(owner.getUUID()),"one reward roll per player");
            for(int slot=0;slot<36;slot++)owner.getInventory().setItem(slot,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,64));
            check(!ledger.deliver(owner)&&ledger.pending(owner.getUUID()),"full inventory retains pending reward");
            ledger=com.twoi.combatdoll.entity.DollAcquisitionData.load(ledger.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            check(ledger.pending(owner.getUUID()),"pending record survives serialized world reload");
            owner.getInventory().setItem(4,net.minecraft.world.item.ItemStack.EMPTY);
            check(ledger.deliver(owner)&&!ledger.pending(owner.getUUID()),"pending reward claimed into free slot");
            ledger=com.twoi.combatdoll.entity.DollAcquisitionData.load(ledger.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            check(!ledger.deliver(owner)&&!ledger.begin(owner.getUUID()),"claimed record prevents repeat after reload");
            var reward=owner.getInventory().getItem(4);var ownTag=reward.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getCompound("doll");
            check(ownTag.getUUID("owner").equals(owner.getUUID()),"reward belongs to its winner");
            check(!TwoICombatDollMod.DOLL_ENTITY.get().canSummon(),"commands and summoning catalogs cannot create dolls");
            LogUtils.getLogger().info("2I STANDALONE ACQUISITION CHECKS PASSED: once-only roll, full inventory, pending/claimed reload, owner binding and noSummon");
            for(float t=-1;t<200;t+=.125F) {
                check(Float.isFinite(TwoIArcanaTimeline.domain(t))&&TwoIArcanaTimeline.domain(t)>=0&&TwoIArcanaTimeline.domain(t)<=1,"finite bounded domain");
            }
            LogUtils.getLogger().info("2I ARCANA SERVER CHECKS PASSED: natural cast, wind-up, single impact, eight-target cap, owner/pet/passive protection, terrain intact, UI cancellation and item/NBT cooldown persistence");
        } finally { players.remove(owner.getUUID());time(level,before); }
    }
    private static void time(ServerLevel level,long time){level.getServer().getWorldData().overworldData().setGameTime(time);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
