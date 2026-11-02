package com.twoi.combatdoll.mixin;
import com.twoi.combatdoll.combat.Chronostasis;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayer.class)
abstract class TimeStopPlayerMixin {
    @Inject(method="doTick",at=@At("HEAD"),cancellable=true)
    private void twoi$freeze(CallbackInfo ci){if(Chronostasis.frozen((ServerPlayer)(Object)this))ci.cancel();}
}
