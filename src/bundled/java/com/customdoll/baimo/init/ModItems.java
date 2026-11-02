package com.customdoll.baimo.init;
import com.customdoll.baimo.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.baimo.content.item.DollItem> TWO_I_DOLL = ITEMS.register("baimo_doll", com.customdoll.baimo.content.item.DollItem::new);
}