package com.twoi.combatdoll.client;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Original voxel animal: a full milk-white face, layered violet-blue pixel eyes,
 * petal gills, short feet, a long fin tail and a shallow living back garden. All four forms
 * share this rig and original pink/milk pixel skin. Origin: feet, +Y up, -Z nose. */
public final class CangshengAxolotlModel {
    public static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("two_i_combat_doll","textures/entity/cangsheng_axolotl_skin.png");
    private static final ResourceLocation RIDGE_TEXTURE=ResourceLocation.fromNamespaceAndPath("two_i_combat_doll","textures/entity/cangsheng_guardian_atlas.png");
    private static final float BASE=1.5F;
    private static final ModelPart ROOT=animal();
    private static final ModelPart BODY=ROOT.getChild("body"),HEAD=BODY.getChild("head");
    private static final ModelPart HIND=BODY.getChild("hind"),JAW=HEAD.getChild("jaw");
    private static final ModelPart MOUTH=HEAD.getChild("mouth"),LEFT_MOUTH=HEAD.getChild("left_mouth_corner"),RIGHT_MOUTH=HEAD.getChild("right_mouth_corner");
    private static final ModelPart LEFT_EYE=HEAD.getChild("left_eye"),RIGHT_EYE=HEAD.getChild("right_eye");
    private static final ModelPart LEFT_CHEEK=HEAD.getChild("left_cheek"),RIGHT_CHEEK=HEAD.getChild("right_cheek");
    private static final ModelPart LEFT_FRONT=BODY.getChild("left_front_leg"),RIGHT_FRONT=BODY.getChild("right_front_leg");
    private static final ModelPart LEFT_HIND=BODY.getChild("left_hind_leg"),RIGHT_HIND=BODY.getChild("right_hind_leg");
    private static final ModelPart[] PARTS=ROOT.getAllParts().toArray(ModelPart[]::new);
    private static final ModelPart[] TAILS=tailParts();
    private static final ModelPart[] FRONDS=frondParts();
    private static final ModelPart[] PLUMES=plumeParts();
    private static final Mesh RIDGE_HIGH=landscape(false),RIDGE_LOW=landscape(true);
    private static final Counter COUNTER=new Counter();
    private static int vertices;
    private CangshengAxolotlModel() { }
    public static int lastVertices(){return vertices;}

