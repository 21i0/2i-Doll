package com.twoi.combatdoll.entity;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.api.DollCatalog;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Separate won/claimed records and a stable pending identity, stored in the world. */
public final class DollAcquisitionData extends SavedData {
    private record Award(UUID identity,boolean claimed) { }
    private final Map<UUID,Award> awards=new HashMap<>();
    public static DollAcquisitionData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(DollAcquisitionData::new,DollAcquisitionData::load),"two_i_combat_doll_rewards");}
    public boolean won(UUID player){return awards.containsKey(player);}
    public boolean pending(UUID player){var award=awards.get(player);return award!=null&&!award.claimed;}
    public boolean begin(UUID player){if(won(player))return false;awards.put(player,new Award(UUID.randomUUID(),false));setDirty();return true;}
    public boolean deliver(ServerPlayer player){
        var award=awards.get(player.getUUID());if(award==null||award.claimed)return false;
        var inventory=player.getInventory();
        for(int slot=0;slot<inventory.getContainerSize();slot++){
            var item=inventory.getItem(slot);if(!item.is(TwoICombatDollMod.DOLL_ITEM.get()))continue;
            var own=item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("doll");
            if(own.hasUUID("identity")&&own.getUUID("identity").equals(award.identity)){claim(player.getUUID(),award);return false;}
        }
        if(inventory.getFreeSlot()<0)return false;
        ItemStack item=new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());
        var tag=new CompoundTag();var own=new CompoundTag();own.putUUID("identity",award.identity);own.putUUID("owner",player.getUUID());
        own.putString("kind",DollCatalog.TWO_I.toString());tag.put("doll",own);item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        if(!inventory.add(item))return false;
        claim(player.getUUID(),award);inventory.setChanged();return true;
    }
    private void claim(UUID player,Award award){awards.put(player,new Award(award.identity,true));setDirty();}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var list=new ListTag();awards.forEach((player,award)->{var row=new CompoundTag();row.putUUID("player",player);
            row.putUUID("identity",award.identity);row.putBoolean("claimed",award.claimed);list.add(row);});tag.put("awards",list);return tag;
    }
    public static DollAcquisitionData load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new DollAcquisitionData();for(var value:tag.getList("awards",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)value;if(row.hasUUID("player")&&row.hasUUID("identity"))data.awards.put(row.getUUID("player"),new Award(row.getUUID("identity"),row.getBoolean("claimed")));
        }return data;
    }
}
