package com.twoi.combatdoll.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.twoi.combatdoll.item.ArcanaWeaponItem;
import com.twoi.combatdoll.combat.WeaponKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;

public final class ArcanaWeaponRenderer extends BlockEntityWithoutLevelRenderer {
    private static ArcanaWeaponRenderer instance;
    private ArcanaWeaponRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    public static ArcanaWeaponRenderer get(){if(instance==null)instance=new ArcanaWeaponRenderer();return instance;}
    public static void renderFloatingBook(PoseStack pose,MultiBufferSource buffers,int light,int overlay,float time,int side,float lift){
        pose.pushPose();
        pose.translate(0,lift+(float)Math.sin(time*.08F)*.018F,.025F);
        pose.mulPose(Axis.YP.rotationDegrees(side*7F));
        pose.mulPose(Axis.XP.rotationDegrees(-8F));
        pose.scale(.54F,.54F,.54F);
        var mesh=new TwoIArcanaRenderer.Mesh(pose,buffers.getBuffer(RenderType.entityTranslucentEmissive(TwoIArcanaRenderer.SCARLET_ATLAS,false)),true);
        mesh.book(time,1,1,.12F);
        pose.popPose();
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var kind=((ArcanaWeaponItem)stack.getItem()).kind();float time=TwoIDollItemRenderer.animationTime();
        if(kind==WeaponKind.STAFF){
            pose.pushPose();pose.translate(.5,.47,.5);
            if(context==ItemDisplayContext.GUI){
                pose.translate(0,.08,0);
                pose.mulPose(Axis.YP.rotationDegrees(-24));pose.mulPose(Axis.XP.rotationDegrees(8));
                pose.mulPose(Axis.ZP.rotationDegrees(-13));pose.scale(.315F,.315F,.315F);
            }else if(context.firstPerson()){
                pose.translate(.04,-.19,-.02);pose.mulPose(Axis.YP.rotationDegrees(158));
                pose.mulPose(Axis.XP.rotationDegrees(-10));pose.mulPose(Axis.ZP.rotationDegrees(-11));
                pose.scale(.52F,.52F,.52F);
            }else{
                pose.translate(0,-.10,0);pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.mulPose(Axis.XP.rotationDegrees(-10));pose.scale(.43F,.43F,.43F);
            }
            AstralEdictModel.render(pose,buffers.getBuffer(AstralEdictModel.TYPE),time,1,1);
            pose.popPose();return;
        }
        pose.pushPose();pose.translate(.5,.5,.5);
        if(context==ItemDisplayContext.GUI){pose.mulPose(Axis.YP.rotationDegrees(-25));pose.mulPose(Axis.XP.rotationDegrees(12));pose.scale(.82F,.82F,.82F);}
        else if(context.firstPerson()){pose.translate(kind==WeaponKind.BOOK?-.1:.05,-.09,0);pose.mulPose(Axis.YP.rotationDegrees(180));pose.mulPose(Axis.XP.rotationDegrees(-12));pose.scale(.9F,.9F,.9F);}
        else {pose.scale(.6F,.6F,.6F);pose.mulPose(Axis.YP.rotationDegrees(180));}
        boolean book=kind==WeaponKind.BOOK;
        var mesh=new TwoIArcanaRenderer.Mesh(pose,buffers.getBuffer(RenderType.entityTranslucentEmissive(book?TwoIArcanaRenderer.SCARLET_ATLAS:TwoIArcanaRenderer.ATLAS,false)),book);
        mesh.book(time,1,1,.04F);
        pose.popPose();
    }
}
