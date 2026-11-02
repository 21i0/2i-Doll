package com.twoi.combatdoll.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.config.TwoIVisualConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Terrain replacement before entities, then sky replacement before luminous geometry. */
public final class ArcanaWorldPass {
    private static PostChain chain;
    private static PostPass pass;
    private static int width, height, frames, floorFrames, divisor;
    private static boolean failed;
    private static float focusX=.5F,focusY=.5F;
    private static final ResourceLocation CLOCK=ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"textures/effect/astral_clock.png");
    private static final ResourceLocation GALAXY=ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"textures/effect/galactic_panorama.png");
    private static final ResourceLocation LANDSCAPE=ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"textures/effect/cangsheng_landscape.png");
    private static final ResourceLocation CRIMSON=ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"textures/effect/crimson_landscape.png");
    private static final ResourceLocation CRIMSON_GROUND=ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"textures/effect/crimson_ground_relief.png");
    static boolean available() { return chain != null && !failed; }
    static int frames() { return frames; }
    static int floorFrames() { return floorFrames; }
    public static float focusX(){return focusX;}
    public static float focusY(){return focusY;}
    static void render(float domain,float takeover,float age,float impact,float time,Vec3 eye,Vec3 normal,Vec3 ground,Vec3 anchor,float chargeExtra,float floorStyle,boolean terrain,RenderLevelStageEvent event) {
        if (failed) return;
        Minecraft mc=Minecraft.getInstance(); var main=mc.getMainRenderTarget();
        try {
            if (chain==null) {
                chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),main,
                        ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"shaders/post/two_i_arcana.json"));
                chain.addTempTarget("world",main.width,main.height);
                pass=chain.addPass("two_i_combat_doll:arcana_world",main,chain.getTempTarget("world"),true);
                pass.addAuxAsset("DepthSampler",main::getDepthTextureId,1,1);
                pass.addAuxAsset("ClockSampler",()->mc.getTextureManager().getTexture(CLOCK).getId(),1024,1024);
                pass.addAuxAsset("GalaxySampler",()->mc.getTextureManager().getTexture(GALAXY).getId(),2048,1024);
                pass.addAuxAsset("LandscapeSampler",()->mc.getTextureManager().getTexture(LANDSCAPE).getId(),2048,768);
                pass.addAuxAsset("CrimsonSampler",()->mc.getTextureManager().getTexture(CRIMSON).getId(),2048,768);
                pass.addAuxAsset("CrimsonGroundSampler",()->mc.getTextureManager().getTexture(CRIMSON_GROUND).getId(),1024,1024);
                width=height=0;
            }
            int nextDivisor=TwoIArcanaRenderer.quality()==1?2:1;
            if(width!=main.width||height!=main.height||divisor!=nextDivisor) {
                width=main.width;height=main.height;divisor=nextDivisor;chain.resize(width,height);
                chain.getTempTarget("world").resize(Math.max(1,width/divisor),Math.max(1,height/divisor),Minecraft.ON_OSX);
            }
            var fx=pass.getEffect();
            fx.safeGetUniform("InvProjection").set(new Matrix4f(event.getProjectionMatrix()).invert());
            fx.safeGetUniform("CameraRotation").set(new Matrix4f(event.getModelViewMatrix()).invert());
            fx.safeGetUniform("Eye").set((float)eye.x,(float)eye.y,(float)eye.z);
            fx.safeGetUniform("Ground").set((float)ground.x,(float)ground.y,(float)ground.z);
            fx.safeGetUniform("PlanetAnchor").set((float)anchor.x,(float)anchor.y,(float)anchor.z);
            fx.safeGetUniform("ChargeExtra").set(chargeExtra);
            fx.safeGetUniform("VisualQuality").set((float)TwoIArcanaRenderer.quality());
            if(TwoIArcanaRenderer.spellMode>.5F&&TwoIArcanaRenderer.spellMode<1.5F){
                var planets=CelestialOrbits.matrices(anchor,age,chargeExtra);
                for(int i=0;i<planets.length;i++)fx.safeGetUniform("Planets"+i).set(planets[i]);
                var systems=CelestialOrbits.systemMatrices(anchor,age,chargeExtra);
                for(int i=0;i<systems.length;i++)fx.safeGetUniform("SystemCenters"+i).set(systems[i]);
                var trail=BlackHoleMotion.trailMatrices(event.getCamera().getPosition(),TwoIArcanaRenderer.quality());
                fx.safeGetUniform("HoleTrail0").set(trail[0]);fx.safeGetUniform("HoleTrail1").set(trail[1]);
                fx.safeGetUniform("TrailCount").set((float)BlackHoleMotion.trailPointCount());
            }else fx.safeGetUniform("TrailCount").set(0F);
            var projection=event.getProjectionMatrix();
            var worldToView=new Matrix4f().rotation(event.getCamera().rotation()).invert();
            var cameraUp=new org.joml.Vector3f(0,1,0).rotate(event.getCamera().rotation());
            float lensRadius=TwoIArcanaRenderer.spellMode>.5F?8.4F:6.4F;
            var c=new org.joml.Vector4f((float)eye.x,(float)eye.y,(float)eye.z,1).mul(worldToView).mul(projection);
            var edge=new org.joml.Vector4f((float)(eye.x+cameraUp.x*lensRadius),(float)(eye.y+cameraUp.y*lensRadius),
                    (float)(eye.z+cameraUp.z*lensRadius),1).mul(worldToView).mul(projection);
            if(c.w>.01F&&edge.w>.01F){
                focusX=Math.clamp(c.x/c.w*.5F+.5F,0F,1F);focusY=Math.clamp(c.y/c.w*.5F+.5F,0F,1F);
                fx.safeGetUniform("Lens").set(c.x/c.w*.5F+.5F,c.y/c.w*.5F+.5F,Math.abs(edge.y/edge.w-c.y/c.w)*.5F);
            }else{focusX=focusY=.5F;fx.safeGetUniform("Lens").set(.5F,.5F,0);}
            fx.safeGetUniform("Domain").set(domain*TwoIVisualConfig.STRENGTH.get().floatValue());
            fx.safeGetUniform("Takeover").set(takeover);
            fx.safeGetUniform("Age").set(age);
            fx.safeGetUniform("Impact").set(impact);
            fx.safeGetUniform("Clock").set(time/20);
            fx.safeGetUniform("Mode").set(TwoIArcanaRenderer.spellMode);
            fx.safeGetUniform("FloorStyle").set(floorStyle);
            fx.safeGetUniform("TerrainPass").set(terrain?1F:0F);
            RenderSystem.disableBlend();chain.process(0);
            main.bindWrite(true);chain.getTempTarget("world").blitToScreen(width,height);frames++;
            if (terrain) floorFrames++;
        } catch(Exception error) {
            close();failed=true;
            LogUtils.getLogger().error("2i combat doll sky pass disabled until reload; geometry fallback remains",error);
        } finally {
            main.bindWrite(true);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);RenderSystem.enableCull();RenderSystem.colorMask(true,true,true,true);
            RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
        }
    }
    static void close() { if(chain!=null){chain.close();chain=null;} pass=null;width=height=0;failed=false;focusX=focusY=.5F; }
}
