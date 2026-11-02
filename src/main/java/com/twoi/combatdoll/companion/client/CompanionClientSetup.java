package com.twoi.combatdoll.companion.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
@EventBusSubscriber(modid="two_i_combat_doll",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class CompanionClientSetup {
 public static final net.minecraft.client.KeyMapping RECALL=new net.minecraft.client.KeyMapping("key.two_i_combat_doll.two_i_remote_reclaim",com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_B,"key.categories.two_i_combat_doll");
 @SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e){e.register(RECALL);}

 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(com.twoi.combatdoll.companion.init.ModBlockEntities.TWO_I_DOLL_DISPLAY.get(),com.twoi.combatdoll.companion.client.render.TwoIDollDisplayRenderer::new);}
 @SubscribeEvent public static void curios(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e){if(net.neoforged.fml.ModList.get().isLoaded("curios"))e.enqueueWork(com.twoi.combatdoll.companion.client.render.TwoIDollCurioRenderer::register);}
}
