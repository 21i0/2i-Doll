package com.twoi.combatdoll.mixin;

import com.twoi.combatdoll.client.TwoIDollThirdPerson;
import com.twoi.combatdoll.TwoICombatDollMod;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class TwoIPlayerArmMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void uselessStretcher$carryDoll(LivingEntity entity, float swing, float amount, float age,
                                           float yaw, float pitch, CallbackInfo ci) {
        var model = (PlayerModel<?>)(Object)this;
        if (entity.getMainHandItem().is(TwoICombatDollMod.DOLL_ITEM.get()))
            TwoIDollThirdPerson.poseArm(model, entity.getMainArm(), age);
        else if (entity.getMainHandItem().is(TwoICombatDollMod.BOOK.get()))
            TwoIDollThirdPerson.poseArm(model, entity.getMainArm(), age);
        else if (entity.getMainHandItem().is(TwoICombatDollMod.AXOLOTL.get()))
            TwoIDollThirdPerson.poseArm(model, entity.getMainArm(), age);
        if (entity.getOffhandItem().is(TwoICombatDollMod.DOLL_ITEM.get()))
            TwoIDollThirdPerson.poseArm(model, entity.getMainArm().getOpposite(), age);
        else if (entity.getOffhandItem().is(TwoICombatDollMod.BOOK.get()))
            TwoIDollThirdPerson.poseArm(model, entity.getMainArm().getOpposite(), age);
        else if (entity.getOffhandItem().is(TwoICombatDollMod.AXOLOTL.get()))
            TwoIDollThirdPerson.poseArm(model, entity.getMainArm().getOpposite(), age);
    }
}
