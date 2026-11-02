package com.customdoll.ninxin.init;
import com.customdoll.ninxin.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.ninxin.content.item.DollItem> TWO_I_DOLL = ITEMS.register("ninxin_doll", com.customdoll.ninxin.content.item.DollItem::new);
}