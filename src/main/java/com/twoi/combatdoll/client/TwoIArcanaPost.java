package com.twoi.combatdoll.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.config.TwoIVisualConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** A single shared half-resolution bloom chain, irrespective of the number of visible companions. */
public final class TwoIArcanaPost implements ResourceManagerReloadListener {
    private static PostChain chain;
    private static net.minecraft.client.renderer.PostPass compositePass;
    private static int width, height, resolutionDivisor;
    private static boolean failed, shaderPack;
    private static long nextShaderCheck;
    private static int processedFrames;
    private TwoIArcanaPost() { }
    public static int processedFrames() { return processedFrames; }
    public static boolean enabled() {
        Minecraft mc = Minecraft.getInstance();
        if (!TwoIVisualConfig.POST.get() || failed) return false;
        if (mc.level != null && mc.level.getGameTime() >= nextShaderCheck) {
            nextShaderCheck = mc.level.getGameTime() + 100;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                shaderPack = (boolean)api.getMethod("isShaderPackInUse").invoke(instance);
            } catch (ReflectiveOperationException ignored) { shaderPack = false; }
        }
        return !shaderPack;
    }
    public static boolean worldAvailable() { return enabled() && TwoIVisualConfig.STRENGTH.get()>.001 && ArcanaWorldPass.available(); }
    public static void world(float domain, float takeover, float age, float impact, float time,
                             net.minecraft.world.phys.Vec3 eye, net.minecraft.world.phys.Vec3 normal,
                             net.minecraft.world.phys.Vec3 ground,
                             net.minecraft.world.phys.Vec3 anchor,float chargeExtra,
                             float floorStyle, boolean terrain,
                             net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (domain > .001F && TwoIVisualConfig.STRENGTH.get()>.001 && enabled()) ArcanaWorldPass.render(domain,takeover,age,impact,time,eye,normal,ground,anchor,chargeExtra,floorStyle,terrain,event);
    }
    public static void render(float domain, float garden, float impact, float time) {
        Minecraft mc = Minecraft.getInstance();
        if (domain + garden <= .001F || !enabled()) return;
        var main = mc.getMainRenderTarget();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main,
                        ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID, "shaders/post/two_i_arcana.json"));
                chain.addTempTarget("glow_a", 1, 1);
                chain.addTempTarget("glow_b", 1, 1);
                chain.addTempTarget("composite", main.width, main.height);
                var a = chain.getTempTarget("glow_a"); var b = chain.getTempTarget("glow_b");
                chain.addPass("two_i_combat_doll:arcana_extract", main, a, true);
                chain.addPass("two_i_combat_doll:arcana_blur_h", a, b, true);
                chain.addPass("two_i_combat_doll:arcana_blur_v", b, a, true);
                var composite = chain.addPass("two_i_combat_doll:arcana_composite", main, chain.getTempTarget("composite"), true);
                compositePass=composite;
                composite.addAuxAsset("BloomSampler", a::getColorTextureId, 1, 1);
                width = height = 0;
            }
            int divisor = TwoIArcanaRenderer.quality() == 1 ? 4 : 2;
            if (width != main.width || height != main.height || resolutionDivisor != divisor) {
                width = main.width; height = main.height;
                resolutionDivisor = divisor;
                chain.resize(width, height);
                chain.getTempTarget("glow_a").resize(Math.max(1, width / divisor), Math.max(1, height / divisor), Minecraft.ON_OSX);
                chain.getTempTarget("glow_b").resize(Math.max(1, width / divisor), Math.max(1, height / divisor), Minecraft.ON_OSX);
            }
            chain.setUniform("Domain", domain * TwoIVisualConfig.STRENGTH.get().floatValue());
            chain.setUniform("Garden", garden);
            chain.setUniform("Impact", impact);
            chain.setUniform("Clock", time / 20);
            chain.setUniform("Mode",TwoIArcanaRenderer.spellMode);
            chain.setUniform("Age",TwoIArcanaRenderer.spellAge);
            chain.setUniform("ChargeExtra",TwoIArcanaRenderer.spellChargeExtra);
            compositePass.getEffect().safeGetUniform("FocalPoint").set(ArcanaWorldPass.focusX(),ArcanaWorldPass.focusY());
            RenderSystem.disableBlend();
            chain.process(0);
            // Blit colour only: PostPass targeting minecraft:main would clear its depth buffer.
            main.bindWrite(true);
            chain.getTempTarget("composite").blitToScreen(width, height);
            processedFrames++;
        } catch (Exception error) {
            failed = true;
            close();
            LogUtils.getLogger().error("2i arcana post processing disabled until resource reload; world geometry remains available", error);
        } finally {
            main.bindWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }
    public static void close() {
        if (chain != null) { chain.close(); chain = null; }
        compositePass=null;
        ArcanaWorldPass.close();
        width = height = 0;
    }
    @Override public void onResourceManagerReload(ResourceManager manager) {
        close(); failed = false; nextShaderCheck = 0;
    }
    @EventBusSubscriber(modid = TwoICombatDollMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new TwoIArcanaPost());
        }
    }
}
