package com.twoi.combatdoll.companion.content.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Persistent once-only acquisition announcements, keyed by the doll's stable identity. */
public final class TwoIDollAcquisitionData extends SavedData {
    private transient MinecraftServer server;
    private final Set<UUID> announced = new HashSet<>();

    private static TwoIDollAcquisitionData importLegacy(MinecraftServer server) {
        TwoIDollAcquisitionData imported=load(LegacyTwoIData.saved(server,"TwoIDollAcquisitionData","useless_stretcher_two_i_acquisitions"),server.registryAccess());
        imported.setDirty();
        return imported;
    }

    public static TwoIDollAcquisitionData get(MinecraftServer server) {
        TwoIDollAcquisitionData data = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(() -> importLegacy(server), TwoIDollAcquisitionData::load),
                "two_i_combat_doll_two_i_acquisitions");
        if (data.server != server) {
            TwoIDollAcquisitionData old = load(LegacyTwoIData.saved(server, "TwoIDollAcquisitionData", "useless_stretcher_two_i_acquisitions"), server.registryAccess());
            if (data.announced.addAll(old.announced)) data.setDirty();
        }
        data.server=server;
        return data;
    }

    public boolean markIfNew(UUID dollId) {
        if(dollId==null)return false;
        boolean oldNew=SharedLegacyTwoI.acquisition(server,dollId);
        boolean ownNew=announced.add(dollId);
        if(!ownNew)return false;
        setDirty();
        return oldNew;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag ids = new ListTag();
        for (UUID id : announced) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            ids.add(entry);
        }
        tag.put("announced", ids);
        return tag;
    }

    private static TwoIDollAcquisitionData load(CompoundTag tag, HolderLookup.Provider registries) {
        TwoIDollAcquisitionData data = new TwoIDollAcquisitionData();
        ListTag ids = tag.getList("announced", Tag.TAG_COMPOUND);
        for (int i = 0; i < ids.size(); i++) {
            CompoundTag entry = ids.getCompound(i);
            if (entry.hasUUID("id")) data.announced.add(entry.getUUID("id"));
        }
        return data;
    }
}
