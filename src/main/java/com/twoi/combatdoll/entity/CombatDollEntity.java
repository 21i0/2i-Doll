package com.twoi.combatdoll.entity;

import com.twoi.combatdoll.api.DollCatalog;
import com.twoi.combatdoll.combat.*;
import com.twoi.combatdoll.companion.content.entity.LegacyTwoIData;
import com.twoi.combatdoll.companion.content.entity.TwoIDollEntity;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import java.util.*;

/** Complete standalone 2i companion with the original public combat API and registry identity. */
public class CombatDollEntity extends TwoIDollEntity implements DollCombatAccess {
 private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(CombatDollEntity.class,EntityDataSerializers.OPTIONAL_UUID);
 private static final EntityDataAccessor<String> KIND=SynchedEntityData.defineId(CombatDollEntity.class,EntityDataSerializers.STRING);
 private static final EntityDataAccessor<Boolean> SITTING=SynchedEntityData.defineId(CombatDollEntity.class,EntityDataSerializers.BOOLEAN);
 private final TwoIGrimoireCombat combat=new TwoIGrimoireCombat();
 public CombatDollEntity(EntityType<? extends CombatDollEntity> type,Level level){super(type,level);}
 public static AttributeSupplier.Builder attributes(){return TwoIDollEntity.createAttributes();}
 @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(OWNER,Optional.empty());b.define(KIND,DollCatalog.TWO_I.toString());b.define(SITTING,false);}
 @Override protected void registerGoals(){
  super.registerGoals();
  goalSelector.addGoal(0,new CombatStopGoal(this));
  goalSelector.addGoal(0,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP));}
   @Override public boolean canUse(){return isSitting();}
   @Override public void tick(){getNavigation().stop();}
  });
 }
 @Override public UUID getOwnerId(){UUID synced=entityData.get(OWNER).orElse(null);return synced==null?super.getOwnerId():synced;}
 @Override public void setOwnerId(UUID owner){super.setOwnerId(owner);entityData.set(OWNER,Optional.ofNullable(owner));}
 public ResourceLocation kind(){ResourceLocation id=ResourceLocation.tryParse(entityData.get(KIND));return id==null?DollCatalog.TWO_I:id;}
 public void setKind(ResourceLocation kind){entityData.set(KIND,DollCatalog.get(kind).id().toString());}
 public boolean isSitting(){return entityData.get(SITTING);}
 public void setSitting(boolean sitting){entityData.set(SITTING,sitting);}
 /** Old API now uses the expiring UI lease, never permanently toggles sitting. */
 public void keepInteraction(Player player,boolean held){if(player instanceof ServerPlayer server)super.keepInteraction(server,held);}
 @Override public TwoIGrimoireCombat combatState(){return combat;}
 @Override public UUID combatOwner(){return getOwnerId();}
 @Override public boolean combatBusy(){return isSitting()||super.isInteractionHeld();}
 @Override public float combatDamage(){return DollCatalog.get(kind()).damage();}
 @Override public int combatTargetLimit(){return DollCatalog.get(kind()).targetLimit();}
 @Override public void tick(){
  super.tick();
  if(!level().isClientSide&&(isSitting()||combat.age(this,0)>=0)){
   getNavigation().stop();setDeltaMovement(0,isNoGravity()?0:getDeltaMovement().y,0);
  }
 }
 @Override public ItemStack toDollItem(){
  ItemStack item=super.toDollItem();
  CompoundTag tag=item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
  CompoundTag own=tag.getCompound("doll");
  if(getOwnerId()!=null)own.putUUID("owner",getOwnerId());
  UUID id=item.get(StretcherComponents.TWO_I_DOLL_ID.get());if(id!=null)own.putUUID("identity",id);
  own.putString("kind",kind().toString());own.putBoolean("sitting",isSitting());tag.put("doll",own);
  if(combat!=null)combat.save(tag,level().getGameTime());
  item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return item;
 }
 @Override public void restoreFromItem(ItemStack original){
  ItemStack item=LegacyTwoIData.canonicalStack(original);super.restoreFromItem(item);
  CompoundTag tag=item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag(),own=tag.getCompound("doll");
  UUID owner=item.get(StretcherComponents.TWO_I_OWNER.get());if(owner!=null)setOwnerId(owner);
  if(own.contains("kind")){ResourceLocation id=ResourceLocation.tryParse(own.getString("kind"));if(id!=null)setKind(id);}
  entityData.set(SITTING,own.getBoolean("sitting"));if(combat!=null)combat.load(tag,level().getGameTime());
  if(item.has(DataComponents.CUSTOM_NAME))setCustomName(item.get(DataComponents.CUSTOM_NAME));
 }
 /** Base writer intentionally calls virtual toDollItem: saves full companion + legacy combat CUSTOM_DATA. */
 @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);}
 @Override public void readAdditionalSaveData(CompoundTag tag){
  if(combat!=null)combat.reset();super.readAdditionalSaveData(tag);
  setOwnerId(super.getOwnerId());setNoGravity(false);noPhysics=false;
 }
}
