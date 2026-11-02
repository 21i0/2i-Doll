package com.twoi.combatdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.DollCombatAccess;
import com.twoi.combatdoll.combat.TwoIArcanaTimeline;
import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Original, finite crystal-island stage. All geometry is visual and leaves no terrain behind. */
public final class AxolotlDreamScene {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"textures/effect/cangsheng_crystal.png");
    private static final RenderType TYPE=RenderType.entityTranslucentEmissive(WHITE,false);
    // The exported material already has authored albedo and one directional
    // shade below. Vanilla's emissive entity type still adds normal lighting.
    private static final RenderType ROCK=AstralStaffRenderType.staff(ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"textures/effect/cangsheng_dream_materials.png"));
    private static int frames,lastVertices;
    private AxolotlDreamScene() { }
    public static int frames(){return frames;}
    public static int lastVertices(){return lastVertices;}
    public static float impact(float age){return age<160?0:(float)Math.exp(-(age-160)*.24);}
    public static int render(PoseStack pose,MultiBufferSource.BufferSource buffers,LivingEntity actor,float age,float time,float partial,Vec3 camera){
        float presence=TwoIArcanaTimeline.presence(age);
        if(presence<.001)return 0;
        var state=DollCombatAccess.state(actor);
        Vec3 origin=state.sceneOrigin(actor);
        Vec3 forward=state.target(actor).subtract(origin).multiply(1,0,1).normalize();
        if(forward.lengthSqr()<.1)forward=new Vec3(0,0,-1);
        Vec3 right=new Vec3(-forward.z,0,forward.x);
        float rise=TwoIArcanaTimeline.smooth(8,52,age);
        int mountainVertices=CangshengMountainStage.render(pose,buffers,ROCK,TYPE,origin,right,forward,camera,rise,presence);
        Mesh mesh=new Mesh(pose,buffers.getBuffer(TYPE),camera);mesh.vertices=mountainVertices;
        // The broad central massif carries the creature, not an isolated spike.
        Vec3 summit=origin.add(forward.scale(43));
        float roar=TwoIArcanaTimeline.smooth(90,101,age)*(1-TwoIArcanaTimeline.smooth(112,119,age));
        if(roar>.001){
            for(int i=0;i<3;i++)mesh.ring(summit.add(0,CangshengMountainStage.SUMMIT_HEIGHT+6,0),forward,4+(age-96-i*3)*1.1F,0xF4D3EE,roar*(.55F-i*.12F));
        }
        float hit=impact(age);
        if(hit>.001){
            Vec3 landing=origin.add(forward.scale(16)).add(0,.14,0);
            for(int i=0;i<4;i++)mesh.ring(landing.add(0,i*.11,0),new Vec3(0,1,0),2+(age-160)*1.8F+i*1.7F,0xF6D6F0,hit*(.65F-i*.1F));
            int debris=TwoIArcanaRenderer.quality()==1?20:36;
            for(int i=0;i<debris;i++){
                double a=i*2.39996;
                double t=Math.max(0,age-160-i%4*.6);
                double radius=t*(.42+i%3*.10)+3;
                double lift=Math.max(0,t*(.44+i%5*.075)-t*t*.018);
                Vec3 shard=landing.add(Math.cos(a)*radius,lift,Math.sin(a)*radius);
                mesh.slab(shard,.45F+i%4*.21F,.22F+i%3*.15F,i%2==0?0xE1D7E7:0xB5A8C9,
                        presence*(1-TwoIArcanaTimeline.smooth(182,203,age)),(float)(a+t*.035));
            }
        }
        // Bounded petal drift adds depth before the distant mountain silhouettes.
        int petals=TwoIArcanaRenderer.quality()*16;
        for(int i=0;i<petals;i++){
            double a=i*2.39996+time*.007;
            Vec3 at=origin.add(Math.cos(a)*(5+i%8*2),2+(i*.63+time*.025)%15,Math.sin(a)*(5+i%8*2));
            mesh.petal(at,.045F+(i%3)*.014F,presence*.8F,(float)(a+time*.03));
        }
        buffers.endBatch(ROCK);buffers.endBatch(TYPE);
        int count=mesh.vertices;
        // The real entity remains authoritative; all casts share one visible
        // giant choreography. Its normal renderer hides only the casting titan.
        {
            float crawl=TwoIArcanaTimeline.smooth(40,91,age);
            float leap=TwoIArcanaTimeline.smooth(125,144,age);
            float fall=TwoIArcanaTimeline.smooth(144,160,age);
            Vec3 at=summit.add(0,10+crawl*(CangshengMountainStage.SUMMIT_HEIGHT-10),0).lerp(origin.add(forward.scale(16)),fall);
            at=at.add(0,Math.sin(leap*Math.PI*.5)*23*(1-fall),0);
            float visible=TwoIArcanaTimeline.smooth(35,60,age)*(1-TwoIArcanaTimeline.smooth(194,218,age));
            if(visible>.001){
                pose.pushPose();
                Vec3 relative=at.subtract(camera);pose.translate(relative.x,relative.y,relative.z);
                pose.mulPose(Axis.YP.rotation((float)Math.atan2(forward.x,forward.z)+.46F*(1-fall)));
                pose.mulPose(Axis.XP.rotation(-.37F*(1-fall)+leap*.24F*(1-fall)));
                float squash=hit*.14F;
                pose.scale(17*visible*(1+squash),17*visible*(1-squash),17*visible*(1+squash));
                // Match the body rig's wind-up / air / landing keys to the
                // authoritative 160-tick impact, rather than a linear clock.
                float action=age<120?.46F*TwoIArcanaTimeline.smooth(40,120,age):
                        age<145?.46F+.30F*TwoIArcanaTimeline.smooth(120,145,age):
                        age<160?.76F+.08F*TwoIArcanaTimeline.smooth(145,160,age):
                        .84F+.16F*TwoIArcanaTimeline.smooth(160,194,age);
                CangshengAxolotlModel.renderScene(pose,buffers,0xF000F0,time,action,roar);
                pose.popPose();count+=CangshengAxolotlModel.lastVertices();
            }
        }
        frames++;lastVertices=count;return count;
    }
    private static final class Mesh {
        final PoseStack pose;final VertexConsumer consumer;final Vec3 camera;int vertices;
        Mesh(PoseStack p,VertexConsumer c,Vec3 cam){pose=p;consumer=c;camera=cam;}
        void vertex(Vec3 p,int rgb,float alpha){
            p=p.subtract(camera);
            consumer.addVertex(pose.last(),(float)p.x,(float)p.y,(float)p.z)
                    .setColor((rgb>>16)&255,(rgb>>8)&255,rgb&255,Math.clamp((int)(alpha*255),0,255))
                    .setUv(.5F,.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose.last(),0,1,0);vertices++;
        }
        void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha){vertex(a,color,alpha);vertex(b,color,alpha);vertex(c,color,alpha);vertex(d,color,alpha);}
        void slab(Vec3 at,float radius,float height,int color,float alpha,float tilt){
            Vec3[] rim=new Vec3[4];
            for(int i=0;i<4;i++){
                double a=i*Math.PI*.5+tilt;
                rim[i]=at.add(Math.cos(a)*radius,Math.sin(a+tilt)*radius*.24,Math.sin(a)*radius);
            }
            quad(rim[0],rim[1],rim[2],rim[3],color,alpha);
            int dark=((color>>16&255)*3/5)<<16|((color>>8&255)*3/5)<<8|(color&255)*3/5;
            for(int i=0;i<4;i++)quad(rim[i],rim[(i+1)%4],rim[(i+1)%4].add(0,-height,0),rim[i].add(0,-height,0),dark,alpha);
        }
        void crystal(Vec3 base,float radius,float height,int color,float alpha,float rotation){
            if(height<.01)return;
            for(int i=0;i<6;i++){
                double a=i*Math.PI/3+rotation,b=(i+1)*Math.PI/3+rotation;
                Vec3 p=base.add(Math.cos(a)*radius,0,Math.sin(a)*radius),q=base.add(Math.cos(b)*radius,0,Math.sin(b)*radius);
                Vec3 highP=base.add(Math.cos(a)*radius*.57,height*.76,Math.sin(a)*radius*.57);
                Vec3 highQ=base.add(Math.cos(b)*radius*.57,height*.76,Math.sin(b)*radius*.57);
                float shade=.72F+i*.046F;
                int r=(int)(((color>>16)&255)*shade),g=(int)(((color>>8)&255)*shade),bl=(int)((color&255)*shade);
                int rgb=r<<16|g<<8|bl;
                quad(p,q,highQ,highP,rgb,alpha);
                Vec3 top=base.add(.1*radius,height,.1*radius);
                quad(highP,highQ,top,top,color,alpha);
            }
        }
        void petal(Vec3 p,float size,float alpha,float angle){
            Vec3 x=new Vec3(Math.cos(angle)*size,Math.sin(angle)*size,0),y=new Vec3(-Math.sin(angle)*size*.65,Math.cos(angle)*size*.65,size*.35);
            quad(p.add(x),p.add(y),p.subtract(x),p.subtract(y),0xFFD0E5,alpha);
        }
        void ring(Vec3 p,Vec3 normal,float radius,int color,float alpha){
            if(radius<0)return;
            Vec3 x=normal.cross(new Vec3(0,1,0));if(x.lengthSqr()<.1)x=new Vec3(1,0,0);else x=x.normalize();
            Vec3 y=normal.cross(x).normalize();
            for(int i=0;i<48;i++){
                double a=i*Math.PI/24,b=(i+1)*Math.PI/24;
                Vec3 d=x.scale(Math.cos(a)).add(y.scale(Math.sin(a))),e=x.scale(Math.cos(b)).add(y.scale(Math.sin(b)));
                quad(p.add(d.scale(radius)),p.add(e.scale(radius)),p.add(e.scale(radius+.09)),p.add(d.scale(radius+.09)),color,alpha);
            }
        }
    }
}
