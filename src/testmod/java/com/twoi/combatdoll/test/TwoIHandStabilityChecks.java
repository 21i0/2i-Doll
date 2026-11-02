package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.ClientHooks;

import java.util.UUID;

/** Exercises the real NeoForge hook used by ItemInHandRenderer after inventory synchronization. */
public final class TwoIHandStabilityChecks {
    private TwoIHandStabilityChecks() { }

    public static void run() throws Exception {
        var selectedSlot = ClientHooks.class.getDeclaredField("slotMainHand");
        selectedSlot.setAccessible(true);
        int originalSlot = selectedSlot.getInt(null);
        try {
            selectedSlot.setInt(null, 0);
            for(boolean typed:new boolean[]{false,true})for (int slot : new int[]{0, -1}) {
                ItemStack displayed = new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());
                identity(displayed,new UUID(0,slot==-1?9:8),typed);
                for (int second = 1; second <= 600; second++) {
                    // Container synchronization replaces the stack object even while the player holds still.
                    ItemStack synced = displayed.copy();
                    var tag=synced.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putInt("cooldown",second);
                    synced.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
                    check(!ClientHooks.shouldCauseReequipAnimation(displayed, synced, slot),
                            "stationary "+(typed?"typed":"legacy custom")+" doll must not reequip after companion/owner/bond updates in slot " + slot);
                    displayed = synced;
                }
                check(!ClientHooks.shouldCauseReequipAnimation(displayed, displayed.copy(), slot),
                        "identical item resync must not move the hand");
                check(ClientHooks.shouldCauseReequipAnimation(displayed, ItemStack.EMPTY, slot),
                        "putting the doll away still animates");
                check(ClientHooks.shouldCauseReequipAnimation(ItemStack.EMPTY, displayed, slot),
                        "taking the doll out still animates");
                check(ClientHooks.shouldCauseReequipAnimation(displayed, new ItemStack(Items.STONE), slot),
                        "switching to another item still animates");
            }
            ItemStack doll = ItemStack.EMPTY;
            // Player action: repeatedly exchange the two otherwise identical dolls between the hands.
            for(boolean typed:new boolean[]{false,true}){
                doll = new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());identity(doll,new UUID(0,8),typed);
                ItemStack otherDoll = doll.copy();identity(otherDoll,new UUID(0,9),typed);
                selectedSlot.setInt(null, 0);
                for (int swap = 0; swap < 100; swap++) {
                check(ClientHooks.shouldCauseReequipAnimation(doll, otherDoll, 0),
                        "dual-doll "+(typed?"typed":"legacy custom")+" hand swap must still animate the main hand");
                check(ClientHooks.shouldCauseReequipAnimation(otherDoll, doll, -1),
                        "dual-doll "+(typed?"typed":"legacy custom")+" hand swap must still animate the off hand");
                ItemStack previous = doll; doll = otherDoll; otherDoll = previous;
                }
            }
            UUID stableId=new UUID(0,15);
            ItemStack legacy=new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());identity(legacy,stableId,false);
            ItemStack migrated=legacy.copy();identity(migrated,stableId,true);
            selectedSlot.setInt(null,0);
            for(int slot:new int[]{0,-1}){
                check(!ClientHooks.shouldCauseReequipAnimation(legacy,migrated,slot),"same UUID legacy-to-typed migration preserves stable hand in slot "+slot);
                check(!ClientHooks.shouldCauseReequipAnimation(migrated,legacy,slot),"same UUID typed-to-legacy in-flight resync preserves stable hand in slot "+slot);
            }
            ItemStack staleCustom=migrated.copy();identity(staleCustom,new UUID(0,16),false);
            ItemStack otherStaleCustom=staleCustom.copy();identity(otherStaleCustom,new UUID(0,17),false);
            for(int slot:new int[]{0,-1}){
                check(!ClientHooks.shouldCauseReequipAnimation(staleCustom,otherStaleCustom,slot),"old custom identity cannot override identical authoritative typed UUID in slot "+slot);
            }
            ItemStack differentTyped=staleCustom.copy();identity(differentTyped,new UUID(0,18),true);
            for(int slot:new int[]{0,-1}){
                check(ClientHooks.shouldCauseReequipAnimation(staleCustom,differentTyped,slot),"different typed UUIDs must animate even with matching stale custom identity in slot "+slot);
            }
            ItemStack before=staleCustom.copy();ClientHooks.shouldCauseReequipAnimation(staleCustom,otherStaleCustom,-1);
            check(ItemStack.isSameItemSameComponents(before,staleCustom),"render hook reads identity without mutating carrier components");
            selectedSlot.setInt(null, 0);
            check(ClientHooks.shouldCauseReequipAnimation(doll, doll.copy(), 1),
                    "switching hotbar slots still animates even between dolls");
            ItemStack stone = new ItemStack(Items.STONE);
            check(ClientHooks.shouldCauseReequipAnimation(stone, stone.copy(), -1),
                    "the ordinary item's hook remains unchanged");
            LogUtils.getLogger().info("2I HAND STABILITY PASSED: 2400 typed/legacy real sync hooks without idle reequip; 200 dual-doll hand swaps animate both hands; same UUID migration stable, typed identity wins stale custom data, no hook component writes; put-away/take-out/item/slot changes preserved");
        } finally {
            selectedSlot.setInt(null, originalSlot);
        }
    }

    private static void identity(ItemStack item,UUID id,boolean typed){
        if(typed){item.set(StretcherComponents.TWO_I_DOLL_ID.get(),id);return;}
        var tag=new CompoundTag();var own=new CompoundTag();own.putUUID("identity",id);tag.put("doll",own);item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError("2i hand stability: " + message);
    }
}
