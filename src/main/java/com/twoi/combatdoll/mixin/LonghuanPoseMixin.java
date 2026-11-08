package com.twoi.combatdoll.mixin;
import com.twoi.combatdoll.combat.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.customdoll.longhuan.client.render.DollRenderer$Model",remap=false)
abstract class LonghuanPoseMixin extends PlayerModel<PathfinderMob> {
    LonghuanPoseMixin(ModelPart root,boolean slim){super(root,slim);}
    @Shadow private void copySkinLayers(){throw new AssertionError();}
    @Shadow public abstract void setupBookRiding(float time,float yaw,float pitch,float speed,boolean animateUpper);
    @Inject(method="setupAnim(Lcom/customdoll/longhuan/content/entity/DollEntity;FFFFF)V",at=@At("TAIL"))
    private void twoi$reading(@Coerce PathfinderMob actor,float swing,float amount,float time,float yaw,float pitch,CallbackInfo ci){
        float partial=net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        if(LonghuanBookFlightGoal.riding(actor)) {
            boolean dancing=com.customdoll.longhuan.client.DollDanceClient.elapsed(actor,partial)>=0;
            boolean gesturing=((com.customdoll.longhuan.content.entity.DollEntity)(Object)actor).getPoseAction()!=0;
            setupBookRiding(time,yaw,pitch,(float)actor.getDeltaMovement().horizontalDistance()*3,!dancing&&!gesturing);
        }
        float age=CombatActors.state(actor).age(actor,partial);
        if(age<0||age>=TwoIArcanaTimeline.DURATION)return;
        float p=TwoIArcanaTimeline.presence(age),hit=TwoIArcanaTimeline.impact(age);
        leftArm.xRot=Mth.lerp(p,leftArm.xRot,-1.15F);rightArm.xRot=Mth.lerp(p,rightArm.xRot,-1.08F-hit*.25F);
        leftArm.yRot=.25F*p;rightArm.yRot=-.25F*p;leftArm.zRot=-.12F*p;rightArm.zRot=.12F*p;
        head.xRot=Mth.lerp(p,head.xRot,.12F);head.zRot+=Mth.sin(age*.07F)*.025F*p;copySkinLayers();
    }
}
