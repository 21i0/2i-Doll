package com.customdoll.sishier.init;
import com.customdoll.sishier.DollMod;
public final class ModBlocks {
public static final net.neoforged.neoforge.registries.DeferredRegister.Blocks BLOCKS = net.neoforged.neoforge.registries.DeferredRegister.createBlocks(DollMod.MODID);
 public static final java.util.function.Supplier<net.minecraft.world.level.block.Block> TWO_I_DOLL_DISPLAY = BLOCKS.register("sishier_display", () -> new com.customdoll.sishier.content.block.DollDisplayBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(0.2F).noOcclusion().noCollission()));
}