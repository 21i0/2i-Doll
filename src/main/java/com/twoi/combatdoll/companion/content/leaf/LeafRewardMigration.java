package com.twoi.combatdoll.companion.content.leaf;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.companion.event.StandaloneDollLeafDrops;
import com.twoi.combatdoll.companion.content.entity.TwoIDollAcquisitionData;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import com.twoi.combatdoll.entity.DollAcquisitionData;
import com.twoi.combatdoll.compat.StretcherCompanionBridge;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Identity sidecar for old standalone rewards. The shared four-set ledger
 * stays readable by Stretcher; its SavedData type is never replaced while
 * that mod is loaded. Replaying a sidecar after a partial save is idempotent.
 */
@EventBusSubscriber(modid = "two_i_combat_doll")
public final class LeafRewardMigration extends SavedData {
    private static final String NAME = "two_i_combat_doll_leaf_identities";
    private static final ResourceLocation OLD_ITEM = ResourceLocation.parse("useless_stretcher:two_i_doll");
    private static final ResourceLocation OLD_ID = ResourceLocation.parse("useless_stretcher:two_i_doll_id");
    private record Award(UUID identity, boolean claimed) { }
    private final Map<UUID, Award> awards = new HashMap<>();
    private boolean initialized;
    private Ledger activeLedger;
    private final Set<UUID> onlinePending = new HashSet<>();

    public interface Ledger {
        boolean hasRolledDoll(UUID player);
        boolean hasClaimedDoll(UUID player);
        boolean beginDollDelivery(UUID player);
        void markDollDelivered(UUID player);
        default boolean hasPendingDoll(UUID player) { return hasRolledDoll(player) && !hasClaimedDoll(player); }
    }

    public static Ledger standalone(StandaloneLeafDropData data) {
        return new Ledger() {
            public boolean hasRolledDoll(UUID player) { return data.hasRolledDoll(player); }
            public boolean hasClaimedDoll(UUID player) { return data.hasClaimedDoll(player); }
            public boolean beginDollDelivery(UUID player) { return data.beginDollDelivery(player); }
            public void markDollDelivered(UUID player) { data.markDollDelivered(player); }
        };
    }

    /** Returns the owning mod's real ledger, without cross-casting SavedData classes. */
    public static Ledger ledger(MinecraftServer server) { return prepare(server).activeLedger; }

