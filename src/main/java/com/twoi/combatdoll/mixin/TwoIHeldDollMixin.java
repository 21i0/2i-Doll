package com.twoi.combatdoll.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.twoi.combatdoll.client.ArcanaWeaponRenderer;
import com.twoi.combatdoll.client.TwoIDollItemRenderer;
import com.twoi.combatdoll.client.TwoIDollCarryMotion;
import com.twoi.combatdoll.client.TwoIDollThirdPerson;
import com.twoi.combatdoll.client.TwoIDollWeather;
import com.twoi.combatdoll.TwoICombatDollMod;
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
public abstract class TwoIHeldDollMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$renderDoll(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
            HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) return;
        var parent = ((ItemInHandLayer<?, ?>)(Object)this).getParentModel();
        if (!(parent instanceof PlayerModel<?> model)) return;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        if (stack.is(TwoICombatDollMod.AXOLOTL.get())) {
            pose.pushPose();
            TwoIDollThirdPerson.transformToPalm(arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm, arm,
                    player.getSkin().model() == PlayerSkin.Model.SLIM, pose);
            pose.translate(0,-.025F,-.055F);
            pose.scale(.29F,.29F,.29F);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            com.twoi.combatdoll.client.CangshengAxolotlItemRenderer.renderPet(pose,buffers,light,
                    TwoIDollItemRenderer.animationTime(),TwoIDollCarryMotion.sample(player,partial));
            pose.popPose();ci.cancel();return;
        }
        if (stack.is(TwoICombatDollMod.BOOK.get())) {
            pose.pushPose();
            TwoIDollThirdPerson.transformToPalm(arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm, arm,
                    player.getSkin().model() == PlayerSkin.Model.SLIM, pose);
            ArcanaWeaponRenderer.renderFloatingBook(pose, buffers, light, 0,
                    TwoIDollItemRenderer.animationTime(), arm == HumanoidArm.RIGHT ? 1 : -1, -.14F);
            pose.popPose();
            ci.cancel();
            return;
        }
        if (!stack.is(TwoICombatDollMod.DOLL_ITEM.get())) return;
        TwoIDollThirdPerson.render(stack, model, arm, player.getSkin().model() == PlayerSkin.Model.SLIM,
                pose, buffers, light, TwoIDollCarryMotion.sample(player, partial),
                TwoIDollWeather.sample(player, player.level(), player.blockPosition(), partial, player.getUUID().hashCode()));
        ci.cancel();
    }
}
