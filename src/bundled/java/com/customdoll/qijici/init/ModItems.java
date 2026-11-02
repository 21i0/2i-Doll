package com.customdoll.qijici.init;
import com.customdoll.qijici.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.qijici.content.item.DollItem> TWO_I_DOLL = ITEMS.register("qijici_doll", com.customdoll.qijici.content.item.DollItem::new);
}