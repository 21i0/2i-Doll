package com.customdoll.chuanshuo.content;

import com.customdoll.chuanshuo.DollMod;
import com.customdoll.chuanshuo.content.blockentity.DollDisplayBlockEntity;
import com.customdoll.chuanshuo.content.entity.DollEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Server-authorized, ephemeral performance; it never changes inventory, AI flags or the world. */
@EventBusSubscriber(modid = DollMod.MODID)
public final class DollLaugh {
    public static final int DURATION = 240;
    private static final Map<ServerLevel, List<Session>> ACTIVE = new WeakHashMap<>();
    private record Session(UUID id, Player owner, Object source, long start) {
        Vec3 center() {
            return source instanceof DollEntity e ? e.position()
                    : Vec3.atCenterOf(((DollDisplayBlockEntity)source).getBlockPos());
        }
        boolean valid(ServerLevel level) {
            if (!owner.isAlive() || owner.isRemoved() || owner.level() != level) return false;
            if (source instanceof DollEntity e) return e.level() == level && e.isAlive() && !e.isRemoved()
                    && level.hasChunkAt(e.blockPosition()) && !e.storedCopy().isEmpty()
                    && owner.getUUID().equals(e.ownerId());
            var b = (DollDisplayBlockEntity)source;
            return !b.isRemoved() && level.hasChunkAt(b.getBlockPos())
                    && level.getBlockEntity(b.getBlockPos()) == b && !b.copyDoll().isEmpty()
                    && DollOwnership.mayUse(b.copyDoll(), owner);
        }
        LaughPayload packet(boolean stop) {
            return new LaughPayload(id, source instanceof DollEntity e ? e.getId() : -1,
                    source instanceof DollEntity e ? e.getUUID() : new UUID(0, 0),
                    source instanceof DollDisplayBlockEntity b ? b.getBlockPos() : BlockPos.ZERO,
                    center(), start, stop);
        }
    }
    public static boolean isLaughing(DollEntity entity) {
        return entity.level() instanceof ServerLevel level && ACTIVE.getOrDefault(level, List.of()).stream()
                .anyMatch(s -> s.source == entity && level.getGameTime() - s.start >= 0
                        && level.getGameTime() - s.start < DURATION && s.valid(level));
    }
    public static int activeCount(ServerLevel level) { return ACTIVE.getOrDefault(level, List.of()).size(); }
    public static boolean start(Player player, Object source) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()
                || player.isRemoved() || player.isSpectator()) return false;
        Vec3 center;
        if (source instanceof DollEntity e) {
            if (e.level() != level || !e.isAlive() || e.isRemoved() || e.storedCopy().isEmpty()
                    || !player.getUUID().equals(e.ownerId())) return false;
            center = e.position();
        } else if (source instanceof DollDisplayBlockEntity b) {
            if (b.getLevel() != level || b.isRemoved() || !level.hasChunkAt(b.getBlockPos())
                    || level.getBlockEntity(b.getBlockPos()) != b || b.copyDoll().isEmpty()
                    || !DollOwnership.mayUse(b.copyDoll(), player)) return false;
            center = Vec3.atCenterOf(b.getBlockPos());
        } else return false;
        if (!level.hasChunkAt(BlockPos.containing(center)) || player.distanceToSqr(center) > 36
                || !level.getWorldBorder().isWithinBounds(BlockPos.containing(center))) return false;
        var sessions = ACTIVE.computeIfAbsent(level, k -> new ArrayList<>());
        // One session per owner prevents spam through a shelf of different dolls.
        if (sessions.size() >= 32 || sessions.stream().anyMatch(s -> s.source == source
                || s.owner.getUUID().equals(player.getUUID()))) return false;
        var session = new Session(UUID.randomUUID(), player, source, level.getGameTime());
        sessions.add(session);
        if (source instanceof DollEntity e) e.getNavigation().stop();
        send(level, session, false);
        return true;
    }
    private static void send(ServerLevel level, Session session, boolean stop) {
        PacketDistributor.sendToPlayersInDimension(level, session.packet(stop));
    }
    @SubscribeEvent public static void block(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof DollDisplayBlockEntity display) {
            event.setCanceled(true);
            if (!event.getLevel().isClientSide && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START)
                start(event.getEntity(), display);
        }
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (event.getTarget() instanceof DollEntity doll) {
            event.setCanceled(true);
            if (!event.getEntity().level().isClientSide) start(event.getEntity(), doll);
        }
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var sessions = ACTIVE.get(level);
        if (sessions == null) return;
        for (var it = sessions.iterator(); it.hasNext();) {
            var s = it.next(); long elapsed = level.getGameTime() - s.start;
            if (!s.valid(level) || elapsed < 0 || elapsed >= DURATION) { send(level, s, true); it.remove(); }
            else if (level.getGameTime() % 20 == 0) send(level, s, false);
        }
        if (sessions.isEmpty()) ACTIVE.remove(level);
    }
    private static void stopOwner(UUID owner) {
        for (var entry : ACTIVE.entrySet()) entry.getValue().removeIf(s -> {
            if (!s.owner.getUUID().equals(owner)) return false;
            send(entry.getKey(), s, true); return true;
        });
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        stopOwner(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void dimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        stopOwner(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { ACTIVE.clear(); }
}
