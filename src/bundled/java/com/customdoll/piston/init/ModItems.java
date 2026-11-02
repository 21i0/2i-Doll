package com.customdoll.piston.init;
import com.customdoll.piston.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.piston.content.item.DollItem> TWO_I_DOLL = ITEMS.register("piston_doll", com.customdoll.piston.content.item.DollItem::new);
}