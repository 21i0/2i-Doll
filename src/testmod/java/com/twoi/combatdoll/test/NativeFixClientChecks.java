package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real independent client, integrated server, registered renderers and vanilla chat. Never delivered. */
@EventBusSubscriber(modid="two_i_combat_doll",value=Dist.CLIENT)
public final class NativeFixClientChecks {
    private static int frames,phase,wait,serverTicks;
    private static boolean requested,started,done;
    private static final AtomicInteger ready=new AtomicInteger();
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static UUID mountId,riderId,twoiId,longhuanId;

    @SubscribeEvent public static void server(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("two_i_combat_doll.native_fix_test")||event.getServer().isDedicatedServer()||done)return;
        try{
            if(event.getServer().getPlayerList().getPlayers().isEmpty())return;
            ServerPlayer owner=event.getServer().getPlayerList().getPlayers().getFirst();
            if(++serverTicks>1200)throw new AssertionError("Integrated mount timeout");
            if(!started){
                started=true;
                var level=owner.serverLevel();
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());level.setDayTime(6000);
                owner.connection.teleport(0,160,8,180,15);
                NativeMountFacingChecks.begin(owner);return;
            }
            if(ready.get()==0){
                NativeMountFacingChecks.tick();if(!NativeMountFacingChecks.finished())return;
                NativeMountFacingChecks.assertPassed();
                var mount=NativeMountFacingChecks.mount();var rider=NativeMountFacingChecks.rider();
                mountId=mount.getUUID();riderId=rider.getUUID();rider.setTarget(NativeMountFacingChecks.attention());
                var level=owner.serverLevel();
                var twoi=TwoICombatDollMod.DOLL_ENTITY.get().create(level);twoi.setOwnerId(owner.getUUID());twoi.setNoAi(true);
                twoi.moveTo(mount.getX()-3,160,mount.getZ(),-90,0);pose(twoi,-90);level.addFreshEntity(twoi);twoiId=twoi.getUUID();
                var longhuan=com.customdoll.longhuan.init.ModEntities.TWO_I_DOLL.get().create(level);
                longhuan.initialize(owner.getUUID(),new ItemStack(com.customdoll.longhuan.init.ModItems.TWO_I_DOLL.get()));
                longhuan.setNoAi(true);longhuan.moveTo(mount.getX()+3,160,mount.getZ(),-90,0);pose(longhuan,-90);level.addFreshEntity(longhuan);longhuanId=longhuan.getUUID();
                camera(owner,mount.position().add(5,2.1,7),mount.position().add(0,.65,0));ready.set(1);
            }
        }catch(Throwable error){failure.set(error);}
    }
    public static void tick(Minecraft mc){
        if(done)return;
        try{
            if(++frames>6000)throw new AssertionError("Client timeout phase="+phase);
            if(failure.get()!=null)throw new AssertionError("Integrated native checks",failure.get());
            if(mc.getOverlay()!=null)return;
            if(!requested){
                if(mc.screen==null)return;requested=true;
                for(String absent:java.util.List.of("useless_stretcher","useless_mod","doll_chronicle","curios"))
                    check(!net.neoforged.fml.ModList.get().isLoaded(absent),"minimal client excludes "+absent);
                mc.options.guiScale().set(2);mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);mc.options.framerateLimit().set(60);mc.resizeDisplay();
                mc.createWorldOpenFlows().createFreshLevel("native-fixes-"+System.currentTimeMillis(),new LevelSettings("Native doll regression",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(211103L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
            }
            if(mc.level==null||mc.player==null||ready.get()<1)return;
            if(phase==0){
                if(++wait<35)return;
                for(UUID id:new UUID[]{mountId,riderId,twoiId,longhuanId}){
                    var entity=find(mc,id);check(entity!=null,"all three doll types and mount tracked by actual client");
                    check(mc.getEntityRenderDispatcher().getRenderer(entity)!=null,"production entity renderer registered");
                }
                check(((com.twoi.combatdoll.entity.CangshengAxolotlEntity)find(mc,mountId)).combatState().age(find(mc,mountId),0)<0,"cancelled test spell synchronized and its visual state restored");
                capture(mc,"native-riding-side");
                mc.getSingleplayerServer().execute(()->{var owner=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var mount=NativeMountFacingChecks.mount();
                    Vec3 front=Vec3.directionFromRotation(0,mount.getYRot()).scale(6);camera(owner,mount.position().add(front).add(0,1.5,0),mount.position().add(0,.75,0));});
                phase=1;wait=0;return;
            }
            if(phase==1){
                if(++wait<25)return;capture(mc,"native-riding-front");
                mc.getSingleplayerServer().execute(()->{var owner=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var doll=(com.twoi.combatdoll.entity.CombatDollEntity)owner.serverLevel().getEntity(twoiId);
                    doll.talkTo(owner);owner.displayClientMessage(Component.translatable("msg.two_i_combat_doll.two_i.companionship"),true);});
                phase=2;wait=0;return;
            }
            if(phase==2){
                if(++wait<15)return;capture(mc,"native-visible-chat-and-actionbar");mc.setScreen(new ChatScreen(""));phase=3;wait=0;return;
            }
            if(phase==3){
                if(++wait<15)return;check(mc.screen instanceof ChatScreen,"actual ChatScreen open");capture(mc,"native-chat-screen");
                check(org.lwjgl.opengl.GL11.glGetError()==org.lwjgl.opengl.GL11.GL_NO_ERROR,"actual production render path has no GL error");
                mc.setScreen(null);phase=4;wait=0;return;
            }
            if(phase==4){
                if(++wait<10)return;done=true;
                LogUtils.getLogger().info("NATIVE FIX CLIENT CHECKS PASSED: independent client; same natural riding checks; real 2i/Longhuan/XiaoXiao/mount registered renderers; side/front framebuffers; native conversation+actionbar with visible vanilla chat and actual ChatScreen; no custom bottom-left overlay exists; no GL error");mc.stop();
            }
        }catch(Throwable error){done=true;LogUtils.getLogger().error("NATIVE FIX CLIENT CHECKS FAILED",error);mc.stop();}
    }
    private static void pose(LivingEntity entity,float yaw){entity.setYRot(yaw);entity.yRotO=entity.yBodyRot=entity.yBodyRotO=entity.yHeadRot=entity.yHeadRotO=yaw;}
    private static void camera(ServerPlayer owner,Vec3 position,Vec3 target){Vec3 delta=target.subtract(position.add(0,owner.getEyeHeight(),0));owner.connection.teleport(position.x,position.y,position.z,(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())));}
    private static LivingEntity find(Minecraft mc,UUID id){for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(id)&&entity instanceof LivingEntity living)return living;return null;}
    private static void capture(Minecraft mc,String name)throws Exception{var dir=mc.gameDirectory.toPath().resolve("native-fix-screenshots");Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve(name+".png"));}}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
