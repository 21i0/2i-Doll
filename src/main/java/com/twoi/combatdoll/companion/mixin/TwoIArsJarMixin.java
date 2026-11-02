package com.twoi.combatdoll.companion.mixin;

import com.twoi.combatdoll.companion.content.entity.TwoIDollEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.common.block.tile.MobJarTile", remap = false)
public abstract class TwoIArsJarMixin {
    @Inject(method = "setEntityData", at = @At("HEAD"), cancellable = true)
    private void twoICompanion$rejectCapture(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof TwoIDollEntity) cir.setReturnValue(false);
    }

    @Inject(method = "writeSimple", at = @At("HEAD"), cancellable = true)
    private void twoICompanion$rejectSimpleCapture(Entity entity, CallbackInfo ci) {
        if (entity instanceof TwoIDollEntity) ci.cancel();
    }

    /** Old jars must not yield new dolls when dispelled, or run behaviors on cached copies. */
    @Inject(method = "loadEntityFromTag", at = @At("HEAD"), cancellable = true)
    private static void twoICompanion$rejectStoredDoll(Level level, CompoundTag tag,
                                                        CallbackInfoReturnable<Entity> cir) {
        if (com.twoi.combatdoll.companion.content.entity.TwoICaptureProtection.containsDoll(tag))
            cir.setReturnValue(null);
    }
}
