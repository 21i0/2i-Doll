package com.customdoll.fenghua.init;
import com.customdoll.fenghua.DollMod;
import com.customdoll.fenghua.content.entity.DollEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;
public final class ModEntities {
 public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,DollMod.MODID);
 public static final java.util.function.Supplier<EntityType<DollEntity>> TWO_I_DOLL=ENTITIES.register("fenghua_doll",()->EntityType.Builder.<DollEntity>of(DollEntity::new,MobCategory.MISC).sized(.44F,.96F).clientTrackingRange(10).updateInterval(2).fireImmune().noSummon().build("fenghua_doll"));
 public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,DollMod.MODID);
 public static final java.util.function.Supplier<SoundEvent> PAT=SOUNDS.register("fenghua_pat",()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(DollMod.MODID,"fenghua_pat")));
 public static void attributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) { e.put(TWO_I_DOLL.get(),DollEntity.createAttributes().build()); }
}
