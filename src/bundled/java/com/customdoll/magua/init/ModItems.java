package com.customdoll.magua.init;
import com.customdoll.magua.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.magua.content.item.DollItem> TWO_I_DOLL = ITEMS.register("magua_doll", com.customdoll.magua.content.item.DollItem::new);
}