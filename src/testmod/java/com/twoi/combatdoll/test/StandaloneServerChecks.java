package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.entity.CombatDollEntity;
import com.twoi.combatdoll.companion.content.entity.*;
import com.twoi.combatdoll.companion.content.item.TwoIDollItem;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import com.twoi.combatdoll.companion.content.leaf.LeafRewardMigration;
import com.twoi.combatdoll.companion.content.leaf.StandaloneLeafDropData;
import com.twoi.combatdoll.companion.event.StandaloneDollLeafDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Dedicated-server fixture: intentionally contains no client class references. Never shipped. */
public final class StandaloneServerChecks {
 private static int ticks;private static boolean initialized,done;
 public static void tick(net.minecraft.server.MinecraftServer server){
  if(done||++ticks<100)return;
  try{
   if(!initialized){
    initialized=true;
    if(Boolean.getBoolean("two_i_combat_doll.standalone_minimal"))for(String absent:List.of("useless_stretcher","useless_mod","doll_chronicle","curios"))check(!net.neoforged.fml.ModList.get().isLoaded(absent),"minimal mod set excludes "+absent);
    if(Boolean.getBoolean("two_i_combat_doll.standalone_recovery_fault_test")){
     StandaloneRecoveryFaultChecks.run(server);finish(server);return;
    }
    var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"StandaloneTest"));
    runServer(player);
    StandaloneRestartChecks.run(server);
    BundledCompanionFeatureChecks.start(player);return;
   }
   if(!BundledCompanionFeatureChecks.finished())return;
   BundledCompanionFeatureChecks.assertPassed();
   LogUtils.getLogger().info("STANDALONE DEDICATED SERVER CHECKS PASSED: no client classes loaded; common companion/leaf/ownership/state and natural-tick final bundled companion feature checks passed");finish(server);
  }catch(Throwable failure){LogUtils.getLogger().error("STANDALONE DEDICATED SERVER CHECKS FAILED",failure);finish(server);}
 }
 private static void finish(net.minecraft.server.MinecraftServer server){done=true;server.saveEverything(true,true,true);server.halt(false);}
 public static void runServer(ServerPlayer player)throws Exception{
  var level=player.serverLevel();
  LegacyChuanshouMigrationChecks.run(player);
  TwoIStorySessionChecks.main(new String[0]);TwoIStarfieldDataChecks.run(level.registryAccess());
  com.twoi.combatdoll.companion.regression.TwoIInteractionChecks.run(level);
  ItemStack legacy=new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());UUID identity=UUID.randomUUID();
  CompoundTag custom=new CompoundTag(),own=new CompoundTag();own.putUUID("owner",player.getUUID());own.putUUID("identity",identity);own.putString("kind",com.twoi.combatdoll.api.DollCatalog.TWO_I.toString());own.putBoolean("sitting",true);custom.put("doll",own);custom.putString("extension","keep me");legacy.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));
  ItemStack migrated=LegacyTwoIData.canonicalStack(legacy);check(identity.equals(migrated.get(StretcherComponents.TWO_I_DOLL_ID.get()))&&player.getUUID().equals(migrated.get(StretcherComponents.TWO_I_OWNER.get())),"1.2 custom owner/identity imported");
  var memory=new TwoIDollMemory(new TwoIDollMemory.Cooldowns(17,1200,100,500,0,0,-1,false),new TwoIDollMemory.Interaction(-1,0,0,0,0,0,-1,"minecraft:stone",0,1));
  migrated.set(StretcherComponents.TWO_I_MEMORY.get(),memory);migrated.set(StretcherComponents.TWO_I_BOND.get(),42);migrated.set(StretcherComponents.TWO_I_INVITATION_USED.get(),true);migrated.set(DataComponents.CUSTOM_NAME,Component.literal("port memory"));
  var doll=TwoICombatDollMod.DOLL_ENTITY.get().create(level);doll.restoreFromItem(migrated);doll.combatState().hold(new ItemStack(TwoICombatDollMod.STAFF.get()));
  CompoundTag saved=doll.saveWithoutId(new CompoundTag());var loaded=TwoICombatDollMod.DOLL_ENTITY.get().create(level);loaded.load(saved);ItemStack returned=loaded.toDollItem();
  check(loaded.isSitting()&&loaded.kind().equals(doll.kind())&&loaded.combatState().armed(),"entity load keeps sitting/kind/bound combat weapon");
  check(loaded.getGiftScore()==42&&loaded.isInvitationUsed()&&memory.equals(returned.get(StretcherComponents.TWO_I_MEMORY.get())),"entity load keeps bond/invitation/full cooldown+budgets");
  check(identity.equals(returned.get(StretcherComponents.TWO_I_DOLL_ID.get()))&&returned.getHoverName().getString().equals("port memory")&&returned.get(DataComponents.CUSTOM_DATA).copyTag().getString("extension").equals("keep me"),"entity load keeps identity/name/unrelated extension");
  var ledger=TwoIDollAcquisitionData.get(level.getServer());UUID receipt=UUID.randomUUID();check(ledger.markIfNew(receipt)&&!ledger.markIfNew(receipt),"acquisition announcement once");
  check(!TwoICombatDollMod.DOLL_ENTITY.get().canSummon(),"canonical type excluded from summon catalog");
  sharedLedgers(level);
  leafMigration(level);
  leafEvents(level);
  leaf(level);
  // Exercise real lifecycle mixins. Ordinary destructive removal is refused; recall remains final.
  loaded.setSitting(false);loaded.moveTo(6,80,6,0,0);check(level.addFreshEntity(loaded),"protected fixture added");loaded.setHealth(0);loaded.kill();loaded.discard();check(!loaded.isRemoved()&&loaded.getHealth()==20,"forced death/discard cannot remove companion");check(loaded.discardForReclaim()&&loaded.isRemoved(),"authorized recall works without resurrection");
  if(net.neoforged.fml.ModList.get().isLoaded("curios")) CompanionCuriosChecks.run(level);
  StandaloneOptionalChecks.run(player);
  StandaloneRecipeChecks.run(level);
  ThreeNewDollChecks.runServer(player);
  LogUtils.getLogger().info("COMPANION SERVER CHECKS PASSED (pure and registered actions)");
 }
 @SuppressWarnings({"rawtypes","unchecked"}) private static void sharedLedgers(net.minecraft.server.level.ServerLevel level)throws Exception{
  Object original=SharedLegacyTwoI.data(level.getServer(),"TwoICompanionshipData");if(original==null)return;
  UUID doll=UUID.randomUUID();long time=240000L+5900L;
  var memory=new TwoIDollMemory(TwoIDollMemory.empty().cooldowns(),new TwoIDollMemory.Interaction(10,0,0,0,0,5900,-1,"",0,0),time-100);
  var data=TwoICompanionshipData.get(level.getServer());
  check(data.sample(doll,memory,time).awarded(),"canonical form reaches one five-minute reward");
  Class<?> oldMemoryType=Class.forName("com.sorrowmist.useless.stretcher.content.entity.TwoIDollMemory");
  com.mojang.serialization.Codec codec=(com.mojang.serialization.Codec)oldMemoryType.getField("CODEC").get(null);
  var tag=TwoIDollMemory.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,memory).getOrThrow();
  Object oldMemory=codec.parse(net.minecraft.nbt.NbtOps.INSTANCE,tag).getOrThrow();
  Object sample=original.getClass().getMethod("sample",UUID.class,oldMemoryType,long.class).invoke(original,doll,oldMemory,time);
  check(!(boolean)sample.getClass().getMethod("awarded").invoke(sample),"old form same ID/tick cannot award again");
  // Serialize a real legacy carrier, convert through codecs, and drop legacy
  // typed keys entirely so a later standalone load does not require their codecs.
  ItemStack legacyItem=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("useless_stretcher:two_i_doll")));
  var oldOwner=(net.minecraft.core.component.DataComponentType)net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.get(net.minecraft.resources.ResourceLocation.parse("useless_stretcher:two_i_owner"));
  var oldMemoryComponent=(net.minecraft.core.component.DataComponentType)net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.get(net.minecraft.resources.ResourceLocation.parse("useless_stretcher:two_i_memory"));
  legacyItem.set(oldOwner,doll);legacyItem.set(oldMemoryComponent,oldMemory);
  var converted=LegacyTwoIData.canonicalStack(legacyItem);
  check(doll.equals(converted.get(StretcherComponents.TWO_I_OWNER.get()))&&memory.equals(converted.get(StretcherComponents.TWO_I_MEMORY.get())),"real old typed components migrate by codec");
  String serialized=converted.save(level.registryAccess()).toString();
  check(!serialized.contains("useless_stretcher:two_i_"),"canonical serialized carrier no longer references old typed codecs or tombstones");
  UUID player=UUID.randomUUID();TwoIStoryData.get(level.getServer()).complete(player,2);
  Object story=SharedLegacyTwoI.data(level.getServer(),"TwoIStoryData");
  check(((int)story.getClass().getMethod("completedMask",UUID.class).invoke(story,player)&4)!=0,"canonical chapter visible to old entity");
  story.getClass().getMethod("complete",UUID.class,int.class).invoke(story,player,3);
  check((TwoIStoryData.get(level.getServer()).completedMask(player)&12)==12,"old chapter visible to canonical entity");
  var courtyard=TwoIStarfieldData.get(level.getServer());courtyard.beginVisit(player,1000,true);courtyard.recordRewardDrop(player);
  Object oldGarden=SharedLegacyTwoI.data(level.getServer(),"TwoIStarfieldData");
  check((int)oldGarden.getClass().getMethod("rewardsRemaining",UUID.class).invoke(oldGarden,player)==7,"courtyard rewards shared between dimensions");
  oldGarden.getClass().getMethod("recordRewardDrop",UUID.class).invoke(oldGarden,player);
  check(courtyard.rewardsRemaining(player)==6,"old courtyard cannot issue a second independent budget");
  LogUtils.getLogger().info("COMPANION LIVE LEGACY LEDGER CHECKS PASSED");
 }
 private static CompoundTag award(UUID player,UUID identity,boolean claimed){
  var row=new CompoundTag();row.putUUID("player",player);row.putUUID("identity",identity);row.putBoolean("claimed",claimed);return row;
 }
 private static CompoundTag awards(CompoundTag...rows){
  var tag=new CompoundTag();var list=new net.minecraft.nbt.ListTag();for(var row:rows)list.add(row);tag.put("awards",list);return tag;
 }
 private static StandaloneLeafDropData reloadLeaf(StandaloneLeafDropData data,net.minecraft.server.level.ServerLevel level)throws Exception{
  var load=StandaloneLeafDropData.class.getDeclaredMethod("load",CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class);load.setAccessible(true);
  return (StandaloneLeafDropData)load.invoke(null,data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
 }
 private static ItemStack customReceipt(UUID owner,UUID identity){
  var item=new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());var custom=new CompoundTag();var own=new CompoundTag();
  own.putUUID("owner",owner);own.putUUID("identity",identity);own.putString("kind","two_i_combat_doll:two_i");custom.put("doll",own);
  item.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));return item;
 }
 private static int dollCount(ServerPlayer player){
  int count=0;for(int i=0;i<player.getInventory().getContainerSize();i++){
   var item=player.getInventory().getItem(i);String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
   if(id.equals("two_i_combat_doll:two_i_doll")||id.equals("useless_stretcher:two_i_doll"))count+=item.getCount();
  }return count;
 }
 private static void leafMigration(net.minecraft.server.level.ServerLevel level)throws Exception{
  // Exercise the real historical loader/saver rather than a handoff's completion claims.
  UUID pending=UUID.randomUUID(),claimed=UUID.randomUUID(),conflict=UUID.randomUUID(),staffPending=UUID.randomUUID(),staffClaimed=UUID.randomUUID();
  UUID oldId=UUID.randomUUID(),claimedId=UUID.randomUUID(),conflictId=UUID.randomUUID();
  var old=com.twoi.combatdoll.entity.DollAcquisitionData.load(awards(award(pending,oldId,false),award(claimed,claimedId,true),award(conflict,conflictId,false)),level.registryAccess());
  var data=new StandaloneLeafDropData();data.beginStaffDelivery(staffPending);data.beginStaffDelivery(staffClaimed);data.markStaffDelivered(staffClaimed);
  data.beginDollDelivery(conflict);data.markDollDelivered(conflict);
  var migration=new LeafRewardMigration();migration.importAwards(old.save(new CompoundTag(),level.registryAccess()),LeafRewardMigration.standalone(data));
  check(data.hasPendingDoll(pending)&&migration.identity(pending).equals(oldId),"historical pending keeps its original random identity");
  check(data.hasRolledDoll(claimed)&&data.hasClaimedDoll(claimed),"historical claimed imports both rolled and claimed");
  check(data.hasClaimedDoll(conflict)&&!data.hasPendingDoll(conflict),"shared claimed wins over an older standalone pending");
  var restored=reloadLeaf(data,level);var ids=LeafRewardMigration.load(migration.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
  check(restored.hasPendingStaff(staffPending)&&restored.hasClaimedStaff(staffClaimed),"all staff fields survive independent doll migration and reload");
  check(ids.identity(pending).equals(oldId),"sidecar saves the original pending identity");
  restored.markDollDelivered(pending);ids.markClaimed(pending);ids.importAwards(old.save(new CompoundTag(),level.registryAccess()),LeafRewardMigration.standalone(restored));
  check(restored.hasClaimedDoll(pending)&&!restored.hasPendingDoll(pending),"replaying old pending cannot undo a newer claim");
  var torn=new StandaloneLeafDropData();ids.importAwards(new CompoundTag(),LeafRewardMigration.standalone(torn));
  check(torn.hasClaimedDoll(pending)&&torn.hasClaimedDoll(claimed),"saved sidecar repairs a missing shared-ledger write without a second reward");

  var live=LeafRewardMigration.prepare(level.getServer());var ledger=LeafRewardMigration.ledger(level.getServer());
  var receiptOwner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"LeafReceipt"));UUID receiptId=UUID.randomUUID();
  live.importAwards(awards(award(receiptOwner.getUUID(),receiptId,false)),ledger);
  for(int i=0;i<receiptOwner.getInventory().items.size();i++)receiptOwner.getInventory().items.set(i,new ItemStack(Items.STONE,64));
  ItemStack receipt=customReceipt(receiptOwner.getUUID(),receiptId);
  receipt.set(StretcherComponents.TWO_I_DOLL_ID.get(),UUID.randomUUID()); // A legacy tick may already have added an unrelated new component.
  receiptOwner.getInventory().offhand.set(0,receipt);
  LeafRewardMigration.login(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(receiptOwner));
  check(ledger.hasClaimedDoll(receiptOwner.getUUID())&&dollCount(receiptOwner)==1&&!StandaloneDollLeafDrops.deliver(receiptOwner,ledger),"HIGHEST login reconciles full inventory CUSTOM_DATA receipt without duplicate even with a different new typed ID");

  var waiting=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"LeafWaiting"));UUID waitingId=UUID.randomUUID();
  live.importAwards(awards(award(waiting.getUUID(),waitingId,false)),ledger);
  for(int i=0;i<waiting.getInventory().items.size();i++)waiting.getInventory().items.set(i,new ItemStack(Items.STONE,64));
  live.reconcile(waiting);
  check(!StandaloneDollLeafDrops.deliver(waiting,ledger)&&ledger.hasPendingDoll(waiting.getUUID()),"old pending remains safe while backpack is full");
  var pendingReload=LeafRewardMigration.load(live.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
  check(pendingReload.identity(waiting.getUUID()).equals(waitingId),"pending migration survives restart with exact historical ID");
  waiting.getInventory().items.set(0,ItemStack.EMPTY);
  check(StandaloneDollLeafDrops.deliver(waiting,ledger)&&waitingId.equals(waiting.getInventory().getItem(0).get(StretcherComponents.TWO_I_DOLL_ID.get())),"old pending delivers canonical item with original ID after space is made");
  check(!StandaloneDollLeafDrops.deliver(waiting,ledger)&&dollCount(waiting)==1,"old pending delivers at most once");
  ItemStack typed=new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());typed.set(StretcherComponents.TWO_I_DOLL_ID.get(),waitingId);
  check(LeafRewardMigration.receiptMatches(typed,waitingId),"canonical typed receipt recognized");
  if(net.neoforged.fml.ModList.get().isLoaded("useless_stretcher")){
   Class<?> original=Class.forName("com.sorrowmist.useless.stretcher.content.leaf.StaffLeafDropData");Object originalData=original.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,level.getServer());
   UUID staff=UUID.randomUUID();original.getMethod("beginStaffDelivery",UUID.class).invoke(originalData,staff);
   live.importAwards(awards(award(UUID.randomUUID(),UUID.randomUUID(),true)),ledger);
   check((boolean)original.getMethod("hasPendingStaff",UUID.class).invoke(originalData,staff),"dual-install reflection merge leaves original pending staff untouched");
   var oldType=(net.minecraft.core.component.DataComponentType)net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.get(net.minecraft.resources.ResourceLocation.parse("useless_stretcher:two_i_doll_id"));
   ItemStack legacyItem=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("useless_stretcher:two_i_doll")));legacyItem.set(oldType,waitingId);
   check(LeafRewardMigration.receiptMatches(legacyItem,waitingId),"legacy typed carrier receipt recognized without cross-casting SavedData");
  }
  LogUtils.getLogger().info("COMPANION LEAF MIGRATION CHECKS PASSED: old random IDs/claims/pending/partial save/typed+custom receipts/staff preservation");
 }
 @SuppressWarnings({"rawtypes","unchecked"}) private static void leafEvents(net.minecraft.server.level.ServerLevel level)throws Exception{
  boolean dual=net.neoforged.fml.ModList.get().isLoaded("useless_stretcher");
  var chanceField=com.twoi.combatdoll.companion.config.StretcherConfig.class.getDeclaredField("LEAF_PROBABILITY");chanceField.setAccessible(true);
  var chance=(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue)chanceField.get(null);double oldChance=chance.get();chance.set(1D);
  var restore=new LinkedHashMap<net.neoforged.neoforge.common.ModConfigSpec.ConfigValue,Object>();
  try{
   if(dual){Class<?> config=Class.forName("com.sorrowmist.useless.stretcher.config.StretcherConfig");
    var dollChance=(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue)config.getField("SERVER_DOLL_LEAF_DROP_PROBABILITY").get(null);restore.put(dollChance,dollChance.get());dollChance.set(1D);
    var staffChance=(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue)config.getField("SERVER_STAFF_LEAF_DROP_PROBABILITY").get(null);restore.put(staffChance,staffChance.get());staffChance.set(0D);
    var enabled=(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue)config.getField("SERVER_DOLL_LEAF_DROP").get(null);restore.put(enabled,enabled.get());enabled.set(true);
   }
   var breaker=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"LeafBus"));breaker.setGameMode(GameType.SURVIVAL);
   var ledger=LeafRewardMigration.ledger(level.getServer());
   var notLeaf=new net.neoforged.neoforge.event.level.BlockDropsEvent(level,new BlockPos(12,80,12),Blocks.STONE.defaultBlockState(),null,new ArrayList<>(),breaker,ItemStack.EMPTY);
   net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(notLeaf);check(!ledger.hasRolledDoll(breaker.getUUID()),"real stone drops event cannot roll doll");
   var canceled=new net.neoforged.neoforge.event.level.BlockDropsEvent(level,new BlockPos(12,80,12),Blocks.OAK_LEAVES.defaultBlockState(),null,new ArrayList<>(),breaker,ItemStack.EMPTY);canceled.setCanceled(true);
   net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(canceled);check(!ledger.hasRolledDoll(breaker.getUUID()),"canceled leaf event cannot roll doll");
   for(int i=0;i<5;i++)net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockDropsEvent(level,new BlockPos(12,80,12),Blocks.OAK_LEAVES.defaultBlockState(),null,new ArrayList<>(),breaker,ItemStack.EMPTY));
   check(ledger.hasClaimedDoll(breaker.getUUID())&&dollCount(breaker)==1,"five real leaf events award exactly one doll in "+(dual?"dual install":"standalone"));
   check(!com.twoi.combatdoll.entity.DollAcquisitionData.get(level.getServer()).won(breaker.getUUID()),"retired independent leaf subscription cannot roll a second legacy award");
   for(var method:com.twoi.combatdoll.entity.DollLeafRewards.class.getDeclaredMethods())check(!method.isAnnotationPresent(SubscribeEvent.class),"legacy facade has no event subscription");
   LogUtils.getLogger().info("COMPANION REAL BLOCK DROPS CHECKS PASSED ({})",dual?"shared original ledger":"standalone ledger");
  }finally{chance.set(oldChance);restore.forEach((key,value)->key.set(value));}
 }
 private static void leaf(net.minecraft.server.level.ServerLevel level)throws Exception{
  if(net.neoforged.fml.ModList.get().isLoaded("useless_stretcher")){LogUtils.getLogger().info("Standalone leaf fixture skipped: stretcher owns shared ledger");return;}
  var owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"PortLeaf"));
  var data=com.twoi.combatdoll.companion.content.leaf.StandaloneLeafDropData.get(level.getServer());
  check(data.beginDollDelivery(owner.getUUID())&&!data.beginDollDelivery(owner.getUUID()),"only one leaf roll");
  for(int i=0;i<owner.getInventory().items.size();i++)owner.getInventory().items.set(i,new ItemStack(Items.STONE,64));
  check(!com.twoi.combatdoll.companion.event.StandaloneDollLeafDrops.deliver(owner,data)&&data.hasPendingDoll(owner.getUUID()),"full backpack retains safe pending claim");
  var load=data.getClass().getDeclaredMethod("load",CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class);load.setAccessible(true);
  var restored=(com.twoi.combatdoll.companion.content.leaf.StandaloneLeafDropData)load.invoke(null,data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());check(restored.hasPendingDoll(owner.getUUID()),"pending survives actual codec reload");
  owner.getInventory().items.set(0,ItemStack.EMPTY);
  check(com.twoi.combatdoll.companion.event.StandaloneDollLeafDrops.deliver(owner,restored)&&restored.hasClaimedDoll(owner.getUUID()),"free slot delivers one claim");
  check(!com.twoi.combatdoll.companion.event.StandaloneDollLeafDrops.deliver(owner,restored)&&!restored.beginDollDelivery(owner.getUUID()),"claimed reward never redelivered");
  check(!restored.hasRolledStaff(owner.getUUID())&&!restored.hasClaimedStaff(owner.getUUID()),"doll does not spend staff roll or claim");
 }
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError("Companion port: "+message);}
}
