package com.twoi.combatdoll.client;
import com.twoi.combatdoll.TwoICombatDollMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid=TwoICombatDollMod.MODID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClientRegistration {
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){event.registerLayerDefinition(TwoIDollRenderer.LAYER,TwoIDollRenderer.Model::createLayer);}
    @SubscribeEvent public static void entities(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(TwoICombatDollMod.DOLL_ENTITY.get(),TwoIDollRenderer::new);event.registerEntityRenderer(TwoICombatDollMod.AXOLOTL_ENTITY.get(),CangshengAxolotlRenderer::new);}
    @SubscribeEvent public static void items(RegisterClientExtensionsEvent event){
        event.registerItem(new IClientItemExtensions(){
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){return TwoIDollItemRenderer.get();}
        },TwoICombatDollMod.DOLL_ITEM.get());
        event.registerItem(new IClientItemExtensions(){
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){return ArcanaWeaponRenderer.get();}
        },TwoICombatDollMod.BOOK.get(),TwoICombatDollMod.STAFF.get());
        event.registerItem(new IClientItemExtensions(){
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){return CangshengAxolotlItemRenderer.get();}
        },TwoICombatDollMod.AXOLOTL.get());
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){event.registerReloadListener(TwoIDollItemRenderer.get());}
}
