package com.twoi.combatdoll.entity;

import com.twoi.combatdoll.companion.event.StandaloneDollLeafDrops;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Compatibility entry points; only StandaloneDollLeafDrops subscribes to the event bus. */
public final class DollLeafRewards {
    private DollLeafRewards() {}

    public static void leaves(BlockDropsEvent event) {
        StandaloneDollLeafDrops.drops(event);
    }

    public static void pending(ServerTickEvent.Post event) {
        StandaloneDollLeafDrops.tick(event);
    }
}
