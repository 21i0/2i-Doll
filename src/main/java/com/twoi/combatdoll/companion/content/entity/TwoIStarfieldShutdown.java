package com.twoi.combatdoll.companion.content.entity;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.companion.mixin.TwoIStarfieldChunkLifecycleAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** Finishes this mod's recent native dimension-transfer work before vanilla's final unload loop. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID)
public final class TwoIStarfieldShutdown {
    private static final Map<MinecraftServer, Set<ResourceKey<Level>>> TRAVEL_LEVELS = new WeakHashMap<>();
    private static final long DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(10);
    private TwoIStarfieldShutdown() { }

    static void travelled(ServerLevel source, ServerLevel destination) {
        var levels = TRAVEL_LEVELS.computeIfAbsent(source.getServer(), ignored -> new LinkedHashSet<>());
        levels.add(source.dimension()); levels.add(destination.dimension());
    }

    private record Work(int tasks, int holders, int unready, long references) {
        boolean ready() { return tasks == 0 && unready == 0; }
    }

    private static Work work(ServerLevel level) {
        var access = (TwoIStarfieldChunkLifecycleAccess) level.getChunkSource().chunkMap;
        Set<ChunkHolder> holders = Collections.newSetFromMap(new IdentityHashMap<>());
        holders.addAll(access.twoi$updatingChunks().values());
        holders.addAll(access.twoi$pendingUnloads().values());
        int unready = 0; long references = 0;
        for (ChunkHolder holder : holders) {
            if (!holder.isReadyForSaving()) unready++;
            references += holder.getGenerationRefCount();
        }
        return new Work(access.twoi$pendingGenerationTasks().size(), holders.size(), unready, references);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        Set<ResourceKey<Level>> visited = TRAVEL_LEVELS.get(server);
        if (visited == null || visited.isEmpty()) return;
        List<ServerLevel> levels = visited.stream().map(server::getLevel).filter(Objects::nonNull).toList();
        long started = System.nanoTime(), deadline = started + DEADLINE_NANOS;
        int settled = 0, passes = 0;
        Map<String, Work> state = new LinkedHashMap<>();
        LogUtils.getLogger().info("2I STARFIELD STOP DRAIN BEGIN: recent native travel dimensions {}", visited);
        do {
            // These are vanilla task pumps, before MinecraftServer.stopServer's
            // unlimited unload budget. No chunk queue, ticket or refcount is edited.
            boolean progressed = server.pollTask();
            for (ServerLevel level : levels) progressed |= level.getChunkSource().pollTask();
            state.clear();
            for (ServerLevel level : levels) state.put(level.dimension().location().toString(), work(level));
            passes++;
            if (state.values().stream().allMatch(Work::ready)) settled++; else settled = 0;
            if (settled >= 2) {
                LogUtils.getLogger().info("2I STARFIELD STOP DRAIN READY: {} ms, {} task-pump passes; {}",
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started), passes, state);
                return;
            }
            // Cooperatively wait for unfinished worker futures, only while the
            // readiness predicate remains false and no main-thread task ran.
            if (!progressed) LockSupport.parkNanos(100_000L);
        } while (System.nanoTime() < deadline);
        LogUtils.getLogger().warn("2I STARFIELD STOP DRAIN DEADLINE: generation still pending after bounded wait; keeping all chunks and vanilla save state intact: {}", state);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { TRAVEL_LEVELS.remove(event.getServer()); }
}
