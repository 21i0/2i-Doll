package com.twoi.combatdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.twoi.combatdoll.TwoICombatDollMod;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** One bounded, original block model shared by item and companion rendering. */
public final class AstralEdictModel {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TwoICombatDollMod.MODID, "textures/item/astral_edict.png");
    public static final RenderType TYPE = AstralStaffRenderType.staff(TEXTURE);
    private static final float[][] TILES = new float[8][4];
    private static final float[] NEBULA = {1F/512,129F/512,383F/512,511F/512};
    private static final float SHAFT_ANCHOR = .30F;
    private static final float SHAFT_STRETCH = 1.45F;
    private static final float FOOT_SHIFT = (-.935F-SHAFT_ANCHOR)*(SHAFT_STRETCH-1F);
    static {
        for(int i=0;i<TILES.length;i++) TILES[i]=new float[]{(i*32+1F)/256,1F/256,(i*32+31F)/256,31F/256};
    }
    private AstralEdictModel() { }

    public static int render(PoseStack pose, VertexConsumer out, float time, float alpha, float size) {
        pose.pushPose();pose.scale(size,size,size);
        Shape m=new Shape(pose,out,alpha);
        // The load-bearing body is fixed. Only the contained crystal and small
        // celestial ornaments move: the player's grip never inherits their phase.
        // Lengthen only the shaft below its head socket; keep the cosmic head
        // at its original size instead of stretching the entire weapon.
        pose.pushPose();pose.translate(0,SHAFT_ANCHOR,0);
        pose.scale(1,SHAFT_STRETCH,1);pose.translate(0,-SHAFT_ANCHOR,0);
        m.box(-.0575F,-.83F,-.0575F,.115F,1.13F,.115F,0);
        for(int side=-1;side<=1;side+=2){
            m.box(side*.052F-.008F,-.78F,-.065F,.016F,.94F,.012F,0);
            m.box(-.009F,-.78F,side*.063F-.004F,.018F,.94F,.008F,2);
            m.box(-.003F,-.76F,side*.068F-.002F,.006F,.90F,.004F,5);
        }
        // Continuous, stepped spiral inlay replaces the old isolated gold dots.
        for(int i=0;i<32;i++){
            double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
            float y=-.73F+i*.025F;
            m.link((float)Math.cos(a)*.072F,y,(float)Math.sin(a)*.072F,
                    (float)Math.cos(b)*.072F,y+.025F,(float)Math.sin(b)*.072F,.006F,6);
        }
        for(float y:new float[]{-.76F,-.44F,-.12F}){
            m.collar(y-.018F,.078F,.038F,2);
            m.collar(y-.026F,.094F,.017F,1);m.collar(y+.018F,.094F,.017F,1);
            for(int side=-1;side<=1;side+=2){
                m.box(-.018F,y-.016F,side*.098F-.004F,.036F,.035F,.008F,5);
            }
        }
        m.collar(-.35F,.071F,.18F,2);
        m.collar(-.36F,.082F,.022F,1);m.collar(-.18F,.082F,.022F,1);
        for(int side=-1;side<=1;side+=2){
            m.box(side*.071F-.004F,-.333F,-.040F,.008F,.145F,.080F,1);
            m.box(-.018F,-.302F,side*.074F-.004F,.036F,.076F,.008F,5);
        }
        // Indigo armoured sockets and inset blue gems give both ends weight.
        m.collar(-.91F,.068F,.17F,2);
        m.collar(-.935F,.087F,.035F,1);m.collar(-.78F,.089F,.030F,1);
        m.collar(-.88F,.096F,.045F,2);
        m.collar(.09F,.074F,.17F,2);
        m.collar(.08F,.096F,.035F,1);m.collar(.232F,.104F,.034F,1);
        for(int side=-1;side<=1;side+=2){
            m.star(0,-.855F,side*.106F,.055F,1);
            pose.pushPose();pose.translate(0,.176,side*.088);
            pose.mulPose(Axis.ZP.rotationDegrees(45));
            m.box(-.046F,-.046F,-.013F,.092F,.092F,.026F,1);
            m.box(-.028F,-.028F,side*.018F-.009F,.056F,.056F,.018F,5);
            pose.popPose();
        }
        m.collar(.275F,.071F,.050F,0);
        pose.popPose();

        pose.pushPose();pose.translate(0,.63,0);
        // A dark, deep chassis, stepped porcelain crown and separate gold locks
        // surround the full-size cosmic cube. Open space separates the layers.
        m.ring(.343F,.044F,-.157F,.112F,2);
        pose.pushPose();pose.mulPose(Axis.ZP.rotationDegrees(45));
        m.ring(.282F,.038F,-.126F,.066F,0);
        m.ring(.296F,.014F,-.088F,.035F,1);pose.popPose();
        m.box(-.328F,-.286F,-.055F,.042F,.572F,.133F,0);
        m.box(.286F,-.286F,-.055F,.042F,.572F,.133F,0);
        for(int i=-2;i<=2;i++)for(int side=-1;side<=1;side+=2){
            float x=i*.128F,step=.286F+(2-Math.abs(i))*.023F;
            m.box(x-.063F,side*step-.024F,-.080F,.126F,.048F,.160F,0);
            m.box(x-.046F,side*step-.008F,.083F,.092F,.016F,.023F,1);
        }
        m.ring(.294F,.022F,.083F,.035F,1);
        m.ring(.350F,.012F,-.170F,.023F,1);
        // Four square keystones have blue cutouts, gold edging, ivory shoulders.
        for(int sx=-1;sx<=1;sx+=2)for(int sy=-1;sy<=1;sy+=2){
            m.box(sx*.294F-.046F,sy*.294F-.046F,-.126F,.092F,.092F,.20F,2);
            m.box(sx*.324F-.039F,sy*.324F-.039F,-.075F,.078F,.078F,.13F,0);
            m.box(sx*.294F-.027F,sy*.294F-.027F,.078F,.054F,.054F,.044F,1);
            m.box(sx*.294F-.010F,sy*.294F-.010F,.124F,.020F,.020F,.010F,5);
        }
        for(int side=-1;side<=1;side+=2){
            m.box(side*.373F-.023F,-.086F,-.105F,.046F,.172F,.17F,1);
            m.box(side*.405F-.021F,-.053F,-.080F,.042F,.106F,.12F,0);
            m.box(side*.368F-.036F,-.119F,-.123F,.072F,.040F,.205F,0);
            m.box(side*.368F-.036F,.079F,-.123F,.072F,.040F,.205F,0);
            m.star(side*.258F,-.231F,.127F,.062F,1);
        }
        // Cube faces carry dense stars and two contrasting nebula currents.
        // A narrow cyan lattice and small nucleus leave the cloud visible.
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(9+(float)Math.sin(time*.008F)*4));
        pose.mulPose(Axis.ZP.rotationDegrees(-7));
        m.box(-.203F,-.203F,-.158F,.406F,.406F,.316F,NEBULA);
        m.crystalEdges(.204F,.159F,.0035F,5);
        m.spark(0,0,.164F,.064F,4);m.spark(0,0,-.170F,.064F,5);
        pose.popPose();
        m.collar(.347F,.061F,.045F,2);m.collar(.383F,.048F,.036F,1);
        m.star(0,.460F,.010F,.088F,1);
        // Three tilted orbital paths, seven small celestial bodies and bounded
        // dust nodes. This is one item mesh, not particles, entities or jobs.
        for(int orbit=0;orbit<3;orbit++){
            pose.pushPose();pose.mulPose(Axis.ZP.rotationDegrees(-18+orbit*28));
            pose.mulPose(Axis.XP.rotationDegrees(24+orbit*8));
            float radius=.50F+orbit*.045F;
            for(int i=0;i<32;i++){
                double a=i*Math.PI/16,b=(i+1)*Math.PI/16;
                m.link((float)Math.cos(a)*radius,0,(float)Math.sin(a)*radius,
                        (float)Math.cos(b)*radius,0,(float)Math.sin(b)*radius,.0035F,orbit==1?5:6);
            }
            int bodies=orbit==0?3:2;
            for(int i=0;i<bodies;i++){
                double a=time*(orbit==1?-.006:.008)+i*Math.PI*2/bodies+orbit*.87;
                pose.pushPose();pose.translate(Math.cos(a)*radius,0,Math.sin(a)*radius);
                pose.mulPose(Axis.YP.rotationDegrees(time*.18F+i*53));
                if((i+orbit)%2==0){
                    pose.mulPose(Axis.ZP.rotationDegrees(30));m.wireCube(.029F,.004F,5);
                    m.box(-.010F,-.010F,-.010F,.020F,.020F,.020F,4);
                }else m.star(0,0,0,.042F,1);
                pose.popPose();
            }
            for(int i=0;i<6;i++){
                double a=time*.006+i*Math.PI/3+orbit*.7;
                m.box((float)Math.cos(a)*radius-.003F,-.003F,(float)Math.sin(a)*radius-.003F,.006F,.006F,.006F,i%2==0?4:6);
            }
            pose.popPose();
        }
        pose.popPose();
        // An articulated-looking gilded foot and a short suspended crystal.
        // Move the foot to the longer shaft without elongating its star/crystal.
        pose.pushPose();pose.translate(0,FOOT_SHIFT,0);
        m.collar(-.965F,.058F,.033F,2);m.collar(-.984F,.068F,.025F,1);
        m.box(-.006F,-1.027F,-.006F,.012F,.044F,.012F,1);
        pose.pushPose();pose.translate(0,-1.061,0);
        pose.mulPose(Axis.YP.rotationDegrees(time*.16F));pose.mulPose(Axis.ZP.rotationDegrees(45));
        m.wireCube(.030F,.0045F,5);m.box(-.011F,-.011F,-.011F,.022F,.022F,.022F,4);
        pose.popPose();
        pose.popPose();
        pose.popPose();return m.vertices;
    }

    private static final class Shape {
        private final PoseStack pose;private final VertexConsumer out;private final float alpha;
        private int vertices;
        private Shape(PoseStack pose,VertexConsumer out,float alpha){this.pose=pose;this.out=out;this.alpha=alpha;}
        void collar(float y,float radius,float h,int tile){box(-radius,y,-radius,radius*2,h,radius*2,tile);}
        void ring(float radius,float width,float z,float depth,int tile){
            box(-radius,-radius,z,radius*2,width,depth,tile);
            box(-radius,radius-width,z,radius*2,width,depth,tile);
            box(-radius,-radius+width,z,width,radius*2-width*2,depth,tile);
            box(radius-width,-radius+width,z,width,radius*2-width*2,depth,tile);
        }
        void star(float x,float y,float z,float radius,int tile){
            float core=radius*.24F,step=radius*.49F;
            box(x-core,y-radius,z,core*2,radius*2,.022F,tile);
            box(x-radius,y-core,z,radius*2,core*2,.022F,tile);
            box(x-step,y-step,z-.004F,step*2,step*2,.031F,tile);
            box(x-core*.64F,y-core*.64F,z+.03F,core*1.28F,core*1.28F,.012F,4);
        }
        void wireCube(float r,float width,int tile){
            for(int a=-1;a<=1;a+=2)for(int b=-1;b<=1;b+=2){
                box(-r,a*r-width*.5F,b*r-width*.5F,r*2,width,width,tile);
                box(a*r-width*.5F,-r,b*r-width*.5F,width,r*2,width,tile);
                box(a*r-width*.5F,b*r-width*.5F,-r,width,width,r*2,tile);
            }
        }
        void crystalEdges(float r,float depth,float width,int tile){
            for(int a=-1;a<=1;a+=2)for(int b=-1;b<=1;b+=2){
                box(-r,a*r-width*.5F,b*depth-width*.5F,r*2,width,width,tile);
                box(a*r-width*.5F,-r,b*depth-width*.5F,width,r*2,width,tile);
                box(a*r-width*.5F,b*r-width*.5F,-depth,width,width,depth*2,tile);
            }
        }
        void spark(float x,float y,float z,float radius,int tile){
            box(x-.0025F,y-radius,z,.005F,radius*2,.003F,tile);
            box(x-radius*.65F,y-.0025F,z,radius*1.3F,.005F,.003F,tile);
            box(x-.008F,y-.008F,z-.001F,.016F,.016F,.006F,tile);
        }
        void link(float ax,float ay,float az,float bx,float by,float bz,float width,int tile){
            float dx=bx-ax,dy=by-ay,dz=bz-az;
            float horizontal=(float)Math.sqrt(dx*dx+dz*dz),length=(float)Math.sqrt(horizontal*horizontal+dy*dy);
            pose.pushPose();pose.translate(ax,ay,az);
            pose.mulPose(Axis.YP.rotation((float)Math.atan2(dx,dz)));
            pose.mulPose(Axis.XP.rotation((float)-Math.atan2(dy,horizontal)));
            box(-width*.5F,-width*.5F,0,width,width,length,tile);pose.popPose();
        }
        void box(float x,float y,float z,float w,float h,float d,int tile){box(x,y,z,w,h,d,TILES[tile]);}
        void box(float x,float y,float z,float w,float h,float d,float[] uv){
            face(x,y,z+d,x+w,y,z+d,x+w,y+h,z+d,x,y+h,z+d,0,0,1,1F,uv);
            // Dedicated unlit staff shader preserves this restrained authored
            // face hierarchy without a second GUI/world directional-light tint.
            face(x+w,y,z,x,y,z,x,y+h,z,x+w,y+h,z,0,0,-1,.86F,uv);
            face(x+w,y,z+d,x+w,y,z,x+w,y+h,z,x+w,y+h,z+d,1,0,0,.90F,uv);
            face(x,y,z,x,y,z+d,x,y+h,z+d,x,y+h,z,-1,0,0,.80F,uv);
            face(x,y+h,z+d,x+w,y+h,z+d,x+w,y+h,z,x,y+h,z,0,1,0,1F,uv);
            face(x,y,z,x+w,y,z,x+w,y,z+d,x,y,z+d,0,-1,0,.72F,uv);
        }
        void face(float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,
                  float x3,float y3,float z3,float nx,float ny,float nz,float shade,float[] uv){
            vertex(x0,y0,z0,uv[0],uv[3],nx,ny,nz,shade);
            vertex(x1,y1,z1,uv[2],uv[3],nx,ny,nz,shade);
            vertex(x2,y2,z2,uv[2],uv[1],nx,ny,nz,shade);
            vertex(x3,y3,z3,uv[0],uv[1],nx,ny,nz,shade);
        }
        void vertex(float x,float y,float z,float u,float v,float nx,float ny,float nz,float shade){
            int c=(int)(shade*255);
            out.addVertex(pose.last(),x,y,z).setColor(c,c,c,(int)(Math.clamp(alpha,0,1)*255))
                    .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                    .setNormal(pose.last(),nx,ny,nz);vertices++;
        }
    }
}