    public static LeafRewardMigration prepare(MinecraftServer server) {
        LeafRewardMigration data = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(LeafRewardMigration::new, LeafRewardMigration::load), NAME);
        if (!data.initialized) {
            data.activeLedger = StretcherCompanionBridge.leafLedger(server);
            if (data.activeLedger == null) data.activeLedger = standalone(StandaloneLeafDropData.get(server));
            CompoundTag legacy = DollAcquisitionData.get(server).save(new CompoundTag(), server.registryAccess());
            data.importAwards(legacy, data.activeLedger);
            // The retired root handler announced at begin(), even if the bag
            // was full. Import those announcement receipts as well, rather
            // than replaying a whole-server acquisition message after upgrade.
            for (Tag value : legacy.getList("awards", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) value;
                if (row.hasUUID("identity")) TwoIDollAcquisitionData.get(server).markIfNew(row.getUUID("identity"));
            }
            // The two SavedData files may have reached disk in either order.
            // Import/replay runs once per server, never once per tick.
            data.initialized = true;
        }
        return data;
    }

    public UUID identity(UUID player) {
        Award award = awards.get(player);
        return award == null ? StandaloneDollLeafDrops.rewardIdentity(player) : award.identity();
    }

    public void rememberPending(UUID player) {
        if (awards.putIfAbsent(player, new Award(StandaloneDollLeafDrops.rewardIdentity(player), false)) == null) setDirty();
    }

    public void markClaimed(UUID player) {
        Award award = awards.get(player);
        if (award != null && !award.claimed()) {
            awards.put(player, new Award(award.identity(), true)); setDirty();
        }
        onlinePending.remove(player);
    }

    /** Also used by the registered fixture to exercise actual old NBT. */
    public void importAwards(CompoundTag legacy, Ledger ledger) {
        ListTag rows = legacy.getList("awards", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            if (!row.hasUUID("player") || !row.hasUUID("identity")) continue;
            UUID player = row.getUUID("player");
            Award previous = awards.get(player);
            // Keep the original pending identity. A claim from either history wins.
            Award merged = new Award(previous == null ? row.getUUID("identity") : previous.identity(),
                    row.getBoolean("claimed") || previous != null && previous.claimed() || ledger.hasClaimedDoll(player));
            if (!merged.equals(previous)) { awards.put(player, merged); setDirty(); }
        }
        replay(ledger);
    }

    private void replay(Ledger ledger) {
        for (var entry : awards.entrySet()) {
            UUID player = entry.getKey();
            ledger.beginDollDelivery(player);
            if (entry.getValue().claimed()) ledger.markDollDelivered(player);
            if (ledger.hasClaimedDoll(player) && !entry.getValue().claimed()) {
                entry.setValue(new Award(entry.getValue().identity(), true)); setDirty();
            }
        }
    }

    /** Checks receipts before insertion, including pre-component carriers. */
    public static boolean hasReceipt(ServerPlayer player, UUID identity) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            if (receiptMatches(player.getInventory().getItem(slot), identity)) return true;
        return false;
    }

    public static boolean receiptMatches(ItemStack stack, UUID identity) {
        if (stack.isEmpty() || !(stack.is(TwoICombatDollMod.DOLL_ITEM.get())
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(OLD_ITEM))) return false;
        if (identity.equals(stack.get(StretcherComponents.TWO_I_DOLL_ID.get()))) return true;
        DataComponentType<?> oldType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(OLD_ID);
        if (oldType != null && identity.equals(stack.get(oldType))) return true;
        CompoundTag doll = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("doll");
        return doll.hasUUID("identity") && identity.equals(doll.getUUID("identity"));
    }

    /** Login precedes the old handler's first pending reward tick. */
    public void reconcile(ServerPlayer player) {
        UUID owner = player.getUUID();
        if (!activeLedger.hasPendingDoll(owner)) {
            if (activeLedger.hasClaimedDoll(owner)) markClaimed(owner);
            return;
        }
        // The deterministic fallback covers a partial new standalone save
        // before its identity sidecar reached disk.
        if (hasReceipt(player, identity(owner))) {
            activeLedger.markDollDelivered(owner); markClaimed(owner); return;
        }
        if (awards.containsKey(owner) && !awards.get(owner).claimed()) {
            onlinePending.add(owner);
            StandaloneDollLeafDrops.deliver(player, activeLedger);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void started(ServerStartedEvent event) {
        LeafRewardMigration data = prepare(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) data.reconcile(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) prepare(player.getServer()).reconcile(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) prepare(player.getServer()).onlinePending.remove(player.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void pendingBeforeOldHandler(ServerTickEvent.Post event) {
        if (!ModList.get().isLoaded("useless_stretcher")) return;
        LeafRewardMigration data = prepare(event.getServer());
        // Only online migrated pending owners are visited; inventory scans run
        // on login, when a slot is free, or once per ten seconds while full.
        for (UUID id : Set.copyOf(data.onlinePending)) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null || !data.activeLedger.hasPendingDoll(id)) { data.onlinePending.remove(id); continue; }
            if (!player.isAlive() || player.isRemoved()) continue;
            if (player.getInventory().getFreeSlot() >= 0 || event.getServer().getTickCount() % 200 == 0)
                StandaloneDollLeafDrops.deliver(player, data.activeLedger);
        }
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        awards.forEach((player, award) -> {
            CompoundTag row = new CompoundTag(); row.putUUID("player", player); row.putUUID("identity", award.identity());
            row.putBoolean("claimed", award.claimed()); rows.add(row);
        });
        tag.put("awards", rows); return tag;
    }

    public static LeafRewardMigration load(CompoundTag tag, HolderLookup.Provider registries) {
        LeafRewardMigration data = new LeafRewardMigration();
        for (Tag value : tag.getList("awards", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) value;
            if (!row.hasUUID("player") || !row.hasUUID("identity")) continue;
            UUID player = row.getUUID("player"); Award previous = data.awards.get(player);
            data.awards.put(player, new Award(previous == null ? row.getUUID("identity") : previous.identity(),
                    row.getBoolean("claimed") || previous != null && previous.claimed()));
        }
        return data;
    }
}
