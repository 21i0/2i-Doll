package com.customdoll.canmeng.init;
import com.customdoll.canmeng.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.canmeng.content.item.DollItem> TWO_I_DOLL = ITEMS.register("canmeng_doll", com.customdoll.canmeng.content.item.DollItem::new);
}