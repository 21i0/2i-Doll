package com.customdoll.ooolala.init;
import com.customdoll.ooolala.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.ooolala.content.item.DollItem> TWO_I_DOLL = ITEMS.register("ooolala_doll", com.customdoll.ooolala.content.item.DollItem::new);
}