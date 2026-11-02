package com.twoi.combatdoll.compat;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.companion.content.entity.LegacyTwoIData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Converts only an actual legacy carrier, preserving its identity and public components. */
@EventBusSubscriber(modid="two_i_combat_doll")
public final class LegacyDollInventoryBridge {
    private static final ResourceLocation LEGACY=ResourceLocation.parse("useless_stretcher:two_i_doll");
    private static final Set<UUID> WARNED=new HashSet<>();
    private LegacyDollInventoryBridge(){}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player)convert(player);}
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(event.getEntity() instanceof ServerPlayer player&&Math.floorMod(player.tickCount+player.getId(),40)==0)convert(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){WARNED.clear();}
    public static void convert(ServerPlayer player){
        boolean hasLegacy=BuiltInRegistries.ITEM.containsKey(LEGACY);
        var old=BuiltInRegistries.ITEM.get(LEGACY);boolean changed=false;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
            var original=player.getInventory().getItem(slot);
            if(original.isEmpty()||!(hasLegacy&&original.is(old))&&!needsImport(original))continue;
            try{
                var converted=LegacyTwoIData.canonicalStack(original);
                player.getInventory().setItem(slot,converted);changed=true;
            }catch(RuntimeException error){
                if(WARNED.add(player.getUUID()))LogUtils.getLogger().error("Preserving original legacy 2i item because its components could not be migrated safely",error);
            }
        }
        if(changed){player.getInventory().setChanged();player.containerMenu.broadcastChanges();}
    }
    private static boolean needsImport(net.minecraft.world.item.ItemStack stack){
        if(!stack.is(com.twoi.combatdoll.TwoICombatDollMod.DOLL_ITEM.get()))return false;
        for(var entry:stack.getComponentsPatch().entrySet()){
            var key=BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
            if(key!=null&&key.getNamespace().equals("useless_stretcher")&&switch(key.getPath()){
                case "two_i_rotation","two_i_gift_score","two_i_bond","two_i_owner","two_i_doll_id","two_i_memory","two_i_invitation_used"->true;
                default->false;
            })return true;
        }
        var own=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("doll");
        return own.hasUUID("owner")&&!stack.has(com.twoi.combatdoll.companion.init.StretcherComponents.TWO_I_OWNER.get())
                ||own.hasUUID("identity")&&!stack.has(com.twoi.combatdoll.companion.init.StretcherComponents.TWO_I_DOLL_ID.get());
    }
}
