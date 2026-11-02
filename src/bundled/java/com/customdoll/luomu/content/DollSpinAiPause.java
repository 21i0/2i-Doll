package com.customdoll.luomu.content;

import com.customdoll.luomu.DollMod;
import com.customdoll.luomu.content.blockentity.DollDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Temporary server-side NoAI, with the previous value persisted on each affected mob. */
@EventBusSubscriber(modid = DollMod.MODID)
public final class DollSpinAiPause {
    public static final double RADIUS = 8.0;
    private static final String MARKER = "luomu:spin_ai_pause";
    private static final Map<ServerLevel, State> LEVELS = new IdentityHashMap<>();

    private static final class State {
        final Set<BlockPos> sources = new HashSet<>();
        final Set<Mob> affected = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private DollSpinAiPause() { }

    public static void changed(DollDisplayBlockEntity display) {
        if (!(display.getLevel() instanceof ServerLevel level)) return;
        if (level.getBlockEntity(display.getBlockPos()) != display) return;
        State state = LEVELS.computeIfAbsent(level, ignored -> new State());
        if (!display.isRemoved() && display.isSpinning() && !display.copyDoll().isEmpty())
            state.sources.add(display.getBlockPos().immutable());
        else state.sources.remove(display.getBlockPos());
        update(level, state);
    }

    public static void removed(DollDisplayBlockEntity display) {
        if (!(display.getLevel() instanceof ServerLevel level)) return;
        State state = LEVELS.get(level);
        if (state == null) return;
        state.sources.remove(display.getBlockPos());
        update(level, state);
    }

    private static void pause(Mob mob) {
        var data = mob.getPersistentData();
        if (!data.contains(MARKER, Tag.TAG_COMPOUND)) {
            var previous = new net.minecraft.nbt.CompoundTag();
            previous.putBoolean("was_no_ai", mob.isNoAi());
            data.put(MARKER, previous);
        }
        mob.setNoAi(true);
        mob.getNavigation().stop();
    }

    private static void restore(Mob mob) {
        var data = mob.getPersistentData();
        if (!data.contains(MARKER, Tag.TAG_COMPOUND)) return;
        mob.setNoAi(data.getCompound(MARKER).getBoolean("was_no_ai"));
        data.remove(MARKER);
    }

    private static void update(ServerLevel level, State state) {
        Set<Mob> current = Collections.newSetFromMap(new IdentityHashMap<>());
        var positions = state.sources.iterator();
        while (positions.hasNext()) {
            BlockPos pos = positions.next();
            // Never load a chunk just to keep an aura alive.
            if (!level.hasChunkAt(pos)
                    || !(level.getBlockEntity(pos) instanceof DollDisplayBlockEntity display)
                    || display.isRemoved() || !display.isSpinning() || display.copyDoll().isEmpty()) {
                positions.remove();
                continue;
            }
            Vec3 center = Vec3.atCenterOf(pos);
            current.addAll(level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(RADIUS),
                    mob -> mob.isAlive() && mob.position().distanceToSqr(center) <= RADIUS * RADIUS));
        }
        for (Mob mob : state.affected) if (!current.contains(mob)) restore(mob);
        for (Mob mob : current) pause(mob);
        state.affected.clear();
        state.affected.addAll(current);
        if (state.sources.isEmpty() && state.affected.isEmpty()) LEVELS.remove(level);
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = LEVELS.get(level);
        if (state != null) update(level, state);
    }

    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        // A chunk may have saved a mob while the aura was active. Restore before re-evaluating it.
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Mob mob) restore(mob);
    }

    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) return;
        restore(mob);
        State state = LEVELS.get(level);
        if (state != null) state.affected.remove(mob);
    }

    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        release(level);
    }

    private static void release(ServerLevel level) {
        State state = LEVELS.remove(level);
        if (state != null) state.affected.forEach(DollSpinAiPause::restore);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) release(level);
    }
}
