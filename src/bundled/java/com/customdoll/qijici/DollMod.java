package com.customdoll.qijici;
import com.customdoll.qijici.init.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
@Mod(DollMod.MODID)
public final class DollMod {
 public static final String MODID = "qijici";
 public DollMod(IEventBus bus) {
  bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> {
   if (net.neoforged.fml.ModList.get().isLoaded("curios"))
    event.enqueueWork(com.customdoll.qijici.compat.DollCuriosCompat::register);
  });
  ModBlocks.BLOCKS.register(bus); ModItems.ITEMS.register(bus); ModBlockEntities.BLOCK_ENTITIES.register(bus);
  
  bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
   if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(ModItems.TWO_I_DOLL.get());
  });
 }
}
