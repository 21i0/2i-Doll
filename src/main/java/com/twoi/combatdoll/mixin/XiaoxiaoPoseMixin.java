package com.twoi.combatdoll.mixin;

import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional visual bridge: the real passenger sits instead of walking through its mount. */
@Pseudo
@Mixin(targets="com.customdoll.xiaoxiao.client.render.DollRenderer$Model",remap=false)
abstract class XiaoxiaoPoseMixin extends PlayerModel<PathfinderMob> {
    XiaoxiaoPoseMixin(ModelPart root,boolean slim){super(root,slim);}
    @Shadow private void copySkinLayers(){throw new AssertionError();}
    @Shadow private boolean umbrellaPosed;

    @Inject(method="setupAnim(Lcom/customdoll/xiaoxiao/content/entity/DollEntity;FFFFF)V",at=@At("TAIL"))
    private void twoi$mounted(@Coerce PathfinderMob actor,float swing,float amount,float time,float yaw,float pitch,CallbackInfo ci){
        if(!(actor.getVehicle() instanceof CangshengAxolotlEntity mount)||!mount.mountMode())return;
        // The inherited model forcibly resets riding=false in every setupStanding call.
        // Put the pelvis at the passenger origin, fold both legs, and remove the
        // standing foot plant. Vertical travel comes solely from the actual mount.
        float breath=Mth.sin(time*.085F)*.035F;
        body.y=head.y=14.82F+breath;body.x=0;body.zRot=0;head.x=0;
        leftArm.y=rightArm.y=16.22F+breath;
        leftLeg.y=rightLeg.y=22.30F;
        leftLeg.z=rightLeg.z=-1.50F;
        leftLeg.x=2.60F;rightLeg.x=-2.60F;
        leftLeg.xRot=rightLeg.xRot=-1.43F;
        leftLeg.yRot=-.30F;rightLeg.yRot=.30F;
        leftLeg.zRot=-.06F;rightLeg.zRot=.06F;
        if(!umbrellaPosed){
            float sway=Mth.sin(time*.15F)*.025F;
            leftArm.xRot=-.76F+sway;rightArm.xRot=-.76F-sway;
            leftArm.yRot=.12F;rightArm.yRot=-.12F;
            leftArm.zRot=-.16F;rightArm.zRot=.16F;
        }
        copySkinLayers();
    }
}
