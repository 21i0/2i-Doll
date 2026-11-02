package com.customdoll.longhuan.mixin;
import com.customdoll.longhuan.client.DollDanceClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public abstract class DollDanceLivingMixin {
    @Inject(method="setupRotations",at=@At("RETURN"))
    private void longhuan$dance(LivingEntity entity,PoseStack pose,float bob,float yaw,float partial,float scale,CallbackInfo ci) {
        if(!(entity instanceof com.customdoll.longhuan.content.entity.DollEntity))
            DollDanceClient.animateMob(entity,pose,partial);
    }
}
