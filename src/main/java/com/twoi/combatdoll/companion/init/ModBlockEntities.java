package com.twoi.combatdoll.companion.init;
import com.twoi.combatdoll.companion.content.blockentity.TwoIDollDisplayBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
public final class ModBlockEntities {
 public static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"two_i_combat_doll");
 public static final net.neoforged.neoforge.registries.DeferredHolder<BlockEntityType<?>,BlockEntityType<TwoIDollDisplayBlockEntity>> TWO_I_DOLL_DISPLAY=TYPES.register("two_i_doll_display",()->BlockEntityType.Builder.of(TwoIDollDisplayBlockEntity::new,ModBlocks.TWO_I_DOLL_DISPLAY.get()).build(null));
}
