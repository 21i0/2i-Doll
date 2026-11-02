package com.customdoll.qijiu.init;
import com.customdoll.qijiu.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.qijiu.content.item.DollItem> TWO_I_DOLL = ITEMS.register("qijiu_doll", com.customdoll.qijiu.content.item.DollItem::new);
}