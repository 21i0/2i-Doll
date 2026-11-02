package com.customdoll.luomu.client;
import com.customdoll.luomu.DollMod;
import com.customdoll.luomu.client.render.*;
import com.customdoll.luomu.init.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;
@EventBusSubscriber(modid=DollMod.MODID, value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
 @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
  if (net.neoforged.fml.ModList.get().isLoaded("curios"))
   event.enqueueWork(DollCurioRenderer::register);
 }
 @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) { e.registerLayerDefinition(DollRenderer.LAYER, DollRenderer.Model::createLayer); }
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
  e.registerBlockEntityRenderer(ModBlockEntities.TWO_I_DOLL_DISPLAY.get(), DollDisplayRenderer::new);
  e.registerEntityRenderer(ModEntities.TWO_I_DOLL.get(), DollRenderer::new);
 }
 @SubscribeEvent public static void items(RegisterClientExtensionsEvent e) { e.registerItem(new IClientItemExtensions() {
  @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return DollItemRenderer.get(); }
 },ModItems.TWO_I_DOLL.get()); }
 @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e) { e.registerReloadListener(DollItemRenderer.get()); }
}
