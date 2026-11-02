package com.customdoll.chuanshuo.init;
import com.customdoll.chuanshuo.DollMod;
public final class ModBlockEntities {
public static final net.neoforged.neoforge.registries.DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = net.neoforged.neoforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, DollMod.MODID);
 public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.customdoll.chuanshuo.content.blockentity.DollDisplayBlockEntity>> TWO_I_DOLL_DISPLAY = BLOCK_ENTITIES.register("chuanshuo_display", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(com.customdoll.chuanshuo.content.blockentity.DollDisplayBlockEntity::new, ModBlocks.TWO_I_DOLL_DISPLAY.get()).build(null));
}