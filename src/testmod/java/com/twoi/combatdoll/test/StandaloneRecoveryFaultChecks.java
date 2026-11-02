package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.companion.content.entity.TwoIDollMemory;
import com.twoi.combatdoll.companion.content.entity.TwoIDollRecoveryData;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.nio.file.Files;
import java.util.UUID;

/** Isolated failure injection. Only removes its own verified-empty temporary blocker. Never shipped. */
public final class StandaloneRecoveryFaultChecks {
 private static void check(boolean value,String message){if(!value)throw new AssertionError("Recovery fault: "+message);}
 public static void run(MinecraftServer server)throws Exception{
  var folder=server.getWorldPath(LevelResource.ROOT).resolve("data");Files.createDirectories(folder);
  var blocker=folder.resolve("two_i_combat_doll_recovery.dat.tmp");check(!Files.exists(blocker),"fresh fault fixture never replaces evidence");
  var owner=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"RecoveryFault"));UUID identity=UUID.randomUUID();
  var original=new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());original.set(StretcherComponents.TWO_I_OWNER.get(),owner.getUUID());original.set(StretcherComponents.TWO_I_DOLL_ID.get(),identity);original.set(StretcherComponents.TWO_I_BOND.get(),42);original.set(StretcherComponents.TWO_I_MEMORY.get(),TwoIDollMemory.empty());
  var custom=new CompoundTag();custom.putString("fault-preserve","complete original carrier");original.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));var expected=original.copy();
  var data=TwoIDollRecoveryData.get(server);
  try{
   Files.createDirectory(blocker);
   LogUtils.getLogger().info("STANDALONE EXPECTED RECOVERY IO FAILURE BEGIN: a test-created empty .tmp directory blocks writes; following persistence errors are intentional");
   check(!TwoIDollRecoveryData.queue(owner,original),"blocked persistence must not authorize discarding detached entity");
   check(data.pending(identity)&&data.isDirty(),"complete pending carrier remains retained and dirty after failure");
   server.overworld().getDataStorage().save();
   check(data.pending(identity)&&data.isDirty()&&owner.getInventory().getItem(0).isEmpty(),"native SavedData autosave failure keeps pending dirty and never issues an item");
   original.set(StretcherComponents.TWO_I_BOND.get(),99);
   check(!TwoIDollRecoveryData.queue(owner,original),"already-pending carrier must flush successfully before authorizing discard");
   data.reconcile(owner);check(owner.getInventory().getItem(0).isEmpty()&&data.pending(identity)&&data.isDirty(),"failed persistence never delivers or marks claimed");
   LogUtils.getLogger().info("STANDALONE EXPECTED RECOVERY IO FAILURE END: original carrier still pending, no item issued");
   check(Files.isDirectory(blocker),"only our known directory may be removed");try(var contents=Files.list(blocker)){check(contents.findAny().isEmpty(),"only an empty blocker may be removed");}Files.delete(blocker);
   server.overworld().getDataStorage().save();
   check(data.pending(identity)&&!data.isDirty()&&owner.getInventory().getItem(0).isEmpty(),"native SavedData autosave synchronously persists original pending carrier before delivery");
   var saved=readDelivery(folder.resolve("two_i_combat_doll_recovery.dat"),identity);
   check(!saved.getBoolean("claimed")&&ItemStack.isSameItemSameComponents(expected,ItemStack.parseOptional(server.registryAccess(),saved.getCompound("item"))),"actual autosave disk NBT retains complete first pending carrier");
   check(TwoIDollRecoveryData.queue(owner,original)&&data.pending(identity)&&!data.isDirty(),"disk recovery durably persists retained carrier and authorizes release");
   data.reconcile(owner);data.reconcile(owner);check(data.claimed(identity)&&!data.pending(identity)&&!data.isDirty(),"recovered delivery is durably claimed once");
   check(ItemStack.isSameItemSameComponents(expected,owner.getInventory().getItem(0))&&owner.getInventory().getItem(1).isEmpty(),"complete first carrier restored once; caller mutation cannot overwrite pending state");
   check(readDelivery(folder.resolve("two_i_combat_doll_recovery.dat"),identity).getBoolean("claimed"),"actual disk NBT records the final claimed receipt");
   LogUtils.getLogger().info("STANDALONE RECOVERY IO FAILURE RETRY CHECKS PASSED: blocked queue=false and native autosave retain dirty full pending; repeated blocked queue/reconcile issue nothing; native autosave after disk recovery preserves exact first carrier, then durable claimed receipt and delivery once");
  }finally{if(Files.isDirectory(blocker)){try(var contents=Files.list(blocker)){if(contents.findAny().isEmpty())Files.delete(blocker);}}}
 }
 private static CompoundTag readDelivery(java.nio.file.Path path,UUID identity)throws Exception{
  var root=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap());
  for(Tag value:root.getCompound("data").getList("deliveries",Tag.TAG_COMPOUND)){var row=(CompoundTag)value;if(row.hasUUID("doll")&&identity.equals(row.getUUID("doll")))return row;}
  throw new AssertionError("Recovery fault: actual disk receipt missing "+identity);
 }
}
