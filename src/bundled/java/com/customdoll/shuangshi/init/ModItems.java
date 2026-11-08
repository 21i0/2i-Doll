package com.customdoll.shuangshi.init;
import com.customdoll.shuangshi.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.shuangshi.content.item.DollItem> TWO_I_DOLL = ITEMS.register("shuangshi_doll", com.customdoll.shuangshi.content.item.DollItem::new);
}