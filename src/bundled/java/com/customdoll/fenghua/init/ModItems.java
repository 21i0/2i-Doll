package com.customdoll.fenghua.init;
import com.customdoll.fenghua.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.fenghua.content.item.DollItem> TWO_I_DOLL = ITEMS.register("fenghua_doll", com.customdoll.fenghua.content.item.DollItem::new);
}