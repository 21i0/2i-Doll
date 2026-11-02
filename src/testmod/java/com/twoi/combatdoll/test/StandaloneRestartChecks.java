package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.CombatWorldData;
import com.twoi.combatdoll.combat.WeaponData;
import com.twoi.combatdoll.companion.content.entity.*;
import com.twoi.combatdoll.companion.content.leaf.LeafRewardMigration;
import com.twoi.combatdoll.companion.content.leaf.StandaloneLeafDropData;
import com.twoi.combatdoll.companion.event.StandaloneDollLeafDrops;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import com.twoi.combatdoll.entity.CombatDollEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Actual on-disk historical import and a deliberately separate JVM restart. Never shipped. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID)
public final class StandaloneRestartChecks {
 private static final String FIXTURE="standalone-restart-fixture.dat";
 private static final List<String> SNAPSHOTS=List.of("useless_stretcher_two_i_story","useless_stretcher_two_i_companionship","useless_stretcher_two_i_starfield","useless_stretcher_two_i_acquisitions");
 private static UUID id(String name){return UUID.nameUUIDFromBytes(("twoi-standalone-"+name).getBytes(StandardCharsets.UTF_8));}
 private static boolean minimal(){return Boolean.getBoolean("two_i_combat_doll.standalone_minimal");}
 private static boolean restart(){return Boolean.getBoolean("two_i_combat_doll.standalone_restart_test");}
 private static Path root(MinecraftServer server){return server.getWorldPath(LevelResource.ROOT);}
 private static void check(boolean value,String message){if(!value)throw new AssertionError("Standalone restart: "+message);}
 private static CompoundTag row(String key,UUID value){var row=new CompoundTag();row.putUUID(key,value);return row;}
 private static CompoundTag list(String key,CompoundTag...rows){var tag=new CompoundTag();var list=new ListTag();Collections.addAll(list,rows);tag.put(key,list);return tag;}
 private static void file(MinecraftServer server,String name,CompoundTag data)throws Exception{
  Path target=root(server).resolve("data").resolve(name+".dat");check(!Files.exists(target),"fresh fixture never overwrites "+target);Files.createDirectories(target.getParent());
  var tag=new CompoundTag();tag.put("data",data);tag.putInt("DataVersion",net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());NbtIo.writeCompressed(tag,target);
 }
 @SubscribeEvent public static void seed(ServerAboutToStartEvent event){
  if(!Boolean.getBoolean("two_i_combat_doll.standalone_test")||!minimal())return;
  if(Boolean.getBoolean("two_i_combat_doll.standalone_recovery_fault_test"))return;
  try{
   var server=event.getServer();if(restart()){lateLegacy(server);return;}check(!Files.exists(root(server).resolve(FIXTURE)),"first process requires a fresh test world");
   var story=row("player",id("legacy-owner"));story.putInt("completed",21);story.putInt("last",4);file(server,SNAPSHOTS.get(0),list("met_players",story));
   var clock=row("doll",id("legacy-doll"));clock.putLong("day",7);clock.putInt("earned",1);clock.putInt("ticks",2300);clock.putLong("last_sample",170000);file(server,SNAPSHOTS.get(1),list("dolls",clock));
   var garden=row("player",id("legacy-owner"));garden.putInt("drops",2);file(server,SNAPSHOTS.get(2),list("reward_progress",garden));
   file(server,SNAPSHOTS.get(3),list("announced",row("id",id("legacy-doll"))));
   var oldPending=row("player",id("pending-owner"));oldPending.putUUID("identity",id("pending-doll"));oldPending.putBoolean("claimed",false);
   var oldClaimed=row("player",id("claimed-owner"));oldClaimed.putUUID("identity",id("claimed-doll"));oldClaimed.putBoolean("claimed",true);
   file(server,"two_i_combat_doll_rewards",list("awards",oldPending,oldClaimed));
   var leaf=new CompoundTag();leaf.put("staff_rolled_players",list("x",row("player",id("staff-pending")),row("player",id("staff-claimed"))).getList("x",Tag.TAG_COMPOUND));
   leaf.put("staff_claimed_players",list("x",row("player",id("staff-claimed"))).getList("x",Tag.TAG_COMPOUND));leaf.put("doll_rolled_players",new ListTag());leaf.put("doll_claimed_players",new ListTag());file(server,"useless_stretcher_staff_leaf_drops",leaf);
   var hashes=new CompoundTag();for(String name:SNAPSHOTS)hashes.putString(name,hash(root(server).resolve("data").resolve(name+".dat")));NbtIo.writeCompressed(hashes,root(server).resolve("standalone-legacy-hashes.dat"));
   LogUtils.getLogger().info("STANDALONE HISTORICAL FILE FIXTURE SEEDED: shared four-set ledger plus old story/clock/garden/announcement/random-identity rewards; no files overwritten");
  }catch(Exception failure){throw new IllegalStateException("Cannot create standalone legacy fixture",failure);}
 }
 private static void lateLegacy(MinecraftServer server)throws Exception{
  var evidence=NbtIo.readCompressed(root(server).resolve(FIXTURE),NbtAccounter.unlimitedHeap());
  check(evidence.getLong("pid")!=ProcessHandle.current().pid(),"late legacy update runs only in a new process");
  for(String name:SNAPSHOTS){
   Path original=root(server).resolve("data").resolve(name+".dat");Path backup=root(server).resolve("data").resolve(name+".before-late.dat");
   check(!Files.exists(backup),"do not overwrite initial legacy evidence");Files.copy(original,backup);
   var outer=NbtIo.readCompressed(original,NbtAccounter.unlimitedHeap());var data=outer.getCompound("data");
   if(name.endsWith("story")){data.getList("met_players",Tag.TAG_COMPOUND).getCompound(0).putInt("completed",10);data.getList("met_players",Tag.TAG_COMPOUND).getCompound(0).putInt("last",3);}
   else if(name.endsWith("companionship")){var p=data.getList("dolls",Tag.TAG_COMPOUND).getCompound(0);p.putInt("earned",2);p.putInt("ticks",300);p.putLong("last_sample",170100);}
   else if(name.endsWith("acquisitions"))data.getList("announced",Tag.TAG_COMPOUND).add(row("id",id("late-announced")));
   else {data.getList("reward_progress",Tag.TAG_COMPOUND).getCompound(0).putInt("drops",5);var stale=row("player",id("legacy-owner"));stale.putString("dimension","minecraft:overworld");stale.putDouble("x",666);stale.putDouble("y",80);stale.putDouble("z",666);data.put("return_points",list("x",stale).getList("x",Tag.TAG_COMPOUND));var show=row("player",id("legacy-owner"));show.putLong("end",999999);data.put("celebrations",list("x",show).getList("x",Tag.TAG_COMPOUND));}
   NbtIo.writeCompressed(outer,original);
  }
  var hashes=new CompoundTag();for(String name:SNAPSHOTS)hashes.putString(name,hash(root(server).resolve("data").resolve(name+".dat")));NbtIo.writeCompressed(hashes,root(server).resolve("standalone-legacy-hashes.dat"));
  LogUtils.getLogger().info("STANDALONE LATE LEGACY UPDATE SEEDED: existing local data plus later offline old files; initial files preserved as before-late copies");
 }
 private static String hash(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
 private static void verifyImports(MinecraftServer server)throws Exception{
  check(TwoIStoryData.get(server).completedMask(id("legacy-owner"))==(restart()?31:21),"historical six-chapter mask merges monotonically into existing data");
  var progress=TwoICompanionshipData.get(server).progress(id("legacy-doll"),TwoIDollMemory.empty());check(progress.day()==7&&progress.earned()==(restart()?2:1)&&progress.ticks()==(restart()?300:2300)&&progress.lastSample()==(restart()?170100:170000),"historical five-minute clock imports later progress into existing data");
  check(TwoIStarfieldData.get(server).rewardsRemaining(id("legacy-owner"))==(restart()?3:6),"historical garden rewards preserved and late higher count merged");
  if(restart()){check(!TwoIDollAcquisitionData.get(server).markIfNew(id("late-announced")),"later old announcement merged into already-existing local ledger");check(TwoIStarfieldData.get(server).returnPoint(id("legacy-owner"))==null&&TwoIStarfieldData.get(server).celebrationEnd(id("legacy-owner"))==0,"late stale return point and celebration do not resurrect");}
  check(!TwoIDollAcquisitionData.get(server).markIfNew(id("legacy-doll")),"old acquisition is not announced again");
  var ledger=LeafRewardMigration.ledger(server);check(ledger.hasPendingDoll(id("pending-owner"))&&ledger.hasClaimedDoll(id("claimed-owner")),"old pending/claimed ledger imported");
  check(LeafRewardMigration.prepare(server).identity(id("pending-owner")).equals(id("pending-doll")),"old random pending identity retained");
  var staff=StandaloneLeafDropData.get(server);check(staff.hasPendingStaff(id("staff-pending"))&&staff.hasClaimedStaff(id("staff-claimed")),"independent historical staff records retained");
  var hashes=NbtIo.readCompressed(root(server).resolve("standalone-legacy-hashes.dat"),NbtAccounter.unlimitedHeap());for(String name:SNAPSHOTS)check(hashes.getString(name).equals(hash(root(server).resolve("data").resolve(name+".dat"))),"legacy snapshot not rewritten: "+name);
 }
 private static ServerPlayer player(MinecraftServer server,String name){return FakePlayerFactory.get(server.overworld(),new GameProfile(id(name),name));}
 private static void full(ServerPlayer player){for(int i=0;i<player.getInventory().items.size();i++)player.getInventory().items.set(i,new ItemStack(Items.STONE,64));}
 private static ItemStack carrier(ServerPlayer owner,UUID identity){
  var stack=new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());stack.set(StretcherComponents.TWO_I_OWNER.get(),owner.getUUID());stack.set(StretcherComponents.TWO_I_DOLL_ID.get(),identity);stack.set(StretcherComponents.TWO_I_BOND.get(),42);stack.set(StretcherComponents.TWO_I_INVITATION_USED.get(),true);
  stack.set(StretcherComponents.TWO_I_MEMORY.get(),new TwoIDollMemory(new TwoIDollMemory.Cooldowns(17,23,37,53,0,0,-1,false),new TwoIDollMemory.Interaction(0,1,2,3,1,2100,-1,"minecraft:stone",0,1)));
  var extension=new CompoundTag();extension.putString("extension","recovery-preserve-all-components");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(extension));return stack;
 }
 private static long count(ServerPlayer player,UUID identity){long count=0;for(int i=0;i<player.getInventory().getContainerSize();i++)if(LeafRewardMigration.receiptMatches(player.getInventory().getItem(i),identity))count++;return count;}
 private static void fullBagRecall(MinecraftServer server){
  var owner=player(server,"recall-owner");owner.setPos(.5,80,.5);full(owner);var level=server.overworld();
  CombatDollEntity doll=TwoICombatDollMod.DOLL_ENTITY.get().create(level);doll.restoreFromItem(carrier(owner,id("recall-doll")));doll.setSitting(true);doll.setNoAi(true);doll.moveTo(.5,80,.5,0,0);doll.combatState().hold(new ItemStack(TwoICombatDollMod.STAFF.get()));check(level.addFreshEntity(doll),"live recall fixture added");
  var expected=doll.toDollItem();doll.reclaim(owner);doll.reclaimRemote(owner);check(!doll.isRemoved()&&count(owner,id("recall-doll"))==0,"full backpack retains the unique live entity for local+remote recall");
  check(ItemStack.isSameItemSameComponents(expected,doll.toDollItem()),"full bag does not alter memory or weapon");owner.getInventory().items.set(0,ItemStack.EMPTY);doll.reclaim(owner);doll.reclaim(owner);check(doll.isRemoved()&&count(owner,id("recall-doll"))==1,"free slot reclaims exactly once");check(ItemStack.isSameItemSameComponents(expected,owner.getInventory().getItem(0)),"normal recall preserves every component");
  LogUtils.getLogger().info("STANDALONE FULL-BAG LIVE RECALL CHECKS PASSED");
 }
 public static void run(MinecraftServer server)throws Exception{
  fullBagRecall(server);if(!minimal())return;
  verifyImports(server);
  if(restart()){verifyRestart(server);return;}
  var evidence=new CompoundTag();evidence.putLong("pid",ProcessHandle.current().pid());
  var waiting=player(server,"pending-owner");full(waiting);LeafRewardMigration.login(new PlayerEvent.PlayerLoggedInEvent(waiting));check(!StandaloneDollLeafDrops.deliver(waiting,LeafRewardMigration.ledger(server)),"historical leaf pending survives full bag");evidence.put("waiting",waiting.saveWithoutId(new CompoundTag()));
  var owner=player(server,"recovery-owner");full(owner);ItemStack original=carrier(owner,id("recovery-doll"));check(TwoIDollRecoveryData.queue(owner,original),"failed-transfer queue durably accepts bound carrier");TwoIDollRecoveryData.get(server).reconcile(owner);check(TwoIDollRecoveryData.get(server).pending(id("recovery-doll"))&&count(owner,id("recovery-doll"))==0,"failed-transfer remains safely pending while full");evidence.put("recovery-player",owner.saveWithoutId(new CompoundTag()));evidence.put("recovery-item",original.save(server.registryAccess()));
  var live=TwoICombatDollMod.DOLL_ENTITY.get().create(server.overworld());live.restoreFromItem(carrier(owner,id("witness-doll")));live.setNoAi(true);live.setSitting(true);live.moveTo(4.5,80,4.5,0,0);var weapon=new ItemStack(TwoICombatDollMod.STAFF.get());UUID weaponId=WeaponData.ensure(weapon);live.combatState().hold(weapon);check(server.overworld().addFreshEntity(live),"real persistent entity added");server.overworld().setChunkForced(0,0,true);evidence.putUUID("entity",live.getUUID());evidence.putUUID("weapon",weaponId);check(CombatWorldData.get(server).reserve(weaponId),"weapon cooldown reserved");evidence.putLong("deadline",CombatWorldData.get(server).clock()+CombatWorldData.get(server).remaining(weaponId));
  TwoIStoryData.get(server).complete(owner.getUUID(),5);var garden=TwoIStarfieldData.get(server);garden.beginVisit(owner.getUUID(),server.overworld().getGameTime(),true);garden.recordRewardDrop(owner.getUUID());garden.setReturnPoint(owner.getUUID(),server.overworld(),new net.minecraft.world.phys.Vec3(17,80,19),35,12);
  server.saveEverything(true,true,true);NbtIo.writeCompressed(evidence,root(server).resolve(FIXTURE));
  LogUtils.getLogger().info("STANDALONE FIRST PROCESS SAVE CHECKS PASSED: PID {}; historical disk import, full-bag leaf+recovery, real entity, weapon identity/deadline, story and garden saved; rerun same world with standaloneRestartTests",ProcessHandle.current().pid());
 }
 private static void verifyRestart(MinecraftServer server)throws Exception{
  var evidence=NbtIo.readCompressed(root(server).resolve(FIXTURE),NbtAccounter.unlimitedHeap());check(evidence.getLong("pid")!=ProcessHandle.current().pid(),"restart is a genuinely different operating-system process");
  var waiting=player(server,"pending-owner");waiting.load(evidence.getCompound("waiting"));LeafRewardMigration.login(new PlayerEvent.PlayerLoggedInEvent(waiting));check(LeafRewardMigration.ledger(server).hasPendingDoll(waiting.getUUID())&&count(waiting,id("pending-doll"))==0,"full-bag leaf pending survived process restart");waiting.getInventory().items.set(0,ItemStack.EMPTY);check(StandaloneDollLeafDrops.deliver(waiting,LeafRewardMigration.ledger(server)),"historical pending delivered after restart");LeafRewardMigration.login(new PlayerEvent.PlayerLoggedInEvent(waiting));LeafRewardMigration.login(new PlayerEvent.PlayerLoggedInEvent(waiting));check(!StandaloneDollLeafDrops.deliver(waiting,LeafRewardMigration.ledger(server))&&count(waiting,id("pending-doll"))==1,"relogin plus redelivery produces one doll with its historical ID");
  var owner=player(server,"recovery-owner");owner.load(evidence.getCompound("recovery-player"));var recovery=TwoIDollRecoveryData.get(server);check(recovery.pending(id("recovery-doll")),"orphan queue survived actual process restart");recovery.reconcile(owner);check(count(owner,id("recovery-doll"))==0&&recovery.pending(id("recovery-doll")),"restart with full bag still waits");owner.getInventory().items.set(0,ItemStack.EMPTY);recovery.reconcile(owner);recovery.reconcile(owner);TwoIDollRecoveryData.login(new PlayerEvent.PlayerLoggedInEvent(owner));check(count(owner,id("recovery-doll"))==1&&recovery.claimed(id("recovery-doll")),"orphan delivered exactly once after process restart/relogin");ItemStack expected=ItemStack.parse(server.registryAccess(),evidence.getCompound("recovery-item")).orElseThrow();check(ItemStack.isSameItemSameComponents(expected,owner.getInventory().getItem(0)),"orphan restores complete owner/id/memory/skin/extensions");
  server.overworld().getChunk(0,0);var found=server.overworld().getEntity(evidence.getUUID("entity"));check(found instanceof CombatDollEntity,"same real entity loaded from disk, not reconstructed");var live=(CombatDollEntity)found;check(live.getGiftScore()==42&&id("witness-doll").equals(live.toDollItem().get(StretcherComponents.TWO_I_DOLL_ID.get()))&&WeaponData.identity(live.combatState().weapon()).equals(evidence.getUUID("weapon")),"loaded entity preserves relationship/identity/weapon");
  var combat=CombatWorldData.get(server);long remaining=combat.remaining(evidence.getUUID("weapon"));check(remaining>0&&combat.clock()+remaining==evidence.getLong("deadline")&&!combat.reserve(evidence.getUUID("weapon")),"ten-minute weapon deadline survives process restart and cannot reset");
  check(TwoIStoryData.get(server).completedMask(owner.getUUID())==32,"story completion survives process restart");var garden=TwoIStarfieldData.get(server);check(garden.rewardsRemaining(owner.getUUID())==7&&garden.returnPoint(owner.getUUID()).x()==17&&garden.returnPoint(owner.getUUID()).z()==19,"garden budget and return destination survive process restart");
  LogUtils.getLogger().info("STANDALONE REAL NEW-PROCESS RESTART CHECKS PASSED: old PID {}, new PID {}; historical staff untouched, once-only leaf+recovery, full components, saved real entity, cooldown, story, garden and return destination",evidence.getLong("pid"),ProcessHandle.current().pid());
 }
}
