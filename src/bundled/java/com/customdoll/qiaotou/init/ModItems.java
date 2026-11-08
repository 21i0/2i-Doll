package com.customdoll.qiaotou.init;
import com.customdoll.qiaotou.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.qiaotou.content.item.DollItem> TWO_I_DOLL = ITEMS.register("qiaotou_doll", com.customdoll.qiaotou.content.item.DollItem::new);
}