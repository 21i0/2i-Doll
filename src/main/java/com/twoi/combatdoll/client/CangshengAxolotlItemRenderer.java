package com.twoi.combatdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;

/** A breathing, blinking animal on the palm; no tick writes or item identity churn. */
public final class CangshengAxolotlItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static CangshengAxolotlItemRenderer instance;
    private CangshengAxolotlItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    public static CangshengAxolotlItemRenderer get(){if(instance==null)instance=new CangshengAxolotlItemRenderer();return instance;}
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        pose.pushPose();pose.translate(.5,.20,.5);
        if(context==ItemDisplayContext.GUI){
            pose.translate(0,-.08F,0);pose.mulPose(Axis.YP.rotationDegrees(145));pose.mulPose(Axis.XP.rotationDegrees(18));pose.scale(.40F,.40F,.40F);
        }else if(context==ItemDisplayContext.GROUND){pose.translate(0,-.15F,0);pose.scale(.30F,.30F,.30F);}
        else if(context==ItemDisplayContext.FIXED){pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(.38F,.38F,.38F);}
        else if(context==ItemDisplayContext.HEAD){pose.scale(.42F,.42F,.42F);}
        else {pose.mulPose(Axis.XP.rotationDegrees(90));pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(.45F,.45F,.45F);}
        renderPet(pose,buffers,light,TwoIDollItemRenderer.animationTime(),0);
        pose.popPose();
    }
    /** Call at the hand surface, +Y up. */
    public static void renderPet(PoseStack pose,MultiBufferSource buffers,int light,float time,float motion){
        renderPet(pose,buffers,light,time,new TwoIDollCarryMotion.Pose(0,0,0,0,0,motion));
    }
    public static void renderPet(PoseStack pose,MultiBufferSource buffers,int light,float time,TwoIDollCarryMotion.Pose motion){
        pose.pushPose();
        motion.apply(pose);
        pose.translate(0,Mth.sin(time*.08F)*.007F,0);
        pose.mulPose(Axis.ZP.rotation(Mth.sin(time*.051F)*.025F));
        CangshengAxolotlModel.render(pose,buffers,light,time,Math.min(.6F,Math.abs(motion.speed())),motion.airborne()*.12F,0,0,1+motion.falling()*.7F);
        pose.popPose();
    }
}
