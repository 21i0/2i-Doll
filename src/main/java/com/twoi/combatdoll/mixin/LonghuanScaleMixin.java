package com.twoi.combatdoll.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.twoi.combatdoll.combat.*;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.customdoll.longhuan.client.render.DollRenderer",remap=false)
abstract class LonghuanScaleMixin {
    @Inject(method="scale(Lcom/customdoll/longhuan/content/entity/DollEntity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",at=@At("TAIL"))
    private void twoi$hover(@Coerce PathfinderMob actor,PoseStack pose,float partial,CallbackInfo ci){
        float age=CombatActors.state(actor).age(actor,partial);if(age<0||age>=TwoIArcanaTimeline.DURATION)return;
        float p=TwoIArcanaTimeline.presence(age),hit=TwoIArcanaTimeline.impact(age);
        pose.translate(0,(.12+Math.sin(age*.065)*.025)*p,0);pose.scale(1+hit*.035F,1-hit*.05F,1+hit*.035F);
    }
}
