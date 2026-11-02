package com.customdoll.chuanshuo.client;

import com.customdoll.chuanshuo.DollMod;
import com.customdoll.chuanshuo.content.DollLaugh;
import com.customdoll.chuanshuo.content.LaughPayload;
import com.customdoll.chuanshuo.content.blockentity.DollDisplayBlockEntity;
import com.customdoll.chuanshuo.content.entity.DollEntity;
import com.customdoll.chuanshuo.init.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

@EventBusSubscriber(modid = DollMod.MODID, value = Dist.CLIENT)
public final class DollLaughClient {
    private static final Map<UUID, Performance> ACTIVE = new LinkedHashMap<>();
    private static ClientLevel world;
    private static int renderedLaughFrames;
    private static final class Performance {
        LaughPayload state;
        Vec3 center;
        LaughSound sound;
        boolean resolved;
        Performance(LaughPayload p) { state = p; center = p.center(); }
        boolean entitySource() { return state.entityId() >= 0; }
        double elapsed(float partial) { return world.getGameTime() + partial - state.start(); }
        boolean resolve() {
            if (entitySource()) {
                var entity = world.getEntity(state.entityId());
                if (!(entity instanceof DollEntity doll) || !entity.getUUID().equals(state.entity())
                        || !doll.isAlive() || doll.isRemoved()) return false;
                center = doll.position(); return true;
            }
            center = state.center();
            return world.hasChunkAt(state.block())
                    && world.getBlockEntity(state.block()) instanceof DollDisplayBlockEntity b
                    && !b.isRemoved() && !b.copyDoll().isEmpty();
        }
    }
    public static void receive(LaughPayload p) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        if (world != client.level) { clear(); world = client.level; }
        if (p.stop()) { remove(p.id()); return; }
        var old = ACTIVE.get(p.id());
        if (old != null) { old.state = p; return; }
        double age = world.getGameTime() - p.start();
        if (age < -4 || age >= DollLaugh.DURATION || ACTIVE.size() >= 32
                || !Double.isFinite(p.center().x) || !Double.isFinite(p.center().y) || !Double.isFinite(p.center().z)) return;
        var s = new Performance(p); s.resolved = s.resolve(); ACTIVE.put(p.id(), s);
        // Vanilla streams cannot seek. Late observers see the correct pose without replaying an out-of-sync clip.
        if (age >= 0 && age <= 6 && s.resolved) { s.sound = new LaughSound(s); client.getSoundManager().play(s.sound); }
    }
    private static void remove(UUID id) {
        var s = ACTIVE.remove(id);
        if (s != null && s.sound != null) Minecraft.getInstance().getSoundManager().stop(s.sound);
    }
    private static void clear() {
        for (var s : ACTIVE.values()) if (s.sound != null) Minecraft.getInstance().getSoundManager().stop(s.sound);
        ACTIVE.clear(); world = null;
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var c = Minecraft.getInstance();
        if (c.level != world) { clear(); world = c.level; }
        if (world == null) return;
        for (var it = ACTIVE.values().iterator(); it.hasNext();) {
            var s = it.next(); boolean wasResolved = s.resolved; s.resolved = s.resolve();
            if (s.elapsed(0) >= DollLaugh.DURATION || s.elapsed(0) < -4 || wasResolved && !s.resolved) {
                if (s.sound != null) c.getSoundManager().stop(s.sound); it.remove();
            } else if (s.sound == null && s.resolved && s.elapsed(0) >= 0 && s.elapsed(0) <= 6) {
                s.sound = new LaughSound(s); c.getSoundManager().play(s.sound);
            }
        }
    }
    public static float elapsed(DollEntity doll, float partial) {
        if (world == null || doll.level() != world || !doll.isAlive()) return -1;
        for (var s : ACTIVE.values()) if (s.resolved && s.entitySource()
                && s.state.entityId() == doll.getId() && s.state.entity().equals(doll.getUUID())) return time(s, partial);
        return -1;
    }
    public static float elapsed(DollDisplayBlockEntity doll, float partial) {
        if (world == null || doll.getLevel() != world) return -1;
        for (var s : ACTIVE.values()) if (s.resolved && !s.entitySource()
                && s.state.block().equals(doll.getBlockPos())) return time(s, partial);
        return -1;
    }
    private static float time(Performance p, float partial) {
        double t = p.elapsed(partial); return t >= 0 && t < DollLaugh.DURATION ? (float)t : -1;
    }
    public static void rendered() { renderedLaughFrames++; }
    public static int renderedLaughFrames() { return renderedLaughFrames; }
    public static int activeCount() { return ACTIVE.size(); }
    public static boolean hasSound() {
        return ACTIVE.values().stream().anyMatch(s -> s.sound != null
                && Minecraft.getInstance().getSoundManager().isActive(s.sound));
    }
    private static final class LaughSound extends AbstractTickableSoundInstance {
        private final Performance performance;
        LaughSound(Performance p) {
            super(ModEntities.LAUGH.get(), SoundSource.NEUTRAL, net.minecraft.util.RandomSource.create());
            performance = p; volume = .85F; pitch = 1; looping = false;
            x = p.center.x; y = p.center.y + .3; z = p.center.z;
        }
        @Override public void tick() {
            if (world == null || !ACTIVE.containsValue(performance) || !performance.resolved
                    || performance.elapsed(0) >= DollLaugh.DURATION) { stop(); return; }
            x = performance.center.x; y = performance.center.y + .3; z = performance.center.z;
        }
    }
}
