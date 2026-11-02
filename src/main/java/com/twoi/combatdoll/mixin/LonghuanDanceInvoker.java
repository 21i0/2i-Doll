package com.twoi.combatdoll.mixin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;
@Pseudo
@Mixin(targets="com.customdoll.longhuan.content.DollDance",remap=false)
public interface LonghuanDanceInvoker {
    @Invoker("isDancing") static boolean twoi$dancing(Entity entity){throw new AssertionError();}
}
