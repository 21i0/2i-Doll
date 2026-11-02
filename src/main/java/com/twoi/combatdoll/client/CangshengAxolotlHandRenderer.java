package com.twoi.combatdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.twoi.combatdoll.TwoICombatDollMod;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Low relaxed palm, with swap/swing impulses kept in the render transform. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID,value=Dist.CLIENT)
public final class CangshengAxolotlHandRenderer {
    private CangshengAxolotlHandRenderer() { }
    @SubscribeEvent public static void render(RenderHandEvent event){
        if(!event.getItemStack().is(TwoICombatDollMod.AXOLOTL.get()))return;
        var player=net.minecraft.client.Minecraft.getInstance().player;if(player==null)return;
        HumanoidArm arm=event.getHand()==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();
        int side=arm==HumanoidArm.RIGHT?1:-1;
        float swing=net.minecraft.util.Mth.sin(net.minecraft.util.Mth.sqrt(event.getSwingProgress())*net.minecraft.util.Mth.PI);
        PoseStack pose=event.getPoseStack();pose.pushPose();
        pose.translate(side*(.34F-.05F*swing),-.40F-.56F*event.getEquipProgress()+.04F*swing,-.82F);
        pose.mulPose(Axis.YP.rotationDegrees(side*(4-10*swing)));
        pose.mulPose(Axis.ZP.rotationDegrees(side*(-4*swing)));
        TwoIDollHandRenderer.renderBarePalm(arm,player.getSkin(),player.isModelPartShown(arm==HumanoidArm.RIGHT?PlayerModelPart.RIGHT_SLEEVE:PlayerModelPart.LEFT_SLEEVE),pose,event.getMultiBufferSource(),event.getPackedLight());
        pose.translate(0,.025F,-.07F);pose.mulPose(Axis.YP.rotationDegrees(142+side*9));
        pose.mulPose(Axis.XP.rotationDegrees(swing*-8));pose.scale(.24F,.24F,.24F);
        CangshengAxolotlItemRenderer.renderPet(pose,event.getMultiBufferSource(),event.getPackedLight(),TwoIDollItemRenderer.animationTime(),TwoIDollCarryMotion.sample(player,event.getPartialTick()));
        pose.popPose();event.setCanceled(true);
    }
}
