package com.twoi.combatdoll.mixin;

import net.minecraft.world.entity.PathfinderMob;
import com.twoi.combatdoll.combat.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.sorrowmist.useless.stretcher.client.render.TwoIDollRenderer$Model", remap = false)
abstract class DollPoseMixin extends PlayerModel<PathfinderMob> {
    DollPoseMixin(ModelPart root, boolean slim) { super(root, slim); }
    @Shadow private void moveTorso(float dy) { throw new AssertionError(); }
    @Shadow private void copySkinLayers() { throw new AssertionError(); }
    @Inject(method="setupAnim(Lcom/sorrowmist/useless/stretcher/content/entity/TwoIDollEntity;FFFFF)V", at=@At(value="INVOKE", target="Lcom/sorrowmist/useless/stretcher/client/render/TwoIDollRenderer$Model;applyWeather(Lcom/sorrowmist/useless/stretcher/client/render/TwoIDollWeather$Pose;F)V"))
    private void twoi$pose(@Coerce PathfinderMob doll, float swing, float amount, float time, float yaw, float pitch, CallbackInfo ci) {
        float age = DollCombatAccess.state(doll).age(doll, time - doll.tickCount);
        if (age < 0 || age >= TwoIArcanaTimeline.DURATION) return;
        float p = TwoIArcanaTimeline.presence(age), hit = TwoIArcanaTimeline.impact(age);
        leftArm.xRot = Mth.lerp(p, leftArm.xRot, -1.23F + Mth.sin(time*.08F)*.045F);
        rightArm.xRot = Mth.lerp(p, rightArm.xRot, -1.18F - hit*.45F);
        leftArm.yRot=.22F*p; rightArm.yRot=-.22F*p;
        leftArm.zRot=-.13F*p; rightArm.zRot=.13F*p;
        head.xRot=Mth.lerp(p,head.xRot,.12F-hit*.18F); head.zRot+=Mth.sin(time*.04F)*.035F*p;
        moveTorso(-.3F*p + Mth.sin(time*.12F)*.10F*p); copySkinLayers();
    }
}
