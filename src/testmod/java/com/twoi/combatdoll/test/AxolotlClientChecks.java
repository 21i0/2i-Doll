package com.twoi.combatdoll.test;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.client.*;
import com.twoi.combatdoll.combat.*;
import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Actual client use, pet tracking, independent shaders, XiaoXiao passenger and flute lifecycle. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID,value=Dist.CLIENT)
public final class AxolotlClientChecks {
    private static int ticks,phase,wait,round,startFloor,maxVertices;
    private static boolean requested,done,seen;
    private static final AtomicInteger ready=new AtomicInteger();
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static final AtomicReference<UUID> actorId=new AtomicReference<>();
    private static final Set<String> shots=new HashSet<>();
    private static final List<CompletableFuture<Void>> writes=new ArrayList<>();
    private static final List<String> samples=new ArrayList<>();
    private static CangshengAxolotlEntity serverPet;
    private static LivingEntity serverDoll;
    private static List<Mob> enemies;
    private static UUID weaponIdentity;
    private static CloudStatus clouds;
    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored){
        if(Boolean.getBoolean("two_i_combat_doll.model_mesh_export_only"))return;
        if(!Boolean.getBoolean("two_i_combat_doll.axolotl_test")||!Boolean.getBoolean("two_i_combat_doll.client_test")||done)return;
        var mc=Minecraft.getInstance();
        try{
            check(++ticks<4000,"timeout phase="+phase+" wait="+wait+" round="+round);
            if(failure.get()!=null)throw new AssertionError("server fixture",failure.get());
            if(mc.getOverlay()!=null)return;
            if(!requested){
                if(mc.screen==null)return;requested=true;
                int quality=Integer.getInteger("two_i_combat_doll.axolotl_visual_quality",3);
                check(quality>=1&&quality<=3,"explicit client visual quality within config bounds");
                com.twoi.combatdoll.config.TwoIVisualConfig.QUALITY.set(quality);
                mc.options.guiScale().set(1);mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(8);
                mc.options.framerateLimit().set(120);mc.options.particles().set(ParticleStatus.ALL);mc.options.fov().set(90);clouds=mc.options.cloudStatus().get();
                var settings=new LevelSettings("Cangsheng guardian visual checks",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("cangsheng-"+System.currentTimeMillis(),settings,new WorldOptions(627,false,false),
                        access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||(mc.screen!=null&&phase!=9))return;
            wait++;
            if(phase==0){phase=1;wait=0;server(mc,()->setup(mc));return;}
            if(phase==1){
                if(ready.get()!=1||wait<30||!mc.player.getMainHandItem().is(TwoICombatDollMod.AXOLOTL.get()))return;
                capture(mc,"pet-hand-front");
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));phase=9;wait=0;return;
            }
            if(phase==9){if(wait<8)return;capture(mc,"pet-inventory-gui");mc.setScreen(null);mc.player.swing(InteractionHand.MAIN_HAND);phase=2;wait=0;return;}
            if(phase==2){
                if(wait==6)capture(mc,"pet-hand-play-swing");
                if(wait<15)return;
                check(mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND).consumesAction(),"actual right click deploys titan");
                phase=3;wait=0;return;
            }
            if(phase==3){
                CangshengAxolotlEntity pet=findAny(mc);
                if(pet==null)return;
                actorId.set(pet.getUUID());
                if(wait==4)server(mc,()->owner(mc).connection.teleport(pet.getX(),pet.getY()+5,pet.getZ()+52,180,0));
                if(wait>=15)captureOnce(mc,"titan-emergence");
                if(wait>=42)captureOnce(mc,"titan-full-size");
                if(wait<48)return;
                check(!pet.mountMode(),"player releases titan mode");
                check(pet.getBbWidth()>12&&pet.getBbHeight()>6,"client titan dimensions refreshed, not builder miniature");
                check(mc.player.getMainHandItem().isEmpty(),"live pet transferred out of creative hand too");
                phase=4;wait=0;startFloor=TwoIArcanaRenderer.floorFrames();server(mc,()->start(mc,false));return;
            }
            if(phase==4){
                var pet=find(mc);if(pet==null)return;
                float age=pet.attackAge(mc.getTimer().getGameTimeDeltaPartialTick(true));
                if(age>=0&&age<220){
                    seen=true;maxVertices=Math.max(maxVertices,TwoIArcanaRenderer.lastVertices());
                    String label=round==0?"titan":"xiaoxiao";
                    if(age>=32){look(mc,180,-18);}
                    for(int mark:new int[]{18,40,62,84,98,108,124,138,150,161,174,200})
                        if(age>=mark&&age<mark+4)captureOnce(mc,label+"-stage-"+mark);
                    if(age>60&&age<180){
                        check(!ClientTimeStop.active(),"dream realm does not freeze world");
                        check(TwoIArcanaRenderer.skyTaken(),"dream takeover suppresses vanilla clouds");
                        check(mc.options.cloudStatus().get()==clouds,"original cloud setting preserved");
                    }
                    // Allow the requested camera to be rendered before reading
                    // its framebuffer; a ClientTick screenshot is the last frame.
                    if(age>85&&age<93){look(mc,180,26);if(age>=89)captureOnce(mc,label+"-floor-active");}
                    if(age>110&&age<119){look(mc,158,-12);if(age>=114)captureOnce(mc,label+"-left-view");}
                    if(age>181&&age<189){look(mc,201,-12);if(age>=185)captureOnce(mc,label+"-right-view");}
                    samples.add(label+","+age+","+TwoIArcanaRenderer.spellMode+","+TwoIArcanaRenderer.lastVertices()+","+AxolotlDreamScene.frames()+","+mc.getFps());
                }
                if(seen&&age<0){
                    check(!ClientTimeStop.active(),"no frozen state after attack");
                    check(TwoIArcanaRenderer.floorFrames()>startFloor+12,"real dream floor pass executed");
                    check(AxolotlDreamScene.frames()>20,"real crystal stage was rendered");
                    check(maxVertices>1000&&maxVertices<140000,"bounded detailed scene geometry "+maxVertices);
                    check(GL11.glGetError()==GL11.GL_NO_ERROR,"valid GL state");
                    captureOnce(mc,(round==0?"titan":"xiaoxiao")+"-after");
                    phase=5;wait=0;server(mc,()->finishAttack(mc));
                }return;
            }
            if(phase==5){
                if(ready.get()!=2||wait<20)return;
                if(round==0){
                    if(!mc.player.getMainHandItem().is(TwoICombatDollMod.AXOLOTL_FLUTE.get()))return;
                    check(mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND).consumesAction(),"actual client flute use");
                    phase=6;wait=0;return;
                }
                server(mc,()->{
                    var owner=owner(mc);owner.connection.teleport(serverDoll.getX(),serverDoll.getY(),serverDoll.getZ()+2,180,0);
                    try{check((Boolean)serverDoll.getClass().getMethod("reclaim",net.minecraft.world.entity.player.Player.class).invoke(serverDoll,owner),"actual XiaoXiao reclaim");}
                    catch(ReflectiveOperationException e){throw new AssertionError(e);}
                    check(serverPet.isRemoved(),"mount retired when XiaoXiao reclaimed");ready.set(4);
                });phase=8;wait=0;return;
            }
            if(phase==6){
                var pet=find(mc);
                if(pet!=null){if(pet.recalling()&&pet.animationAge(0)>12)captureOnce(mc,"titan-flute-retreat");return;}
                if(wait<45)return;
                phase=7;wait=0;server(mc,()->{
                    var owner=owner(mc);ItemStack returned=ItemStack.EMPTY;
                    for(var s:owner.getInventory().items)if(s.is(TwoICombatDollMod.AXOLOTL.get())){check(returned.isEmpty(),"only one returned pet");returned=s;}
                    check(!returned.isEmpty()&&Objects.equals(weaponIdentity,WeaponData.identity(returned)),"flute preserves pet identity");
                    var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("xiaoxiao:xiaoxiao_doll"));
                    serverDoll=(LivingEntity)type.create(owner.serverLevel());check(serverDoll instanceof DollCombatAccess,"optional XiaoXiao bridge active");
                    try{serverDoll.getClass().getMethod("initialize",UUID.class,ItemStack.class).invoke(serverDoll,owner.getUUID(),new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("xiaoxiao:xiaoxiao_doll"))));}
                    catch(ReflectiveOperationException e){throw new AssertionError(e);}
                    serverDoll.moveTo(.5,100,-1,180,0);owner.serverLevel().addFreshEntity(serverDoll);
                    ItemStack gift=new ItemStack(TwoICombatDollMod.AXOLOTL.get()); // fresh independent pet, no old titan cooldown
                    ItemEntity drop=new ItemEntity(owner.level(),.5,100,-1,gift);drop.setThrower(owner);owner.serverLevel().addFreshEntity(drop);
                    WeaponEquipment.collect(serverDoll);
                    check(serverDoll.getVehicle() instanceof CangshengAxolotlEntity,"real mount available for close camera");
                    serverPet=(CangshengAxolotlEntity)serverDoll.getVehicle();serverPet.setYRot(0);serverPet.yBodyRot=0;
                    ready.set(3);
                });return;
            }
            if(phase==7){
                if(ready.get()!=3)return;
                var pet=findAny(mc);
                if(pet==null||!pet.mountMode()||pet.getPassengers().isEmpty())return;
                actorId.set(pet.getUUID());captureOnce(mc,"xiaoxiao-real-hopping-mount");
                if(wait==15){mc.options.hideGui=true;server(mc,()->owner(mc).connection.teleport(serverPet.getX(),serverPet.getY()+.2,serverPet.getZ()+3,180,4));}
                if(wait>=24&&wait<31){look(mc,180,4);captureOnce(mc,"xiaoxiao-mount-close-front");}
                if(wait==32)server(mc,()->owner(mc).connection.teleport(serverPet.getX()+3,serverPet.getY()+.2,serverPet.getZ(),90,4));
                if(wait>=40&&wait<47){look(mc,90,4);captureOnce(mc,"xiaoxiao-mount-close-side");}
                if(wait==48)server(mc,()->owner(mc).connection.teleport(serverPet.getX(),serverPet.getY()+.2,serverPet.getZ()-3,0,4));
                if(wait>=56&&wait<63){look(mc,0,4);captureOnce(mc,"xiaoxiao-mount-close-back");}
                if(wait<70)return;mc.options.hideGui=false;
                round=1;seen=false;maxVertices=0;phase=4;wait=0;server(mc,()->start(mc,true));return;
            }
            if(phase==8){
                if(ready.get()!=4||wait<25)return;
                check(mc.level.entitiesForRendering().spliterator()!=null,"level remains usable");
                check(!TwoIArcanaRenderer.skyTaken(),"dream sky/cloud suppression restored");
                CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
                var path=mc.gameDirectory.toPath().resolve("screenshots/axolotl-frame-states.csv");
                Files.writeString(path,"actor,age,mode,vertices,scene_frames,fps_snapshot\n"+String.join("\n",samples));
                LogUtils.getLogger().info("CANGSHENG AXOLOTL CLIENT CHECKS PASSED: real client deploy/flute recall, living palm animation, titan and actual XiaoXiao passenger, both complete dream attacks, terrain/sky replacement, GL state, cloud restore, same returned identity, bounded scene geometry; screenshots require independent art review");
                done=true;mc.stop();
            }
        }catch(Throwable error){LogUtils.getLogger().error("CANGSHENG AXOLOTL CLIENT CHECKS FAILED phase="+phase+" round="+round,error);done=true;mc.stop();}
    }
    private static ServerPlayer owner(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static void setup(Minecraft mc){
        var owner=owner(mc);var level=owner.serverLevel();level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
        for(int x=-32;x<=32;x++)for(int z=-40;z<=24;z++)level.setBlock(new BlockPos(x,99,z),(x<0&&z>4?Blocks.MAGENTA_CONCRETE:Blocks.STONE_BRICKS).defaultBlockState(),2);
        for(int x=-16;x<=16;x++)for(int z=25;z<=80;z++)level.setBlock(new BlockPos(x,99,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
        owner.connection.teleport(.5,100,13.5,180,-5);owner.getInventory().clearContent();owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TwoICombatDollMod.AXOLOTL.get()));ready.set(1);
    }
    private static void start(Minecraft mc,boolean mounted){
        var owner=owner(mc);var level=owner.serverLevel();
        serverPet=level.getEntitiesOfClass(CangshengAxolotlEntity.class,owner.getBoundingBox().inflate(80),p->p.mountMode()==mounted).getFirst();
        if(!mounted)weaponIdentity=serverPet.leaseIdentity();
        serverPet.teleportTo(.5,100,mounted?-1:1);owner.connection.teleport(.5,100,17.5,180,-18);
        enemies=new ArrayList<>();for(int i=0;i<3;i++){var z=EntityType.ZOMBIE.create(level);z.setNoAi(true);z.setPos(-3+i*3,100,-11);z.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));level.addFreshEntity(z);enemies.add(z);}
        check(serverPet.combatState().begin(serverPet,level,false),"actual pet begins dream attack");
    }
    private static void finishAttack(Minecraft mc){
        check(enemies.stream().noneMatch(Entity::isAlive),"actual authoritative crush kills enemies");
        check(serverPet.isAlive()&&!serverPet.recalling(),"titan stays alive until flute");
        if(round==0)owner(mc).setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TwoICombatDollMod.AXOLOTL_FLUTE.get()));ready.set(2);
    }
    private static CangshengAxolotlEntity find(Minecraft mc){for(var e:mc.level.entitiesForRendering())if(e.getUUID().equals(actorId.get())&&e instanceof CangshengAxolotlEntity p)return p;return null;}
    private static CangshengAxolotlEntity findAny(Minecraft mc){for(var e:mc.level.entitiesForRendering())if(e instanceof CangshengAxolotlEntity p)return p;return null;}
    private static void look(Minecraft mc,float yaw,float pitch){mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;}
    private static void server(Minecraft mc,Runnable action){mc.getSingleplayerServer().execute(()->{try{action.run();}catch(Throwable e){failure.set(e);}});}
    private static void captureOnce(Minecraft mc,String name)throws Exception{if(shots.add(name))capture(mc,name);}
    private static void capture(Minecraft mc,String name)throws Exception{
        if(name.equals("pet-hand-front")){
            // Authoring receives the exact baked Java landscape, never a
            // prettier approximation made separately for offline screenshots.
            Path authoring=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(authoring);
            for(String lod:new String[]{"HIGH","LOW"}){
                var field=CangshengAxolotlModel.class.getDeclaredField("RIDGE_"+lod);field.setAccessible(true);
                Object mesh=field.get(null);var data=mesh.getClass().getDeclaredMethod("data");data.setAccessible(true);
                Files.writeString(authoring.resolve("cangsheng-ridge-"+lod.toLowerCase(java.util.Locale.ROOT)+".json"),new com.google.gson.Gson().toJson(data.invoke(mesh)));
            }
        }
        if(name.startsWith("pet-hand-")||name.startsWith("xiaoxiao-mount-close-")){
            int quality=TwoIArcanaRenderer.quality(),actual=CangshengAxolotlModel.lastVertices();
            int limit=quality==1?7000:15000;
            check(actual>0&&actual<limit,"actual rendered guardian geometry "+name+" quality="+quality+" vertices="+actual+" limit="+limit);
            LogUtils.getLogger().info("CANGSHENG MODEL VERTICES shot={} quality={} actual={} limit={}",name,quality,actual,limit);
        }
        Path dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);NativeImage shot=Screenshot.takeScreenshot(mc.getMainRenderTarget());
        writes.add(CompletableFuture.runAsync(()->{try(shot){shot.writeToFile(dir.resolve(name+".png"));}catch(Exception e){throw new CompletionException(e);}},net.minecraft.Util.ioPool()));
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
