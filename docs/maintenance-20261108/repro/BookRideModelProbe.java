import com.customdoll.longhuan.client.render.DollRenderer;
import net.minecraft.client.model.geom.ModelPart;
import java.nio.file.*;
import java.util.*;

/** Real baked production doll rig, no Minecraft process, world or graphics device. */
public final class BookRideModelProbe {
    static long checks;
    static void check(boolean okay,String message) { checks++;if(!okay)throw new AssertionError(message); }
    static void near(float a,float b,String label) {check(Math.abs(a-b)<.00001F,label+" "+a+" != "+b);}
    static void skin(ModelPart base,ModelPart overlay,String label) {
        near(base.x,overlay.x,label+" x");near(base.y,overlay.y,label+" y");near(base.z,overlay.z,label+" z");
        near(base.xRot,overlay.xRot,label+" xRot");near(base.yRot,overlay.yRot,label+" yRot");near(base.zRot,overlay.zRot,label+" zRot");
        near(base.xScale,overlay.xScale,label+" xScale");near(base.yScale,overlay.yScale,label+" yScale");near(base.zScale,overlay.zScale,label+" zScale");
    }
    public static void main(String[] args)throws Exception {
        var model=new DollRenderer.Model(DollRenderer.Model.createLayer().bakeRoot());
        var other=new DollRenderer.Model(DollRenderer.Model.createLayer().bakeRoot());
        for(int frame=0;frame<12000;frame++) {
            float t=frame*.25F,yaw=(float)Math.sin(t*.023)*100,pitch=(float)Math.cos(t*.017)*75;
            float speed=(frame%101)/100F;
            model.setupStanding(t*.3F,speed,t,yaw,pitch,0,0);
            float blink=model.blink();
            model.setupBookRiding(t,yaw,pitch,speed,true);
            near(model.body.y,14.8125F,"seated torso");
            near(model.leftArm.xScale,.5625F,"left thin arm");near(model.rightArm.xScale,.5625F,"right thin arm");
            check(model.footBottom(model.leftLeg)<=24.00001F,"left foot below seat");
            check(model.footBottom(model.rightLeg)<=24.00001F,"right foot below seat");
            check(model.head.yRot>=-.72001F&&model.head.yRot<=.72001F,"owner look yaw bounds");
            check(Float.isFinite(model.head.xRot)&&Float.isFinite(model.body.zRot),"finite pose");
            near(model.blink(),blink,"preserved blink");
            skin(model.leftArm,model.leftSleeve,"left sleeve");skin(model.rightArm,model.rightSleeve,"right sleeve");
            skin(model.leftLeg,model.leftPants,"left pants");skin(model.rightLeg,model.rightPants,"right pants");
            skin(model.body,model.jacket,"jacket");skin(model.head,model.hat,"hat");
            // Upper gestures/dance keep their original angles when requested.
            other.setupStanding(0,0,t,yaw,pitch,3,.5F);
            model.setupStanding(0,0,t,yaw,pitch,3,.5F);
            model.setupBookRiding(t,yaw,pitch,speed,false);
            near(model.rightArm.xRot,other.rightArm.xRot,"native pat gesture");
            near(model.head.zRot,other.head.zRot,"native head gesture");
            // A real rain grip keeps its palm transform when switching lower body.
            model.setupStanding(0,0,t,0,0,0,0);model.umbrellaGrip(1);
            float ax=model.rightArm.xRot,ay=model.rightArm.yRot,az=model.rightArm.zRot;
            model.setupBookRiding(t,0,0,speed,true);
            near(model.rightArm.xRot,ax,"umbrella x grip");near(model.rightArm.yRot,ay,"umbrella y grip");near(model.rightArm.zRot,az,"umbrella z grip");
        }
        String json="{\"status\":\"passed\",\"frames\":12000,\"checks\":"+checks+",\"usesRealBakedProductionModel\":true,\"startsMinecraft\":false}\n";
        Files.writeString(Path.of(args[0]),json);System.out.print(json);
    }
}
