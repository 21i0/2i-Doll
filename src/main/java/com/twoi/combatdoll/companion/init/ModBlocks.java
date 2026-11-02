package com.twoi.combatdoll.companion.init;
import com.twoi.combatdoll.companion.content.block.TwoIDollDisplayBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
public final class ModBlocks {
 public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("two_i_combat_doll");
 public static final net.neoforged.neoforge.registries.DeferredBlock<TwoIDollDisplayBlock> TWO_I_DOLL_DISPLAY = BLOCKS.register("two_i_doll_display", () -> new TwoIDollDisplayBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(.5F).noOcclusion()));
}
