package com.twoi.combatdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The same living pet expands into a mount or a crystal-backed titan. */
public final class CangshengAxolotlRenderer extends EntityRenderer<CangshengAxolotlEntity> {
    public CangshengAxolotlRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.55F;}
    @Override public ResourceLocation getTextureLocation(CangshengAxolotlEntity entity){return CangshengAxolotlModel.TEXTURE;}
    @Override public void render(CangshengAxolotlEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        // The dream scene owns the whole attack choreography. Keep the real
        // persistent carrier, but do not draw a second titan below that scene.
        if(!entity.mountMode()&&entity.isAttacking())return;
        shadowRadius=entity.mountMode()?.55F:8F;
        pose.pushPose();
        float emerging=entity.emerging()?Mth.clamp(entity.animationAge(partial)/40,0,1):1;
        float recalling=entity.recalling()?Mth.clamp(entity.animationAge(partial)/40,0,1):0;
        if(recalling>0&&entity.ownerId()!=null){
            var owner=entity.level().getPlayerByUUID(entity.ownerId());
            if(owner!=null){
                float toward=(float)(Mth.atan2(owner.getZ()-entity.getZ(),owner.getX()-entity.getX())*180/Math.PI)-90;
                yaw=Mth.rotLerp(Math.min(1,recalling*2),yaw,toward);
            }
        }
        pose.mulPose(Axis.YP.rotationDegrees(180-yaw));
        // Client model animation only: authoritative riding and attack coordinates are untouched.
        float time=entity.tickCount+partial;
        float motion=Mth.clamp((float)entity.getDeltaMovement().horizontalDistance()*6,0,1);
        float hop=entity.onGround()?Mth.abs(Mth.sin(time*.30F))*motion:Math.min(.8F,(float)Math.abs(entity.getDeltaMovement().y)*1.5F);
        // Synced dynamic hit boxes may arrive a tick after initial spawn.
        float size=entity.mountMode()?1F:16F;
        if(entity.emerging()){
            float p=emerging;
            float ease=p*p*(3-2*p);size*=.035F+.965F*ease;
            pose.translate(0,Mth.sin(p*Mth.PI)*.16F,0);
        }
        if(entity.recalling()){
            float p=recalling;
            // First look at the owner and collect all four feet and tail;
            // only then return to the small handheld living form.
            float shrink=Mth.clamp((p-.30F)/.70F,0,1),ease=shrink*shrink*(3-2*shrink);
            size*=Math.max(.035F,1-ease);
            pose.translate(0,ease*entity.getBbHeight()*.7F,0);
        }
        pose.scale(size,size,size);
        float attack=entity.isAttacking()?Mth.clamp(entity.attackAge(partial)/220F,0,1):0;
        float roar=entity.isAttacking()?Mth.sin(Mth.clamp((entity.attackAge(partial)-55)/45,0,1)*Mth.PI):0;
        CangshengAxolotlModel.renderLiving(pose,buffers,light,time,motion,hop,attack,roar,0,emerging,recalling);
        pose.popPose();
        super.render(entity,yaw,partial,pose,buffers,light);
    }
    @Override public boolean shouldRender(CangshengAxolotlEntity entity,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){
        return frustum.isVisible(entity.getBoundingBox().inflate(entity.mountMode()?2:48));
    }
}
