package com.twoi.combatdoll.mixin;
import com.twoi.combatdoll.combat.Chronostasis;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
abstract class TimeStopMovementMixin {
    @Shadow public ServerPlayer player;
    @Inject(method={"handleMovePlayer","handleMoveVehicle","handlePlayerAction","handleUseItem","handleUseItemOn","handleInteract","handleContainerClick"},at=@At("HEAD"),cancellable=true)
    private void twoi$hold(CallbackInfo ci){if(player.getServer().isSameThread()&&Chronostasis.frozen(player))ci.cancel();}
}
