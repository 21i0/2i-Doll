package com.twoi.combatdoll.companion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
public final class CompanionBootstrap {
 public static void init(IEventBus bus,ModContainer container) {
  com.twoi.combatdoll.companion.init.ModBlocks.BLOCKS.register(bus);
  com.twoi.combatdoll.companion.init.ModBlockEntities.TYPES.register(bus);
  com.twoi.combatdoll.companion.init.StretcherComponents.init(bus);
  com.twoi.combatdoll.companion.init.ModSounds.TWO_I_PAT.getId();
  com.twoi.combatdoll.companion.dimension.world.TwoIStarfieldDimensions.init(bus);
  bus.addListener(com.twoi.combatdoll.companion.network.Network::register);
  container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,com.twoi.combatdoll.companion.config.StretcherConfig.SPEC,"two_i_companion-server.toml");
  bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e)->{
   if(net.neoforged.fml.ModList.get().isLoaded("curios"))e.enqueueWork(com.twoi.combatdoll.companion.compat.TwoICuriosCompat::register);
  });
 }
}
