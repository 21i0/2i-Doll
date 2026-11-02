package com.twoi.combatdoll.companion.content.item;

import com.twoi.combatdoll.companion.init.ModItems;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import com.twoi.combatdoll.companion.content.entity.TwoIDollAcquisitionData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Server-side feedback for a player who actually receives a 2i doll item. */
public final class TwoIDollAcquisition {
    private TwoIDollAcquisition() { }

    public static void notifyAcquired(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || !stack.is(ModItems.TWO_I_DOLL.get())) return;
        UUID dollId = com.twoi.combatdoll.companion.content.item.TwoIDollItem.ensureIdentity(stack);
        MinecraftServer server = player.getServer();
        if (server == null || !TwoIDollAcquisitionData.get(server).markIfNew(dollId)) return;
        UUID ownerId = stack.get(StretcherComponents.TWO_I_OWNER.get());
        Component owner = resolveOwner(player.getServer(), ownerId);
        Component message = Component.translatable(
                "msg.two_i_combat_doll.two_i.acquired_broadcast",
                Component.literal(player.getGameProfile().getName()),
                Component.translatable("item.two_i_combat_doll.two_i_doll"), owner);
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static Component resolveOwner(MinecraftServer server, UUID ownerId) {
        if (ownerId == null) return Component.translatable("msg.two_i_combat_doll.two_i.owner_unbound");
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(ownerId);
            if (online != null) return Component.literal(online.getGameProfile().getName());
            var cache = server.getProfileCache();
            if (cache != null) {
                var profile = cache.get(ownerId);
                if (profile.isPresent()) return Component.literal(profile.get().getName());
            }
        }
        return Component.literal(ownerId.toString());
    }
}
