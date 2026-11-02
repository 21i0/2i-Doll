package com.twoi.combatdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.twoi.combatdoll.TwoICombatDollMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** First-person grimoire presentation: a small floating book above the supporting palm. */
@EventBusSubscriber(modid = TwoICombatDollMod.MODID, value = Dist.CLIENT)
public final class ArcanaBookHandRenderer {
    private ArcanaBookHandRenderer() { }

    @SubscribeEvent
    public static void render(RenderHandEvent event) {
        if (!event.getItemStack().is(TwoICombatDollMod.BOOK.get())) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float swing = net.minecraft.util.Mth.sin(net.minecraft.util.Mth.sqrt(event.getSwingProgress())
                * net.minecraft.util.Mth.PI);
        pose.translate(side * (0.34F - 0.05F * swing), -0.35F - 0.62F * event.getEquipProgress()
                + 0.04F * swing, -0.88F);
        pose.mulPose(Axis.YP.rotationDegrees(side * (5 - 8 * swing)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * -3 * swing));
        TwoIDollHandRenderer.renderBarePalm(arm, player.getSkin(),
                player.isModelPartShown(arm == HumanoidArm.RIGHT
                        ? net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE
                        : net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE),
                pose, event.getMultiBufferSource(), event.getPackedLight());
        ArcanaWeaponRenderer.renderFloatingBook(pose, event.getMultiBufferSource(), event.getPackedLight(), 0,
                TwoIDollItemRenderer.animationTime(), side, .14F);
        pose.popPose();
        event.setCanceled(true);
    }
}
