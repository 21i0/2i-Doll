package com.twoi.combatdoll.companion.content.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player story progress survives death, reclaiming the doll, and server restarts. */
public final class TwoIStoryData extends SavedData {
    private transient MinecraftServer server;
    /** Two integers per player, never a growing conversation transcript. */
    private record Progress(int completedMask, int lastChapter) {}
    private final Map<UUID, Progress> players = new HashMap<>();

    private static TwoIStoryData importLegacy(MinecraftServer server) {
        TwoIStoryData imported=load(LegacyTwoIData.saved(server,"TwoIStoryData","useless_stretcher_two_i_story"),server.registryAccess());
        imported.setDirty();
        return imported;
    }

    public static TwoIStoryData get(MinecraftServer server) {
        TwoIStoryData data = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(() -> importLegacy(server), TwoIStoryData::load), "two_i_combat_doll_two_i_story");
        // Stretcher may have advanced its files while this mod was absent.
        // Merge once per process even when our own newer-format file exists.
        if (data.server != server) {
            TwoIStoryData old = load(LegacyTwoIData.saved(server, "TwoIStoryData", "useless_stretcher_two_i_story"), server.registryAccess());
            old.players.forEach((id, progress) -> {
                Progress own = data.players.get(id);
                int mask = (own == null ? 0 : own.completedMask()) | progress.completedMask();
                if (own == null || mask != own.completedMask()) {
                    data.players.put(id, new Progress(mask, progress.lastChapter())); data.setDirty();
                }
            });
        }
        data.server=server;
        return data;
    }

    private void sync(UUID player){
        int[] old=SharedLegacyTwoI.story(server,player);
        Progress own=players.get(player);
        if(old==null){
            if(own!=null&&SharedLegacyTwoI.data(server,"TwoIStoryData")!=null)for(int chapter=0;chapter<6;chapter++)if((own.completedMask()&(1<<chapter))!=0)SharedLegacyTwoI.complete(server,player,chapter);
            return;
        }
        int mask=(own==null?0:own.completedMask())|old[0];
        if(own==null||mask!=own.completedMask()){players.put(player,new Progress(mask,old[1]));setDirty();}
        // New standalone chapters must remain unlocked for a still-active old entity too.
        if(own!=null)for(int chapter=0;chapter<6;chapter++)if((own.completedMask()&(1<<chapter))!=0&&(old[0]&(1<<chapter))==0)SharedLegacyTwoI.complete(server,player,chapter);
    }
    public boolean metBefore(UUID player) { sync(player);return players.containsKey(player); }

    /** Read-only library access; looking at old chapters must never advance progress. */
    public int completedMask(UUID player) {
        sync(player);
        Progress progress = players.get(player);
        return progress == null ? 0 : progress.completedMask() & 63;
    }

    public void complete(UUID player) {
        complete(player, 0);
    }

    public int nextChapter(UUID player, int bond) {
        sync(player);
        Progress progress = players.get(player);
        if (progress == null) return 0;
        return TwoIStorySession.nextChapterFor(bond, progress.completedMask(), progress.lastChapter());
    }

    public void complete(UUID player, int chapter) {
        if (chapter < 0 || chapter > 5) return;
        sync(player);
        SharedLegacyTwoI.complete(server,player,chapter);
        Progress old = players.getOrDefault(player, new Progress(0, -1));
        players.put(player, new Progress(old.completedMask() | (1 << chapter), chapter));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag savedPlayers = new ListTag();
        for (Map.Entry<UUID, Progress> player : players.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player.getKey());
            entry.putInt("completed", player.getValue().completedMask());
            entry.putInt("last", player.getValue().lastChapter());
            savedPlayers.add(entry);
        }
        tag.put("met_players", savedPlayers);
        return tag;
    }

    private static TwoIStoryData load(CompoundTag tag, HolderLookup.Provider registries) {
        TwoIStoryData data = new TwoIStoryData();
        ListTag players = tag.getList("met_players", Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = players.getCompound(index);
            if (entry.hasUUID("player")) data.players.put(entry.getUUID("player"),
                    new Progress(entry.contains("completed") ? entry.getInt("completed") & 63 : 1,
                            Math.max(0, Math.min(5, entry.getInt("last")))));
        }
        return data;
    }
}
