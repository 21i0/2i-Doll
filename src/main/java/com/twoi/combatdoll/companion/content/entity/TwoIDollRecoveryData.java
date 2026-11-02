package com.twoi.combatdoll.companion.content.entity;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.companion.content.item.TwoIDollAcquisition;
import com.twoi.combatdoll.companion.content.leaf.LeafRewardMigration;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Rare failed-transfer recovery. Ordinary full-bag recall keeps the live doll. */
@EventBusSubscriber(modid = TwoICombatDollMod.MODID)
public final class TwoIDollRecoveryData extends SavedData {
    private static final String NAME = "two_i_combat_doll_recovery";
    private record Delivery(UUID owner, ItemStack stack, boolean claimed) { }
    private final Map<UUID, Delivery> deliveries = new HashMap<>();

    public static TwoIDollRecoveryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TwoIDollRecoveryData::new, TwoIDollRecoveryData::load), NAME);
    }

    /**
     * Called only after a native transfer could not re-add its unique entity.
     * Persist before the caller discards that last in-memory carrier.
     */
    public static boolean queue(ServerPlayer player, ItemStack original) {
        if (player == null || original.isEmpty() || !original.is(TwoICombatDollMod.DOLL_ITEM.get())) return false;
        UUID identity = original.get(StretcherComponents.TWO_I_DOLL_ID.get());
        UUID owner = original.get(StretcherComponents.TWO_I_OWNER.get());
        if (identity == null || !player.getUUID().equals(owner)) return false;
        TwoIDollRecoveryData data = get(player.getServer());
        Delivery previous = data.deliveries.get(identity);
        if (previous != null && !previous.claimed()) return previous.owner().equals(owner) && data.flush(player.getServer());
        data.deliveries.put(identity, new Delivery(owner, original.copyWithCount(1), false));
        data.setDirty();
        if (!data.flush(player.getServer())) {
            // Both world joins may already have failed. Keep the complete
            // in-memory carrier dirty so a temporary disk failure can retry.
            return false;
        }
        return true;
    }

    public boolean pending(UUID identity) {
        Delivery delivery = deliveries.get(identity);
        return delivery != null && !delivery.claimed();
    }

    public boolean claimed(UUID identity) {
        Delivery delivery = deliveries.get(identity);
        return delivery != null && delivery.claimed();
    }

    public void reconcile(ServerPlayer player) {
        for (var entry : deliveries.entrySet()) {
            Delivery delivery = entry.getValue();
            if (delivery.claimed() || !delivery.owner().equals(player.getUUID())) continue;
            // A rejected join may mean a unique live carrier is already present.
            if (TwoIDollEntity.trackedFor(player.getServer(), delivery.owner()).stream().anyMatch(entity ->
                    entity.isAddedToLevel() && !entity.isRemoved() && entry.getKey().equals(
                            entity.toDollItem().get(StretcherComponents.TWO_I_DOLL_ID.get())))) continue;
            if (!flush(player.getServer())) continue;
            if (LeafRewardMigration.hasReceipt(player, entry.getKey())) {
                player.getServer().getPlayerList().saveAll();
                entry.setValue(new Delivery(delivery.owner(), ItemStack.EMPTY, true)); setDirty(); flush(player.getServer()); continue;
            }
            int slot = player.getInventory().getFreeSlot();
            if (slot < 0) continue;
            ItemStack restored = delivery.stack().copy();
            player.getInventory().setItem(slot, restored);
            // Persist the receipt first. If the later ledger write is interrupted,
            // login sees this exact item and closes the pending delivery safely.
            player.getServer().getPlayerList().saveAll();
            entry.setValue(new Delivery(delivery.owner(), ItemStack.EMPTY, true));
            setDirty(); flush(player.getServer());
            player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
            TwoIDollAcquisition.notifyAcquired(player, restored);
        }
    }

    private boolean flush(MinecraftServer server) {
        return flush(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME + ".dat"), server.registryAccess());
    }

    /** Use one writer for autosaves and explicit delivery barriers. Vanilla's
     * asynchronous SavedData writer clears dirty before its I/O has succeeded. */
    @Override public void save(java.io.File file, HolderLookup.Provider registries) {
        flush(file.toPath(), registries);
    }

    private boolean flush(Path target, HolderLookup.Provider registries) {
        if (!isDirty()) return true;
        Path folder = target.getParent();
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(folder);
            CompoundTag root = new CompoundTag();
            root.put("data", save(new CompoundTag(), registries));
            root.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            NbtIo.writeCompressed(root, temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            setDirty(false);
            return true;
        } catch (java.io.IOException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Cannot persist unique 2i recovery carrier; keeping it pending", failure);
            return false;
        }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) get(player.getServer()).reconcile(player);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        TwoIDollRecoveryData data = get(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) data.reconcile(player);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        deliveries.forEach((id, delivery) -> {
            CompoundTag row = new CompoundTag(); row.putUUID("doll", id); row.putUUID("owner", delivery.owner());
            row.putBoolean("claimed", delivery.claimed());
            if (!delivery.claimed()) row.put("item", delivery.stack().save(registries));
            rows.add(row);
        });
        tag.put("deliveries", rows); return tag;
    }

    private static TwoIDollRecoveryData load(CompoundTag tag, HolderLookup.Provider registries) {
        TwoIDollRecoveryData data = new TwoIDollRecoveryData();
        for (Tag value : tag.getList("deliveries", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) value;
            if (!row.hasUUID("doll") || !row.hasUUID("owner")) continue;
            UUID id = row.getUUID("doll"), owner = row.getUUID("owner");
            Delivery previous = data.deliveries.get(id);
            if (previous != null && previous.claimed()) continue;
            if (row.getBoolean("claimed")) data.deliveries.put(id, new Delivery(owner, ItemStack.EMPTY, true));
            else ItemStack.parse(registries, row.getCompound("item")).filter(stack ->
                    stack.is(TwoICombatDollMod.DOLL_ITEM.get()) && id.equals(stack.get(StretcherComponents.TWO_I_DOLL_ID.get()))
                            && owner.equals(stack.get(StretcherComponents.TWO_I_OWNER.get()))).ifPresent(stack ->
                    data.deliveries.put(id, new Delivery(owner, stack.copyWithCount(1), false)));
        }
        return data;
    }
}
