package com.customdoll.xiaoxiao.init;
import com.customdoll.xiaoxiao.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.xiaoxiao.content.item.DollItem> TWO_I_DOLL = ITEMS.register("xiaoxiao_doll", com.customdoll.xiaoxiao.content.item.DollItem::new);
}