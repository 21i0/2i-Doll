package com.twoi.combatdoll.companion.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

/** Read-only stop-time readiness checks; never mutates vanilla chunk scheduling. */
@Mixin(ChunkMap.class)
public interface TwoIStarfieldChunkLifecycleAccess {
    @Accessor("updatingChunkMap") Long2ObjectLinkedOpenHashMap<ChunkHolder> twoi$updatingChunks();
    @Accessor("pendingUnloads") Long2ObjectLinkedOpenHashMap<ChunkHolder> twoi$pendingUnloads();
    @Accessor("pendingGenerationTasks") List<ChunkGenerationTask> twoi$pendingGenerationTasks();
}
