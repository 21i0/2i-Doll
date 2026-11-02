package com.customdoll.chuanshuo.init;
import com.customdoll.chuanshuo.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.chuanshuo.content.item.DollItem> TWO_I_DOLL = ITEMS.register("chuanshuo_doll", com.customdoll.chuanshuo.content.item.DollItem::new);
}