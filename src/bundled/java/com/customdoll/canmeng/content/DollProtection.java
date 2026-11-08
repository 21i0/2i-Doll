package com.customdoll.canmeng.content;

import com.customdoll.canmeng.DollMod;
import com.customdoll.canmeng.content.blockentity.DollDisplayBlockEntity;
import com.customdoll.canmeng.init.ModItems;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

@EventBusSubscriber(modid = DollMod.MODID)
public final class DollProtection {
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof DollDisplayBlockEntity display
                && !DollOwnership.mayUse(display.copyDoll(), event.getPlayer())) {
            event.setCanceled(true);
            DollOwnership.deny(event.getPlayer());
        }
    }
    @SubscribeEvent public static void equipped(LivingEquipmentChangeEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof Player p
                && event.getTo().is(ModItems.TWO_I_DOLL.get()) && DollOwnership.mayUse(event.getTo(), p))
            DollOwnership.claim(event.getTo(), p);
    }
}

