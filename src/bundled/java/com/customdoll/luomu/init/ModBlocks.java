package com.customdoll.luomu.init;
import com.customdoll.luomu.DollMod;
public final class ModBlocks {
public static final net.neoforged.neoforge.registries.DeferredRegister.Blocks BLOCKS = net.neoforged.neoforge.registries.DeferredRegister.createBlocks(DollMod.MODID);
 public static final java.util.function.Supplier<net.minecraft.world.level.block.Block> TWO_I_DOLL_DISPLAY = BLOCKS.register("luomu_display", () -> new com.customdoll.luomu.content.block.DollDisplayBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(0.2F).noOcclusion().noCollission()));
}