package com.customdoll.sishier.init;
import com.customdoll.sishier.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.sishier.content.item.DollItem> TWO_I_DOLL = ITEMS.register("sishier_doll", com.customdoll.sishier.content.item.DollItem::new);
}