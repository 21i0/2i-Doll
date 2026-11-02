package com.twoi.combatdoll.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.PathfinderMob;
import com.twoi.combatdoll.combat.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.sorrowmist.useless.stretcher.client.render.TwoIDollRenderer", remap = false)
abstract class DollScaleMixin {
    @Inject(method="scale(Lcom/sorrowmist/useless/stretcher/content/entity/TwoIDollEntity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at=@At("TAIL"))
    private void twoi$elastic(@Coerce PathfinderMob doll, PoseStack pose, float partial, CallbackInfo ci) {
        float age = DollCombatAccess.state(doll).age(doll, partial);
        if (age < 0 || age >= TwoIArcanaTimeline.DURATION) return;
        float recoil = TwoIArcanaTimeline.impact(age), presence = TwoIArcanaTimeline.presence(age);
        float wave = (float)Math.sin(age * .12) * .025F * presence;
        pose.translate(0, (.15 + Math.sin(age * .065) * .035) * presence, 0);
        pose.scale(1 + recoil * .055F - wave/2, 1 - recoil * .075F + wave, 1 + recoil * .055F - wave/2);
    }
}
