package com.twoi.combatdoll.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.twoi.combatdoll.TwoICombatDollMod;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;

/** The staff's authored ivory/gold materials retain their colour in every view. */
public final class AstralStaffRenderType extends RenderType {
    private static ShaderInstance shader;
    private AstralStaffRenderType(){
        super("two_i_staff_factory",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,
                1536,false,false,()->{},()->{});
    }
    public static RenderType staff(ResourceLocation texture){
        return create("two_i_astral_staff_unlit",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,
                16384,false,true,CompositeState.builder()
                        .setShaderState(new ShaderStateShard(()->shader))
                        .setTextureState(new TextureStateShard(texture,false,false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
    }
    @EventBusSubscriber(modid=TwoICombatDollMod.MODID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws IOException{
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"staff_unlit"),
                    DefaultVertexFormat.NEW_ENTITY),loaded->shader=loaded);
        }
    }
}
