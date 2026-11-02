package com.twoi.combatdoll.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.sorrowmist.useless.stretcher.client.render.TwoIStarfieldEffects", remap = false)
public interface StarfieldClockAccessor {
    @Accessor("showEnd") static long twoi$showEnd() { throw new AssertionError(); }
}
