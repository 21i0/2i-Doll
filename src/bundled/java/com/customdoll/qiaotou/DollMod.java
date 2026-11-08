package com.customdoll.qiaotou;
import com.customdoll.qiaotou.init.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
@Mod(DollMod.MODID)
public final class DollMod {
 public static final String MODID = "qiaotou";
 public DollMod(IEventBus bus) {
  bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> {
   if (net.neoforged.fml.ModList.get().isLoaded("curios"))
    event.enqueueWork(com.customdoll.qiaotou.compat.DollCuriosCompat::register);
  });
  ModBlocks.BLOCKS.register(bus); ModItems.ITEMS.register(bus); ModBlockEntities.BLOCK_ENTITIES.register(bus);
  ModEntities.ENTITIES.register(bus); ModEntities.SOUNDS.register(bus); bus.addListener(ModEntities::attributes);
  bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
   if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(ModItems.TWO_I_DOLL.get());
  });
 }
}
