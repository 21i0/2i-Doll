package com.customdoll.longhuan.content;

import com.customdoll.longhuan.DollMod;
import com.customdoll.longhuan.content.entity.DollEntity;
import com.customdoll.longhuan.content.blockentity.DollDisplayBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Ephemeral performance: never changes an item, mob position, AI or saved inventory. */
@EventBusSubscriber(modid=DollMod.MODID)
public final class DollDance {
    public static final int DURATION=483; // full 24.102 s reference audio
    public static final double RADIUS=8;
    private static final Map<ServerLevel,List<Session>> ACTIVE=new WeakHashMap<>();
    private record Session(UUID id, Player owner, Object source, long start) {
        Vec3 center() {
            return source instanceof DollEntity e ? e.position()
                : Vec3.atBottomCenterOf(((DollDisplayBlockEntity)source).getBlockPos());
        }
        boolean valid(ServerLevel level) {
            if (!owner.isAlive() || owner.isRemoved() || owner.level()!=level) return false;
            return source instanceof DollEntity e ? e.isAlive() && !e.isRemoved()
                : source instanceof DollDisplayBlockEntity b && !b.isRemoved()
                  && level.hasChunkAt(b.getBlockPos())
                  && level.getBlockEntity(b.getBlockPos())==b;
        }
        DancePayload packet(boolean stop) {
            return new DancePayload(id,source instanceof DollEntity e?e.getUUID():new UUID(0,0),
                source instanceof DollDisplayBlockEntity b?b.getBlockPos():BlockPos.ZERO,center(),start,stop);
        }
    }
    public static boolean isDancing(Entity entity) {
        return entity.level() instanceof ServerLevel l && ACTIVE.getOrDefault(l,List.of()).stream()
            .anyMatch(s->s.source==entity && l.getGameTime()-s.start<DURATION);
    }
    public static boolean start(Player player, Object source) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) return false;
        Vec3 center;
        if (source instanceof DollEntity e) {
            if(e.level()!=level || !e.isAlive() || e.isRemoved() || e.storedCopy().isEmpty()
                || !player.getUUID().equals(e.ownerId())) return false;
            center=e.position();
        } else if(source instanceof DollDisplayBlockEntity b) {
            if(b.getLevel()!=level || b.isRemoved() || level.getBlockEntity(b.getBlockPos())!=b
                || b.copyDoll().isEmpty() || !DollOwnership.mayUse(b.copyDoll(),player)) return false;
            center=Vec3.atCenterOf(b.getBlockPos());
        } else return false;
        if (player.distanceToSqr(center)>36 || !level.getWorldBorder().isWithinBounds(BlockPos.containing(center))) return false;
        var sessions=ACTIVE.computeIfAbsent(level,k->new ArrayList<>());
        if(sessions.size()>=32 || sessions.stream().anyMatch(s->level.getGameTime()-s.start<DURATION
            && s.center().distanceToSqr(center)<RADIUS*RADIUS*4)) return false;
        var s=new Session(UUID.randomUUID(),player,source,level.getGameTime());
        sessions.add(s);
        if(source instanceof DollEntity e) e.getNavigation().stop();
        send(level,s,false);
        return true;
    }
    private static void send(ServerLevel l,Session s,boolean stop) {
        // Dimension-wide state allows late observers; clients only animate within 8 blocks of the source.
        PacketDistributor.sendToPlayersInDimension(l,s.packet(stop));
    }
    @SubscribeEvent public static void block(PlayerInteractEvent.LeftClickBlock e) {
        if(e.getLevel().getBlockEntity(e.getPos()) instanceof DollDisplayBlockEntity b) {
            e.setCanceled(true); // both sides: creative cannot destroy the doll
            if(e.getAction()==PlayerInteractEvent.LeftClickBlock.Action.START && !e.getLevel().isClientSide)
                start(e.getEntity(),b);
        }
    }
    @SubscribeEvent public static void attack(AttackEntityEvent e) {
        if(e.getTarget() instanceof DollEntity doll) {
            e.setCanceled(true);
            if(!e.getEntity().level().isClientSide) start(e.getEntity(),doll);
        }
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) {
        if(!(e.getLevel() instanceof ServerLevel l))return;
        var list=ACTIVE.get(l);if(list==null)return;
        for(var it=list.iterator();it.hasNext();) {
            var s=it.next();
            if(!s.valid(l)||l.getGameTime()-s.start>=DURATION) {send(l,s,true);it.remove();}
            else if(l.getGameTime()%20==0)send(l,s,false);
        }
        if(list.isEmpty())ACTIVE.remove(l);
    }
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) { ACTIVE.clear(); }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        for(var entry:ACTIVE.entrySet())entry.getValue().removeIf(s->{
            if(s.owner.getUUID().equals(e.getEntity().getUUID())) {send(entry.getKey(),s,true);return true;}
            return false;
        });
    }
}
