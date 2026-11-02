package com.twoi.combatdoll.combat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** A save-owned clock continues while vanilla simulation is frozen. Cooldowns follow weapon identities. */
public final class CombatWorldData extends SavedData {
    public static final int COOLDOWN=12_000;
    private long clock;
    private final Map<UUID,Long> ready=new HashMap<>();
    public static CombatWorldData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(CombatWorldData::new,CombatWorldData::load),"two_i_combat_weapons"); }
    public long clock(){return clock;}
    public void tick(){clock++;if(clock%1200==0)ready.values().removeIf(deadline->deadline<=clock);if(clock%20==0)setDirty();}
    public long remaining(UUID id){return id==null?0:Math.max(0,ready.getOrDefault(id,0L)-clock);}
    public boolean reserve(UUID id){if(remaining(id)>0)return false;ready.put(id,clock+COOLDOWN);setDirty();return true;}
    public void restoreDeadline(UUID id,long deadline){if(deadline>clock&&deadline>ready.getOrDefault(id,0L)){ready.put(id,Math.min(deadline,clock+COOLDOWN));setDirty();}}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        tag.putLong("clock",clock);var list=new ListTag();
        ready.forEach((id,time)->{if(time>clock){var row=new CompoundTag();row.putUUID("id",id);row.putLong("ready",time);list.add(row);}});
        tag.put("weapons",list);return tag;
    }
    public static CombatWorldData load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new CombatWorldData();data.clock=Math.max(0,tag.getLong("clock"));
        for(var row:tag.getList("weapons",Tag.TAG_COMPOUND)){var r=(CompoundTag)row;if(r.hasUUID("id"))data.ready.put(r.getUUID("id"),r.getLong("ready"));}
        return data;
    }
}
