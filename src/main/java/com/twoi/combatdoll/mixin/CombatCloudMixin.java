package com.twoi.combatdoll.mixin;
import com.twoi.combatdoll.client.TwoIArcanaRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class CombatCloudMixin {
    @Inject(method="renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V",at=@At("HEAD"),cancellable=true)
    private void twoi$takeSky(CallbackInfo ci) { if(TwoIArcanaRenderer.skyTaken()) ci.cancel(); }
}
