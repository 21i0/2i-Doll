package com.twoi.combatdoll.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.client.*;
import com.twoi.combatdoll.combat.TwoIArcanaTimeline;
import com.twoi.combatdoll.config.TwoIVisualConfig;
import com.twoi.combatdoll.entity.CombatDollEntity;
import com.twoi.combatdoll.entity.TwoIDollEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;

/** Opt-in test-only capture of the real renderer output, with no GPU buffers or
 * reconstructed geometry. Import QUADS as quads, then triangulate in Blender.
 * Every animation frame invokes the production model and captures the transformed
 * position/normal, actual UV, vertex color and packed light/overlay components. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID,value=Dist.CLIENT)
public final class ModelMeshExports {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static final int LIGHT=0xF000F0;
    private static boolean coreDone,externalDone,failed;
    private static Path directory;
    private static final List<Map<String,Object>> frames=new ArrayList<>();
    private static final Map<String,Map<String,Object>> textures=new LinkedHashMap<>();
    private static int frameIndex;
    private ModelMeshExports() { }

    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored) {
        if(!Boolean.getBoolean("two_i_combat_doll.model_mesh_exports")||failed)return;
        var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null)return;
        try {
            // Run before the integrated visual fixture starts casting. A slow
            // mesh write must never steal the client's required combat frames.
            if(!coreDone&&(mc.screen!=null||mc.level!=null)) {
                directory=Path.of(System.getProperty("two_i_combat_doll.model_mesh_export_dir",
                        mc.gameDirectory.toPath().resolve("screenshots/model-exports").toString())).toAbsolutePath().normalize();
                Files.createDirectories(directory.resolve("frames"));
                Files.createDirectories(directory.resolve("textures"));
                if(Files.exists(directory.resolve("manifest.json")))throw new IOException("Refusing to replace model evidence: "+directory);
                exportCore(mc);coreDone=true;writeManifest(false);
                LogUtils.getLogger().info("MODEL MESH CORE EXPORTS PASSED: frames={} textures={} directory={}",frames.size(),textures.size(),directory);
                if(Boolean.getBoolean("two_i_combat_doll.model_mesh_export_only")){mc.stop();return;}
            }
            if(coreDone&&!externalDone&&mc.level!=null&&mc.player!=null&&mc.screen==null) {
                exportExternal(mc);externalDone=true;writeManifest(true);
                LogUtils.getLogger().info("MODEL MESH OPTIONAL EXPORTS PASSED: frames={} textures={} directory={}",frames.size(),textures.size(),directory);
            }
        } catch(Throwable error) {
            failed=true;LogUtils.getLogger().error("MODEL MESH EXPORTS FAILED",error);
            if(Boolean.getBoolean("two_i_combat_doll.model_mesh_export_only"))mc.stop();
        }
    }

    private static void exportCore(Minecraft mc)throws Exception {
        ModelPart root=TwoIDollRenderer.Model.createLayer().bakeRoot();
        var doll=new TwoIDollRenderer.Model(root);
        for(String action:List.of("standing","walking","sitting","carried","rain","sneeze","thunder")) {
            for(int frame=0;frame<8;frame++) {
                float time=frame*20F,p=frame/7F;
                if(action.equals("standing")||action.equals("walking"))
                    doll.setupStanding(time*.55F,action.equals("walking")?.38F:0,time,(float)Math.sin(time*.025)*24,0,CombatDollEntity.ACTION_NONE,0);
                else doll.setupDecoration(time);
                TwoIDollCarryMotion.Pose motion=action.equals("carried")
                        ?new TwoIDollCarryMotion.Pose((float)Math.sin(p*Math.PI)*.09F,
                        (float)Math.sin(p*Math.PI*2)*.17F,(float)Math.cos(p*Math.PI*2)*.10F,
                        (float)Math.sin(p*Math.PI),p>.4F?.7F:0,.65F):TwoIDollCarryMotion.Pose.STILL;
                doll.applyCarriedMotion(motion,time);
                TwoIDollWeather.Pose weather=switch(action) {
                    case "rain"->new TwoIDollWeather.Pose(TwoIDollEnvironment.RAIN,1,-1);
                    case "sneeze"->new TwoIDollWeather.Pose(TwoIDollEnvironment.COLD,0,p);
                    case "thunder"->new TwoIDollWeather.Pose(TwoIDollEnvironment.RAIN|TwoIDollEnvironment.THUNDER,1,-1);
                    default->TwoIDollWeather.Pose.CLEAR;
                };
                doll.applyWeather(weather,time);
                var source=new CaptureSource(mc);var pose=new PoseStack();motion.apply(pose);
                pose.translate(0,TwoIDollRenderer.FEET_Y/16F*TwoIDollRenderer.RENDER_SCALE,0);
                pose.scale(TwoIDollRenderer.RENDER_SCALE,-TwoIDollRenderer.RENDER_SCALE,-TwoIDollRenderer.RENDER_SCALE);
                source.group="body";
                doll.renderToBuffer(pose,source.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(doll.blink()))),LIGHT,OverlayTexture.NO_OVERLAY);
                source.group="accessories";doll.renderAccessories(pose,source,LIGHT,OverlayTexture.NO_OVERLAY);
                var rig=new LinkedHashMap<String,Object>();rig.put("model_parts",partTree("root",root));
                rig.put("tail",tailRig(field(doll,"tail")));
                saveFrame("two_i",action,frame,time,Map.of("progress",p,"blink",doll.blink()),source,rig);
            }
        }
        // Include the real blink material states, independently of whether an
        // eight-frame animation happens to sample the short natural blink.
        for(int frame=0;frame<3;frame++) {
            float blink=new float[]{0,.4F,1}[frame];doll.setupDecoration(40);
            var source=new CaptureSource(mc);var pose=new PoseStack();
            pose.translate(0,TwoIDollRenderer.FEET_Y/16F*TwoIDollRenderer.RENDER_SCALE,0);
            pose.scale(TwoIDollRenderer.RENDER_SCALE,-TwoIDollRenderer.RENDER_SCALE,-TwoIDollRenderer.RENDER_SCALE);
            source.group="body";doll.renderToBuffer(pose,source.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(blink))),LIGHT,OverlayTexture.NO_OVERLAY);
            source.group="accessories";doll.renderAccessories(pose,source,LIGHT,OverlayTexture.NO_OVERLAY);
            saveFrame("two_i","blink_material",frame,40,Map.of("blink",blink),source,Map.of("model_parts",partTree("root",root)));
        }
        for(int frame=0;frame<12;frame++) {
            float time=frame*15F;var source=new CaptureSource(mc);source.group="staff";
            int returned=AstralEdictModel.render(new PoseStack(),source.getBuffer(AstralEdictModel.TYPE),time,1,1);
            saveFrame("astral_edict","orbit",frame,time,Map.of("returned_vertex_count",returned),source,Map.of("animation","Production time-driven ornaments and crystal transforms, captured as exact posed meshes"));
        }
        // Closely spaced actual physical-clock poses expose an accidental
        // high-frequency tail animation without reconstructing the model.
        for(int frame=0;frame<6;frame++) {
            float time=200+frame*.05F;var source=new CaptureSource(mc);source.group="staff";
            int returned=AstralEdictModel.render(new PoseStack(),source.getBuffer(AstralEdictModel.TYPE),time,1,1);
            saveFrame("astral_edict","continuity",frame,time,Map.of("returned_vertex_count",returned),source,
                    Map.of("animation","Exact adjacent physical-clock poses; assess bounded movement and a stable physical shaft"));
        }
        ItemStack staff=new ItemStack(TwoICombatDollMod.STAFF.get());
        for(ItemDisplayContext context:ItemDisplayContext.values()) {
            var source=new CaptureSource(mc);source.group="actual_item_context";
            float time=TwoIDollItemRenderer.animationTime();
            ArcanaWeaponRenderer.get().renderByItem(staff,context,new PoseStack(),source,LIGHT,OverlayTexture.NO_OVERLAY);
            saveFrame("astral_edict_display",context.name().toLowerCase(Locale.ROOT),0,time,
                    Map.of("item_display_context",context.name(),"actual_production_bewlr",true),source,
                    Map.of("coordinate_space","Actual BEWLR output before ItemRenderer's outer player/display matrices; no invented hand transform"));
        }
        for(int frame=0;frame<9;frame++) {
            float open=frame/8F,time=frame*10F;var source=new CaptureSource(mc);source.group="book";
            new TwoIArcanaRenderer.Mesh(new PoseStack(),source.getBuffer(RenderType.entityTranslucentEmissive(TwoIArcanaRenderer.SCARLET_ATLAS,false)),true).book(time,1,1,open);
            saveFrame("scarlet_codex","opening",frame,time,Map.of("open",open),source,Map.of("animation","Production hinged cover and parallax iris; no reconstructed book mesh"));
        }
        for(String action:List.of("idle","carried","hop","roar","attack","emerge","recall")) {
            int count=action.equals("attack")?16:8;
            for(int frame=0;frame<count;frame++) {
                float p=(float)frame/(count-1),time=frame*8F;
                float motion=action.equals("hop")?1:action.equals("carried")?.5F:0;
                float hop=action.equals("hop")?(float)Math.sin(p*Math.PI):0;
                float attack=action.equals("attack")?p:0;
                float roar=action.equals("roar")?(float)Math.sin(p*Math.PI):0;
                float carried=action.equals("carried")?1:0;
                float emerge=action.equals("emerge")?p:1,recall=action.equals("recall")?p:0;
                var source=new CaptureSource(mc);source.group="animal_and_mountain";
                CangshengAxolotlModel.renderLiving(new PoseStack(),source,LIGHT,time,motion,hop,attack,roar,carried,emerge,recall);
                saveFrame("cangsheng_axolotl",action,frame,time,
                        Map.of("progress",p,"motion",motion,"hop",hop,"attack",attack,"roar",roar,"carried",carried,"emerge",emerge,"recall",recall),
                        source,Map.of("model_parts",partTree("root",(ModelPart)field(CangshengAxolotlModel.class,"ROOT"))));
            }
        }
        exportVisualStages(mc);
    }

    /** Capture actual finite geometry only. The shader galaxy, black hole,
     * floor takeover and reflection passes are deliberately not fabricated as
     * offline 3-D meshes; their evidence remains the real client framebuffer. */
    private static void exportVisualStages(Minecraft mc)throws Exception {
        Class<?> stage=Class.forName("com.twoi.combatdoll.client.CangshengMountainStage");
        var render=stage.getDeclaredMethod("render",PoseStack.class,MultiBufferSource.BufferSource.class,
                RenderType.class,RenderType.class,Vec3.class,Vec3.class,Vec3.class,Vec3.class,float.class,float.class);
        render.setAccessible(true);
        RenderType rock=(RenderType)field(AxolotlDreamScene.class,"ROCK");
        RenderType detail=(RenderType)field(AxolotlDreamScene.class,"TYPE");
        int quality=TwoIVisualConfig.QUALITY.get();ParticleStatus particles=mc.options.particles().get();
        int priorTentacles=(Integer)field(TwoIArcanaRenderer.class,"lastTentacles");
        int priorTentacleVertices=(Integer)field(TwoIArcanaRenderer.class,"lastTentacleVertices");
        int priorSlam=(Integer)field(TwoIArcanaRenderer.class,"lastBookSlam");
        Map<Field,Object> priorPortalState=new LinkedHashMap<>();
        for(Field field:TwoIArcanaRenderer.class.getDeclaredFields())if(field.getName().startsWith("lastPortal")
                &&java.lang.reflect.Modifier.isStatic(field.getModifiers())&&!java.lang.reflect.Modifier.isFinal(field.getModifiers())){
            field.setAccessible(true);priorPortalState.put(field,field.get(null));
        }
        try {
            TwoIVisualConfig.QUALITY.set(3);
            Object authored;
            try {authored=field(stage,"AUTHORED");}
            catch(NoSuchFieldException oldStage){authored=null;}
            for(String lod:List.of("HIGH","LOW")) {
                mc.options.particles().set(lod.equals("LOW")?ParticleStatus.MINIMAL:ParticleStatus.ALL);
                var cached=(List<?>)(authored==null?field(stage,lod):field(authored,lod.toLowerCase(Locale.ROOT)));
                int expected=cached.size(),limit=lod.equals("HIGH")?60000:25000;
                String topologySource=authored==null?"built_in_fallback":"authored_blender_asset";
                if(expected<=0||expected>=limit)throw new AssertionError("Actual stage cached topology exceeds its finite budget: "+lod+" source="+topologySource+" vertices="+expected+" limit="+limit);
                for(int frame=0;frame<2;frame++) {
                    float rise=frame==0?.25F:1;
                    var source=new CaptureSource(mc);source.group="baked_mountain_stage_"+lod.toLowerCase(Locale.ROOT);
                    int count=(Integer)render.invoke(null,new PoseStack(),new CaptureBufferSource(source),rock,detail,
                            Vec3.ZERO,new Vec3(1,0,0),new Vec3(0,0,1),Vec3.ZERO,rise,1F);
                    int actual=source.buckets.stream().mapToInt(b->b.consumer.rows.size()).sum();
                    if(count!=expected||actual!=count)throw new AssertionError("Actual stage capture topology count differs: "+lod+" returned="+count+" baked="+expected+" emitted="+actual);
                    saveFrame("cangsheng_mountain_stage",lod.toLowerCase(Locale.ROOT),frame,rise,
                            Map.of("rise",rise,"alpha",1,"baked_vertex_count",expected,"returned_vertex_count",count,
                                    "cached_vertex_count",expected,"topology_source",topologySource,"authored_loaded",authored!=null,
                                    "basis","origin=camera=(0,0,0), right=(1,0,0), forward=(0,0,1)"),
                            source,Map.of("capture","Actual CangshengMountainStage.render invoked with a CPU-only BufferSource adapter; selected authored high/low topology or explicit complete fallback and all runtime emission code preserved"));
                }
            }
            mc.options.particles().set(ParticleStatus.ALL);
            var tentacles=TwoIArcanaRenderer.class.getDeclaredMethod("tentacles",TwoIArcanaRenderer.Mesh.class,
                    Vec3.class,Vec3.class,Vec3.class,Vec3.class,float.class,float.class);
            tentacles.setAccessible(true);
            Vec3 center=mc.gameRenderer.getMainCamera().getPosition(),forward=new Vec3(0,0,-1);
            Vec3 eye=center.add(forward.scale(14)).add(0,18,0),aim=center.add(forward.scale(12));
            int first=TwoIArcanaTimeline.bookSlamTick(0),last=TwoIArcanaTimeline.bookSlamTick(TwoIArcanaTimeline.bookSlamCount()-1);
            int[] ages={first-6,first,first+6,last-6,last,last+8};
            for(int frame=0;frame<ages.length;frame++) {
                float age=ages[frame],presence=TwoIArcanaTimeline.presence(age);
                var source=new CaptureSource(mc);source.group="actual_book_tentacle_tubes";
                var mesh=new TwoIArcanaRenderer.Mesh(new PoseStack(),source.getBuffer(RenderType.entityTranslucentEmissive(TwoIArcanaRenderer.SCARLET_ATLAS,false)),true);
                resetBookGeometryCounters();
                tentacles.invoke(null,mesh,eye,center,aim,forward,age,presence);
                saveFrame("scarlet_tentacles",frame<3?"first_wave":"last_wave",frame%3,age,
                        Map.of("spell_age",age,"presence",presence,"stage",new String[]{"windup","contact","rebound"}[frame%3],
                                "basis","actual camera translated to local origin, caster forward=(0,0,-1)"),source,
                        Map.of("capture","Actual private TwoIArcanaRenderer.tentacles and Mesh.tube invoked; exact emitted portal/tube topology, contact rings, per-corner color and normal; standalone posed geometry is not server-timeline evidence"));
            }
            // Explicitly sample the actual production portal geometry as posed
            // meshes. The independent live-client test supplies authoritative
            // chronological framebuffer evidence and server-wave execution.
            for(int sample=0;sample<27;sample++){
                int wave=sample<3?-1:(sample-3)/3,phase=sample<3?sample:(sample-3)%3;
                float age=sample<3?new int[]{62,74,88}[sample]
                        :TwoIArcanaTimeline.bookSlamTick(wave)+new int[]{-4,0,7}[phase];
                float presence=TwoIArcanaTimeline.presence(age);
                var source=new CaptureSource(mc);source.group="actual_book_portals_and_tubes";
                var mesh=new TwoIArcanaRenderer.Mesh(new PoseStack(),source.getBuffer(RenderType.entityTranslucentEmissive(TwoIArcanaRenderer.SCARLET_ATLAS,false)),true);
                resetBookGeometryCounters();tentacles.invoke(null,mesh,eye,center,aim,forward,age,presence);
                int actual=source.buckets.stream().mapToInt(b->b.consumer.rows.size()).sum();
                if(actual!=TwoIArcanaRenderer.lastPortalVertices()+TwoIArcanaRenderer.lastTentacleVertices())
                    throw new AssertionError("Production portal/tube accounting does not equal actual emitted vertices age="+age);
                var parameters=new LinkedHashMap<String,Object>();
                parameters.put("pose_sampling_only",true);parameters.put("spell_age",age);parameters.put("presence",presence);
                parameters.put("stage",sample<3?new String[]{"opening","empty_open","emergence"}[phase]
                        :new String[]{"windup","contact","rebound"}[phase]);
                parameters.put("wave",wave);parameters.put("shared_wave_tick",wave<0?-1:TwoIArcanaTimeline.bookSlamTick(wave));
                parameters.put("portal_count",TwoIArcanaRenderer.lastPortalCount());parameters.put("portal_vertices",TwoIArcanaRenderer.lastPortalVertices());
                parameters.put("opening_only_portals",TwoIArcanaRenderer.lastPortalOpeningOnlyCount());
                parameters.put("tube_count",TwoIArcanaRenderer.lastTentacleCount());parameters.put("tube_and_landing_vertices",TwoIArcanaRenderer.lastTentacleVertices());
                parameters.put("min_portal_eye_gap",TwoIArcanaRenderer.lastPortalMinEyeGap());
                parameters.put("max_root_plane_error",TwoIArcanaRenderer.lastPortalMaxRootError());
                parameters.put("max_root_radius_ratio",TwoIArcanaRenderer.lastPortalMaxRootRadiusRatio());
                parameters.put("eye",List.of(eye.x-center.x,eye.y-center.y,eye.z-center.z));
                parameters.put("camera_origin",List.of(center.x,center.y,center.z));parameters.put("caster_origin",List.of(0,0,0));
                parameters.put("basis","actual camera translated to local origin, caster forward=(0,0,-1)");
                saveFrame("scarlet_portal_tentacles",sample<3?"opening":"wave_%02d".formatted(wave+1),phase,age,
                        parameters,source,Map.of("capture","Actual production portal/tube emission captured without reconstructed geometry; root cap, front lip, cavity and eight shared wave poses; live timeline and occlusion remain separate framebuffer checks"));
            }
        } finally {
            TwoIVisualConfig.QUALITY.set(quality);mc.options.particles().set(particles);
            setField(TwoIArcanaRenderer.class,"lastTentacles",priorTentacles);
            setField(TwoIArcanaRenderer.class,"lastTentacleVertices",priorTentacleVertices);
            setField(TwoIArcanaRenderer.class,"lastBookSlam",priorSlam);
            for(var saved:priorPortalState.entrySet())saved.getKey().set(null,saved.getValue());
        }
    }

    private static void resetBookGeometryCounters()throws Exception{
        setField(TwoIArcanaRenderer.class,"lastTentacles",0);
        setField(TwoIArcanaRenderer.class,"lastTentacleVertices",0);
        setField(TwoIArcanaRenderer.class,"lastBookSlam",-1);
    }

    /** Optional mods are accessed through the actual registered renderer. They
     * remain optional and their own geometry/texture is never copied to main. */
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void exportExternal(Minecraft mc)throws Exception {
        for(String id:List.of("longhuan:longhuan_doll","xiaoxiao:xiaoxiao_doll")) {
            var key=ResourceLocation.parse(id);
            if(!BuiltInRegistries.ENTITY_TYPE.containsKey(key))continue;
            Entity entity=BuiltInRegistries.ENTITY_TYPE.get(key).create(mc.level);
            if(!(entity instanceof LivingEntity living))continue;
            entity.setPos(mc.player.getX(),mc.player.getY(),mc.player.getZ());
            EntityRenderer renderer=mc.getEntityRenderDispatcher().getRenderer(entity);
            for(int frame=0;frame<8;frame++) {
                entity.tickCount=frame*12;living.yBodyRot=living.yBodyRotO=0;living.yHeadRot=living.yHeadRotO=0;
                entity.setYRot(0);entity.yRotO=0;entity.setXRot(0);entity.xRotO=0;
                var source=new CaptureSource(mc);source.group="registered_entity_renderer";
                renderer.render(entity,0,0,new PoseStack(),source,LIGHT);
                saveFrame(key.getNamespace(),"actual_entity_idle",frame,entity.tickCount,
                        Map.of("registry_id",id,"renderer_class",renderer.getClass().getName(),"unspawned_client_fixture",true),source,Map.of());
            }
        }
    }

    private static void saveFrame(String model,String action,int frame,float time,Map<String,?> parameters,CaptureSource source,Map<String,?> rig)throws Exception {
        List<Map<String,Object>> groups=source.finish();int count=groups.stream().mapToInt(g->((List<?>)g.get("vertices")).size()).sum();
        if(count==0)throw new AssertionError("Empty actual renderer mesh: "+model+"/"+action);
        String relative="frames/%03d-%s-%s-%02d.json".formatted(frameIndex++,model,action,frame);
        var data=new LinkedHashMap<String,Object>();data.put("schema","two_i_runtime_mesh/1");
        data.put("model",model);data.put("action",action);data.put("frame",frame);data.put("time",time);
        data.put("parameters",parameters);data.put("vertex_count",count);data.put("groups",groups);data.put("rig",rig);
        writeNew(directory.resolve(relative),data);
        frames.add(Map.of("file",relative,"model",model,"action",action,"frame",frame,"time",time,"vertex_count",count,"group_count",groups.size()));
    }

    private static void writeManifest(boolean external)throws IOException {
        var data=new LinkedHashMap<String,Object>();data.put("schema","two_i_runtime_mesh/1");
        data.put("capture","Actual production model methods and registered entity renderers, custom CPU-only VertexConsumer/MultiBufferSource; no approximated geometry");
        data.put("coordinates","Minecraft renderer local coordinates, +Y up; no post-capture axis conversion");
        data.put("vertex_layout",List.of("x","y","z","u","v","r","g","b","a","nx","ny","nz","overlay_u","overlay_v","light_u","light_v"));
        data.put("color_encoding","integer RGBA 0..255, preserve per corner and multiply with sampled texture");
        data.put("uv_encoding","Actual Minecraft UV; image top is v=0. Blender importer must account for image-origin convention");
        data.put("topology","Each material/group preserves RenderType.mode and emitted vertex order; QUADS use consecutive groups of four; do not weld seams before UV/color assignment");
        data.put("animation","Exact independent posed meshes, ordered by model/action/frame; rig snapshots record current local ModelPart transforms in model-pixel units, Euler radians; outer renderer transforms already baked into vertices");
        data.put("shader_boundary","Shader galaxy/black hole, floor takeover and reflections are not 3-D mesh exports. These require actual runtime framebuffer evidence; only finite emitted stage/tube geometry is captured here");
        data.put("book_portal_capture_schema","detached_portal_tube_poses/1");
        data.put("astral_staff_capture_schema","physical_clock_and_actual_item_contexts/1");
        data.put("external_pass_finished",external);data.put("frames",frames);data.put("textures",textures);
        // Core and optional captures intentionally produce separate manifests,
        // retaining the original evidence if a later optional mod fails.
        writeNew(directory.resolve(external?"manifest-with-optional.json":"manifest.json"),data);
    }
    private static void writeNew(Path path,Object value)throws IOException {
        try(var writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){JSON.toJson(value,writer);}
    }

    private static Map<String,Object> partTree(String name,ModelPart part)throws Exception {
        var data=new LinkedHashMap<String,Object>();data.put("name",name);
        data.put("translation_pixels",new float[]{part.x,part.y,part.z});
        data.put("rotation_radians",new float[]{part.xRot,part.yRot,part.zRot});
        data.put("scale",new float[]{part.xScale,part.yScale,part.zScale});
        data.put("visible",part.visible);data.put("skip_draw",part.skipDraw);
        var initial=part.getInitialPose();data.put("initial_translation_pixels",new float[]{initial.x,initial.y,initial.z});
        data.put("initial_rotation_radians",new float[]{initial.xRot,initial.yRot,initial.zRot});
        List<Map<String,Object>> children=new ArrayList<>();
        @SuppressWarnings("unchecked") var values=(Map<String,ModelPart>)field(part,"children");
        for(var entry:new TreeMap<>(values).entrySet())children.add(partTree(entry.getKey(),entry.getValue()));
        data.put("children",children);return data;
    }
    private static Map<String,Object> tailRig(Object tail)throws Exception {
        var data=new LinkedHashMap<String,Object>();
        for(String name:List.of("pitch","yaw","rootY","rootRoll","rootPitch","rootYaw","heightScale","ribbonPitch","ribbonRoll"))data.put(name,field(tail,name));
        for(String name:List.of("matrix","attachment","ribbonMatrix"))data.put(name,((Matrix4f)field(tail,name)).get(new float[16]));
        data.put("skin_matrices_column_major",Arrays.stream((Matrix4f[])field(tail,"skin")).map(m->m.get(new float[16])).toList());
        return data;
    }
    private static Object field(Object target,String name)throws Exception {
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try {
            Field field=c.getDeclaredField(name);field.setAccessible(true);return field.get(target instanceof Class<?>?null:target);
        }catch(NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName()+"."+name);
    }
    private static void setField(Object target,String name,Object value)throws Exception {
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try {
            Field field=c.getDeclaredField(name);field.setAccessible(true);field.set(target instanceof Class<?>?null:target,value);return;
        }catch(NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName()+"."+name);
    }

    private static ResourceLocation texture(RenderType type)throws Exception {
        Object state=field(type,"state"),textureState=field(state,"textureState");
        try {
            Object value=field(textureState,"texture");
            if(value instanceof Optional<?> optional&&optional.orElse(null) instanceof ResourceLocation texture)return texture;
        }catch(NoSuchFieldException ignored) { }
        // The core paths are single-texture render types. A surprising optional
        // material should fail explicitly instead of silently using a wrong PNG.
        throw new IllegalStateException("Unresolved texture for actual render type "+type);
    }
    private static String textureFile(Minecraft mc,ResourceLocation id)throws Exception {
        var existing=textures.get(id.toString());if(existing!=null)return (String)existing.get("file");
        String relative="textures/"+id.getNamespace()+"--"+id.getPath().replace('/','_')+(id.getPath().endsWith(".png")?"":".png");
        Path file=directory.resolve(relative);
        var resource=mc.getResourceManager().getResource(id);
        if(resource.isPresent())try(var stream=resource.get().open()){Files.copy(stream,file);}
        else if(mc.getTextureManager().getTexture(id) instanceof DynamicTexture dynamic&&dynamic.getPixels()!=null)dynamic.getPixels().writeToFile(file);
        else throw new IOException("No actual resource pixels for render material "+id);
        byte[] bytes=Files.readAllBytes(file);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        textures.put(id.toString(),Map.of("file",relative,"sha256",hash,"bytes",bytes.length));return relative;
    }

    /** Deliberately independent of Minecraft BufferSource: requesting the next
     * material never flushes or invalidates a retained consumer. */
    private static final class CaptureSource implements MultiBufferSource {
        private final Minecraft mc;
        private final List<Bucket> buckets=new ArrayList<>();
        private String group="model";
        CaptureSource(Minecraft mc){this.mc=mc;}
        @Override public VertexConsumer getBuffer(RenderType type) {
            for(var b:buckets)if(b.type==type&&b.group.equals(group))return b.consumer;
            Bucket b=new Bucket(group,type);buckets.add(b);return b.consumer;
        }
        List<Map<String,Object>> finish()throws Exception {
            List<Map<String,Object>> result=new ArrayList<>();
            for(var b:buckets) {
                if(b.consumer.rows.isEmpty())continue;
                String mode=b.type.mode().name();int divisor=mode.equals("QUADS")?4:mode.equals("TRIANGLES")?3:1;
                if(b.consumer.rows.size()%divisor!=0)throw new AssertionError("Incomplete actual "+mode+" primitive in "+b.group);
                ResourceLocation texture=texture(b.type);
                result.add(Map.of("name",b.group+"/"+b.type.name,"render_type",b.type.toString(),"mode",mode,
                        "texture",texture.toString(),"texture_file",textureFile(mc,texture),"vertices",b.consumer.rows));
            }
            return result;
        }
    }
    /** This facade only satisfies the production stage's concrete signature.
     * The superclass constructor stores its arguments without using them. All
     * public buffer/flush methods are overridden, so no BufferBuilder, native
     * allocation, render state, fallback flush or GPU draw can be reached. */
    private static final class CaptureBufferSource extends MultiBufferSource.BufferSource {
        private final CaptureSource capture;
        CaptureBufferSource(CaptureSource capture){super(null,new LinkedHashMap<>());this.capture=capture;}
        @Override public VertexConsumer getBuffer(RenderType type){return capture.getBuffer(type);}
        @Override public void endLastBatch(){ }
        @Override public void endBatch(){ }
        @Override public void endBatch(RenderType type){ }
    }
    private record Bucket(String group,RenderType type,CaptureConsumer consumer) {
        Bucket(String group,RenderType type){this(group,type,new CaptureConsumer());}
    }
    private static final class CaptureConsumer implements VertexConsumer {
        private final List<float[]> rows=new ArrayList<>();
        private float[] current;
        @Override public VertexConsumer addVertex(float x,float y,float z) {
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new AssertionError("Nonfinite emitted production vertex");
            current=new float[]{x,y,z,0,0,255,255,255,255,0,0,1,0,0,0,0};rows.add(current);return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a){current[5]=r;current[6]=g;current[7]=b;current[8]=a;return this;}
        @Override public VertexConsumer setUv(float u,float v){current[3]=u;current[4]=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v){current[12]=u;current[13]=v;return this;}
        @Override public VertexConsumer setUv2(int u,int v){current[14]=u;current[15]=v;return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){current[9]=x;current[10]=y;current[11]=z;return this;}
    }
}
