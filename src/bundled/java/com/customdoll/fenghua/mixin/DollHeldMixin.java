package com.customdoll.fenghua.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.customdoll.fenghua.client.render.DollThirdPerson;
import com.customdoll.fenghua.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class DollHeldMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void fenghua$renderDoll(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
            HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!stack.is(ModItems.TWO_I_DOLL.get()) || !(entity instanceof AbstractClientPlayer player)) return;
        var parent = ((ItemInHandLayer<?, ?>)(Object)this).getParentModel();
        if (!(parent instanceof PlayerModel<?> model)) return;
        DollThirdPerson.render(stack, model, arm, player.getSkin().model() == PlayerSkin.Model.SLIM,
                pose, buffers, light, player);
        ci.cancel();
    }
}
