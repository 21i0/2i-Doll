package com.twoi.combatdoll.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry-name migration only: never registers or restores the retired A doll. */
public final class LegacyChuanshouAliases {
    public static final ResourceLocation LEGACY_ITEM = ResourceLocation.parse("chuanshou:chuanshou_doll");
    public static final ResourceLocation ITEM = ResourceLocation.parse("chuanshuo:chuanshuo_doll");
    public static final ResourceLocation LEGACY_DISPLAY = ResourceLocation.parse("chuanshou:chuanshou_display");
    public static final ResourceLocation DISPLAY = ResourceLocation.parse("chuanshuo:chuanshuo_display");

    private LegacyChuanshouAliases() { }

    /** Call during mod construction, before the target DeferredRegisters' RegisterEvents. */
    public static void register(DeferredRegister<Item> items, DeferredRegister<Block> blocks,
                                DeferredRegister<BlockEntityType<?>> blockEntities) {
        items.addAlias(LEGACY_ITEM, ITEM);
        blocks.addAlias(LEGACY_DISPLAY, DISPLAY);
        blockEntities.addAlias(LEGACY_DISPLAY, DISPLAY);
    }
}
