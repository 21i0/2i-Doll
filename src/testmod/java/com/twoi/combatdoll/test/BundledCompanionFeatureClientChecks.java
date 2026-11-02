package com.twoi.combatdoll.test;

import com.customdoll.chuanshuo.client.DollLaughClient;
import com.customdoll.chuanshuo.client.render.DollRenderer;
import com.customdoll.chuanshuo.content.entity.DollEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;

/** Client-only probe of the integrated S2C laugh, actual sound source and original model. */
public final class BundledCompanionFeatureClientChecks {
    private static DollRenderer.Model model;
    private static boolean network,sound,belly,back,returned,done;
    private static int cleanupTicks;
    private BundledCompanionFeatureClientChecks() { }

    public static boolean tick(Minecraft client) {
        if(done)return true;
        if(model==null) {
            model=new DollRenderer.Model(client.getEntityModels().bakeLayer(DollRenderer.LAYER));
            for(boolean seated:new boolean[]{false,true})for(float time=0;time<=240;time+=.5F) {
                if(seated)model.setupDecoration(180);else model.setupStanding(0,0,0,0,0,0,0);
                model.applyLaugh(time);
                for(ModelPart part:new ModelPart[]{model.head,model.body,model.leftArm,model.rightArm,model.leftLeg,model.rightLeg})
                    check(Float.isFinite(part.xRot)&&Float.isFinite(part.yRot)&&Float.isFinite(part.zRot)&&Float.isFinite(part.y)&&part.yScale>0,"finite positive standing/seated performance pose");
                check(model.hat.xRot==model.head.xRot&&model.hat.yScale==model.head.yScale,"hat follows original head");
                check(model.leftSleeve.xRot==model.leftArm.xRot&&model.rightSleeve.zRot==model.rightArm.zRot,"sleeves follow laugh arms");
                check(model.leftPants.y==model.leftLeg.y&&model.rightPants.y==model.rightLeg.y,"trouser layers follow legs");
                check(model.footBottom(model.leftLeg)<=DollRenderer.FEET_Y+.002F&&model.footBottom(model.rightLeg)<=DollRenderer.FEET_Y+.002F,"original feet remain above support");
            }
            LogUtils.getLogger().info("BUNDLED CHUANSHUO MODEL POSE CHECKS PASSED: final integrated model sampled every half tick for the full standing/seated 240-tick duration, all skin layers and support anchors");
        }
        if(client.level!=null&&BundledCompanionFeatureChecks.liveDoll()!=null) {
            DollEntity source=null;
            for(var entity:client.level.entitiesForRendering())
                if(entity instanceof DollEntity doll&&entity.getUUID().equals(BundledCompanionFeatureChecks.liveDoll())){source=doll;break;}
            if(source!=null) {
                float elapsed=DollLaughClient.elapsed(source,0);
                if(elapsed>=0) {
                    network=true;
                    sound|=DollLaughClient.hasSound();
                    model.setupAnim(source,0,0,source.tickCount,0,0);
                    if(elapsed>=20&&elapsed<=85) {
                        check(model.head.xRot>.15F&&model.leftArm.xRot<-.4F&&model.rightArm.xRot<-.4F,"live network state applies belly pose");belly=true;
                    }
                    if(elapsed>=113&&elapsed<=119) {
                        check(model.head.xRot<0&&model.leftArm.xRot<-.9F,"live natural head-back peak applies original pose");back=true;
                    }
                    if(elapsed>=198&&elapsed<=220) {
                        check(model.head.xRot>.15F&&model.blink()>.5F,"live performance returns to belly and closed eyes");returned=true;
                    }
                }
            }
        }
        if(!BundledCompanionFeatureChecks.finished())return false;
        BundledCompanionFeatureChecks.assertPassed();
        check(network&&sound&&belly&&back&&returned,"actual S2C state, active sound and all three live model phases were observed: network="+network+" sound="+sound+" belly="+belly+" back="+back+" return="+returned);
        if(DollLaughClient.activeCount()!=0||DollLaughClient.hasSound()) {
            check(++cleanupTicks<20,"recalled/expired actor releases every client animation and sound source");return false;
        }
        done=true;
        LogUtils.getLogger().info("BUNDLED CHUANSHUO CLIENT FEATURE CHECKS PASSED: actual integrated S2C source and OpenAL sound; natural belly/head-back/return model phases; full-duration finite layer poses; expired/recalled performance and sound released");
        return true;
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError("Bundled companion client feature: "+why);}
}
