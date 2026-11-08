package com.customdoll.qiaotou.content;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Optional Curios/Accessories integration point. Reflection keeps both mods loadable without either dependency. */
public final class DollAccessoryBridge {
    private DollAccessoryBridge() {}
    public static boolean isEquipped(LivingEntity entity, ItemStack stack) {
        return net.neoforged.fml.ModList.get().isLoaded("curios")
                && com.customdoll.qiaotou.compat.DollCuriosCompat.isEquipped(entity, stack);
    }
}
