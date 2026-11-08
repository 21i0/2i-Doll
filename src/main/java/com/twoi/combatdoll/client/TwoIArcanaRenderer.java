package com.twoi.combatdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.config.TwoIVisualConfig;
import com.twoi.combatdoll.combat.TwoIArcanaTimeline;
import com.twoi.combatdoll.combat.DollCombatAccess;
import com.twoi.combatdoll.combat.LonghuanBookFlightGoal;
import com.twoi.combatdoll.client.compat.LegacyStarfieldVisuals;
import com.twoi.combatdoll.companion.client.render.TwoIStarfieldEffects;
import com.twoi.combatdoll.companion.dimension.world.TwoIStarfieldDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.IdentityHashMap;

/** Bounded world meshes; the scarlet codex has its own art, separate from the stellar staff. */
@EventBusSubscriber(modid = TwoICombatDollMod.MODID, value = Dist.CLIENT)
public final class TwoIArcanaRenderer {
    public static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,
            "textures/effect/two_i_arcana.png");
    public static final ResourceLocation SCARLET_ATLAS = ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,
            "textures/effect/scarlet_codex.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(ATLAS, false);
    private static final RenderType SCARLET_TYPE = RenderType.entityTranslucentEmissive(SCARLET_ATLAS, false);
    private static final List<LivingEntity> CASTERS = new ArrayList<>(4);
    // Supporting books are ordinary companion geometry, independent of the
    // four expensive simultaneous spell scenes allowed by CASTERS.
    private static final List<LivingEntity> BOOK_RIDERS = new ArrayList<>();
    private static final int MINT = 0xA0FFDB, WHITE = 0xE4FFF8, CYAN = 0x82CFFF, LILAC = 0xC6A0FF;
    private static final float[] EYE = {0, 0, 1, .5F}, SEAL = {0, .5F, .5F, 1};
    private static final float[] SPARK = {.5F, .5F, .625F, .625F};
    private static final float[] COVER = {.5F, .75F, .75F, 1}, PAGE = {.75F, .75F, 1, 1};
    private static final float[] SOLID = {.504F, .635F, .51F, .641F};
    private static final float[] RED_COVER = {0, 0, .5F, .5F};
    private static final float[] PARCHMENT = {.5F, 0, 1, 368F/1024};
    private static final float[] HEX_EYE = {0, .5F, .5F, 1};
    private static final float[] IRIS = {896F/1024, 384F/1024, 1, .5F};
    private static final float[] ROSE = {.5F, .5F, 1, 1};
    private static final float[] RED_SOLID = {1020F/1024, 1020F/1024, 1020F/1024, 1020F/1024};
    private static final int SCARLET = 0xD52646, IVORY = 0xF3C7B1;
    private static final IdentityHashMap<LivingEntity,RoseBed> ROSE_BEDS = new IdentityHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private static int lastVertices;
    private static int lastTentacles, lastTentacleVertices, lastBookSlam = -1;
    private static int lastPortals,lastPortalVertices,lastPortalOpeningOnly;
    private static float lastPortalEyeGap=Float.POSITIVE_INFINITY,lastPortalRootError,lastPortalRootRadius;
    // Deliberately separate, asymmetric openings. These are not anchored to
    // the eyelid or the seal's vertices; depth offsets give real parallax.
    private static final float[][] PORTALS={
            {-20,8,2},{18,11,0},{-27,-3,3},{25,-5,-1},
            {-10,21,-1},{12,23,3},{-33,14,5},{33,16,5}};
    public static float spellMode, spellAge=-1, spellChargeExtra;
    private TwoIArcanaRenderer() { }
    public static int lastVertices() { return lastVertices; }
    public static int lastTentacleCount() { return lastTentacles; }
    public static int lastTentacleVertices() { return lastTentacleVertices; }
    public static int lastBookSlamIndex() { return lastBookSlam; }
    public static int lastPortalCount(){return lastPortals;}
    public static int lastPortalVertices(){return lastPortalVertices;}
    public static int lastPortalOpeningOnlyCount(){return lastPortalOpeningOnly;}
    public static float lastPortalMinEyeGap(){return lastPortals==0?0:lastPortalEyeGap;}
    public static float lastPortalMaxRootError(){return lastPortalRootError;}
    public static float lastPortalMaxRootRadiusRatio(){return lastPortalRootRadius;}
    private static void resetPortals(){
        lastPortals=lastPortalVertices=lastPortalOpeningOnly=0;
        lastPortalEyeGap=Float.POSITIVE_INFINITY;lastPortalRootError=lastPortalRootRadius=0;
    }
    public static int floorFrames() { return ArcanaWorldPass.floorFrames(); }
    public static boolean skyTaken() {
        if(!TwoIArcanaPost.enabled()||TwoIVisualConfig.STRENGTH.get()<=.001)return false;
        if(ClientTimeStop.active())return true;
        var player=Minecraft.getInstance().player;if(player==null)return false;
        for(var doll:CASTERS)if(!doll.isRemoved()&&doll.distanceToSqr(player)<72*72
                &&takeover(doll,age(doll,0))>.1F)return true;
        return false;
    }
    public static float showAge(float partial) {
        var level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(TwoIStarfieldDimensions.KEY)
                ? TwoIStarfieldEffects.showAge(partial) : LegacyStarfieldVisuals.showAge(level, partial);
    }
    private static float age(LivingEntity doll, float partial) { return DollCombatAccess.state(doll).age(doll, partial); }
    private static float takeover(LivingEntity doll,float age){
        var kind=DollCombatAccess.state(doll).kind();
        if(kind==com.twoi.combatdoll.combat.WeaponKind.AXOLOTL)return TwoIArcanaTimeline.presence(age)*TwoIArcanaTimeline.smooth(4,32,age);
        return kind==com.twoi.combatdoll.combat.WeaponKind.STAFF?TwoIArcanaTimeline.staffTakeover(age):TwoIArcanaTimeline.takeover(age);
    }
    public static int quality() {
        ParticleStatus particles = Minecraft.getInstance().options.particles().get();
        return Math.min(TwoIVisualConfig.QUALITY.get(), particles == ParticleStatus.MINIMAL ? 1
                : particles == ParticleStatus.DECREASED ? 2 : 3);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (lastLevel != mc.level) {
            CASTERS.clear(); BOOK_RIDERS.clear(); ROSE_BEDS.clear(); lastLevel = mc.level;
            TwoIArcanaPost.close();
        }
        if (mc.level == null || mc.player == null) return;
        if (ClientCombatClock.now() % 5 != 0) return;
        CASTERS.clear();
        BOOK_RIDERS.clear();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity rider && LonghuanBookFlightGoal.riding(rider)
                    && rider.distanceToSqr(mc.player) < 80 * 80)
                BOOK_RIDERS.add(rider);
            if (entity instanceof LivingEntity doll && (doll instanceof DollCombatAccess || doll instanceof net.minecraft.world.entity.player.Player)
                    && DollCombatAccess.state(doll).armed() && (doll instanceof DollCombatAccess || age(doll,0)>=0)
                    && age(doll,0) < TwoIArcanaTimeline.DURATION && doll.distanceToSqr(mc.player) < 80 * 80)
                CASTERS.add(doll);
        }
        CASTERS.sort(Comparator.comparingDouble((LivingEntity doll) -> age(doll,0)>=0?0:1)
                .thenComparingDouble(doll -> doll.distanceToSqr(mc.player)));
        if (CASTERS.size() > 4) CASTERS.subList(4, CASTERS.size()).clear();
        ROSE_BEDS.keySet().removeIf(entity -> !CASTERS.contains(entity));
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        boolean terrain = event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        if (!terrain && event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        boolean garden = TwoIVisualConfig.GARDEN.get() && (mc.level.dimension().equals(TwoIStarfieldDimensions.KEY)
                || LegacyStarfieldVisuals.isGarden(mc.level));
        if (!garden && CASTERS.isEmpty() && BOOK_RIDERS.isEmpty() && !ClientTimeStop.active()) {
            lastVertices=lastTentacles=lastTentacleVertices=0;lastBookSlam=-1;resetPortals();return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float time = (ClientCombatClock.now() % 240_000L) + partial;
        float domain = ClientTimeStop.active()?1:0, impact = 0, takeover = 0, mainAge = ClientTimeStop.age();
        float selectedWeight = -1, floorStyle = 1;
        float chargeExtra=0;
        spellMode=ClientTimeStop.active()?1:0;
        Vec3 mainEye = Vec3.ZERO, mainNormal = new Vec3(0,0,-1), mainGround = Vec3.ZERO,mainAnchor=Vec3.ZERO;
        Vec3 camera = event.getCamera().getPosition();
        for (LivingEntity doll : CASTERS) {
            float age = age(doll,partial);
            if (age < 0 || age >= TwoIArcanaTimeline.DURATION || doll.isRemoved()) continue;
            float proximity = 1 - TwoIArcanaTimeline.smooth(30,72,(float)camera.distanceTo(doll.position()));
            float weight = TwoIArcanaTimeline.presence(age) * proximity;
            // Global time stop intensity must never take part in selecting the local
            // caster. Otherwise easing/proximity < 1 leaves Eye and Ground at zero.
            if (weight > selectedWeight) {
                selectedWeight = weight; mainAge = age;spellMode=DollCombatAccess.state(doll).kind().ordinal();
                floorStyle = doll instanceof DollCombatAccess access ? access.combatKind().ordinal() : spellMode;
                var state=DollCombatAccess.state(doll);
                if(state.kind()==com.twoi.combatdoll.combat.WeaponKind.STAFF){
                    Vec3 hole=BlackHoleMotion.sample(doll,state,partial);
                    mainEye=hole.subtract(camera);mainAnchor=state.stageAnchor(doll).subtract(camera);
                    mainGround=state.sceneOrigin(doll).subtract(camera);chargeExtra=state.chargeExtra(doll,partial);
                }else{
                    mainEye=doll.getPosition(partial).add(direction(doll).scale(14)).add(0,18,0).subtract(camera);
                    mainAnchor=mainEye;mainGround=doll.getPosition(partial).subtract(camera);chargeExtra=0;
                }
                mainNormal = mainEye.normalize().scale(-1);
                takeover = takeover(doll,age) * proximity;
            }
            var selectedKind=DollCombatAccess.state(doll).kind();
            float strike=selectedKind==com.twoi.combatdoll.combat.WeaponKind.BOOK
                    ?TwoIArcanaTimeline.bookSlamStrength(age):selectedKind==com.twoi.combatdoll.combat.WeaponKind.AXOLOTL
                    ?AxolotlDreamScene.impact(age):TwoIArcanaTimeline.impact(age);
            impact = Math.max(impact,strike*proximity);
        }
        domain = Math.max(domain, Math.max(0, selectedWeight));
        // Replace the sky before drawing luminous meshes; do not overwrite those meshes with a sky mask.
        spellAge=mainAge;spellChargeExtra=chargeExtra;
        if (selectedWeight > .001F)
            TwoIArcanaPost.world(domain, takeover, mainAge, impact, time, mainEye, mainNormal, mainGround,mainAnchor,chargeExtra, floorStyle, terrain, event);
        if (terrain) return;
        lastTentacles=lastTentacleVertices=0;lastBookSlam=-1;
        resetPortals();
        PoseStack pose = event.getPoseStack(); pose.pushPose();
        try {
            // AFTER_LEVEL is dispatched after vanilla has popped its view matrix.
            pose.mulPose(event.getModelViewMatrix());
            int total=0;
            // These RenderTypes use the shared immediate buffer. Finish one
            // atlas before requesting the other: holding both consumers would
            // leave the first BufferBuilder already ended ("Not building!").
            for(int pass=0;pass<2;pass++){
                boolean scarlet=pass==1;RenderType type=scarlet?SCARLET_TYPE:TYPE;
                Mesh mesh=new Mesh(pose,mc.renderBuffers().bufferSource().getBuffer(type),scarlet);
                if(garden&&!scarlet)garden(mesh,time,camera);
                int i=0;
                for(LivingEntity doll:CASTERS){
                    if(doll.isRemoved()||doll.level()!=mc.level)continue;
                    if(DollCombatAccess.state(doll).kind()==com.twoi.combatdoll.combat.WeaponKind.AXOLOTL)continue;
                    boolean book=DollCombatAccess.state(doll).kind()==com.twoi.combatdoll.combat.WeaponKind.BOOK;
                    if(book!=scarlet)continue;
                    float age=age(doll,partial);
                    if(age<0){orbitWeapon(mesh,doll,time,partial);continue;}
                    if(age>=TwoIArcanaTimeline.DURATION)continue;
                    if(!TwoIArcanaPost.worldAvailable()){
                        float alpha=TwoIArcanaTimeline.takeover(age);
                        if(book)floorCracks(mesh,doll.getPosition(partial),time,alpha);
                        else floorWater(mesh,doll.getPosition(partial),time,alpha);
                    }
                    if(book)spell(mesh,doll,age,time,partial,i++<2);
                    else upperSpell(mesh,doll,age,time,partial);
                }
                if(scarlet)for(LivingEntity rider:BOOK_RIDERS)
                    if(!rider.isRemoved()&&rider.level()==mc.level&&LonghuanBookFlightGoal.riding(rider))
                        riddenBook(mesh,rider,time,partial);
                total+=mesh.vertices;mc.renderBuffers().bufferSource().endBatch(type);
            }
            // The staff uses its own pixel atlas. Request it only after both
            // scene consumers have been fully ended, then finish it before AX.
            total+=renderStaffWeapons(pose,mc.renderBuffers().bufferSource(),time,partial,camera);
            for(LivingEntity doll:CASTERS){
                if(!doll.isRemoved()&&doll.level()==mc.level&&DollCombatAccess.state(doll).kind()==com.twoi.combatdoll.combat.WeaponKind.AXOLOTL){
                    float sceneAge=age(doll,partial);
                    if(sceneAge>=0&&sceneAge<TwoIArcanaTimeline.DURATION)
                        total+=AxolotlDreamScene.render(pose,mc.renderBuffers().bufferSource(),doll,sceneAge,time,partial,camera);
                }
            }
            mc.renderBuffers().bufferSource().endBatch();
            lastVertices=total;
        } finally { pose.popPose(); }
        TwoIArcanaPost.render(domain, garden ? 1 : 0, impact, time);
    }

    private static void orbitWeapon(Mesh m,LivingEntity actor,float time,float partial){
        if(DollCombatAccess.state(actor).kind()!=com.twoi.combatdoll.combat.WeaponKind.BOOK)return;
        if(LonghuanBookFlightGoal.riding(actor))return;
        double a=time*.025+actor.getId();
        Vec3 at=actor.getPosition(partial).add(Math.cos(a)*.68,.9+Math.sin(a*1.3)*.12,Math.sin(a)*.68);
        m.pose.pushPose();m.at(at);m.pose.mulPose(Axis.YP.rotation((float)-a));
        m.book(time,1,.72F,.08F);
        m.pose.popPose();
    }

    /** The same single book slides forward to cast and returns underneath its rider. */
    private static void riddenBook(Mesh m,LivingEntity actor,float time,float partial){
        float age=age(actor,partial);
        float cast=age>=0&&age<TwoIArcanaTimeline.DURATION
                ?TwoIArcanaTimeline.smooth(0,30,age)*(1-TwoIArcanaTimeline.smooth(184,TwoIArcanaTimeline.DURATION,age)):0;
        Vec3 forward=direction(actor);
        float body=Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot);
        float idleYaw=-body;
        float spellYaw=(float)Math.toDegrees(Math.atan2(forward.x,forward.z));
        float size=Mth.lerp(cast,1.15F,1.35F);
        // The spine bands reach z=.079, slightly above the flat cover at.072.
        double seatY=-.079*1.15-.01;
        Vec3 local=new Vec3(forward.x*.65*cast,
                Mth.lerp(cast,seatY,.72+Math.sin(time*.065)*.045),forward.z*.65*cast);
        m.pose.pushPose();m.at(actor.getPosition(partial));
        com.customdoll.longhuan.client.DollDanceClient.transform(m.pose,
                com.customdoll.longhuan.client.DollDanceClient.elapsed(actor,partial),.45F);
        m.pose.translate(local.x,local.y,local.z);
        m.pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(cast,idleYaw,spellYaw)));
        m.pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(cast,-90,-12)));
        // The two flat halves span [-.615,.205], with their spine at -.205.
        m.pose.translate(.205F*size*(1-cast),0,0);
        m.book(time,1,size,1,Mth.lerp(cast,180,155));
        m.pose.popPose();
    }
    private static int renderStaffWeapons(PoseStack pose,net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
                                          float time,float partial,Vec3 camera){
        int vertices=0;
        VertexConsumer out=null;
        for(LivingEntity actor:CASTERS){
            if(actor.isRemoved()||actor.level()!=Minecraft.getInstance().level
                    ||DollCombatAccess.state(actor).kind()!=com.twoi.combatdoll.combat.WeaponKind.STAFF)continue;
            // The first-person item renderer already draws the held staff.
            // Do not put a second full-size world model in the camera's face.
            var mc=Minecraft.getInstance();
            if(actor==mc.player&&mc.options.getCameraType().isFirstPerson()
                    &&(mc.player.getMainHandItem().is(TwoICombatDollMod.STAFF.get())
                    ||mc.player.getOffhandItem().is(TwoICombatDollMod.STAFF.get())))continue;
            float age=age(actor,partial);
            if(age>=TwoIArcanaTimeline.DURATION)continue;
            if(out==null)out=buffers.getBuffer(AstralEdictModel.TYPE);
            pose.pushPose();
            float localTime=time,alpha=1,size=.72F;
            Vec3 at;
            if(age<0){
                double a=time*.025+actor.getId();
                at=actor.getPosition(partial).add(Math.cos(a)*.68,.9+Math.sin(a*1.3)*.12,Math.sin(a)*.68);
                pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
                pose.mulPose(Axis.YP.rotation((float)-a));
            }else{
                // celestialAge accelerates backwards quadratically at the end.
                // It belongs to the planets/clock, not a physical handheld staff:
                // feeding it to bob/ornament sin() made this weapon jerk rapidly.
                // Keep its own client presentation clock and only fade by age.
                Vec3 forward=direction(actor);
                at=actor.getPosition(partial).add(forward.scale(.8)).add(0,1.2+Math.sin(localTime*.06)*.06,0);
                pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
                pose.mulPose(Axis.YP.rotation((float)Math.atan2(forward.x,forward.z)));
                pose.mulPose(Axis.ZP.rotationDegrees(-18+TwoIArcanaTimeline.smooth(12,60,age)*36));
                alpha=TwoIArcanaTimeline.presence(age);size=1.12F;
            }
            vertices+=AstralEdictModel.render(pose,out,localTime,alpha,size);
            pose.popPose();
        }
        if(out!=null)buffers.endBatch(AstralEdictModel.TYPE);
        return vertices;
    }
    private static void upperSpell(Mesh m,LivingEntity actor,float age,float time,float partial){
        time=TwoIArcanaTimeline.celestialAge(age,DollCombatAccess.state(actor).chargeExtra(actor,partial));
        float p=TwoIArcanaTimeline.presence(age),hit=TwoIArcanaTimeline.impact(age);
        Vec3 center=actor.getPosition(partial),aim=DollCombatAccess.state(actor).target(actor);
        Vec3 forward=direction(actor);int gold=0xFFE4A8;
        for(int ring=0;ring<3;ring++){
            m.pose.pushPose();m.at(center.add(0,.04+ring*.04,0));
            m.horizontalSeal((1.2F+ring*.9F)*TwoIArcanaTimeline.smooth(ring*8,35+ring*10,age),time*(ring%2==0?.22F:-.18F),ring==1?LILAC:gold,p*.75F);
            m.pose.popPose();
        }
        // Floating stellar fragments gather close to the caster. The sky remains the black hole,
        // with the ornate clock inside its horizon rather than another enormous outer dial.
        for(int i=0;i<8+quality()*4;i++){
            double a=i*2.39996+time*.008;float r=.8F+(i%3)*.22F;
            Vec3 at=center.add(Math.cos(a)*r,.2+(i%7)*.22+Math.sin(time*.04+i)*.06,Math.sin(a)*r);
            m.star(at,.018F+(i%3)*.008F,i%3==0?gold:LILAC,p*.7F);
        }
        // The verdict descends as a multi-layer spear rather than the book's single green ray.
        if(hit>.002F){
            Vec3 top=aim.add(0,30,0);
            m.beam(top,aim,.20F+hit*.7F,0xBBA5FF,hit*.9F);
            m.beam(top,aim,.10F,WHITE,hit);
            for(int i=0;i<12;i++){
                double a=i*Math.PI/6;double r=1+(age-TwoIArcanaTimeline.IMPACT)*.28;
                Vec3 at=aim.add(Math.cos(a)*r,.1,Math.sin(a)*r);
                m.beam(at,at.add(0,3*hit+.5,0),.035F,gold,hit);
            }
            m.pose.pushPose();m.at(new Vec3(aim.x,center.y+.045,aim.z));
            m.horizontalSeal(2+(age-TwoIArcanaTimeline.IMPACT)*.36F,time*2,gold,hit);m.pose.popPose();
        }
    }

    private static Vec3 direction(LivingEntity doll) {
        Vec3 direction = DollCombatAccess.state(doll).target(doll).subtract(doll.position()).multiply(1,0,1).normalize();
        return direction.lengthSqr() < .1 ? new Vec3(0,0,1) : direction;
    }

    /** Low-cost world geometry keeps the floor takeover attached to the entity's feet. */
    private static void floorWater(Mesh m, Vec3 center, float time, float alpha) {
        // The staff takeover is a shallow reflective pool, not a small magic circle.
        // Keep it as bounded line geometry so it remains cheap even when several dolls cast.
        int rings = quality() == 1 ? 6 : 9;
        int segments = quality() == 1 ? 20 : 30;
        float extent = quality() == 1 ? 14.5F : 21.5F;
        m.pose.pushPose();
        m.at(center.add(0, .085, 0));
        // A soft body layer keeps the takeover readable even on dark stone. The
        // ripple lines above it carry the circular shape and hide the square texel
        // boundary at normal viewing distance.
        m.horizontalSolid(extent * 1.02F, 0x081451, alpha * .52F, time * .008F);
        m.horizontalSolid(extent * .72F, 0x28318F, alpha * .28F, -time * .011F);
        m.horizontalSeal(extent * 1.02F, time * .018F, 0x4B8DFF, alpha * .52F);
        m.horizontalSeal(extent * .78F, -time * .026F, 0xB7C9FF, alpha * .28F);
        m.pose.popPose();
        // The floor takeover needs a real translucent body.  Use the atlas' tiny
        // opaque texel for the fan so the pool remains visible over any block
        // texture; the old UV sample landed in transparent atlas space and made
        // the broad water layer disappear in-game.
        m.waterDisc(center.add(0, .050, 0), extent, 36, 0x08164E, alpha * .52F);
        m.waterDisc(center.add(0, .054, 0), extent * .74F, 30, 0x263B91, alpha * .28F);
        m.waterDisc(center.add(0, .058, 0), extent * .42F, 24, 0x6674E5, alpha * .18F);
        for (int ring = 0; ring < rings; ring++) {
            float radius = .62F + ring * (extent - .62F) / rings
                    + Mth.sin(time * (.035F + ring * .006F) + ring * 1.7F) * (.055F + ring * .012F);
            float ringAlpha = alpha * (.82F - ring * .075F);
            int color = ring % 3 == 0 ? 0x67C8FF : ring % 3 == 1 ? 0xB8D8FF : 0x958DFF;
            for (int segment = 0; segment < segments; segment++) {
                double a = segment * Math.PI * 2 / segments + time * .004 * (ring % 2 == 0 ? 1 : -1);
                double b = (segment + 1) * Math.PI * 2 / segments + time * .004 * (ring % 2 == 0 ? 1 : -1);
                float wobbleA = 1F + Mth.sin((float) a * 5F + time * .022F) * .018F;
                float wobbleB = 1F + Mth.sin((float) b * 5F + time * .022F) * .018F;
                Vec3 p = center.add(Math.cos(a) * radius * wobbleA, .058, Math.sin(a) * radius * wobbleA);
                Vec3 q = center.add(Math.cos(b) * radius * wobbleB, .058, Math.sin(b) * radius * wobbleB);
                m.beam(p, q, .017F + ring * .0025F, color, ringAlpha);
                if (segment % 4 == 0) {
                    Vec3 glint = center.add(Math.cos(a) * (radius + .045F), .065, Math.sin(a) * (radius + .045F));
                    m.beam(glint, glint.add(0, .012, 0), .024F, WHITE, ringAlpha * .78F);
                }
            }
        }
        // Broken radial reflections make the surface read as water from every camera angle.
        int rays = quality() == 1 ? 8 : 13;
        for (int i = 0; i < rays; i++) {
            double a = i * Math.PI * 2 / rays + time * .006;
            float start = .18F + (i % 3) * .22F;
            float end = extent * (.62F + (i % 4) * .075F);
            Vec3 p = center.add(Math.cos(a) * start, .062, Math.sin(a) * start);
            Vec3 q = center.add(Math.cos(a) * end, .062, Math.sin(a) * end);
            m.beam(p, q, .010F, i % 3 == 0 ? 0xD0E7FF : 0x5B9CFF, alpha * .25F);
            if (i % 2 == 0) {
                Vec3 bend = center.add(Math.cos(a + .12) * (end * .72F), .064, Math.sin(a + .12) * (end * .72F));
                m.beam(q, bend, .007F, 0xB5C8FF, alpha * .18F);
            }
        }
    }

    private static void floorCracks(Mesh m, Vec3 center, float time, float alpha) {
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + time * .002;
            float reach = 1.6F + (i % 4) * .46F;
            Vec3 p = center.add(Math.cos(a) * .16, .032, Math.sin(a) * .16);
            Vec3 q = center.add(Math.cos(a) * reach, .032, Math.sin(a) * reach);
            m.beam(p, q, .018F, i % 3 == 0 ? IVORY : SCARLET, alpha * .62F);
            for (int branch = 1; branch < 3; branch++) {
                float along = reach * (branch * .28F);
                Vec3 base = center.add(Math.cos(a) * along, .034, Math.sin(a) * along);
                double side = a + (branch % 2 == 0 ? .72 : -.72);
                Vec3 end = base.add(Math.cos(side) * (.34F + branch * .08F), .004, Math.sin(side) * (.34F + branch * .08F));
                m.beam(base, end, .010F, SCARLET, alpha * .42F);
            }
        }
    }

    private static void spell(Mesh m, LivingEntity doll, float age, float time, float partial, boolean sky) {
        float presence = TwoIArcanaTimeline.presence(age), domain = TwoIArcanaTimeline.domain(age);
        Vec3 center = doll.getPosition(partial), aim = DollCombatAccess.state(doll).target(doll);
        Vec3 direction = direction(doll);
        // A thorn-bound leather codex opens from its spine, rather than a green
        // eye floating in front of two unrelated page planes.
        if(!LonghuanBookFlightGoal.riding(doll)) {
            m.pose.pushPose(); m.at(center.add(direction.scale(.65)).add(0, .72 + Math.sin(time * .065) * .045, 0));
            m.pose.mulPose(Axis.YP.rotation((float)Math.atan2(direction.x, direction.z)));
            m.pose.mulPose(Axis.XP.rotationDegrees(-12));
            m.book(time, presence, 1.35F,TwoIArcanaTimeline.smooth(0,38,age)); m.pose.popPose();
        }
        roses(m,doll,age,presence);
        // Sparse, slow embers leave room for the eye and the ground flowers.
        int motes = 6 + quality() * 3;
        for (int i = 0; i < motes; i++) {
            double a = i * 2.39996 + time * .009;
            double y = ((i * .618 + time * .002) % 1.0) * 2.2;
            m.star(center.add(Math.cos(a) * .8, y, Math.sin(a) * .8), .018F, i % 3 == 0 ? IVORY : SCARLET, presence * .55F);
        }
        Vec3 eye = center.add(direction.scale(14)).add(0, 18, 0);
        if (sky && domain > .001F && !TwoIArcanaPost.worldAvailable()) {
            m.pose.pushPose(); m.at(eye);
            m.pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            float reveal=TwoIArcanaTimeline.smooth(12,78,age);
            m.sprite(-22,-22,0,44,44,HEX_EYE,0xFFFFFF,domain*reveal,0);
            m.pose.popPose();
        }
        if(sky)tentacles(m,eye,center,aim,direction,age,presence);
    }

    /** Separate black cavities open first. Eight groups strike on the original
     * server clock; no limb grows from the eye, and only the server deals damage. */
    private static void tentacles(Mesh m,Vec3 eye,Vec3 center,Vec3 aim,Vec3 forward,float age,float presence){
        lastTentacles=lastTentacleVertices=0;resetPortals();
        int portals=quality()==1?4:quality()==2?6:8;
        Vec3 right=forward.cross(new Vec3(0,1,0)).normalize(),up=new Vec3(0,1,0);
        lastBookSlam=TwoIArcanaTimeline.bookSlamAt(age);
        Vec3[] curve=new Vec3[13];
        for(int portal=0;portal<portals;portal++){
            float born=58+(portal%4)*2;
            float open=TwoIArcanaTimeline.smooth(born,born+9,age)*(1-TwoIArcanaTimeline.smooth(214,220,age));
            if(open<.002F)continue;
            float emerge=TwoIArcanaTimeline.smooth(76+(portal%4)*2,85+(portal%4)*2,age)
                    *(1-TwoIArcanaTimeline.smooth(198,213,age));
            var location=PORTALS[portal];
            Vec3 mouth=eye.add(right.scale(location[0])).add(0,location[1],0).add(forward.scale(location[2]));
            // Fixed world-space mouths face the summoning field rather than
            // standing vertically like bowls seen from underneath. They keep
            // real side-view depth; this orientation never follows the camera.
            Vec3 normal=center.add(0,1.7,0).subtract(mouth).normalize();
            Vec3 baseU=up.cross(normal).normalize(),baseV=normal.cross(baseU).normalize();
            float radius=2.8F+(portal%3)*.35F;
            double turn=(portal%2==0?-.28:.35)+portal*.08;
            Vec3 axisU=baseU.scale(Math.cos(turn)).add(baseV.scale(Math.sin(turn)));
            Vec3 axisV=baseU.scale(-Math.sin(turn)).add(baseV.scale(Math.cos(turn)));
            int beforePortal=m.vertices;
            m.portal(mouth,axisU,axisV,normal,radius,open,presence,age,portal,false);
            lastPortalVertices+=m.vertices-beforePortal;lastPortals++;
            lastPortalEyeGap=Math.min(lastPortalEyeGap,(float)mouth.distanceTo(eye)-radius*1.18F);
            if(emerge<.002F)lastPortalOpeningOnly++;
            if(emerge>=.002F)for(int limb=0;limb<2;limb++){
                int beforeTube=m.vertices,i=portal*2+limb,group=(portal+limb*4)%8;
                float strike=TwoIArcanaTimeline.bookSlamTick(group);
                float down=TwoIArcanaTimeline.smooth(strike-7,strike,age);
                down=down*down*down*(1-TwoIArcanaTimeline.smooth(strike+1,strike+10,age));
                float lift=TwoIArcanaTimeline.smooth(strike-12,strike-7,age)*(1-down);
                float hand=limb==0?-1:1;
                Vec3 root=mouth.add(normal.scale(-.9)).add(axisU.scale(hand*.70));
                Vec3 delta=root.subtract(mouth);
                lastPortalRootError=Math.max(lastPortalRootError,(float)Math.abs(delta.dot(normal)+.9));
                lastPortalRootRadius=Math.max(lastPortalRootRadius,(float)Math.hypot(delta.dot(axisU),delta.dot(axisV))/radius);
                double sway=Math.sin(age*.042+i*1.73)*.65;
                // First tangent points OUT of the cavity; only then does the
                // limb arch and curl. It cannot be read as an eyelid attachment.
                Vec3 rest=mouth.add(normal.scale(6.5+(portal%3))).add(right.scale(hand*(3.7+sway)))
                        .add(0,-2.3+Math.sin(age*.036+i)*1.2+lift*5.5,0);
                Vec3 field=center.add(forward.scale(12));
                double a=i*2.39996323,r=2+(i%7)*.85;
                Vec3 ground=field.add(Math.cos(a)*r, .075,Math.sin(a)*r);
                Vec3 tip=rest.lerp(ground,down);
                Vec3 handle1=root.add(normal.scale(6.0)).add(axisU.scale(hand*.5)).add(0,lift*2,0);
                Vec3 handle2=rest.add(right.scale(hand*(3.0+sway))).add(0,4.8+lift*2,0);
                handle2=handle2.lerp(ground.add(0,6.5,0).add(right.scale(hand*1.4)),down);
                for(int j=0;j<curve.length;j++){
                    double t=(double)j/(curve.length-1)*emerge,u=1-t;
                    curve[j]=root.scale(u*u*u).add(handle1.scale(3*u*u*t))
                            .add(handle2.scale(3*u*t*t)).add(tip.scale(t*t*t));
                    // The distal body lays down and bends along the rock at
                    // contact, instead of a needle tip merely stabbing it.
                    float contact=down*TwoIArcanaTimeline.smooth(.62F,.88F,(float)t);
                    Vec3 laid=ground.add(right.scale(hand*(1-t)*7)).add(0,Math.sin((1-t)*Math.PI)*.55,0);
                    curve[j]=curve[j].lerp(laid,contact);
                }
                float tubeAlpha=presence*emerge;
                m.tube(curve,1.12F+(i%3)*.12F,tubeAlpha);
                lastTentacles++;
                float after=age-strike;
                if(after>=0&&after<10){
                    float flash=(float)Math.exp(-after*.38)*presence,reach=1.1F+after*.60F;
                    for(int k=0;k<8;k++){
                        double aa=k*Math.PI/4;
                        Vec3 p=ground.add(Math.cos(aa)*reach,.035,Math.sin(aa)*reach);
                        Vec3 q=ground.add(Math.cos(aa+.36)*reach,.035,Math.sin(aa+.36)*reach);
                        m.beam(p,q,.045F,SCARLET,flash*.8F);
                    }
                }
                lastTentacleVertices+=m.vertices-beforeTube;
            }
            // Draw the opaque black lip after the limbs. It occludes the
            // buried root while the exterior shaft passes through the hole.
            beforePortal=m.vertices;
            m.portal(mouth,axisU,axisV,normal,radius,open,presence,age,portal,true);
            lastPortalVertices+=m.vertices-beforePortal;
        }
    }

    private record RoseBed(Vec3 origin, Vec3[] roots, float bornAge) { }

    private static void roses(Mesh m,LivingEntity actor,float age,float presence) {
        if(age<70){ROSE_BEDS.remove(actor);return;}
        RoseBed bed=ROSE_BEDS.get(actor);
        int count=quality()==1?20:quality()==2?30:40;
        if(bed==null||bed.origin.distanceToSqr(actor.position())>4||age<bed.bornAge||bed.roots.length!=count){
            Vec3[] roots=new Vec3[count];
            var level=actor.level();
            for(int i=0;i<count;i++){
                double a=i*2.39996323, radius=2.0+Math.sqrt((i+.5)/count)*16;
                double x=actor.getX()+Math.cos(a)*radius,z=actor.getZ()+Math.sin(a)*radius;
                // A small, once-per-cast scan anchors flowers to real local floor
                // surfaces without spawning blocks, entities, particles or jobs.
                for(int dy=3;dy>=-5;dy--){
                    BlockPos pos=BlockPos.containing(x,actor.getY()+dy,z);
                    if(!level.hasChunkAt(pos))continue;
                    var shape=level.getBlockState(pos).getCollisionShape(level,pos);
                    if(shape.isEmpty()||!level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty())continue;
                    roots[i]=new Vec3(x,pos.getY()+shape.max(Direction.Axis.Y)+.018,z);break;
                }
            }
            bed=new RoseBed(actor.position(),roots,age);ROSE_BEDS.put(actor,bed);
        }
        Vec3 camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for(int i=0;i<bed.roots.length;i++){
            Vec3 root=bed.roots[i];if(root==null)continue;
            float growth=TwoIArcanaTimeline.smooth(74+(i%9)*4,112+(i%9)*4,age);
            if(growth<.001)continue;
            for(int j=0;j<3;j++){
                double a=i*1.7+j*Math.PI*2/3;
                Vec3 end=root.add(Math.cos(a)*(.28+growth*.34),.004,Math.sin(a)*(.28+growth*.34));
                m.beam(root,end,.009F,SCARLET,presence*growth*.65F);
            }
            float size=(.95F+(i%5)*.09F)*growth;
            m.pose.pushPose();m.at(root);
            m.pose.mulPose(Axis.YP.rotation((float)Math.atan2(camera.x-root.x,camera.z-root.z)));
            // The transparent image includes its stem, whose base is at y=451/512.
            m.sprite(-size*.5F,-size*(61F/512),0,size,size,ROSE,0xFFFFFF,presence,0);
            m.pose.popPose();
        }
    }

    private static void garden(Mesh m, float time, Vec3 camera) {
        double x = Math.rint((camera.x - 8.5) / 16_384) * 16_384 + 8.5;
        double z = Math.rint((camera.z - 8.5) / 16_384) * 16_384 + 8.5;
        Vec3 center = new Vec3(x, 64, z);
        float distanceFade = 1 - TwoIArcanaTimeline.smooth(48, 100, (float)camera.distanceTo(center));
        if (distanceFade <= 0) return;
        float show = showAge(0);
        float celebration = show < 0 ? 0 : TwoIArcanaTimeline.smooth(0, 90, show) * (1 - TwoIArcanaTimeline.smooth(530, 600, show));
        m.pose.pushPose(); m.at(center.add(0, .035, 0));
        m.horizontalSeal(8.6F, time * .055F, CYAN, .46F * distanceFade);
        m.horizontalSeal(6.9F, -time * .075F, LILAC, .38F * distanceFade);
        m.pose.popPose();
        // Tilted armillary rings: physical world-space geometry, seen correctly from every direction.
        for (int i = 0; i < 3; i++) {
            m.pose.pushPose(); m.at(center.add(0, 14 + Math.sin(time * .013) * .5, 0));
            m.pose.mulPose(Axis.YP.rotation(time * .0015F + i * 1.1F));
            m.pose.mulPose(Axis.XP.rotationDegrees(32 + i * 43));
            float radius = 9 + i * 2 + celebration;
            m.sprite(-radius, -radius, 0, radius * 2, radius * 2, SEAL, i == 1 ? LILAC : CYAN,
                    (.38F + celebration * .25F) * distanceFade, time * (i % 2 == 0 ? .04F : -.06F));
            m.pose.popPose();
        }
        // Broad, translucent aurora curtains; bounded segment count, no particle/entity allocation.
        for (int band = 0; band < (quality() == 1 ? 2 : 4); band++) {
            for (int i = 0; i < 24 + quality() * 8; i++) {
                double a = (i / (24.0 + quality() * 8) * Math.PI * 2) + band * .38;
                double b = ((i + 1) / (24.0 + quality() * 8) * Math.PI * 2) + band * .38;
                float h = (float)Math.sin(a * 3 + time * .012 + band) * 2;
                float h2 = (float)Math.sin(b * 3 + time * .012 + band) * 2;
                double radius = 27 + band * 7;
                Vec3 p = center.add(Math.cos(a) * radius, 19 + h + band, Math.sin(a) * radius);
                Vec3 q = center.add(Math.cos(b) * radius, 19 + h2 + band, Math.sin(b) * radius);
                m.curtain(p, q, 5 + celebration * 3, band % 2 == 0 ? CYAN : LILAC, .13F * distanceFade);
            }
        }
        int stars = 24 + quality() * 24;
        for (int i = 0; i < stars; i++) {
            double a = i * 2.39996 + time * .0004;
            float r = 12 + (i % 11) * 2.7F;
            Vec3 pos = center.add(Math.cos(a) * r, 15 + (i * 7 % 25), Math.sin(a) * r);
            m.star(pos, .08F + (i % 4) * .018F, i % 3 == 0 ? LILAC : WHITE,
                    (.48F + .25F * Mth.sin(time * .025F + i)) * distanceFade);
            if (i % 6 == 0) {
                double b = (i + 1) * 2.39996 + time * .0004;
                m.beam(pos, center.add(Math.cos(b) * r, 15 + ((i + 1) * 7 % 25), Math.sin(b) * r),
                        .012F, CYAN, .18F * distanceFade);
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + time * .004;
            m.star(center.add(Math.cos(a) * 10, 5 + Math.sin(a * 2 + time * .02) * .6, Math.sin(a) * 10),
                    .24F, i % 2 == 0 ? CYAN : LILAC, distanceFade * .8F);
        }
        // A recognisable three-act finale shares the existing server's 30-second clock and reward ledger.
        if (show >= 440 && show < 600) {
            for (int i = 0; i < 5 + quality() * 3; i++) {
                float fall = ((show - 440 + i * 13) % 85) / 85;
                double a = i * 2.39996;
                Vec3 pos = center.add(Math.cos(a) * (20 - fall * 12), 31 - fall * 25, Math.sin(a) * (20 - fall * 12));
                m.star(pos, .16F, WHITE, distanceFade);
                m.beam(pos, pos.add(Math.cos(a) * 2, 3, Math.sin(a) * 2), .06F, CYAN, distanceFade * .7F);
            }
        }
    }

    /** Public for the opt-in renderer fixture; coordinates are local to the supplied pose. */
    public static final class Mesh {
        public final PoseStack pose;
        private final VertexConsumer out;
        private final boolean scarlet;
        private final Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        private int vertices;
        public Mesh(PoseStack pose, VertexConsumer out) { this(pose,out,false); }
        public Mesh(PoseStack pose, VertexConsumer out,boolean scarlet) { this.pose = pose; this.out = out; this.scarlet=scarlet; }
        void at(Vec3 p) { pose.translate(p.x - camera.x, p.y - camera.y, p.z - camera.z); }
        private void vertex(float x, float y, float z, float u, float v, int color, float alpha) {
            out.addVertex(pose.last(), x, y, z).setColor((color >> 16) & 255, (color >> 8) & 255, color & 255,
                    (int)(Math.clamp(alpha, 0, 1) * 255)).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0xF000F0).setNormal(pose.last(), 0, 0, 1);
            vertices++;
        }
        public void sprite(float x, float y, float z, float w, float h, float[] uv, int color, float alpha, float rotation) {
            pose.pushPose(); pose.translate(x + w / 2, y + h / 2, z); pose.mulPose(Axis.ZP.rotationDegrees(rotation));
            vertex(-w/2, -h/2, 0, uv[0], uv[3], color, alpha);
            vertex(w/2, -h/2, 0, uv[2], uv[3], color, alpha);
            vertex(w/2, h/2, 0, uv[2], uv[1], color, alpha);
            vertex(-w/2, h/2, 0, uv[0], uv[1], color, alpha);
            pose.popPose();
        }
        void horizontalSeal(float r, float rotation, int color, float alpha) {
            pose.pushPose(); pose.mulPose(Axis.XP.rotationDegrees(-90));
            sprite(-r, -r, 0, r*2, r*2, SEAL, color, alpha, rotation); pose.popPose();
        }
        void horizontalSolid(float r, int color, float alpha, float rotation) {
            pose.pushPose(); pose.mulPose(Axis.XP.rotationDegrees(-90));
            sprite(-r, -r, 0, r*2, r*2, PAGE, color, alpha, rotation); pose.popPose();
        }
        void star(Vec3 p, float size, int color, float alpha) {
            pose.pushPose(); at(p); pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            sprite(-size, -size, 0, size*2, size*2, scarlet?RED_SOLID:SPARK, color, alpha, scarlet?45:0); pose.popPose();
        }
        void beam(Vec3 a, Vec3 b, float width, int color, float alpha) {
            Vec3 toward = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(a).normalize();
            Vec3 side = b.subtract(a).cross(toward).normalize().scale(width);
            Vec3 p=a.add(side), q=b.add(side), r=b.subtract(side), s=a.subtract(side);
            float u=scarlet?RED_SOLID[0]:.55F, top=scarlet?RED_SOLID[1]:.501F, bottom=scarlet?RED_SOLID[1]:.624F;
            point(p, u, top, color, alpha); point(q, u, top, color, alpha);
            point(r, u, bottom, color, alpha); point(s, u, bottom, color, alpha);
        }
        /** A finite black cavity, not a billboard. Rear bowl precedes the tube,
         * front lip follows it; world-space depth and the rim hide the root. */
        void portal(Vec3 center,Vec3 u,Vec3 v,Vec3 normal,float radius,float open,float alpha,float age,int seed,boolean lip){
            final int segments=24;
            float sx=radius*open,sy=radius*.76F*(float)Math.sqrt(open);
            Vec3 back=center.add(normal.scale(-2.4));
            for(int i=0;i<segments;i++){
                double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
                double ja=1+.035*Math.sin(a*3+seed),jb=1+.035*Math.sin(b*3+seed);
                Vec3 pa=center.add(u.scale(Math.cos(a)*sx*ja)).add(v.scale(Math.sin(a)*sy*ja));
                Vec3 pb=center.add(u.scale(Math.cos(b)*sx*jb)).add(v.scale(Math.sin(b)*sy*jb));
                if(!lip){
                    Vec3 ba=back.add(u.scale(Math.cos(a)*sx*.79)).add(v.scale(Math.sin(a)*sy*.79));
                    Vec3 bb=back.add(u.scale(Math.cos(b)*sx*.79)).add(v.scale(Math.sin(b)*sy*.79));
                    portalQuad(back,ba,bb,back,normal,0x010005,alpha);
                    float shade=(float)(.5+.5*Math.sin(a+age*.014+seed));
                    int color=((int)(8+shade*10)<<16)|((int)(2+shade*2)<<8)|(int)(10+shade*8);
                    portalQuad(ba,pa,pb,bb,normal,color,alpha);
                    // Sparse broken currents remain INSIDE the black bowl.
                    if(i%3==seed%3){
                        Vec3 ca=back.add(u.scale(Math.cos(a+age*.006)*sx*.51)).add(v.scale(Math.sin(a+age*.006)*sy*.51)).add(normal.scale(.04));
                        Vec3 cb=back.add(u.scale(Math.cos(b+age*.006)*sx*.51)).add(v.scale(Math.sin(b+age*.006)*sy*.51)).add(normal.scale(.04));
                        beam(ca,cb,.025F,0x240B29,alpha*.65F);
                    }
                }else{
                    double oa=1.20+.055*Math.sin(a*5+seed),ob=1.20+.055*Math.sin(b*5+seed);
                    Vec3 outerA=center.add(u.scale(Math.cos(a)*sx*oa)).add(v.scale(Math.sin(a)*sy*oa)).add(normal.scale(.18));
                    Vec3 outerB=center.add(u.scale(Math.cos(b)*sx*ob)).add(v.scale(Math.sin(b)*sy*ob)).add(normal.scale(.18));
                    portalQuad(pa,outerA,outerB,pb,normal,0x050207,alpha);
                    Vec3 innerA=pa.add(normal.scale(.06)).lerp(outerA,.07),innerB=pb.add(normal.scale(.06)).lerp(outerB,.07);
                    portalQuad(pa.add(normal.scale(.065)),innerA,innerB,pb.add(normal.scale(.065)),normal,
                            i%5==0?0x541329:0x290A1D,alpha*.88F);
                }
            }
        }
        private void portalQuad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 normal,int color,float alpha){
            tubePoint(a,normal,color,alpha);tubePoint(b,normal,color,alpha);
            tubePoint(c,normal,color,alpha);tubePoint(d,normal,color,alpha);
        }
        /** Quad tube with transported tangent frames and a tapered closed tip. */
        void tube(Vec3[] path,float radius,float alpha){
            final int sides=6;
            Vec3[] prior=new Vec3[sides],next=new Vec3[sides];
            Vec3 oldSide=null;
            Vec3 light=new Vec3(-.35,.8,-.48).normalize();
            for(int ring=0;ring<path.length;ring++){
                Vec3 tangent=path[Math.min(path.length-1,ring+1)].subtract(path[Math.max(0,ring-1)]).normalize();
                Vec3 side=oldSide==null?tangent.cross(Math.abs(tangent.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize():
                        oldSide.subtract(tangent.scale(oldSide.dot(tangent))).normalize();
                if(side.lengthSqr()<.1)side=new Vec3(1,0,0);
                Vec3 up=tangent.cross(side).normalize();oldSide=side;
                double t=(double)ring/(path.length-1);
                // Keep a fleshy distal limb; only its last six percent closes
                // to a rounded tip. The previous full-length taper read as wire.
                double r=radius*(.23+.77*Math.pow(1-t,.72))
                        *(1-TwoIArcanaTimeline.smooth(.94F,1F,(float)t))*(1+.045*Math.sin(t*35));
                for(int j=0;j<sides;j++){
                    double a=j*Math.PI*2/sides;
                    next[j]=path[ring].add(side.scale(Math.cos(a)*r)).add(up.scale(Math.sin(a)*r));
                }
                if(ring==0)for(int j=0;j<sides;j++){
                    tubePoint(path[0],tangent.scale(-1),0x400917,alpha);
                    tubePoint(next[(j+1)%sides],tangent.scale(-1),0x400917,alpha);
                    tubePoint(next[j],tangent.scale(-1),0x400917,alpha);
                    tubePoint(path[0],tangent.scale(-1),0x400917,alpha);
                }
                if(ring>0)for(int j=0;j<sides;j++){
                    int k=(j+1)%sides;
                    Vec3 normal=side.scale(Math.cos((j+.5)*Math.PI*2/sides)).add(up.scale(Math.sin((j+.5)*Math.PI*2/sides)));
                    float shade=(float)(.29+Math.max(0,normal.dot(light))*.71);
                    // A dark claret body, rose-lit ridge and small longitudinal
                    // grooves read as an organic solid, without additive glare.
                    int rr=(int)((j==2?178:105)*shade),gg=(int)((j==2?38:12)*shade),bb=(int)((j==2?58:32)*shade);
                    int color=(rr<<16)|(gg<<8)|bb;
                    tubePoint(prior[j],normal,color,alpha);tubePoint(prior[k],normal,color,alpha);
                    tubePoint(next[k],normal,color,alpha);tubePoint(next[j],normal,color,alpha);
                }
                Vec3[] swap=prior;prior=next;next=swap;
            }
        }
        private void tubePoint(Vec3 p,Vec3 normal,int color,float alpha){
            alpha*=TwoIArcanaTimeline.smooth(1.5F,4F,(float)p.distanceTo(camera));
            out.addVertex(pose.last(),(float)(p.x-camera.x),(float)(p.y-camera.y),(float)(p.z-camera.z))
                    .setColor((color>>16)&255,(color>>8)&255,color&255,(int)(Math.clamp(alpha,0,1)*255))
                    .setUv(RED_SOLID[0],RED_SOLID[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                    .setNormal(pose.last(),(float)normal.x,(float)normal.y,(float)normal.z);vertices++;
        }
        void waterDisc(Vec3 center, float radius, int segments, int color, float alpha) {
            for (int i = 0; i < segments; i++) {
                double a = i * Math.PI * 2 / segments;
                double b = (i + 1) * Math.PI * 2 / segments;
                // The emissive entity buffer is quad based. Close each fan triangle
                // with a fourth center vertex so the translucent surface is actually
                // rasterized on the block floor.
                point(center, PAGE[0], PAGE[1], color, alpha);
                point(center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius), PAGE[0], PAGE[1], color, alpha);
                point(center.add(Math.cos(b) * radius, 0, Math.sin(b) * radius), PAGE[0], PAGE[1], color, alpha);
                point(center, PAGE[0], PAGE[1], color, alpha);
            }
        }
        void curtain(Vec3 p, Vec3 q, float height, int color, float alpha) {
            point(p, .55F, .501F, color, 0); point(q, .55F, .501F, color, 0);
            point(q.add(0, -height, 0), .55F, .57F, color, alpha); point(p.add(0, -height, 0), .55F, .57F, color, alpha);
        }
        private void point(Vec3 p, float u, float v, int color, float alpha) {
            vertex((float)(p.x-camera.x), (float)(p.y-camera.y), (float)(p.z-camera.z), u, v, color, alpha);
        }
        public void book(float time,float alpha,float size){book(time,alpha,size,1);}
        public void book(float time, float alpha, float size,float open) {
            book(time,alpha,size,open,155);
        }
        /** Riding may unfold to a flat180°; ordinary held/casting books retain155°. */
        public void book(float time,float alpha,float size,float open,float openingDegrees) {
            pose.pushPose(); pose.scale(size, size, size);
            open=Mth.clamp(open,0,1);
            // Closed leather binding, ivory page block and a real hinged cover.
            box(-.205F,-.30F,-.068F,.41F,.60F,.025F,RED_COVER,0xFFFFFF,alpha);
            box(-.188F,-.279F,-.041F,.375F,.558F,.095F,PARCHMENT,0xD8B98F,alpha);
            sprite(-.181F,-.272F,.056F,.36F,.544F,PARCHMENT,0xFFFFFF,alpha,0);
            box(-.218F,-.307F,-.067F,.035F,.614F,.145F,RED_COVER,0x932039,alpha);
            for(int i=0;i<4;i++)box(-.225F,-.235F+i*.156F,-.073F,.046F,.025F,.152F,RED_SOLID,0xB99580,alpha);
            pose.pushPose();pose.translate(-.205F,0,.072F);
            pose.mulPose(Axis.YP.rotationDegrees(-open*openingDegrees));
            box(0,-.30F,0,.41F,.60F,.025F,RED_COVER,0xFFFFFF,alpha);
            // Inner cover remains readable when the physical front swings open.
            pose.pushPose();pose.translate(.398F,0,-.002F);pose.mulPose(Axis.YP.rotationDegrees(180));
            sprite(.004F,-.278F,0,.386F,.556F,PARCHMENT,0xE3BAA0,alpha,0);
            sprite(.086F,-.13F,.001F,.22F,.26F,HEX_EYE,0xCA7D84,alpha*.75F,0);pose.popPose();
            // Raised emblem and cornea sit at different physical depths. The
            // small view-dependent iris displacement simulates the recess;
            // it derives from the current view transform, never the frame time.
            sprite(.03F,-.17F,.029F,.35F,.35F,HEX_EYE,0xFFFFFF,alpha,0);
            org.joml.Vector3f view=new org.joml.Matrix4f(pose.last().pose()).invert()
                    .transformDirection(new org.joml.Vector3f(0,0,1)).normalize();
            float dx=Mth.clamp(view.x/Math.max(.25F,Math.abs(view.z)),-1,1)*.009F;
            float dy=Mth.clamp(view.y/Math.max(.25F,Math.abs(view.z)),-1,1)*.006F;
            float iris=.35F*166/512;
            sprite(.205F-iris*.5F+dx,.005F-iris*.5F+dy,.043F,iris,iris,IRIS,0xFFFFFF,alpha,0);
            for(int x=0;x<2;x++)for(int y=0;y<2;y++){
                float px=.012F+x*.362F,py=-.29F+y*.544F;
                box(px,py,.026F,.025F,.036F,.014F,RED_SOLID,0xC7A28F,alpha);
            }
            pose.popPose();
            // A short woven crimson bookmark, no floating eye/data halo.
            box(.12F,-.369F,-.015F,.036F,.105F,.008F,RED_SOLID,0x9E1538,alpha);
            pose.popPose();
        }
        private void box(float x,float y,float z,float w,float h,float d,float[] uv,int color,float alpha) {
            sprite(x,y,z+d,w,h,uv,color,alpha,0);
            pose.pushPose(); pose.translate(x+w,y,z); pose.mulPose(Axis.YP.rotationDegrees(180));
            sprite(0,0,0,w,h,uv,color,alpha,0); pose.popPose();
            for (int i=0;i<2;i++) {
                pose.pushPose(); pose.translate(x+i*w,y,z); pose.mulPose(Axis.YP.rotationDegrees(90));
                sprite(-d,0,0,d,h,uv,color,alpha,0); pose.popPose();
                pose.pushPose(); pose.translate(x,y+i*h,z); pose.mulPose(Axis.XP.rotationDegrees(90));
                sprite(0,0,0,w,d,uv,color,alpha,0); pose.popPose();
            }
        }
    }
}
