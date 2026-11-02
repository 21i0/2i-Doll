package com.twoi.combatdoll.test;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.client.*;
import com.twoi.combatdoll.config.TwoIVisualConfig;
import com.twoi.combatdoll.entity.*;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.*;
import com.twoi.combatdoll.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real integrated world, real hostile AI entry and damage, compiled GLSL and framebuffer screenshots. */
final class TwoIArcanaClientChecks {
    private static int ticks, stage, age, maxVertices, movieFrame=-1, combatFrames, postBefore, framesOff;
    private static boolean requested, finished, castSeen, cloudSuppressed;
    private static net.minecraft.client.CloudStatus originalClouds;
    private static final AtomicInteger serverPhase=new AtomicInteger();
    private static final AtomicReference<UUID> dollId=new AtomicReference<>();
    private static final AtomicReference<Throwable> serverFailure=new AtomicReference<>();
    private static final Set<String> shots=new HashSet<>();
    private static CompletableFuture<Void> reload;
    private static final java.util.List<CompletableFuture<Void>> writes=new java.util.ArrayList<>();
    private static List<net.minecraft.world.entity.monster.Zombie> monsters;
    static void tick(Minecraft mc) {
        if(finished)return;
        try {
            check(++ticks<4500,"timeout stage="+stage);
            if(serverFailure.get()!=null)throw new AssertionError("server fixture failed",serverFailure.get());
            if(mc.getOverlay()!=null)return;
            if(!requested) {
                if(mc.screen==null)return;
                requested=true;mc.options.guiScale().set(1);mc.options.pauseOnLostFocus=false;
                originalClouds=mc.options.cloudStatus().get();
                mc.options.hideGui=true;mc.options.particles().set(ParticleStatus.ALL);
                mc.options.renderDistance().set(8);mc.options.framerateLimit().set(120);
                mc.resizeDisplay();
                var settings=new LevelSettings("2i arcana visual regression",GameType.CREATIVE,false,Difficulty.NORMAL,true,
                        new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("two-i-arcana-"+System.currentTimeMillis(),settings,
                        new WorldOptions(210L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(stage==0) {
                if(mc.screen!=null)return;
                stage=1;
                server(mc,()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var level=player.serverLevel();level.setDayTime(6000);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
                    for(int x=-18;x<=18;x++)for(int z=-18;z<=18;z++)level.setBlock(new BlockPos(x,99,z),
                            (Math.floorMod(x+z,6)==0?Blocks.CHISELED_STONE_BRICKS:Blocks.STONE_BRICKS).defaultBlockState(),2);
                    player.connection.teleport(.5,100,10.5,180,-16);
                    net.minecraft.world.entity.PathfinderMob doll;
                    if(Boolean.getBoolean("two_i_combat_doll.with_stretcher"))doll=StretcherGardenFixture.spawn(player);
                    else {var own=TwoICombatDollMod.DOLL_ENTITY.get().create(level);own.setOwnerId(player.getUUID());doll=own;}
                    doll.moveTo(.5,100,.5,180,0);check(level.addFreshEntity(doll),"doll joined real world");dollId.set(doll.getUUID());
                    monsters=new ArrayList<>();
                    for(int i=0;i<3;i++) {
                        var zombie=EntityType.ZOMBIE.create(level);zombie.setNoAi(true);zombie.setNoGravity(true);
                        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);zombie.setHealth(80);
                        zombie.moveTo(-2.5+i*3,100,-7,0,0);zombie.setInvulnerable(false);
                        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                        check(level.addFreshEntity(zombie),"zombie joined");monsters.add(zombie);
                    }
                    var cow=EntityType.COW.create(level);cow.setNoAi(true);cow.setPos(4,100,0);level.addFreshEntity(cow);
                    serverPhase.set(1);
                });return;
            }
            if(stage==1) {
                if(serverPhase.get()!=1)return;
                net.minecraft.world.entity.PathfinderMob doll=null;
                for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(dollId.get()))doll=(net.minecraft.world.entity.PathfinderMob)entity;
                if(doll==null)return;
                age++;float spellAge=DollCombatAccess.state(doll).age(doll,0);
                maxVertices=Math.max(maxVertices,TwoIArcanaRenderer.lastVertices());
                if(spellAge>=0) {
                    castSeen=true;combatFrames++;
                    if(spellAge>=100&&spellAge<=180){
                        check(TwoIArcanaRenderer.skyTaken(),"world takeover hides clouds");cloudSuppressed=true;
                        check(mc.options.cloudStatus().get()==originalClouds,"cloud preference remains unchanged");
                    }
                    for(int t:new int[]{14,46,74,90,110,126,155,205})if(spellAge>=t)captureOnce(mc,"combat-"+t);
                    int frame=(int)spellAge/4;
                    if(frame!=movieFrame){movieFrame=frame;capture(mc,"motion-%03d".formatted(frame));}
                    if(spellAge>=90&&!shots.contains("resize")) {
                        shots.add("resize");GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1000,650);mc.resizeDisplay();
                    }
                }
                if(castSeen&&spellAge<0&&age>230) {
                    check(cloudSuppressed&&!TwoIArcanaRenderer.skyTaken(),"cloud suppression ends with spell");
                    LogUtils.getLogger().info("ARCANA RENDER COUNTS post={} vertices={} combatTicks={} fps={}",TwoIArcanaPost.processedFrames(),maxVertices,combatFrames,mc.getFps());
                    check(TwoIArcanaPost.processedFrames()>20,"post chain actually compiled and rendered");
                    check(maxVertices>500&&maxVertices<30_000,"bounded nonempty geometry, vertices="+maxVertices);
                    check(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL remained valid");
                    capture(mc,"combat-after");stage=2;age=0;
                    server(mc,()->{
                        LogUtils.getLogger().info("ARCANA LIVE HEALTH: {}",monsters.stream().map(m->m.getHealth()+" @ "+m.position()).toList());
                        check(monsters.stream().allMatch(m->m.getHealth()==56),"real one-impact damage on all three monsters");
                        if(Boolean.getBoolean("two_i_combat_doll.with_stretcher")) monsters.forEach(m->m.discard());
                        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if(Boolean.getBoolean("two_i_combat_doll.with_stretcher")) StretcherGardenFixture.transfer(player);
                        
                        serverPhase.set(2);
                    });
                }
                return;
            }
            if(stage==2) {
                if(serverPhase.get()!=2)return;
                if(!Boolean.getBoolean("two_i_combat_doll.with_stretcher")){stage=4;age=0;postBefore=TwoIArcanaPost.processedFrames();return;}
                if(!mc.level.dimension().location().toString().equals("useless_stretcher:two_i_starfield"))return;
                if(age++==0)server(mc,()->StretcherGardenFixture.show(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())));
                if(TwoIArcanaRenderer.showAge(0)<0){check(age<120,"garden show packet timeout");return;}
                age=0;stage=3;postBefore=TwoIArcanaPost.processedFrames();
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.resizeDisplay();return;
            }
            if(stage==3) {
                age++;maxVertices=Math.max(maxVertices,TwoIArcanaRenderer.lastVertices());
                if(age==45)capture(mc,"garden-armillary");
                if(age==90)mc.options.particles().set(ParticleStatus.MINIMAL);
                if(age==130){capture(mc,"garden-minimal");check(TwoIArcanaRenderer.quality()==1,"particle quality scales meshes");}
                if(age==170)mc.options.particles().set(ParticleStatus.ALL);
                if(age==200){reload=mc.reloadResourcePacks();return;}
                if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}
                if(age==280)capture(mc,"garden-after-reload");
                if(age==320){framesOff=TwoIArcanaPost.processedFrames();TwoIVisualConfig.POST.set(false);}
                if(age==340){check(TwoIArcanaPost.processedFrames()==framesOff,"geometry fallback skips post processing");
                    check(TwoIArcanaRenderer.lastVertices()>100,"geometry remains with post off");capture(mc,"garden-post-off");TwoIVisualConfig.POST.set(true);}
                if(age==450)capture(mc,"garden-finale");
                if(age>=500) {
                    check(TwoIArcanaPost.processedFrames()>postBefore+100,"post processing survived dimension, resize, reload and fallback");
                    check(GL11.glGetError()==GL11.GL_NO_ERROR,"no final GL errors");
                    check(maxVertices<30_000,"garden geometry stays bounded");
                    TwoIUmbrellaChecks.run(mc);TwoIHandStabilityChecks.run();
                    LogUtils.getLogger().info("2I ARCANA CLIENT CHECKS PASSED: live combat, original book/eye/domain, three real 24-point hits, garden, particle quality, resize, resource reload, post fallback, GL state; combatTicks={}, maxVertices={}, postFrames={}, fps={}",
                            combatFrames,maxVertices,TwoIArcanaPost.processedFrames(),mc.getFps());
                    CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
                    finished=true;mc.stop();
                }
            }
            if(stage==4){
                age++;check(age<800,"standalone second cast timeout");
                CombatDollEntity doll=null;
                for(var e:mc.level.entitiesForRendering())if(e.getUUID().equals(dollId.get()))doll=(CombatDollEntity)e;
                if(doll==null)return;
                float cast=doll.combatState().age(doll,0);
                if(cast>=50&&shots.add("quality-change"))mc.options.particles().set(ParticleStatus.MINIMAL);
                if(cast>=60&&shots.add("quality-shot")){check(TwoIArcanaRenderer.quality()==1,"standalone low quality");capture(mc,"standalone-minimal");}
                if(cast>=70&&shots.add("post-disable")){framesOff=TwoIArcanaPost.processedFrames();TwoIVisualConfig.POST.set(false);}
                if(cast>=83&&shots.add("post-disabled-shot")){
                    check(TwoIArcanaPost.processedFrames()==framesOff,"standalone post switch");
                    check(!TwoIArcanaRenderer.skyTaken(),"post-off fallback does not hide clouds");
                    check(TwoIArcanaRenderer.lastVertices()>100,"standalone geometry fallback");capture(mc,"standalone-post-off");
                    TwoIVisualConfig.POST.set(true);mc.options.particles().set(ParticleStatus.ALL);
                }
                if(cast>=95&&shots.add("resource-reload")){reload=mc.reloadResourcePacks();return;}
                if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}
                if(cast>=160&&shots.add("after-reload"))capture(mc,"standalone-after-reload");
                if(shots.contains("after-reload")&&cast<0){
                    check(TwoIArcanaPost.processedFrames()>postBefore+100,"standalone shader recovered after resize/reload/switch");
                    check(GL11.glGetError()==GL11.GL_NO_ERROR,"standalone OpenGL valid");
                    TwoIUmbrellaChecks.run(mc);TwoIHandStabilityChecks.run();
                    CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
                    LogUtils.getLogger().info("2I COMBAT DOLL STANDALONE CLIENT CHECKS PASSED: no stretcher/AE2/useless_mod; own entity/model, real combat, evolving sky, cloud suppression/restoration, resize, reload, minimal quality, fallback, hands and umbrella; postFrames={}, maxVertices={}, fps={}",TwoIArcanaPost.processedFrames(),maxVertices,mc.getFps());
                    finished=true;mc.stop();
                }
            }
        } catch(Throwable failure) { LogUtils.getLogger().error("2I ARCANA CLIENT CHECKS FAILED",failure);finished=true;mc.stop(); }
    }
    private static void server(Minecraft mc,Runnable task){mc.getSingleplayerServer().execute(()->{try{task.run();}catch(Throwable e){serverFailure.set(e);LogUtils.getLogger().error("ARCANA INTEGRATED SERVER FAILED",e);}});}
    private static void captureOnce(Minecraft mc,String name)throws Exception{if(shots.add(name))capture(mc,name);}
    private static void capture(Minecraft mc,String name)throws Exception{
        if(!name.startsWith("motion"))LogUtils.getLogger().info("ARCANA CAPTURE {} time={} post={} vertices={} fps={} screen={}",name,
                mc.level.getGameTime(),TwoIArcanaPost.processedFrames(),TwoIArcanaRenderer.lastVertices(),mc.getFps(),mc.screen==null?"none":mc.screen.getClass().getSimpleName());
        var directory=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(directory);
        if(name.startsWith("motion") && writes.stream().filter(f->!f.isDone()).count()>=3) return;
        NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget());
        writes.add(CompletableFuture.runAsync(()->{
            try(image) { image.writeToFile(directory.resolve(name+".png")); }
            catch(Exception e) { throw new java.util.concurrent.CompletionException(e); }
        },net.minecraft.Util.ioPool()));
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
