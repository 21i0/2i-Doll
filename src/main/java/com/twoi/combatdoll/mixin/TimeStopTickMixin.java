package com.twoi.combatdoll.mixin;
import com.twoi.combatdoll.combat.Chronostasis;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(TickRateManager.class)
abstract class TimeStopTickMixin {
    @Inject(method="isEntityFrozen",at=@At("HEAD"),cancellable=true)
    private void twoi$freeze(Entity entity,CallbackInfoReturnable<Boolean> cir){
        if(Chronostasis.frozen(entity))cir.setReturnValue(true);else if(Chronostasis.exempt(entity))cir.setReturnValue(false);
    }
}
