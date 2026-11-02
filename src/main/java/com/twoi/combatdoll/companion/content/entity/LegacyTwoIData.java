package com.twoi.combatdoll.companion.content.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.nio.file.Path;

/** Additive, once-per-new-world-data migration. Never deletes or rewrites the legacy files. */
public final class LegacyTwoIData {
 private static final java.util.Set<String> LEGACY_COMPONENTS=java.util.Set.of("two_i_rotation","two_i_gift_score","two_i_bond","two_i_owner","two_i_doll_id","two_i_memory","two_i_invitation_used");
 private LegacyTwoIData() { }
 @SuppressWarnings({"rawtypes","unchecked"})
 private static void copyPatch(net.minecraft.core.component.DataComponentPatch.Builder builder,DataComponentType<?> type,java.util.Optional<?> value){
  if(value.isPresent())builder.set((DataComponentType)type,value.get());else builder.remove(type);
 }
 public static CompoundTag saved(MinecraftServer server,String className,String oldKey) {
  CompoundTag snapshot=com.twoi.combatdoll.compat.StretcherCompanionBridge.snapshot(server,className);
  if(snapshot!=null)return snapshot;
  Path path=server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(oldKey+".dat");
  if(!Files.isRegularFile(path))return new CompoundTag();
  try {
   CompoundTag root=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap());
   return root.getCompound("data").copy();
  } catch(java.io.IOException ex){throw new IllegalStateException("Cannot safely import legacy 2i world data: "+path,ex);}
 }

 /** Preserve public components and combat CUSTOM_DATA while binding old independent carriers. */
 public static ItemStack canonicalStack(ItemStack original) {
  if(original.isEmpty())return ItemStack.EMPTY;
  ItemStack result=new ItemStack(com.twoi.combatdoll.TwoICombatDollMod.DOLL_ITEM.get(),original.getCount());
  result.applyComponents(original.getComponentsPatch());importComponents(result);
  // A copied legacy typed component (or a !removed tombstone for it) would need
  // Stretcher's codec on the next load. Rebuild instead of mutating/removing it.
  ItemStack clean=new ItemStack(com.twoi.combatdoll.TwoICombatDollMod.DOLL_ITEM.get(),original.getCount());
  var patch=net.minecraft.core.component.DataComponentPatch.builder();
  for(var entry:result.getComponentsPatch().entrySet()){
   var key=BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
   if(key!=null&&key.getNamespace().equals("useless_stretcher")&&LEGACY_COMPONENTS.contains(key.getPath()))continue;
   copyPatch(patch,entry.getKey(),entry.getValue());
  }
  clean.applyComponents(patch.build());return clean;
 }
 public static void importComponents(ItemStack stack) {
  copy(stack,"two_i_rotation",StretcherComponents.TWO_I_ROTATION.get());
  copy(stack,"two_i_gift_score",StretcherComponents.TWO_I_GIFT_SCORE.get());
  copy(stack,"two_i_bond",StretcherComponents.TWO_I_BOND.get());
  copy(stack,"two_i_owner",StretcherComponents.TWO_I_OWNER.get());
  copy(stack,"two_i_doll_id",StretcherComponents.TWO_I_DOLL_ID.get());
  copy(stack,"two_i_memory",StretcherComponents.TWO_I_MEMORY.get());
  copy(stack,"two_i_invitation_used",StretcherComponents.TWO_I_INVITATION_USED.get());
  CompoundTag own=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("doll");
  if(!stack.has(StretcherComponents.TWO_I_OWNER.get())&&own.hasUUID("owner"))stack.set(StretcherComponents.TWO_I_OWNER.get(),own.getUUID("owner"));
  if(!stack.has(StretcherComponents.TWO_I_DOLL_ID.get())&&own.hasUUID("identity"))stack.set(StretcherComponents.TWO_I_DOLL_ID.get(),own.getUUID("identity"));
 }
 @SuppressWarnings({"rawtypes","unchecked"})
 private static <T> void copy(ItemStack stack,String suffix,DataComponentType<T> target) {
  if(stack.has(target))return;
  DataComponentType source=BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath("useless_stretcher",suffix));
  if(source==null||!stack.has(source))return;
  // Memory is a record from a different package. Re-encode; never cast legacy Java records.
  Codec sourceCodec=source.codec();Codec<T> targetCodec=target.codec();
  if(sourceCodec==null||targetCodec==null)return;
  Object value=stack.get(source);
  DataResult<Tag> encoded=sourceCodec.encodeStart(NbtOps.INSTANCE,value);
  Tag encodedTag=encoded.getOrThrow(message->new IllegalStateException("Cannot encode legacy 2i component "+suffix+": "+message));
  T converted=targetCodec.parse(NbtOps.INSTANCE,encodedTag).getOrThrow(message->new IllegalStateException("Cannot decode legacy 2i component "+suffix+": "+message));
  stack.set(target,converted);
 }
}
