package com.customdoll.nadir.init;
import com.customdoll.nadir.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.nadir.content.item.DollItem> TWO_I_DOLL = ITEMS.register("nadir_doll", com.customdoll.nadir.content.item.DollItem::new);
}