package com.twoi.combatdoll.compat;

import com.twoi.combatdoll.TwoICombatDollMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Keep legacy saves readable, but present one canonical 2i in all creative catalogues. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class CanonicalDollCatalog {
    public static final ResourceLocation LEGACY=ResourceLocation.parse("useless_stretcher:two_i_doll");
    private CanonicalDollCatalog(){}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void creative(BuildCreativeModeTabContentsEvent event){
        if(!BuiltInRegistries.ITEM.containsKey(LEGACY))return;
        var hidden=BuiltInRegistries.ITEM.get(LEGACY);
        // Copy before mutating: a tab can contain component variants of the old doll.
        var entries=new java.util.ArrayList<ItemStack>();
        for(var stack:event.getParentEntries())if(stack.is(hidden))entries.add(stack);
        for(var stack:event.getSearchEntries())if(stack.is(hidden))entries.add(stack);
        for(var stack:entries)event.remove(stack,CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
}
