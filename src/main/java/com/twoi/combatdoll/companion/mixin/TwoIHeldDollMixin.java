package com.twoi.combatdoll.companion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.twoi.combatdoll.client.TwoIDollCarryMotion;
import com.twoi.combatdoll.client.TwoIDollThirdPerson;
import com.twoi.combatdoll.client.TwoIDollWeather;
import com.twoi.combatdoll.companion.init.ModItems;
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
    private void twoICompanion$renderDoll(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
            HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!stack.is(ModItems.TWO_I_DOLL.get()) || !(entity instanceof AbstractClientPlayer player)) return;
        var parent = ((ItemInHandLayer<?, ?>)(Object)this).getParentModel();
        if (!(parent instanceof PlayerModel<?> model)) return;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        TwoIDollThirdPerson.render(stack, model, arm, player.getSkin().model() == PlayerSkin.Model.SLIM,
                pose, buffers, light, TwoIDollCarryMotion.sample(player, partial),
                TwoIDollWeather.sample(player, player.level(), player.blockPosition(), partial, player.getUUID().hashCode()));
        ci.cancel();
    }
}
