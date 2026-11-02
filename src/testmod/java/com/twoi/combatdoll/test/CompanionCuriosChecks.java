package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.companion.content.entity.TwoICompanionshipData;
import com.twoi.combatdoll.companion.content.entity.TwoIDollMemory;
import com.twoi.combatdoll.companion.content.item.TwoIDollItem;
import com.twoi.combatdoll.companion.event.TwoICarriedCompanionship;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.common.event.CuriosEventHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;

import java.util.List;
import java.util.UUID;

/** Private real-Curios fixture; requires the external test slot datapack, never shipped. */
public final class CompanionCuriosChecks {
    private CompanionCuriosChecks() { }

    public static void run(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("curios")) {
            LogUtils.getLogger().info("COMPANION CURIOS SKIPPED: optional mod absent");
            return;
        }
        UUID owner = UUID.randomUUID();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(owner, "CompCurios"));
        player.setPos(59, 180, 59);
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        check(inventory.getCurios().keySet().containsAll(List.of("back", "belt", "body", "bracelet",
                "charm", "hands", "head", "necklace", "ring", "two_i_custom", "two_i_unusual")),
                "external fixture registered native and arbitrary restrictive slots: " + inventory.getCurios().keySet());
        long oldTime = level.getGameTime();
        try {
            ItemStack doll = new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());
            for (var entry : inventory.getCurios().entrySet()) {
                var handler = entry.getValue();
                for (int index = 0; index < handler.getStacks().getSlots(); index++) {
                    var menu = new CurioSlot(player, handler.getStacks(), index, entry.getKey(), 0, 0,
                            handler.getRenders(), handler.getActiveStates(), true, true, false);
                    check(menu.mayPlace(doll) && handler.getStacks().isItemValid(index, doll),
                            "real menu and handler accept canonical doll in " + entry.getKey() + index);
                    check(handler.getCosmeticStacks().isItemValid(index, doll), "cosmetic validation " + entry.getKey());
                    check(!doll.has(StretcherComponents.TWO_I_OWNER.get()) && !doll.has(StretcherComponents.TWO_I_DOLL_ID.get()),
                            "validation leaves unequipped item unbound");
                    check(handler.getStacks().insertItem(index, doll.copy(), false).isEmpty(),
                            "actual insertion accepts " + entry.getKey() + index);
                    ItemStack probe = handler.getStacks().extractItem(index, 1, false);
                    check(probe.is(TwoICombatDollMod.DOLL_ITEM.get()) && probe.getCount() == 1
                            && handler.getStacks().getStackInSlot(index).isEmpty(),
                            "actual extraction returns exactly one canonical doll from " + entry.getKey() + index);
                }
            }
            var custom = inventory.getStacksHandler("two_i_custom").orElseThrow();
            var context = new SlotContext("two_i_custom", player, 0, false, true);
            check(!CuriosApi.isStackValid(context, doll), "raw curios:none validator genuinely denies item");
            check(!custom.getStacks().isItemValid(0, new ItemStack(Items.DIAMOND)), "unrelated item stays denied");
            check(!CuriosApi.getCurio(doll).orElseThrow().canEquipFromUse(context), "right-click remains interaction UI");
            check(custom.getStacks().insertItem(0, doll, false).isEmpty(), "real restrictive-slot insertion");
            new CuriosEventHandler().tick(new EntityTickEvent.Post(player));
            ItemStack equipped = custom.getStacks().getStackInSlot(0);
            check(owner.equals(equipped.get(StretcherComponents.TWO_I_OWNER.get()))
                    && equipped.has(StretcherComponents.TWO_I_DOLL_ID.get()), "actual equip callback assigns owner and identity");
            UUID identity = equipped.get(StretcherComponents.TWO_I_DOLL_ID.get());
            var memory = new TwoIDollMemory(new TwoIDollMemory.Cooldowns(17, 1200, 100, 500, 0, 0, -1, false),
                    TwoIDollMemory.Interaction.empty());
            equipped.set(StretcherComponents.TWO_I_BOND.get(), 37);
            equipped.set(StretcherComponents.TWO_I_MEMORY.get(), memory);
            equipped.set(StretcherComponents.TWO_I_INVITATION_USED.get(), true);
            equipped.set(DataComponents.CUSTOM_NAME, Component.literal("Curios memory fixture"));
            ItemStack before = equipped.copy();

            ServerPlayer other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CompOther"));
            var otherSlots = CuriosApi.getCuriosInventory(other).orElseThrow().getStacksHandler("two_i_custom").orElseThrow();
            check(!otherSlots.getStacks().isItemValid(0, equipped) && !otherSlots.getCosmeticStacks().isItemValid(0, equipped),
                    "another owner rejected by real functional and cosmetic slots");
            custom.getRenders().set(0, false);
            sample(player, level, 0, 2000, false);
            check(ItemStack.isSameItemSameComponents(before, equipped), "hidden Curios 2000 ticks cause zero stack writes");
            var progress = TwoICompanionshipData.get(level.getServer());
            check(progress.progress(identity, memory).ticks() == 2000, "hidden accessory still accumulates sampled time");

            equipped = custom.getStacks().extractItem(0, 1, false);
            check(custom.getStacks().getStackInSlot(0).isEmpty(), "actual extraction leaves no duplicate");
            player.setItemSlot(EquipmentSlot.HEAD, equipped);
            check(ItemStack.isSameItemSameComponents(before, player.getItemBySlot(EquipmentSlot.HEAD)), "Curios to head preserves all components");
            sample(player, level, 2000, 3000, false);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.OFFHAND, equipped);
            sample(player, level, 3000, 4000, false);
            check(ItemStack.isSameItemSameComponents(before, equipped), "head and hand movement preserves memory without premature bond");
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            check(custom.getStacks().insertItem(1, equipped, false).isEmpty(), "re-equip complete item at another index");
            sample(player, level, 4000, 6000, true);
            check(TwoIDollItem.getBond(equipped) == 38 && progress.progress(identity, memory).ticks() == 0,
                    "combined Curios/head/hand 6000 ticks award once despite duplicate events");
            check(memory.equals(equipped.get(StretcherComponents.TWO_I_MEMORY.get()))
                    && identity.equals(equipped.get(StretcherComponents.TWO_I_DOLL_ID.get()))
                    && owner.equals(equipped.get(StretcherComponents.TWO_I_OWNER.get()))
                    && equipped.getOrDefault(StretcherComponents.TWO_I_INVITATION_USED.get(), false)
                    && equipped.getHoverName().getString().equals("Curios memory fixture"), "all non-bond memory survives transitions");

            custom.getActiveStates().set(1, false);
            sample(player, level, 6000, 7000, false);
            check(progress.progress(identity, memory).ticks() == 0, "inactive slot earns no companionship");
            custom.getActiveStates().set(1, true);
            ItemStack cosmetic = new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());
            TwoIDollItem.claimOrCheckOwner(cosmetic, player);
            UUID cosmeticIdentity = cosmetic.get(StretcherComponents.TWO_I_DOLL_ID.get());
            ItemStack cosmeticBefore = cosmetic.copy();
            custom.getCosmeticStacks().setStackInSlot(0, cosmetic);
            sample(player, level, 7000, 7100, false);
            check(ItemStack.isSameItemSameComponents(cosmeticBefore, cosmetic)
                    && progress.progress(cosmeticIdentity, TwoIDollMemory.empty()).ticks() == 0,
                    "cosmetic-only item has no functional reward or component mutation");

            var saved = inventory.saveInventory(false);
            custom.getStacks().setStackInSlot(1, ItemStack.EMPTY);
            inventory.loadInventory(saved);
            ItemStack restored = inventory.getStacksHandler("two_i_custom").orElseThrow().getStacks().getStackInSlot(1);
            check(ItemStack.isSameItemSameComponents(equipped, restored), "real Curios save/load preserves complete canonical item");
            var decoded = TwoICompanionshipData.load(progress.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            check(decoded.progress(identity, memory).equals(progress.progress(identity, memory)), "world timer save/load matches equipped doll progress");
            LogUtils.getLogger().info("COMPANION CURIOS CHECKS PASSED: native/arbitrary restrictive slots, actual menu/equip/owner; Curios-head-hand transitions, hidden/inactive/cosmetic, one 6000-tick reward, complete inventory+world save/load");
        } finally {
            clear(inventory);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            time(level, oldTime);
        }
    }

    private static void sample(ServerPlayer player, ServerLevel level, int start, int end, boolean duplicate) {
        for (int tick = start; tick < end; tick++) {
            time(level, 240_000L + tick);
            TwoICarriedCompanionship.tick(new PlayerTickEvent.Post(player));
            if (duplicate) TwoICarriedCompanionship.tick(new PlayerTickEvent.Post(player));
        }
    }
    private static void time(ServerLevel level, long value) { level.getServer().getWorldData().overworldData().setGameTime(value); }
    private static void clear(ICuriosItemHandler inventory) {
        inventory.getCurios().values().forEach(handler -> {
            for (int index = 0; index < handler.getStacks().getSlots(); index++) {
                handler.getStacks().setStackInSlot(index, ItemStack.EMPTY);
                handler.getCosmeticStacks().setStackInSlot(index, ItemStack.EMPTY);
            }
        });
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError("Companion Curios: " + label); }
}
