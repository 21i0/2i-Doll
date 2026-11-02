package com.customdoll.longhuan.init;
import com.customdoll.longhuan.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.longhuan.content.item.DollItem> TWO_I_DOLL = ITEMS.register("longhuan_doll", com.customdoll.longhuan.content.item.DollItem::new);
}