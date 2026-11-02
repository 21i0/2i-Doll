package com.twoi.combatdoll.companion.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access only the real health key; never guess a synchronized-data numeric ID. */
@Mixin(LivingEntity.class)
public interface LivingHealthDataAccessor {
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> twoICompanion$healthKey() {
        throw new AssertionError("mixin accessor");
    }
}
