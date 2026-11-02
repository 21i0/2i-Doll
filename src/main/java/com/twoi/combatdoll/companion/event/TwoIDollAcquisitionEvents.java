package com.twoi.combatdoll.companion.event;

import com.twoi.combatdoll.companion.UselessStretcherMod;
import com.twoi.combatdoll.companion.content.item.TwoIDollAcquisition;
import com.twoi.combatdoll.companion.init.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/** Announces completed item pickups after the stack entered the player inventory. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class TwoIDollAcquisitionEvents {
    private TwoIDollAcquisitionEvents() { }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void beforeItemPickup(ItemEntityPickupEvent.Pre event) {
        var stack = event.getItemEntity().getItem();
        if (stack.is(ModItems.TWO_I_DOLL.get()))
            com.twoi.combatdoll.companion.content.item.TwoIDollItem.ensureIdentity(stack);
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!event.getOriginalStack().is(ModItems.TWO_I_DOLL.get())) return;
        var original = event.getOriginalStack();
        var id = original.get(com.twoi.combatdoll.companion.init.StretcherComponents.TWO_I_DOLL_ID.get());
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var received = player.getInventory().getItem(i);
            if (!received.is(ModItems.TWO_I_DOLL.get())) continue;
            if (id == null ? !net.minecraft.world.item.ItemStack.isSameItemSameComponents(original, received)
                    : !id.equals(received.get(com.twoi.combatdoll.companion.init.StretcherComponents.TWO_I_DOLL_ID.get()))) continue;
            com.twoi.combatdoll.companion.content.item.TwoIDollItem.claimOrCheckOwner(received, player);
            TwoIDollAcquisition.notifyAcquired(player, received);
            break;
        }
    }
}
