package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Focused production renderer/framebuffer checks; never packaged in production. */
@EventBusSubscriber(modid="two_i_combat_doll",value=Dist.CLIENT)
public final class TitanFreedomClientChecks {
    private static int frames,phase,wait,serverTicks;
    private static boolean requested,started,done;
    private static UUID titanId;
    private static CangshengAxolotlEntity titan;
    private static Vec3 observed;
    private static float observedYaw;
    private static final AtomicInteger ready=new AtomicInteger();
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static Preview preview;
    @SubscribeEvent public static void server(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("two_i_combat_doll.titan_freedom_test")||event.getServer().isDedicatedServer()||done)return;
        try{
            if(event.getServer().getPlayerList().getPlayers().isEmpty())return;
            ServerPlayer owner=event.getServer().getPlayerList().getPlayers().getFirst();
            check(++serverTicks<1500,"integrated deadline");
            if(!started){
                started=true;var level=owner.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());level.setDayTime(6000);
                for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++)level.setBlock(new BlockPos(x,159,z),Blocks.QUARTZ_BLOCK.defaultBlockState(),2);
                ChuanshuoUniquenessChecks.run(owner);
                var source=new ItemStack(TwoICombatDollMod.AXOLOTL.get());titan=CangshengAxolotlEntity.deploy(owner,source,new Vec3(0,160,0),null);check(titan!=null,"actual tracked titan deployed");titanId=titan.getUUID();camera(owner,new Vec3(10,162,11),new Vec3(0,163,0));return;
            }
            if(ready.get()==0&&serverTicks>=65){check(!titan.emerging()&&!titan.mountMode()&&titan.getBbWidth()>12,"full titan emergence and huge collision size");observed=titan.position();observedYaw=titan.getYRot();ready.set(1);}
            if(ready.get()==1){check(titan.position().subtract(observed).horizontalDistance()<.05,"approaching observer keeps titan still");check(Math.abs(net.minecraft.util.Mth.wrapDegrees(titan.getYRot()-observedYaw))<.1,"titan does not rotate its whole body after close observer");}
        }catch(Throwable error){failure.set(error);}
    }
    public static void tick(Minecraft mc){
        if(done)return;
        try{
            check(++frames<5000,"client deadline phase="+phase);if(failure.get()!=null)throw new AssertionError("integrated production actor",failure.get());if(mc.getOverlay()!=null)return;
            if(!requested){
                if(mc.screen==null)return;requested=true;
                for(String absent:List.of("useless_stretcher","useless_mod","doll_chronicle","curios"))check(!net.neoforged.fml.ModList.get().isLoaded(absent),"minimal client excludes "+absent);
                mc.options.guiScale().set(2);mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);mc.options.framerateLimit().set(60);mc.options.hideGui=false;mc.resizeDisplay();
                mc.createWorldOpenFlows().createFreshLevel("titan-observation-"+System.currentTimeMillis(),new LevelSettings("Titan and skin focused regression",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(211105L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
            }
            if(mc.level==null||mc.player==null||ready.get()<1)return;
            if(phase==0){
                if(++wait<35)return;check(find(mc,titanId)!=null,"actual huge entity tracked by client");capture(mc,"titan-close-front");
                mc.getSingleplayerServer().execute(()->camera(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()),new Vec3(-10,162,11),new Vec3(0,163,0)));phase=1;wait=0;return;
            }
            if(phase==1){if(++wait<30)return;capture(mc,"titan-close-other-side");check(mc.getEntityRenderDispatcher().getRenderer(find(mc,titanId))!=null,"production huge renderer registered");preview=new Preview();mc.setScreen(preview);phase=2;return;}
            if(phase==2){if(!preview.done)return;check(mc.screen==null,"preview closes cleanly");check(org.lwjgl.opengl.GL11.glGetError()==org.lwjgl.opengl.GL11.GL_NO_ERROR,"production renderer leaves valid GL state");done=true;LogUtils.getLogger().info("TITAN FREEDOM CLIENT CHECKS PASSED: actual deployed huge titan observed from two close cameras without fleeing or camera-facing; sishier and canonical chuanshuo production entity/palm/head render APIs and skin/blink resources; framebuffer evidence, GL state; minimal environment");mc.stop();}
        }catch(Throwable error){done=true;LogUtils.getLogger().error("TITAN FREEDOM CLIENT CHECKS FAILED",error);mc.stop();}
    }
    private static final class Preview extends net.minecraft.client.gui.screens.Screen {
        private final List<String> names=List.of("sishier","chuanshuo");private final LivingEntity[] actors=new LivingEntity[2];private final ItemStack[] items=new ItemStack[2];private int rendered;private boolean done;
        Preview(){super(Component.literal("Updated dolls / production renderers"));}
        @Override protected void init(){
            try{
                for(int i=0;i<names.size();i++){String n=names.get(i);var id=ResourceLocation.fromNamespaceAndPath(n,n+"_doll");items[i]=new ItemStack(BuiltInRegistries.ITEM.get(id));actors[i]=(LivingEntity)BuiltInRegistries.ENTITY_TYPE.get(id).create(minecraft.level);check(actors[i]!=null&&minecraft.getEntityRenderDispatcher().getRenderer(actors[i])!=null,"actual entity renderer "+n);
                    var renderer=api(n,"DollRenderer");var layer=(net.minecraft.client.model.geom.ModelLayerLocation)renderer.getField("LAYER").get(null);check(minecraft.getEntityModels().bakeLayer(layer)!=null,"actual baked layer "+n);check(net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(items[i]).getCustomRenderer()!=null,"actual custom item extension "+n);
                    for(String texture:List.of("skin.png","skin_blink_half.png","skin_blink_closed.png"))try(var input=minecraft.getResourceManager().open(ResourceLocation.fromNamespaceAndPath(n,"textures/entity/"+texture))){
                        byte[] bytes=input.readAllBytes();try(var image=com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(bytes))){check(image.getWidth()>=64&&image.getHeight()>=64,"actual runtime texture "+n+"/"+texture);if(n.equals("sishier")){check(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)).equals("bbfd4f54898b2fb3cc280cd537d385a8f89e2ba8380240f836662ad030524387"),"runtime skin and inactive blink resources exactly match user29(1).png "+texture);check(com.customdoll.sishier.client.render.DollSkin.usesSlimArms(image),"new Alex arm UVs recognized");}}
                    }
                    if(n.equals("sishier"))try(var input=minecraft.getResourceManager().open(ResourceLocation.fromNamespaceAndPath(n,"textures/entity/skin.json"))){check(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())).equals("dae64f3d6662fc23508335f9e9346b49a245121645b180fb49c0f8506db44405"),"runtime skin config exactly matches reviewed original blink-disabled config");}
                }
            }catch(Exception problem){throw new AssertionError("Updated doll renderer setup",problem);}
        }
        @Override public void render(net.minecraft.client.gui.GuiGraphics gui,int mouseX,int mouseY,float partial){
            try{
                gui.fill(0,0,width,height,0xFF17151C);gui.drawCenteredString(font,"Production renderers: entity / palm / head",width/2,8,0xFFEADDF5);int rh=(height-25)/2;
                for(int i=0;i<2;i++){String n=names.get(i);int top=25+i*rh;gui.drawString(font,n,8,top,0xFFFFFFFF);net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(gui,20,top+10,width/3-5,top+rh-2,Math.max(28,rh-15),.0625F,width/6,top+rh/2,actors[i]);
                    var pose=gui.pose();float size=Math.min(rh*.75F,width*.15F);pose.pushPose();pose.translate(width*.50,top+rh*.77,150);pose.scale(size,-size,size);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(155));
                    api(n,"DollHandRenderer").getMethod("renderOnPalm",ItemStack.class,net.minecraft.world.entity.HumanoidArm.class,net.minecraft.client.resources.PlayerSkin.class,boolean.class,boolean.class,com.mojang.blaze3d.vertex.PoseStack.class,net.minecraft.client.renderer.MultiBufferSource.class,int.class).invoke(null,items[i],net.minecraft.world.entity.HumanoidArm.RIGHT,minecraft.player.getSkin(),true,true,pose,gui.bufferSource(),15728880);gui.flush();pose.popPose();
                    pose.pushPose();pose.translate(width*.82,top+rh*.81,150);pose.scale(size,size,size);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(165));var playerModel=new net.minecraft.client.model.PlayerModel<LivingEntity>(minecraft.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER),false);playerModel.head.render(pose,gui.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entitySolid(minecraft.player.getSkin().texture())),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                    api(n,"DollHeadLayer").getMethod("renderOnHead",ItemStack.class,net.minecraft.client.model.geom.ModelPart.class,com.mojang.blaze3d.vertex.PoseStack.class,net.minecraft.client.renderer.MultiBufferSource.class,int.class).invoke(null,items[i],playerModel.head,pose,gui.bufferSource(),15728880);gui.flush();pose.popPose();
                }
                gui.flush();check(org.lwjgl.opengl.GL11.glGetError()==org.lwjgl.opengl.GL11.GL_NO_ERROR,"production preview GL state");if(++rendered==25){capture(minecraft,"sishier-chuanshuo-production-forms");done=true;minecraft.setScreen(null);}
            }catch(Exception problem){throw new AssertionError("Updated dolls production preview",problem);}
        }
        @Override public boolean isPauseScreen(){return false;}
        private static Class<?> api(String namespace,String name)throws ClassNotFoundException{return Class.forName("com.customdoll."+namespace+".client.render."+name);}
    }
    private static LivingEntity find(Minecraft mc,UUID id){for(var actor:mc.level.entitiesForRendering())if(actor.getUUID().equals(id)&&actor instanceof LivingEntity living)return living;return null;}
    private static void camera(ServerPlayer p,Vec3 pos,Vec3 target){Vec3 d=target.subtract(pos.add(0,p.getEyeHeight(),0));p.connection.teleport(pos.x,pos.y,pos.z,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));}
    private static void capture(Minecraft mc,String name)throws Exception{var folder=mc.gameDirectory.toPath().resolve("titan-skin-dedupe-screenshots");Files.createDirectories(folder);try(var shot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(folder.resolve(name+".png"));}}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError("Titan client: "+message);}
}
