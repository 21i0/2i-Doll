package com.customdoll.luomu.init;
import com.customdoll.luomu.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.luomu.content.item.DollItem> TWO_I_DOLL = ITEMS.register("luomu_doll", com.customdoll.luomu.content.item.DollItem::new);
}