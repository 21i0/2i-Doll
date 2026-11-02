package com.twoi.combatdoll.test;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.client.*;
import com.twoi.combatdoll.combat.*;
import com.twoi.combatdoll.config.TwoIVisualConfig;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real integrated server, real owner-dropped weapons and GLSL/framebuffer checks. */
final class WeaponClientChecks {
    private static int ticks,phase,wait,round,maxVertices,startPost,startFloor,frame=-1;
    private static boolean requested,done,seen,freezeSeen,floorViewed,floorCaptured,mirrorViewed,mirrorCaptured;
    private static CloudStatus clouds;
    private static final AtomicReference<UUID> actorId=new AtomicReference<>();
    private static final AtomicInteger ready=new AtomicInteger();
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static final Set<String> shots=new HashSet<>();
    private static final List<CompletableFuture<Void>> writes=new ArrayList<>();
    private static List<Mob> monsters;
    private static LivingEntity serverActor;
    private static CompletableFuture<Void> reload;
    private static int staffChargeStep,staffChargeWait;
    private static net.minecraft.world.phys.Vec3 firstClientChargeHole,firstServerChargeHole;
    private static long staffAimBaselineClock;
    private static final AtomicReference<StaffChargeSnapshot> staffServerSnapshot=new AtomicReference<>();
    private static final AtomicBoolean staffSnapshotPending=new AtomicBoolean();
    private static final AtomicInteger staffCancelVerified=new AtomicInteger();
    static void tick(Minecraft mc){
        if(Boolean.getBoolean("two_i_combat_doll.model_mesh_export_only"))return;
        if(done)return;
        try{
            check(++ticks<6000,"client timeout phase="+phase+" round="+round);
            if(failure.get()!=null)throw new AssertionError("integrated fixture",failure.get());
            if(mc.getOverlay()!=null)return;
            if(!requested){
                if(mc.screen==null)return;AstralOrbitChecks.run();if(staffAudioOnly())round=3;requested=true;mc.options.guiScale().set(1);mc.options.pauseOnLostFocus=false;
                clouds=mc.options.cloudStatus().get();mc.options.hideGui=true;mc.options.particles().set(ParticleStatus.ALL);
                mc.options.renderDistance().set(8);mc.options.framerateLimit().set(120);mc.resizeDisplay();
                var settings=new LevelSettings("Arcana weapons visual regression",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("arcana-weapons-"+System.currentTimeMillis(),settings,new WorldOptions(210,false,false),
                        access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(phase==0){if(mc.screen!=null)return;phase=1;wait=0;server(mc,()->prepare(mc));return;}
            if(phase==1){
                if(ready.get()!=round+1)return;
                if(!BlackHoleMotionChecks.tick(mc))return;
                if(wait==0)configureRoundQuality(mc);
                mc.options.hideGui=round<2||round==4;
                if(++wait<35)return;
                LivingEntity actor=find(mc);if(actor==null)return;
                check(CombatActors.state(actor).age(actor,0)<0,"unarmed visual actor remains peaceful");
                if(wait==35){floorView(mc,true);return;}
                if(wait<40)return;
                if(wait==40){GroundSurfaceChecks.baseline(mc,label());floorView(mc,false);return;}
                if(wait<44)return;
                startFloor=TwoIArcanaRenderer.floorFrames();
                if(staffRound())AstralAudioChecks.begin(mc,label(),actor);
                phase=2;wait=0;server(mc,()->start(mc));return;
            }
            if(phase==2){
                if(round==3&&!playerStaffControl(mc))return;
                var actor=find(mc);if(actor==null){check(++wait<700,"actor tracking timeout");return;}
                float age=CombatActors.state(actor).age(actor,mc.getTimer().getGameTimeDeltaPartialTick(true));
                BookClockChecks.clientSample(age);
                maxVertices=Math.max(maxVertices,TwoIArcanaRenderer.lastVertices());
                if(age>=0){
                    seen=true;
                    if(!floorViewed){if(staffRound())astralView(mc);else scarletView(mc);}
                    if(staffRound())AstralSequenceChecks.observe(mc,label(),actor,age);
                    if(age>=95&&age<=150){check(TwoIArcanaRenderer.skyTaken(),"world takeover suppresses clouds");check(mc.options.cloudStatus().get()==clouds,"cloud preference remains intact");}
                    if(staffRound()&&age>=48&&age<150){
                        if(!ClientTimeStop.active())server(mc,()->LogUtils.getLogger().error("FREEZE DIAGNOSTIC round={} age={} kind={} frozen={} lease={} actor={}",round,CombatActors.state(serverActor).age(serverActor,0),CombatActors.state(serverActor).kind(),mc.getSingleplayerServer().tickRateManager().isFrozen(),Chronostasis.active(mc.getSingleplayerServer()),serverActor.getUUID()));
                        check(ClientTimeStop.active(),"staff freeze packet arrived age="+age+" kind="+CombatActors.state(actor).kind());freezeSeen=true;
                    }
                    for(int t:new int[]{8,10,24,32,40,42,44,48,56,68,74,80,96,102,104,116,128,140,144,148,164,172,178,182,188,196,200,204,208})if(age>=t&&age<t+3)captureOnce(mc,label()+"-"+t);
                    // BOOK has three required original frames per each of its
                    // eight waves. Avoid an additional full-rate readback movie
                    // that can itself make the integrated server outrun capture.
                    int f=(int)age/4;if(round==1&&f!=frame){frame=f;capture(mc,label()+"-motion-%03d".formatted(f));}
                    if(round==0&&age>=60&&shots.add("resize")){GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1100,720);mc.resizeDisplay();}
                    // Reload blocks the client while the integrated server keeps
                    // running. Observe the actual reverse first; never relabel a
                    // later restored world as a frame from a skipped spell age.
                    if(round==1&&age>=208&&shots.add("reload")){reload=mc.reloadResourcePacks();return;}
                    if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}
                    // The earlier sky-facing screenshots only showed a thin floor strip.
                    // Hold a second camera angle for several real rendered frames before
                    // sampling; its bottom third contains terrain rather than the sky.
                    if(age>=103&&!floorViewed){floorViewed=true;floorView(mc,true);}
                    if(age>=112&&!floorCaptured){
                        check(TwoIArcanaRenderer.floorFrames()>startFloor+8,"terrain takeover pass really rendered");
                        GroundSurfaceChecks.active(mc,label(),staffRound());floorCaptured=true;floorView(mc,false);if(staffRound())astralView(mc);else scarletView(mc);
                    }
                    if(staffRound()){
                        // A second fixed view includes the horizon and mirror below,
                        // rather than treating a sky-only screenshot as reflection proof.
                        if(age>=122&&!mirrorViewed){mirrorViewed=true;mc.options.fov().set(100);mc.player.setXRot(6);mc.player.xRotO=6;}
                        if(age>=130&&!mirrorCaptured){captureOnce(mc,label()+"-astral-water-reflection");mirrorCaptured=true;astralView(mc);}
                        if(age>=40)captureOnce(mc,label()+"-astral-prediction");
                        if(age>=68)captureOnce(mc,label()+"-astral-stopped-a");
                        if(age>=116)captureOnce(mc,label()+"-astral-stopped-b");
                        if(age>=188)captureOnce(mc,label()+"-astral-reverse-a");
                        if(age>=200)captureOnce(mc,label()+"-astral-reverse-b");
                    }
                    if(!staffRound()){
                        BookSlamVisualChecks.observe(mc,label(),age,round==2);
                        ScarletEyeChecks.observe(mc,label(),age);
                        // Keep the complete six-point seal and eye centred. The
                        // obsolete mountain pans hid the eyelids during the blink.
                        if(age>=56)captureOnce(mc,label()+"-scarlet-unfolding");
                        if(age>=80)captureOnce(mc,label()+"-scarlet-overview");
                        if(age>=96)captureOnce(mc,label()+"-scarlet-eye-open");
                        // 104 is deliberately the ground camera; 148 captures the
                        // second blink using exactly the same eye-facing camera.
                        if(age>=116)captureOnce(mc,label()+"-scarlet-eye-reopened");
                        if(age>=148)captureOnce(mc,label()+"-scarlet-eye-blink");
                        if(age>=164)captureOnce(mc,label()+"-scarlet-bloodshot");
                        if(age>=182)captureOnce(mc,label()+"-scarlet-tentacles");
                        if(age>=204)captureOnce(mc,label()+"-scarlet-finale");
                    }
                }
                if(seen&&age<0){
                    check(!ClientTimeStop.active()&&!TwoIArcanaRenderer.skyTaken(),"freeze and clouds restore after spell");
                    check(TwoIArcanaPost.processedFrames()>startPost+30,"real post-processing executed");
                    // A local first-person holder uses the real item BEWLR.
                    // Its duplicate world STAFF is intentionally suppressed;
                    // the bounded spell markers remain, while the full held
                    // model is independently checked in all actual contexts.
                    int minimumSpellVertices=round==3?100:200;
                    check(maxVertices>minimumSpellVertices&&maxVertices<(staffRound()?30000:80000),"bounded spell geometry "+maxVertices);
                    check(floorCaptured,"full takeover floor was sampled before spell ended");
                    if(staffRound()){check(mirrorCaptured,"water reflection camera was captured");AstralSequenceChecks.finish(mc,label());AstralAudioChecks.finish(mc,label());}
                    else BookSlamVisualChecks.finish(mc,label());
                    check(GL11.glGetError()==GL11.GL_NO_ERROR,"valid OpenGL state");
                    captureOnce(mc,label()+"-after");floorView(mc,true);phase=3;
                    server(mc,()->{
                        check(monsters.stream().noneMatch(Entity::isAlive),"real impact kills three targets");
                        if(!(serverActor instanceof net.minecraft.world.entity.player.Player))serverActor.discard();
                        ready.set(0);
                    });wait=0;
                }return;
            }
            if(phase==3){
                if(ready.get()!=0||++wait<15)return;
                GroundSurfaceChecks.after(mc,label());floorView(mc,false);
                LogUtils.getLogger().info("WEAPON VISUAL ROUND PASSED: {} post={} maxVertices={} fps={} freezeSeen={}",label(),TwoIArcanaPost.processedFrames(),maxVertices,mc.getFps(),freezeSeen);
                if(staffAudioOnly()){
                    CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();GroundSurfaceChecks.awaitWrites();
                    LogUtils.getLogger().info("2I ASTRAL AUDIO CLIENT CHECKS PASSED: focused player staff only, real gameMode use/short cancellation/retry/full hold/release, five actual audio sources, stopped charge, one time-stop declaration and reverse, freeze-onset alignment, real impact, floor restore and GL state; not a five-entry regression; postFrames={}",TwoIArcanaPost.processedFrames());
                    done=true;mc.stop();return;
                }
                if(++round<(Boolean.getBoolean("two_i_combat_doll.with_stretcher")?5:4)){seen=false;frame=-1;freezeSeen=false;floorViewed=false;floorCaptured=false;mirrorViewed=false;mirrorCaptured=false;maxVertices=0;startPost=TwoIArcanaPost.processedFrames();phase=1;wait=0;server(mc,()->prepare(mc));return;}
                TwoIUmbrellaChecks.run(mc);TwoIHandStabilityChecks.run();
                phase=4;ScarletBookRenderChecks.begin(mc);return;
            }
            if(phase==4&&ScarletBookRenderChecks.finished()){
                CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();GroundSurfaceChecks.awaitWrites();ScarletEyeChecks.awaitWrites();ScarletBookRenderChecks.awaitWrites();BookSlamVisualChecks.awaitWrites();
                LogUtils.getLogger().info("2I WEAPON CLIENT CHECKS PASSED: companion and player book/staff, client gameMode staff use/hold/early cancellation/full release, astral stage/mirror frame evidence, real target execution, original longhuan, scarlet/water floor replacement and restore, all eight BOOK tube slam stages and three mountain views per caster, scarlet sequence and book angle evidence, server freeze sync, cloud restore, window resize, resource reload, GL state, bounded geometry and stable hands; postFrames={}",TwoIArcanaPost.processedFrames());done=true;mc.stop();
            }
        }catch(Throwable error){LogUtils.getLogger().error("2I WEAPON CLIENT CHECKS FAILED phase="+phase+" round="+round,error);done=true;mc.stop();}
    }
    private static void configureRoundQuality(Minecraft mc){
        int requestedQuality=staffRound()?3:Integer.getInteger("two_i_combat_doll.axolotl_visual_quality",3);
        check(requestedQuality>=1&&requestedQuality<=3,"explicit BOOK visual quality within config bounds");
        TwoIVisualConfig.QUALITY.set(requestedQuality);
        LogUtils.getLogger().info("BOOK/STAFF CLIENT FIXTURE QUALITY: round={} label={} quality={} particles={}",
                round,label(),TwoIArcanaRenderer.quality(),mc.options.particles().get());
    }
    private static void prepare(Minecraft mc){
        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
            // Keep most of the platform as recognisable stone. The left foreground
            // contains highly chromatic markers that a real replacement must hide.
            var block=x<=-2&&z>=2?(Math.floorMod(x+z,2)==0?Blocks.MAGENTA_CONCRETE:Blocks.YELLOW_CONCRETE)
                    :(Math.floorMod(x+z,6)==0?Blocks.CHISELED_STONE_BRICKS:Blocks.STONE_BRICKS);
            level.setBlock(new BlockPos(x,99,z),block.defaultBlockState(),2);
        }
        // Vertical sides must keep their own colour while the horizontal top can
        // receive the floor effect; screenshots retain this occlusion fixture.
        for(int z=2;z<=5;z++)for(int y=100;y<=102;y++)level.setBlock(new BlockPos(6,y,z),
                (y==101?Blocks.WHITE_CONCRETE:Blocks.ORANGE_CONCRETE).defaultBlockState(),2);
        player.connection.teleport(.5,100,10.5,180,-23);player.getInventory().clearContent();
        LivingEntity actor;
        if(round==4)actor=StretcherGardenFixture.spawn(player);
        else if(round>=2)actor=player;
        else if(round==0&&Boolean.getBoolean("two_i_combat_doll.with_longhuan")){
            actor=(LivingEntity)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("longhuan:longhuan_doll")).create(level);
            try{actor.getClass().getMethod("initialize",UUID.class,ItemStack.class).invoke(actor,player.getUUID(),new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("longhuan:longhuan_doll"))));}catch(Exception e){throw new AssertionError(e);}
        }else if(round==0)actor=player;
        else{var d=TwoICombatDollMod.DOLL_ENTITY.get().create(level);d.setOwnerId(player.getUUID());actor=d;}
        if(actor!=player){((Mob)actor).setNoAi(true);actor.moveTo(.5,100,.5,180,0);check(level.addFreshEntity(actor),"companion joins");}
        serverActor=actor;actorId.set(actor.getUUID());monsters=new ArrayList<>();
        for(int i=0;i<3;i++){var z=EntityType.ZOMBIE.create(level);z.setNoAi(true);z.setPos(-2.5+i*3,100,-7);z.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));level.addFreshEntity(z);monsters.add(z);}
        ready.set(round+1);
    }
    private static void start(Minecraft mc){
        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var level=player.serverLevel();
        BookClockChecks.begin(serverActor,label(),mc.gameDirectory.toPath().resolve("screenshots"));
        ItemStack weapon=new ItemStack(staffRound()?TwoICombatDollMod.STAFF.get():TwoICombatDollMod.BOOK.get());
        if(serverActor==player){player.setItemInHand(InteractionHand.MAIN_HAND,weapon);if(round!=3)check(weapon.use(level,player,InteractionHand.MAIN_HAND).getResult().consumesAction(),"manual weapon use");}
        else{var drop=new ItemEntity(level,serverActor.getX(),serverActor.getY(),serverActor.getZ(),weapon);drop.setThrower(player);level.addFreshEntity(drop);WeaponEquipment.collect(serverActor);check(CombatActors.state(serverActor).armed(),"actual owner-dropped weapon equips");}
    }
    private static LivingEntity find(Minecraft mc){if(mc.player.getUUID().equals(actorId.get()))return mc.player;for(var e:mc.level.entitiesForRendering())if(e.getUUID().equals(actorId.get()))return (LivingEntity)e;return null;}
    private static boolean playerStaffControl(Minecraft mc)throws Exception {
        if(staffChargeStep>=5)return true;
        requestStaffSnapshot(mc);
        var state=CombatActors.state(mc.player);
        var observed=staffServerSnapshot.get();
        staffCheck(mc,++staffChargeWait<250,"client held-staff control timeout step="+staffChargeStep);
        if(staffChargeStep==0){
            if(!mc.player.getMainHandItem().is(TwoICombatDollMod.STAFF.get()))return false;
            mc.options.hideGui=false;mc.options.fov().set(100);staffLook(mc,180,-30);
            firstClientChargeHole=firstServerChargeHole=null;staffCancelVerified.set(0);
            mc.options.keyUse.setDown(true);
            check(mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND).consumesAction(),"real client starts staff item use");
            staffChargeStep=1;staffChargeWait=0;return false;
        }
        if(staffChargeStep==1){
            // The fresh integrated server can trail client ticks. Wait for its
            // real use acknowledgement and initial rotation before taking a baseline.
            if(observed!=null&&observed.charging())
                staffCheck(mc,observed.chargeTicks()<32,"short-control server charge deadline before 44-tick commit");
            if(firstClientChargeHole==null){
                staffLook(mc,180,-30);
                if(observed==null||!observed.charging()||!observed.using()||!state.charging()
                        ||!rotationNear(observed,180,-30)
                        ||state.blackHole(mc.player).distanceToSqr(observed.hole())>.05)return false;
                firstClientChargeHole=state.blackHole(mc.player);firstServerChargeHole=observed.hole();
                staffAimBaselineClock=observed.clock();
                LogUtils.getLogger().info("ASTRAL STAFF INITIAL CHARGE ACK: {}",staffDiagnostic(mc));
                staffLook(mc,155,-40);return false;
            }
            // Reapply an actual local camera rotation each tick, as sustained
            // mouse aiming would; vanilla movement packets remain the only input.
            staffLook(mc,155,-40);
            if(observed==null||observed.clock()<=staffAimBaselineClock)return false;
            boolean serverMoved=observed.hole().distanceToSqr(firstServerChargeHole)>.05;
            boolean clientMoved=state.blackHole(mc.player).distanceToSqr(firstClientChargeHole)>.05;
            if(observed.charging()&&rotationNear(observed,155,-40)&&serverMoved&&clientMoved
                    &&state.blackHole(mc.player).distanceToSqr(observed.hole())<.05){
                staffCheck(mc,state.charging()&&mc.player.isUsingItem(),"real client holds charging staff");
                staffCheck(mc,!ClientTimeStop.active(),"client prediction does not freeze early");
                staffCheck(mc,serverMoved&&clientMoved,"server-approved black-hole movement reaches real client while aiming");
                LogUtils.getLogger().info("ASTRAL STAFF SHORT AIM ROUND TRIP PASSED: {}",staffDiagnostic(mc));
                AstralAudioChecks.rememberCancelledVoice();
                mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);
                // Release before the framebuffer read, which can stall while
                // the server keeps ticking. The displayed frame is still the held pose.
                captureOnce(mc,"player-staff-charge-short-control");
                staffChargeStep=2;staffChargeWait=0;
            }return false;
        }
        if(staffChargeStep==2){
            if(staffChargeWait<8)return false;
            if(observed==null||observed.charging()||state.charging())return false;
            staffCheck(mc,state.age(mc.player,0)<0&&!ClientTimeStop.active(),"client short release cancels spell and freeze");
            if(staffCancelVerified.compareAndSet(0,1))server(mc,()->{
                var owner=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                check(CombatWorldData.get(owner.getServer()).remaining(WeaponData.identity(owner.getMainHandItem()))==0,"real client short release reserves no server cooldown");
                staffCancelVerified.set(2);
            });
            if(staffCancelVerified.get()!=2)return false;
            AstralAudioChecks.cancelled(mc);
            mc.options.keyUse.setDown(true);
            check(mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND).consumesAction(),"real client reuses same staff after short release");
            staffChargeStep=3;staffChargeWait=0;return false;
        }
        if(staffChargeStep==3){
            if(staffChargeWait==8){mc.player.setYRot(205);mc.player.setXRot(-23);}
            if(staffChargeWait==24){mc.player.setYRot(180);mc.player.setXRot(-30);}
            if(staffChargeWait%4==0)capture(mc,"player-staff-held-motion-%03d".formatted(staffChargeWait/4));
            if(staffChargeWait>=60&&observed!=null&&observed.charging()&&observed.chargeTicks()>=60){
                staffCheck(mc,state.charging()&&mc.player.isUsingItem(),"full client charge keeps item in use");
                staffCheck(mc,state.age(mc.player,0)<=43&&!ClientTimeStop.active(),"full held charge remains in pre-freeze presentation");
                captureOnce(mc,"player-staff-charge-full-hold");
                mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);
                staffChargeStep=4;staffChargeWait=0;
            }return false;
        }
        if(staffChargeStep==4){
            if(staffChargeWait<6||state.charging())return false;
            check(state.age(mc.player,0)>=44,"client full release enters locked cast");
            check(ClientTimeStop.active(),"full release receives time-stop announcement");
            LogUtils.getLogger().info("ASTRAL CLIENT HOLD CHECKS PASSED: real gameMode use/release, short cancellation, retry, aim rotation and full held preview before committed cast");
            staffChargeStep=5;return true;
        }
        return false;
    }
    private record StaffChargeSnapshot(long clock,long start,boolean charging,float age,float chargeTicks,
                                       boolean using,float yaw,float pitch,net.minecraft.world.phys.Vec3 position,
                                       net.minecraft.world.phys.Vec3 hole,net.minecraft.world.phys.Vec3 aim){ }
    private static void requestStaffSnapshot(Minecraft mc){
        if(!staffSnapshotPending.compareAndSet(false,true))return;
        server(mc,()->{try{
            var owner=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if(owner==null)return;
            var state=CombatActors.state(owner);
            staffServerSnapshot.set(new StaffChargeSnapshot(CombatWorldData.get(owner.getServer()).clock(),state.start(),
                    state.charging(),state.age(owner,0),state.chargeTicks(owner,0),owner.isUsingItem(),
                    owner.getYRot(),owner.getXRot(),owner.position(),state.blackHole(owner),state.aimPoint(owner)));
        }finally{staffSnapshotPending.set(false);}});
    }
    private static void staffLook(Minecraft mc,float yaw,float pitch){mc.player.setYRot(yaw);mc.player.setXRot(pitch);}
    private static boolean rotationNear(StaffChargeSnapshot observed,float yaw,float pitch){
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(observed.yaw()-yaw))<.5&&Math.abs(observed.pitch()-pitch)<.5;
    }
    private static String staffDiagnostic(Minecraft mc){
        var state=CombatActors.state(mc.player);
        return "step="+staffChargeStep+" clientWait="+staffChargeWait+" clientRotation="+mc.player.getYRot()+","+mc.player.getXRot()
                +" clientPosition="+mc.player.position()+" clientUsing="+mc.player.isUsingItem()
                +" clientAge="+state.age(mc.player,0)+" clientCharge="+state.chargeTicks(mc.player,0)
                +" clientCharging="+state.charging()+" clientHole="+state.blackHole(mc.player)
                +" baselineClientHole="+firstClientChargeHole+" baselineServerHole="+firstServerChargeHole
                +" baselineServerClock="+staffAimBaselineClock+" expectedInitialPosition=(0.5,100.0,10.5) server="+staffServerSnapshot.get();
    }
    private static void staffCheck(Minecraft mc,boolean valid,String message){
        if(!valid)throw new AssertionError(message+"; "+staffDiagnostic(mc));
    }
    private static boolean staffRound(){return round%2==1||round==4;}
    private static boolean staffAudioOnly(){return Boolean.getBoolean("two_i_combat_doll.staff_audio_only_test");}
    private static String label(){return round==4?"original-2i-staff":(round<2?"companion-":"player-")+(staffRound()?"staff":"book");}
    private static void floorView(Minecraft mc,boolean floor){
        mc.options.hideGui=floor||round<2||round==4;
        mc.options.fov().set(70);
        mc.player.setYRot(180);mc.player.yRotO=180;
        mc.player.setXRot(floor?20:-23);mc.player.xRotO=floor?20:-23;
    }
    private static void scarletView(Minecraft mc){
        mc.options.hideGui=round<2;
        mc.options.fov().set(100);
        mc.player.setYRot(180);mc.player.yRotO=180;
        float pitch=round==2?-49:-34;
        mc.player.setXRot(pitch);mc.player.xRotO=pitch;
    }
    private static void astralView(Minecraft mc){
        mc.options.hideGui=round<2||round==4;
        mc.options.fov().set(100);
        mc.player.setYRot(180);mc.player.yRotO=180;
        float pitch=round==3?-42:-30;
        mc.player.setXRot(pitch);mc.player.xRotO=pitch;
    }
    private static void server(Minecraft mc,Runnable task){mc.getSingleplayerServer().execute(()->{try{task.run();}catch(Throwable e){failure.set(e);}});}
    private static void captureOnce(Minecraft mc,String name)throws Exception{if(shots.add(name))capture(mc,name);}
    private static void capture(Minecraft mc,String name)throws Exception{
        var path=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(path);
        if(name.contains("motion")&&writes.stream().filter(f->!f.isDone()).count()>2)return;
        NativeImage image=FramebufferCapture.take(mc);writes.add(CompletableFuture.runAsync(()->{try(image){image.writeToFile(path.resolve(name+".png"));}catch(Exception e){throw new CompletionException(e);}},net.minecraft.Util.ioPool()));
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
