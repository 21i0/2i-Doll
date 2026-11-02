package com.customdoll.shaniao.init;
import com.customdoll.shaniao.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.shaniao.content.item.DollItem> TWO_I_DOLL = ITEMS.register("shaniao_doll", com.customdoll.shaniao.content.item.DollItem::new);
}