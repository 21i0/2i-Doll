package com.twoi.combatdoll.api;
import net.minecraft.resources.ResourceLocation;

/** A doll identity is separate from the shared entity, item persistence, combat and rendering infrastructure. */
public record DollDefinition(ResourceLocation id, ResourceLocation skin, float damage, int targetLimit) {
    public DollDefinition {
        if (damage<0 || !Float.isFinite(damage) || targetLimit<1 || targetLimit>32) throw new IllegalArgumentException("Invalid doll combat profile");
    }
}
