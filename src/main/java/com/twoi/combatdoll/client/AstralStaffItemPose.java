package com.twoi.combatdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemDisplayContext;

/** Stable item poses. The staff's animated ornaments never move its physical grip. */
public final class AstralStaffItemPose {
    // Authored grip band [-.35, -.18], stretched below the .30 head socket by 1.45.
    public static final float GRIP_Y = .30F + (-.265F - .30F) * 1.45F;
    public static final float GROUND_SCALE = .35F;

    private AstralStaffItemPose() { }

    public static void apply(PoseStack pose, ItemDisplayContext context) {
        pose.translate(.5, .47, .5);
        if (context == ItemDisplayContext.GUI) {
            pose.translate(0, .08, 0);
            pose.mulPose(Axis.YP.rotationDegrees(-24));
            pose.mulPose(Axis.XP.rotationDegrees(8));
            pose.mulPose(Axis.ZP.rotationDegrees(-13));
            pose.scale(.315F, .315F, .315F);
        } else if (context.firstPerson()) {
            int side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -1 : 1;
            pose.translate(side * .04, -.19, -.02);
            pose.mulPose(Axis.YP.rotationDegrees(side * 158));
            pose.mulPose(Axis.XP.rotationDegrees(-10));
            pose.mulPose(Axis.ZP.rotationDegrees(side * -11));
            pose.scale(.52F, .52F, .52F);
            pose.translate(0, -GRIP_Y, 0);
        } else if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            pose.translate(0, .03, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            // Vanilla's hand layer already applies X -90 / Y 180. After the
            // character coordinate flip this keeps the staff head above the grip.
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(.43F, .43F, .43F);
            pose.translate(0, -GRIP_Y, 0);
        } else if (context == ItemDisplayContext.GROUND) {
            // Lay the long shaft flat. Vanilla contributes at least .25 blocks
            // of ground lift with this model's identity GROUND transform.
            // Its full animated depth is <= .54, so .35 scale leaves clearance.
            pose.translate(0, .03, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(GROUND_SCALE, GROUND_SCALE, GROUND_SCALE);
        } else {
            // Item frames, head and context-free previews retain their old pose.
            pose.translate(0, -.10, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.mulPose(Axis.XP.rotationDegrees(-10));
            pose.scale(.43F, .43F, .43F);
        }
    }
}