    public static void renderScene(PoseStack pose,MultiBufferSource buffers,int light,float time,float attack,float roar){
        render(pose,buffers,light,time,0,0,attack,roar,0);
    }
    public static void render(PoseStack pose,MultiBufferSource buffers,int light,float time,float motion,float hop,float attack,float roar,float carried){
        renderLiving(pose,buffers,light,time,motion,hop,attack,roar,carried,1,0);
    }
    /** Deployment unfolds the animal before standing; recall gathers the feet
     * and curls the articulated tail before the outer renderer shrinks it. */
    public static void renderLiving(PoseStack pose,MultiBufferSource buffers,int light,float time,float motion,float hop,float attack,float roar,float carried,float emerge,float recall){
        vertices=0;
        float crouch=bell(attack,.01F,.25F,.48F),air=bell(attack,.46F,.66F,.80F),land=bell(attack,.76F,.84F,.99F);
        float breath=Mth.sin(time*.075F),open=Mth.clamp(roar+bell(attack,.27F,.40F,.55F),0,1);
        float asleep=1-smooth(.15F,.8F,emerge),gather=smooth(.08F,.65F,recall),stand=smooth(.40F,.96F,emerge);
        // Look, hold, then settle: a pet observes its surroundings rather than
        // moving every facial part with the same perpetual sine wave.
        float watch=frac(time/232F),glance=smooth(.08F,.12F,watch)-smooth(.32F,.36F,watch)
                -smooth(.55F,.59F,watch)+smooth(.79F,.83F,watch);
        float curious=(bell(watch,.10F,.22F,.38F)+bell(watch,.58F,.68F,.82F)*.65F)*(1-open)*(1-asleep);
        float sniff=bell(frac(time/173F),.53F,.55F,.59F)+bell(frac(time/173F),.60F,.62F,.66F)*.55F;
        float smile=Mth.clamp(carried*.48F+curious*.58F+motion*.12F,0,1)*(1-open);
        for(ModelPart part:PARTS)part.resetPose();
        BODY.xRot=-air*.18F+crouch*.05F+land*.08F+asleep*.09F+gather*.04F;
        BODY.zRot=Mth.sin(time*.12F)*motion*.025F;
        BODY.yScale=1+breath*.012F-open*.025F;
        HIND.yRot=Mth.sin(time*.071F-1.1F)*(.025F+motion*.055F);
        HIND.zRot=Mth.sin(time*.063F+.8F)*.010F;
        HEAD.xRot=-open*.19F+breath*.016F-curious*.065F-sniff*.025F-carried*.018F+asleep*.20F-gather*.10F;
        HEAD.yRot=glance*(.085F+carried*.07F)+Mth.sin(time*.025F)*.018F+gather*.32F;
        HEAD.zRot=glance*curious*(.035F+carried*.025F)+Mth.sin(time*.033F)*.008F;
        JAW.xRot=.012F+open*.61F+Math.max(0,breath)*.023F+sniff*.04F;
        // A short paired blink and occasional relaxed half-lid animate the
        // medium layered pixel eyes. The eye shift leads the small head turn.
        float cycle=frac(time/181F),blink=Math.max(bell(cycle,.77F,.783F,.805F),bell(cycle,.825F,.838F,.857F)*.82F);
        float rest=bell(watch,.38F,.43F,.50F)*.26F*(1-open);
        eye(LEFT_EYE,-1,glance,curious,blink,rest,asleep);
        eye(RIGHT_EYE,1,glance,curious,blink,rest,asleep);
        float pant=Math.max(0,breath)*.12F+sniff*.20F;
        MOUTH.yScale=.075F*(1+open*1.90F+pant*.35F);MOUTH.xScale=.075F*(1+open*.12F);MOUTH.zScale=1;
        LEFT_CHEEK.xScale=RIGHT_CHEEK.xScale=.105F;
        LEFT_CHEEK.yScale=RIGHT_CHEEK.yScale=.105F;LEFT_CHEEK.zScale=RIGHT_CHEEK.zScale=1;
        LEFT_MOUTH.y-=smile*.13F;RIGHT_MOUTH.y-=smile*.13F;
        LEFT_MOUTH.zRot=smile*.30F;RIGHT_MOUTH.zRot=-smile*.30F;
        LEFT_MOUTH.xScale=RIGHT_MOUTH.xScale=1-open*.96F;
        LEFT_MOUTH.yScale=RIGHT_MOUTH.yScale=1;
        float kick=Mth.sin(time*.23F)*motion*.38F,fold=air*.66F+gather*.90F;
        leg(LEFT_FRONT,kick-fold,.04F,-.12F-gather*.18F,stand,open);
        leg(RIGHT_FRONT,-kick-fold,-.04F,.12F+gather*.18F,stand,open);
        leg(LEFT_HIND,-kick+fold*.55F,.14F,-.12F-gather*.22F,stand,0);
        leg(RIGHT_HIND,kick+fold*.55F,-.14F,.12F+gather*.22F,stand,0);
        for(int i=0;i<FRONDS.length;i++){
            ModelPart f=FRONDS[i];int side=i<3?-1:1,branch=i%3;
            f.yRot=side*(.13F+branch*.09F+Mth.sin(time*.067F-branch*.48F)*.055F+asleep*.35F-open*.24F-curious*.045F);
            f.zRot=side*(.11F+Mth.sin(time*.085F+branch*.73F)*.055F-open*.16F+curious*.075F+sniff*.055F);
            f.xRot=Mth.sin(time*.062F-branch*.61F)*.05F+sniff*(.06F-branch*.018F);
        }
        for(int i=0;i<PLUMES.length;i++){
            ModelPart petal=PLUMES[i];int side=i<15?-1:1;
            petal.zRot+=side*Mth.sin(time*.084F-i*.47F)*(.025F+motion*.035F);
            petal.xRot+=Mth.sin(time*.061F-i*.38F)*.035F+sniff*.025F;
        }
        for(int i=0;i<TAILS.length;i++){
            ModelPart tail=TAILS[i];
            tail.yRot=Mth.sin(time*.077F-i*.52F)*(.065F+i*.032F+motion*.12F+carried*.045F)+gather*(.16F+i*.065F);
            tail.xRot=Mth.sin(time*.051F-i*.43F)*.032F+air*.065F-gather*(.04F+i*.025F);
            tail.zRot=Mth.sin(time*.059F-i*.5F)*.014F;
        }
        COUNTER.target=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));COUNTER.count=0;
        pose.pushPose();
        float lift=.37F+breath*.006F+Math.max(0,hop)*.10F-crouch*.045F-land*.045F-(1-stand)*.11F-gather*.025F;
        pose.translate(0,lift,0);
        pose.scale(1+land*.035F,1-land*.055F,1+land*.018F);
        pose.pushPose();pose.scale(BASE,-BASE,BASE);
        BODY.render(pose,COUNTER,light,OverlayTexture.NO_OVERLAY);pose.popPose();
        int animalCount=COUNTER.count;
        // Finish every animal vertex before switching buffers to mountain atlas.
        VertexConsumer scenery=buffers.getBuffer(RenderType.entityCutoutNoCull(RIDGE_TEXTURE));
        pose.pushPose();
        pose.mulPose(Axis.XP.rotation(-BODY.xRot));
        pose.mulPose(Axis.ZP.rotation(-BODY.zRot));
        pose.scale(1.03F,.84F,.84F);
        Mesh ridge=TwoIArcanaRenderer.quality()==1?RIDGE_LOW:RIDGE_HIGH;
        ridge.render(pose,scenery,light,time);
        pose.popPose();pose.popPose();vertices=animalCount+ridge.data.length;
    }
    private static void leg(ModelPart leg,float x,float y,float z,float stand,float roar){
        leg.xRot=x;leg.yRot=y;leg.zRot=z+(leg.x>0?1:-1)*(1-stand)*.36F;
        leg.getChild("ankle").xRot=-x*.44F+roar*.14F;
    }
    private static void eye(ModelPart eye,int side,float glance,float curious,float blink,float rest,float asleep){
        eye.x+=glance*.17F;eye.y-=curious*.045F;
        eye.xScale=.122F;eye.yScale=.122F*Math.max(.055F,1-blink*.95F-rest-asleep*.90F);
        eye.zScale=1;eye.zRot=side*curious*.055F;
    }
    private static float smooth(float a,float b,float x){float p=Mth.clamp((x-a)/(b-a),0,1);return p*p*(3-2*p);}
    private static float bell(float x,float start,float peak,float end){if(x<=start||x>=end)return 0;float p=x<peak?(x-start)/(peak-start):(end-x)/(end-peak);return p*p*(3-2*p);}

    /** Shared authoring data is read once during class initialization. Blender
     * uses this exact part/cuboid hierarchy rather than a separate show model. */
    private static ModelPart animal(){
        String resource="/assets/two_i_combat_doll/models/entity/cangsheng_rig.json";
        try(var stream=CangshengAxolotlModel.class.getResourceAsStream(resource)){
            if(stream==null)throw new IllegalStateException("Missing shared Cangsheng rig "+resource);
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(json.get("format").getAsInt()!=1)throw new IllegalStateException("Unsupported Cangsheng rig format");
            MeshDefinition mesh=new MeshDefinition();var parts=new HashMap<String,PartDefinition>();parts.put("root",mesh.getRoot());
            for(var element:json.getAsJsonArray("nodes")){
                var node=element.getAsJsonObject();var parent=parts.get(node.get("parent").getAsString());
                if(parent==null)throw new IllegalStateException("Unknown Cangsheng parent "+node.get("parent"));
                var cubes=CubeListBuilder.create();
                for(var entry:node.getAsJsonArray("boxes")){
                    var box=entry.getAsJsonObject();var uv=box.getAsJsonArray("uv");var c=box.getAsJsonArray("box");
                    cubes.texOffs(uv.get(0).getAsInt(),uv.get(1).getAsInt()).addBox(c.get(0).getAsFloat(),c.get(1).getAsFloat(),c.get(2).getAsFloat(),
                            c.get(3).getAsFloat(),c.get(4).getAsFloat(),c.get(5).getAsFloat());
                }
                var p=node.getAsJsonArray("pose");
                var part=parent.addOrReplaceChild(node.get("name").getAsString(),cubes,PartPose.offsetAndRotation(
                        p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat(),p.get(3).getAsFloat(),p.get(4).getAsFloat(),p.get(5).getAsFloat()));
                parts.put(node.get("path").getAsString(),part);
            }
            return LayerDefinition.create(mesh,json.get("texture_width").getAsInt(),json.get("texture_height").getAsInt()).bakeRoot();
        }catch(Exception error){
            LogUtils.getLogger().error("Unable to load shared Cangsheng rig {}; retaining the built-in complete animal",resource,error);
            return fallbackAnimal();
        }
    }
    /** Complete articulated fallback keeps pets visible if an installation has
     * a corrupt resource; normal builds exclusively use the shared JSON rig. */
    private static ModelPart fallbackAnimal(){
        MeshDefinition mesh=new MeshDefinition();var root=mesh.getRoot();
        var body=root.addOrReplaceChild("body",CubeListBuilder.create()
                .texOffs(0,0).addBox(-4.10F,-1.75F,-5.8F,8.2F,3.55F,13)
                .texOffs(1,1).addBox(-3.75F,-2.08F,-5.05F,7.5F,.65F,11.7F)
                .texOffs(64,24).addBox(-3.85F,.40F,-6,7.7F,1.7F,13.15F)
                .texOffs(64,24).addBox(-3.85F,-.70F,-6.55F,7.7F,2.4F,2.7F)
                .texOffs(64,56).addBox(-3.72F,-2.15F,-1.5F,7.44F,.32F,7.85F),PartPose.ZERO);
        var hind=body.addOrReplaceChild("hind",CubeListBuilder.create().texOffs(0,2)
                .addBox(-3.55F,-1.55F,-.8F,7.1F,3.25F,4)
                .texOffs(64,25).addBox(-3.05F,.55F,-.8F,6.1F,1.3F,4),PartPose.offset(0,0,5.5F));
        var head=body.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,32)
                .addBox(-4.60F,-2.85F,-5.70F,9.20F,5.60F,6)
                .texOffs(1,33).addBox(-4.20F,-3.25F,-5.32F,8.4F,.6F,5.45F)
                .texOffs(1,33).addBox(-4.20F,2.52F,-5.34F,8.4F,.5F,5.45F),PartPose.offset(0,-.48F,-6.40F));
        head.addOrReplaceChild("mouth",CubeListBuilder.create().texOffs(48,96)
                .addBox(-8,-5,-.025F,16,10,.05F),PartPose.offset(0,1.12F,-5.755F));
        for(int side=-1;side<=1;side+=2)head.addOrReplaceChild((side<0?"left":"right")+"_mouth_corner",CubeListBuilder.create(),PartPose.offset(side*.65F,1.12F,-5.74F));
        head.addOrReplaceChild("jaw",CubeListBuilder.create().texOffs(40,32)
                .addBox(-1.10F,0,-2.05F,2.20F,.28F,2.10F),PartPose.offset(0,2.55F,-3.35F));
        for(int side=-1;side<=1;side+=2){
            String name=side<0?"left":"right";
            head.addOrReplaceChild(name+"_eye",CubeListBuilder.create().texOffs(0,96)
                    .addBox(-8,-10,-.025F,16,20,.05F),PartPose.offset(side*2.30F,-.40F,-5.755F));
            head.addOrReplaceChild(name+"_cheek",CubeListBuilder.create().texOffs(88,96)
                    .addBox(-6,-3,-.02F,12,6,.04F),PartPose.offset(side*3.13F,1.20F,-5.748F));
            for(int branch=0;branch<3;branch++){
                int index=side<0?branch:3+branch;
                var gill=head.addOrReplaceChild("frond_"+index,CubeListBuilder.create().texOffs(64,0)
                        .addBox(side<0?-1.25F:0,-.34F,-.32F,1.25F,.68F,.64F),
                        PartPose.offset(side*4.36F,-2.25F+branch*1.85F,-2.10F+branch*.20F));
                var leaf=gill.addOrReplaceChild("leaf_frond_"+index,CubeListBuilder.create().texOffs(65,2)
                        .addBox(side<0?-3.85F:0,-.23F,-.25F,3.85F,.46F,.50F),
                        PartPose.offsetAndRotation(side*.65F,branch==0?-.18F:branch==2?-.15F:0,0,0,0,side*(branch==0?-.42F:branch==2?.23F:-.015F)));
                float[][]plumes={{.70F,-.18F,-.55F},{1.30F,.18F,.50F},{1.95F,-.16F,-.45F},{2.58F,.14F,.42F},{3.18F,-.03F,0}};
                for(int j=0;j<5;j++)leaf.addOrReplaceChild("plume_"+j,CubeListBuilder.create().texOffs(65+j*3,3+j*2)
                        .addBox(side<0?-1.40F:-.15F,-.48F,-.28F,1.55F,.96F,.56F)
                        .texOffs(66+j*3,4+j*2).addBox(side<0?-1.33F:.28F,-.66F,-.22F,1.05F,1.32F,.44F)
                        .texOffs(67+j*3,5+j*2).addBox(side<0?-1.90F:1.12F,-.30F,-.17F,.78F,.60F,.34F),
                        PartPose.offsetAndRotation(side*plumes[j][0],plumes[j][1],-.05F*(j%2),0,0,side*plumes[j][2]));
            }
        }
        for(int side=-1;side<=1;side+=2)for(int rear=0;rear<2;rear++){
            String name=(side<0?"left":"right")+(rear==0?"_front_leg":"_hind_leg");
            var leg=body.addOrReplaceChild(name,CubeListBuilder.create().texOffs(0,56)
                    .addBox(side<0?-2.1F:-.2F,-.55F,-1.1F,2.3F,1.65F,2.4F),
                    PartPose.offset(side*4.0F,.8F,rear==0?-4.0F:5.15F));
            var ankle=leg.addOrReplaceChild("ankle",CubeListBuilder.create().texOffs(13,57)
                    .addBox(-1.0F,-.15F,-1.25F,2F,1.4F,2.5F)
                    .texOffs(64,26).addBox(-1.25F,.8F,-1.8F,2.5F,.65F,3.2F),
                    PartPose.offset(side*1.55F,1.1F,.2F));
            for(int toe=0;toe<3;toe++)ankle.addOrReplaceChild("toe_"+toe,CubeListBuilder.create().texOffs(25,58)
                    .addBox(-.25F,0,-.8F,.50F,.4F,1F),PartPose.offset((toe-1)*.73F,1.05F,-1.30F));
        }
        var parent=hind;
        for(int i=0;i<5;i++){
            float width=6.2F-i*1.13F,height=3.4F-i*.53F,length=3.5F-i*.28F;
            float fan=i==0?1.0F:i==1?1.8F:i==2?2.7F:i==3?3.0F:2.0F;
            var cubes=CubeListBuilder.create().texOffs(44,i*3).addBox(-width/2,-height/2,-.4F,width,height,length+.75F)
                    .texOffs(64,24).addBox(-width*.40F,height*.17F,-.25F,width*.8F,height*.30F,length+.4F)
                    .texOffs(65,5+i*2).addBox(-.36F,-height/2-fan,-.1F,.72F,fan+.25F,length+.45F)
                    .texOffs(66,7+i*2).addBox(-.27F,-height/2-fan-.55F,.55F,.54F,.75F,Math.max(.5F,length-.6F))
                    .texOffs(65,5+i*2).addBox(-.30F,height/2-.2F,-.1F,.60F,fan*.66F,length+.35F)
                    .texOffs(64,24).addBox(-.38F,-height/2-fan*.52F,.2F,.76F,fan*.45F,length+.1F);
            parent=parent.addOrReplaceChild("tail_"+i,cubes,i==0?PartPose.offset(0,0,4.0F):PartPose.offset(0,0,3.5F-(i-1)*.28F));
        }
        ModelPart baked=LayerDefinition.create(mesh,128,128).bakeRoot();
        ModelPart face=baked.getChild("body").getChild("head");
        for(String name:new String[]{"left_eye","right_eye"}){
            ModelPart eye=face.getChild(name);eye.xScale=.122F;eye.yScale=.122F;eye.zScale=1;
        }
        return baked;
    }
    private static ModelPart[] tailParts(){ModelPart[] parts=new ModelPart[5];ModelPart p=HIND;for(int i=0;i<5;i++){parts[i]=p.getChild("tail_"+i);p=parts[i];}return parts;}
    private static ModelPart[] frondParts(){ModelPart[] parts=new ModelPart[6];for(int i=0;i<6;i++)parts[i]=HEAD.getChild("frond_"+i);return parts;}
    private static ModelPart[] plumeParts(){ModelPart[] parts=new ModelPart[30];for(int i=0;i<6;i++)for(int j=0;j<5;j++)parts[i*5+j]=FRONDS[i].getChild("leaf_frond_"+i).getChild("plume_"+j);return parts;}

    private static final int ROCK=0,PETAL=1,MOSS=2,WATER=3,WOOD=4,SKIN=5;
    private record V(float x,float y,float z,float nx,float ny,float nz,float u,float v,int color,int material) {
        V uv(float u,float v,int mat){return new V(x,y,z,nx,ny,nz,u,v,color,mat);}
    }
    private static final class Builder{
        final ArrayList<V> data=new ArrayList<>();
        void face(V a,V b,V c,V d,int mat,float seed){
            float x=mat==PETAL?.5F:mat==WATER?.5F:mat>=WOOD?.75F:0;
            float y=mat<2?0:mat==SKIN?.75F:.5F;
            float width=mat>=WATER?.25F:.5F,height=mat==WOOD||mat==SKIN?.25F:.5F;
            // Comparable pixel density to the animal skin. Mapping half an
            // atlas onto every tiny rock quad created noisy dollhouse gravel.
            float edge=(float)Math.sqrt((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y)+(a.z-b.z)*(a.z-b.z));
            float rise=(float)Math.sqrt((b.x-c.x)*(b.x-c.x)+(b.y-c.y)*(b.y-c.y)+(b.z-c.z)*(b.z-c.z));
            float du=Mth.clamp(edge*32/512,1/512F,width*.30F),dv=Mth.clamp(rise*32/512,1/512F,height*.35F);
            float u=x+width*.015F+frac(seed*.731F)*width*.40F;
            float v=y+height*.015F+frac(seed*.419F)*height*.31F;
            data.add(a.uv(u,v+dv,mat));data.add(b.uv(u+du,v+dv,mat));data.add(c.uv(u+du,v,mat));data.add(d.uv(u,v,mat));
        }
        Mesh build(){return new Mesh(data.toArray(V[]::new));}
    }
    private record Mesh(V[] data){
        void render(PoseStack pose,VertexConsumer out,int light,float time){
            var p=pose.last();for(V v:data){
                float uvY=v.material==WATER?.502F+frac((v.v-.5F)*1.99F+time*.017F)*.49F:v.v;
                out.addVertex(p.pose(),v.x,v.y,v.z).setColor(v.color).setUv(v.u,uvY).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,v.nx,v.ny,v.nz);
            }
        }
    }
    private static V v(float x,float y,float z,float nx,float ny,float nz,int c){return new V(x,y,z,nx,ny,nz,0,0,c,ROCK);}
    private static float frac(float x){return x-(float)Math.floor(x);}
    private static float ground(float x,float z){
        // Thin soft moss and flowers; no grey pyramid or rock collar. The
        // front center stays clear for XiaoXiao's existing riding position.
        return .267F+Mth.sin(x*8+z*3)*.006F+Mth.cos(z*9-x*4)*.006F;
    }
    private static Mesh landscape(boolean low){
        Builder b=new Builder();int nx=low?10:16,nz=low?18:28;
        float stepX=.80F/nx,stepZ=1.44F/nz;
        for(int iz=0;iz<nz;iz++)for(int ix=0;ix<nx;ix++){
            float x=-.40F+ix*stepX,z=-.66F+iz*stepZ,cx=x+stepX*.5F,cz=z+stepZ*.5F;
            if(cx*cx/(.39F*.39F)+(cz-.06F)*(cz-.06F)/(.70F*.70F)>1)continue;
            float h1=ground(x,z),h2=ground(x+stepX,z),h3=ground(x+stepX,z+stepZ),h4=ground(x,z+stepZ);
            float dx=(h2+h3-h1-h4)*.5F/stepX,dz=(h3+h4-h1-h2)*.5F/stepZ;
            float inv=1/(float)Math.sqrt(1+dx*dx+dz*dz),normalX=-dx*inv,normalZ=-dz*inv;
            int mat=Math.max(Math.abs(dx),Math.abs(dz))>.85F?ROCK:MOSS;
            float shade=.87F+.13F*(.5F+normalX*.3F-normalZ*.2F);
            int color=tint(mat==ROCK?0xFFE1D8ED:0xFFF5E9F1,shade);
            b.face(v(x,h1,z,normalX,inv,normalZ,color),v(x+stepX,h2,z,normalX,inv,normalZ,color),
                    v(x+stepX,h3,z+stepZ,normalX,inv,normalZ,color),v(x,h4,z+stepZ,normalX,inv,normalZ,color),mat,ix*.73F+iz*.41F);
        }
        // Only low flowers remain. No peak, collar, tree, root curtain or
        // waterfall grows behind the face or through the rider's front seat.
        int shrubs=low?3:6;
        for(int i=0;i<shrubs;i++){
            float a=i*2.399963F+.7F,r=.38F+.52F*frac(i*.4142F),x=Mth.cos(a)*.34F*r,z=.06F+Mth.sin(a)*.65F*r;
            if(Math.abs(x)<.27F&&z<-.20F)continue;
            crown(b,x,ground(x,z)+.016F,z,.034F+frac(i*.731F)*.035F,6,i+90);
        }
        return b.build();
    }
    private static void crown(Builder b,float x,float y,float z,float size,int sides,float seed){
        // Three gentle voxel lobes share the body's stair-step visual language.
        block(b,x-size*.80F,y-size*.25F,z-size*.65F,size*1.60F,size*.48F,size*1.30F,PETAL,0xFFF3E3EE,seed);
        block(b,x-size*.58F,y+size*.14F,z-size*.48F,size*1.16F,size*.43F,size*.96F,PETAL,0xFFF8EAF1,seed+1);
        block(b,x-size*.32F,y+size*.49F,z-size*.29F,size*.64F,size*.25F,size*.58F,PETAL,0xFFFFF1F7,seed+2);
    }
    private static void block(Builder b,float x,float y,float z,float w,float h,float d,int material,int color,float seed){
        float xx=x+w,yy=y+h,zz=z+d;
        b.face(v(x,y,z,0,0,-1,color),v(xx,y,z,0,0,-1,color),v(xx,yy,z,0,0,-1,color),v(x,yy,z,0,0,-1,color),material,seed);
        b.face(v(xx,y,zz,0,0,1,color),v(x,y,zz,0,0,1,color),v(x,yy,zz,0,0,1,color),v(xx,yy,zz,0,0,1,color),material,seed+1);
        b.face(v(x,y,zz,-1,0,0,color),v(x,y,z,-1,0,0,color),v(x,yy,z,-1,0,0,color),v(x,yy,zz,-1,0,0,color),material,seed+2);
        b.face(v(xx,y,z,1,0,0,color),v(xx,y,zz,1,0,0,color),v(xx,yy,zz,1,0,0,color),v(xx,yy,z,1,0,0,color),material,seed+3);
        b.face(v(x,yy,z,0,1,0,color),v(xx,yy,z,0,1,0,color),v(xx,yy,zz,0,1,0,color),v(x,yy,zz,0,1,0,color),material,seed+4);
        b.face(v(x,y,zz,0,-1,0,color),v(xx,y,zz,0,-1,0,color),v(xx,y,z,0,-1,0,color),v(x,y,z,0,-1,0,color),material,seed+5);
    }
    private static void tree(Builder b,float x,float y,float z,float size,float seed){
        for(int s=0;s<4;s++){
            float a=s*Mth.HALF_PI,c=(s+1)*Mth.HALF_PI,r=.008F;
            b.face(v(x+Mth.cos(a)*r,y,z+Mth.sin(a)*r,Mth.cos(a),0,Mth.sin(a),0xFFE0C8D9),
                    v(x+Mth.cos(c)*r,y,z+Mth.sin(c)*r,Mth.cos(c),0,Mth.sin(c),0xFFE0C8D9),
                    v(x+Mth.cos(c)*r,y+size*.9F,z+Mth.sin(c)*r,Mth.cos(c),0,Mth.sin(c),0xFFFFEAF3),
                    v(x+Mth.cos(a)*r,y+size*.9F,z+Mth.sin(a)*r,Mth.cos(a),0,Mth.sin(a),0xFFFFEAF3),WOOD,seed);
        }
        crown(b,x,y+size*.75F,z,size,7,seed);
        crown(b,x+size*.35F,y+size*1.25F,z-size*.16F,size*.68F,6,seed+31);
    }
    private static void root(Builder b,float x,float y,float z,float length,float angle,float seed){
        for(int i=0;i<3;i++){
            float t=i/3F,u=(i+1)/3F,w=.011F*(1-t*.6F);
            float x1=x+Mth.sin(t*3+seed)*.012F,x2=x+Mth.sin(u*3+seed)*.012F;
            float z1=z+t*.02F,z2=z+u*.02F;
            b.face(v(x1-w,y-t*length,z1,0,.2F,1,0xFFE1C9DE),v(x1+w,y-t*length,z1,0,.2F,1,0xFFE1C9DE),
                    v(x2+w*.6F,y-u*length,z2,0,.2F,1,0xFFF2D8E9),v(x2-w*.6F,y-u*length,z2,0,.2F,1,0xFFF2D8E9),WOOD,seed+i);
        }
    }
    private static void waterfall(Builder b,float x,float y,float z,float length,float angle,float seed){
        float dx=Mth.cos(angle)*.025F,dz=Mth.sin(angle)*.025F;
        for(int i=0;i<6;i++){
            float t=i/6F,u=(i+1)/6F,slide=t*t*.04F,next=u*u*.04F;
            float xx=x+Mth.cos(angle)*slide,zz=z+Mth.sin(angle)*slide,nx=x+Mth.cos(angle)*next,nz=z+Mth.sin(angle)*next;
            b.face(v(xx-dz,y-t*length,zz+dx,Mth.cos(angle),.1F,Mth.sin(angle),0xFFE5FFFF),v(xx+dz,y-t*length,zz-dx,Mth.cos(angle),.1F,Mth.sin(angle),0xFFE5FFFF),
                    v(nx+dz,y-u*length,nz-dx,Mth.cos(angle),.1F,Mth.sin(angle),0xFFC9E9FF),v(nx-dz,y-u*length,nz+dx,Mth.cos(angle),.1F,Mth.sin(angle),0xFFC9E9FF),WATER,seed+i*.11F);
        }
    }
    private static void crystal(Builder b,float x,float y,float z,float radius,float height,float seed){
        for(int s=0;s<5;s++){
            float a=s*Mth.TWO_PI/5,c=(s+1)*Mth.TWO_PI/5,nx=Mth.cos((a+c)/2),nz=Mth.sin((a+c)/2);
            V tip=v(x+.010F,y+height,z,nx,.4F,nz,0xFFF7E6FF);
            b.face(v(x+Mth.cos(a)*radius,y,z+Mth.sin(a)*radius,nx,.2F,nz,0xFFC6AAEA),v(x+Mth.cos(c)*radius,y,z+Mth.sin(c)*radius,nx,.2F,nz,0xFFE0BBED),tip,tip,SKIN,seed+s);
        }
    }
    private static int tint(int c,float a){return(c&0xFF000000)|((int)(((c>>16)&255)*a)<<16)|((int)(((c>>8)&255)*a)<<8)|(int)((c&255)*a);}

    /** One reusable adapter counts actual emitted original animal vertices. */
    private static final class Counter implements VertexConsumer{
        VertexConsumer target;int count;
        public VertexConsumer addVertex(float x,float y,float z){target.addVertex(x,y,z);count++;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){target.setColor(r,g,b,a);return this;}
        public VertexConsumer setUv(float u,float v){target.setUv(u,v);return this;}
        public VertexConsumer setUv1(int u,int v){target.setUv1(u,v);return this;}
        public VertexConsumer setUv2(int u,int v){target.setUv2(u,v);return this;}
        public VertexConsumer setNormal(float x,float y,float z){target.setNormal(x,y,z);return this;}
    }
}
