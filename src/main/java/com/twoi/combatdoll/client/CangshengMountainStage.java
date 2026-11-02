package com.twoi.combatdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

/** Baked finite mountain mesh: shaded cliff mass, terraces, groves and falls. */
final class CangshengMountainStage {
    static final float SUMMIT_HEIGHT=16F;
    private record V(float x,float y,float z,int color,boolean stone) { }
    private record AuthoredV(float x,float y,float z,float u,float v,float nx,float ny,float nz,int color,float alpha) { }
    private record Authored(List<AuthoredV> high,List<AuthoredV> low) { }
    private static final Authored AUTHORED=loadAuthored();
    private static final List<V> HIGH=AUTHORED==null?bake(64):List.of(),LOW=AUTHORED==null?bake(28):List.of();
    private CangshengMountainStage(){ }
    static int render(PoseStack pose,MultiBufferSource.BufferSource buffers,RenderType rockType,RenderType detailType,Vec3 origin,Vec3 right,Vec3 forward,Vec3 camera,float rise,float alpha){
        if(AUTHORED!=null){
            var mesh=TwoIArcanaRenderer.quality()==1?AUTHORED.low:AUTHORED.high;
            VertexConsumer out=buffers.getBuffer(rockType);
            Vec3 anchor=origin.add(forward.scale(43));
            double ax=anchor.x-camera.x,ay=anchor.y-camera.y,az=anchor.z-camera.z;
            // Transform scalars directly: this path is tens of thousands of
            // vertices, so temporary Vec3s here would cause needless GC spikes.
            for(var v:mesh){
                float px=(float)(ax+right.x*v.x+forward.x*v.z),py=(float)(ay+(SUMMIT_HEIGHT+v.y)*rise),pz=(float)(az+right.z*v.x+forward.z*v.z);
                float nx=(float)(right.x*v.nx+forward.x*v.nz),nz=(float)(right.z*v.nx+forward.z*v.nz);
                float light=.70F+.30F*Math.max(0,-v.nx*.48F+v.ny*.77F-v.nz*.42F);
                int r=(int)((v.color>>16&255)*light),g=(int)((v.color>>8&255)*light),b=(int)((v.color&255)*light);
                out.addVertex(pose.last(),px,py,pz)
                        .setColor(r,g,b,Math.clamp((int)(alpha*v.alpha*255),0,255))
                        .setUv(v.u,v.v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                        .setNormal(pose.last(),nx,v.ny,nz);
            }
            buffers.endBatch(rockType);return mesh.size();
        }
        var data=TwoIArcanaRenderer.quality()==1?LOW:HIGH;
        // Custom RenderTypes share BufferSource's fallback builder. Finish each
        // material before requesting the next; never keep an invalid consumer.
        for(int material=0;material<2;material++){
            RenderType type=material==0?rockType:detailType;
            VertexConsumer out=buffers.getBuffer(type);
            for(var v:data){
                if(v.stone!=(material==0))continue;
                Vec3 p=origin.add(right.scale(v.x)).add(forward.scale(v.z)).add(0,v.y*rise-2,0).subtract(camera);
                out.addVertex(pose.last(),(float)p.x,(float)p.y,(float)p.z)
                    .setColor(v.color>>16&255,v.color>>8&255,v.color&255,(int)(alpha*255))
                    .setUv(v.stone?v.x*.13F+v.z*.09F:.5F,v.stone?v.y*.12F:.5F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose.last(),0,1,0);
            }
            buffers.endBatch(type);
        }return data.size();
    }
    /** One-time load of the same original Blender meshes used for the authoring
     * views. The finite legacy stage remains available if a pack is damaged. */
    private static Authored loadAuthored(){
        String path="/assets/two_i_combat_doll/models/effect/cangsheng_dream_stage.json";
        try(var stream=CangshengMountainStage.class.getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException("Missing authored dream stage "+path);
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!"cangsheng_dream_stage_v1".equals(root.get("format").getAsString())||root.get("scale").getAsFloat()!=1F)
                throw new IllegalStateException("Unsupported authored stage coordinates");
            var lods=root.getAsJsonObject("lods");
            return new Authored(readLod(lods.getAsJsonObject("HIGH"),60000),readLod(lods.getAsJsonObject("LOW"),25000));
        }catch(Exception error){
            LogUtils.getLogger().error("Unable to load authored Cangsheng dream stage; retaining bounded built-in stage",error);
            return null;
        }
    }
    private static List<AuthoredV> readLod(com.google.gson.JsonObject lod,int budget){
        int declared=lod.get("vertex_count").getAsInt();
        if(declared<4||declared>budget||declared%4!=0)throw new IllegalStateException("Dream stage exceeds vertex budget "+declared);
        var result=new ArrayList<AuthoredV>(declared);
        for(var element:lod.getAsJsonArray("quads")){
            var quad=element.getAsJsonObject();var points=quad.getAsJsonArray("p");var uvs=quad.getAsJsonArray("uv");
            var normal=quad.getAsJsonArray("n");var rgba=quad.getAsJsonArray("rgba");
            if(points.size()!=4||uvs.size()!=4||normal.size()!=3||rgba.size()!=4)throw new IllegalStateException("Malformed dream quad");
            int color=channel(rgba.get(0).getAsFloat())<<16|channel(rgba.get(1).getAsFloat())<<8|channel(rgba.get(2).getAsFloat());
            float a=finite(rgba.get(3).getAsFloat());
            if(a<0||a>1)throw new IllegalStateException("Dream alpha must be normalized");
            for(int i=0;i<4;i++){
                var p=points.get(i).getAsJsonArray();var uv=uvs.get(i).getAsJsonArray();
                result.add(new AuthoredV(finite(p.get(0).getAsFloat()),finite(p.get(1).getAsFloat()),finite(p.get(2).getAsFloat()),
                        finite(uv.get(0).getAsFloat()),finite(uv.get(1).getAsFloat()),finite(normal.get(0).getAsFloat()),
                        finite(normal.get(1).getAsFloat()),finite(normal.get(2).getAsFloat()),color,a));
            }
        }
        if(result.size()!=declared)throw new IllegalStateException("Dream stage count mismatch");
        return List.copyOf(result);
    }
    private static float finite(float value){if(!Float.isFinite(value))throw new IllegalStateException("Nonfinite dream vertex");return value;}
    private static int channel(float value){finite(value);if(value<0||value>1)throw new IllegalStateException("Dream RGB must be normalized");return Math.round(value*255);}
    private static List<V> bake(int resolution){
        var list=new ArrayList<V>();
        float step=68F/resolution;
        for(int x=0;x<resolution;x++)for(int z=0;z<resolution;z++){
            float ax=-34+x*step,az=18+z*step;
            float h=terrace(ax+step*.5F,az+step*.5F);
            if(h<.05)continue;
            // Horizontal stone shelves and vertical broken escarpments retain
            // the pixel world's scale. The former smooth cone never had ledges.
            int top=h>30?0xE9E2F0:(x+z)%7<2?0xCFADC9:0xB7AACD;
            stoneQuad(list,new Vec3(ax,h,az),new Vec3(ax+step,h,az),new Vec3(ax+step,h,az+step),new Vec3(ax,h,az+step),top);
            float east=terrace(ax+step*1.5F,az+step*.5F),south=terrace(ax+step*.5F,az+step*1.5F);
            float west=terrace(ax-step*.5F,az+step*.5F),north=terrace(ax+step*.5F,az-step*.5F);
            if(h>east)cliff(list,ax+step,az,ax+step,az+step,h,east,.78F);
            if(h>south)cliff(list,ax+step,az+step,ax,az+step,h,south,.65F);
            if(h>west)cliff(list,ax,az+step,ax,az,h,west,.92F);
            if(h>north)cliff(list,ax,az,ax+step,az,h,north,1F);
        }
        // Groves cling to real ledges and the lower shoulders, away from the creature's reveal.
        for(int i=0;i<48;i++){
            float x=(float)(Math.sin(i*2.39996)*(8+i%10*1.5)),z=27+i%9*4.3F;
            float y=height(x,z);if(y<3||y>32||Math.abs(x)<5&&z<51)continue;
            tree(list,x,y,z,.65F+(i%4)*.18F,i);
        }
        pavilion(list,-14,height(-14,42),42,1.05F);
        pavilion(list,18,height(18,50),50,.75F);
        bridge(list,-21,40,-7,43,19);
        bridge(list,8,54,24,56,22);
        floatingIsland(list,-28,22,31,3.1F,4);
        floatingIsland(list,29,30,44,3.6F,9);
        floatingIsland(list,-20,35,66,2.6F,14);
        // Cascades follow the actual cliff surface, remaining visible instead
        // of falling vertically inside the solid mountain.
        for(int i=0;i<3;i++){
            float x=-15+i*14,z=47+i*3;
            for(int segment=0;segment<30;segment++){
                float zz=z-segment,zz2=zz-1;
                float xx=x+(float)Math.sin(zz*.17)*.8F,xx2=x+(float)Math.sin(zz2*.17)*.8F;
                float y=height(xx,zz)+.16F,y2=height(xx2,zz2)+.16F;
                if(y<.2&&y2<.2)continue;
                for(int ribbon=0;ribbon<3;ribbon++){
                    float u=(ribbon-1)*.25F;
                    quad(list,new Vec3(xx+u,y,zz),new Vec3(xx+u+.25,y,zz),new Vec3(xx2+u+.25,y2,zz2),new Vec3(xx2+u,y2,zz2),ribbon==1?0xD4EFFF:0xA9C9E9);
                }
            }
        }
        return List.copyOf(list);
    }
    private static Vec3 point(float x,float z){return new Vec3(x,height(x,z),z);}
    private static float terrace(float x,float z){return (float)Math.floor(height(x,z)/1.7F)*1.7F;}
    private static void stoneQuad(List<V> out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color){stone(out,a,color);stone(out,b,color);stone(out,c,color);stone(out,d,color);}
    private static void cliff(List<V> out,float ax,float az,float bx,float bz,float high,float low,float shade){
        for(float bottom=low;bottom<high;bottom+=3.4F){
            float top=Math.min(high,bottom+3.4F);
            int layer=((int)(bottom/3.4F)&1)==0?0x9F99B9:0xB4A8C5;
            stoneQuad(out,new Vec3(ax,bottom,az),new Vec3(bx,bottom,bz),new Vec3(bx,top,bz),new Vec3(ax,top,az),shade(layer,shade));
        }
    }
    private static float height(float x,float z){
        float h=0;
        float[][] peaks={{0,44,37,15,19},{-12,42,26,15,15},{13,51,29,14,17},{-22,62,24,14,18},{22,69,26,13,16},{-4,73,23,17,18}};
        for(var p:peaks){
            double dx=(x-p[0])/p[3],dz=(z-p[1])/p[4];
            double angle=Math.atan2(dz,dx),r=Math.sqrt(dx*dx+dz*dz);
            double contour=r*(1+.075*Math.sin(angle*5+p[0])+.045*Math.sin(angle*11));
            double mass=Math.pow(Math.max(0,1-contour),.62)*p[2];
            h=Math.max(h,(float)mass);
        }
        float weather=(float)(Math.sin(x*1.7+Math.sin(z*.48))*Math.sin(z*.91+x*.33));
        return Math.max(0,h+(weather*.65F+(float)Math.sin(x*.43+z*.37)*.6F)*Math.min(1,h/5));
    }
    private static void triangle(List<V> out,Vec3 a,Vec3 b,Vec3 c){
        Vec3 normal=b.subtract(a).cross(c.subtract(a)).normalize();if(normal.y<0)normal=normal.scale(-1);
        float light=(float)(.42+.58*Math.max(0,normal.dot(new Vec3(-.48,.72,-.5).normalize())));
        stone(out,a,rock(a,normal,light));stone(out,b,rock(b,normal,light));stone(out,c,rock(c,normal,light));stone(out,c,rock(c,normal,light));
    }
    private static int rock(Vec3 p,Vec3 n,float light){
        double stripe=.5+.5*Math.sin(p.x*3.5+Math.sin(p.z*1.3)*2+p.y*.14);
        int color=p.y>27&&n.y>.32?0xEEE4F4:0x8883AA;
        float shade=light*(float)(.88+stripe*.12);
        if(n.y>.65&&p.y>6&&p.y<28&&Math.sin(p.x*.71+p.z*.35)>.5)color=0xD5A2C7;
        return shade(color,shade);
    }
    private static int shade(int c,float s){return Math.min(255,(int)((c>>16&255)*s))<<16|Math.min(255,(int)((c>>8&255)*s))<<8|Math.min(255,(int)((c&255)*s));}
    private static void emit(List<V> out,Vec3 p,int color){out.add(new V((float)p.x,(float)p.y,(float)p.z,color,false));}
    private static void stone(List<V> out,Vec3 p,int color){out.add(new V((float)p.x,(float)p.y,(float)p.z,color,true));}
    private static void quad(List<V> out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color){emit(out,a,color);emit(out,b,color);emit(out,c,color);emit(out,d,color);}
    private static void tree(List<V> out,float x,float y,float z,float scale,int id){
        box(out,x-.07F*scale,y,z-.07F*scale,.14F*scale,1.3F*scale,.14F*scale,0x8A6384);
        for(int lobe=0;lobe<4;lobe++){
            double a=lobe*2.39996+id;
            float cx=x+(float)Math.cos(a)*scale*.50F,cz=z+(float)Math.sin(a)*scale*.50F;
            crown(out,cx,y+scale*(1.25F+lobe*.14F),cz,scale*(.8F-lobe*.07F),id+lobe);
        }
    }
    private static void crown(List<V> out,float x,float y,float z,float r,int id){
        int pink=id%2==0?0xF1BDD8:0xDFAFD3;
        box(out,x-r,y-r*.18F,z-r*.7F,r*2,r*.52F,r*1.4F,pink);
        box(out,x-r*.65F,y+r*.25F,z-r*.54F,r*1.3F,r*.46F,r*1.08F,0xF6D0E4);
        box(out,x-r*.73F,y-r*.08F,z-r,r*1.46F,r*.35F,r*2,shade(pink,.92F));
    }
    private static void bridge(List<V> out,float ax,float az,float bx,float bz,float y){
        for(int i=0;i<14;i++){
            float t=i/13F,x=ax+(bx-ax)*t,z=az+(bz-az)*t;
            float arch=(float)Math.sin(t*Math.PI)*2.1F;
            box(out,x-.65F,y+arch,z-.65F,1.3F,.45F,1.3F,0xE2D2E2);
            if(i%3==0){box(out,x-.65F,y+arch+.4F,z-.65F,.15F,.75F,.15F,0xC3ABC8);box(out,x+.5F,y+arch+.4F,z+.5F,.15F,.75F,.15F,0xC3ABC8);}
            if(i==0||i==13){float ground=height(x,z);box(out,x-.5F,ground,z-.5F,1,Math.max(.1F,y-ground),1,0xADA2C2);}
        }
    }
    private static void floatingIsland(List<V> out,float x,float y,float z,float r,int id){
        for(int layer=0;layer<5;layer++){
            float width=r*(1-layer*.16F),yy=y-layer*1.25F;
            box(out,x-width,yy-1.25F,z-width*.74F,width*2,1.28F,width*1.48F,layer==0?0xD8B5D4:shade(0xB6ACCE,1-layer*.06F));
        }
        tree(out,x-.7F,y+.2F,z,.95F,id);
        pavilion(out,x+1.0F,y+.1F,z+.3F,.42F);
    }
    private static void pavilion(List<V> out,float x,float y,float z,float s){
        box(out,x-1.6F*s,y,z-1.6F*s,3.2F*s,.4F*s,3.2F*s,0xC9BDDB);
        for(int a=-1;a<=1;a+=2)for(int b=-1;b<=1;b+=2)box(out,x+a*1.12F*s-.08F,y,z+b*1.12F*s-.08F,.16F*s,2.6F*s,.16F*s,0xC1A2BC);
        for(int tier=0;tier<2;tier++){
            float yy=y+(2.6F+tier*1.15F)*s,r=(1.9F-tier*.6F)*s;
            for(int i=0;i<4;i++){
                double a=Math.PI/4+i*Math.PI/2,b=a+Math.PI/2;
                quad(out,new Vec3(x+Math.cos(a)*r,yy,z+Math.sin(a)*r),new Vec3(x+Math.cos(b)*r,yy,z+Math.sin(b)*r),new Vec3(x,yy+.85*s,z),new Vec3(x,yy+.85*s,z),i%2==0?0x857DAB:0xADA0CC);
            }
        }
    }
    private static void box(List<V> out,float x,float y,float z,float w,float h,float d,int c){
        Vec3 a=new Vec3(x,y,z),b=a.add(w,0,0),cc=a.add(w,0,d),dd=a.add(0,0,d);
        quad(out,a,b,b.add(0,h,0),a.add(0,h,0),c);quad(out,b,cc,cc.add(0,h,0),b.add(0,h,0),shade(c,.8F));
        quad(out,cc,dd,dd.add(0,h,0),cc.add(0,h,0),shade(c,.7F));quad(out,dd,a,a.add(0,h,0),dd.add(0,h,0),c);
        quad(out,a.add(0,h,0),b.add(0,h,0),cc.add(0,h,0),dd.add(0,h,0),c);
    }
}
