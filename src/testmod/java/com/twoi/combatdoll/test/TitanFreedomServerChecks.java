package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.*;
import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.nio.file.*;
import java.util.*;

/** Focused natural-tick test, including an actual second-JVM continuation. Never shipped. */
public final class TitanFreedomServerChecks {
    private static final String WITNESS="titan-freedom-restart.dat";
    private static int ticks,phase,elapsed;
    private static boolean done;
    private static Map<UUID,ServerPlayer> players;
    private static ServerPlayer owner,other;
    private static CangshengAxolotlEntity titan,mount;
    private static com.customdoll.xiaoxiao.content.entity.DollEntity rider;
    private static Vec3 anchor,previous;
    private static UUID identity;
    private static ItemStack stale;
    private static ItemStack remoteStale;
    private static CangshengAxolotlEntity staleActor;
    private static UUID remoteIdentity;
    private static CangshengAxolotlEntity crossActor;
    private static ItemStack crossSource;
    private static CompoundTag witness;
    private static double mountStart;
    private static float restingYaw;
    private static final List<String> trace=new ArrayList<>();
    private TitanFreedomServerChecks(){}

    public static void tick(MinecraftServer server){
        if(done||++ticks<100)return;
        try{
            check(ticks<2000,"deadline phase="+phase);
            if(Boolean.getBoolean("two_i_combat_doll.titan_freedom_restart_test")){restart(server);return;}
            if(phase==0){setup(server);phase=1;elapsed=0;return;}
            elapsed++;record();
            if(phase<9)check(titan.isAlive()&&!titan.isRemoved(),"actual titan persists before explicit recall");
            if(phase==1){
                if(elapsed<50)return;
                check(!titan.emerging(),"natural emergence completes");anchor=titan.position();restingYaw=titan.getYRot();
                var legacy=titan.saveWithoutId(new CompoundTag());check(legacy.contains("TitanHomeX",Tag.TAG_ANY_NUMERIC),"new titan records local home");
                legacy.remove("TitanHomeX");legacy.remove("TitanHomeY");legacy.remove("TitanHomeZ");titan.load(legacy);
                owner.setPos(anchor.add(0,0,4));phase=2;elapsed=0;return;
            }
            if(phase==2){
                check(titan.position().subtract(anchor).horizontalDistance()<12,"near observer does not chase or flee across its whole observation patch");
                check(Math.abs(net.minecraft.util.Mth.wrapDegrees(titan.getYRot()-restingYaw))<.1,"walking round idle titan does not drag its whole body after camera");
                if(elapsed==20){owner.setPos(anchor.add(-4,0,0));var upgraded=titan.saveWithoutId(new CompoundTag());check(Math.abs(upgraded.getDouble("TitanHomeX")-anchor.x)<.01&&Math.abs(upgraded.getDouble("TitanHomeY")-anchor.y)<.01&&Math.abs(upgraded.getDouble("TitanHomeZ")-anchor.z)<.01,"legacy absent-home save upgrades in place, not at owner position");}
                if(elapsed<45)return;
                anchor=titan.position();owner.setPos(anchor.add(100,0,0));phase=3;elapsed=0;return;
            }
            if(phase==3){
                check(titan.position().distanceTo(anchor)<12,"owner beyond old64-block threshold never snaps titan to player");
                check(titan.distanceToSqr(owner)>75*75,"far owner remains far, rather than owner-seeking following");
                if(elapsed<35)return;
                anchor=titan.position();owner.getAbilities().flying=true;owner.setPos(anchor.add(90,35,0));phase=4;elapsed=0;return;
            }
            if(phase==4){
                check(!titan.isNoGravity()&&!titan.noPhysics,"owner creative flight does not make unmounted titan fly or phase");
                check(titan.getY()<164&&titan.position().subtract(anchor).horizontalDistance()<12,"flying owner cannot drag titan into sky");
                if(elapsed<35)return;
                owner.getAbilities().flying=false;owner.setPos(titan.position().add(0,0,3));
                titan.setNoAi(true);titan.setNoGravity(true);titan.noPhysics=true;titan.setDeltaMovement(Vec3.ZERO);
                anchor=titan.position();titan.setYRot(-117);phase=5;elapsed=0;return;
            }
            if(phase==5){
                check(titan.position().distanceToSqr(anchor)<1E-8&&Math.abs(net.minecraft.util.Mth.wrapDegrees(titan.getYRot()+117))<.001,"NoAI director position and orientation remain authoritative");
                if(elapsed<12)return;
                titan.setNoAi(false);titan.setNoGravity(false);titan.noPhysics=false;setupMount(server);phase=6;elapsed=0;return;
            }
            if(phase==6){
                check(rider.getVehicle()==mount,"actual XiaoXiao passenger stays on mount");
                if(elapsed<50)return;
                mount.teleportTo(-24,160,-14);mount.setDeltaMovement(Vec3.ZERO);mount.setOnGround(true);owner.setPos(-24,160,10);
                mountStart=owner.position().subtract(mount.position()).horizontalDistance();phase=7;elapsed=0;return;
            }
            if(phase==7){
                check(rider.getVehicle()==mount,"mount passenger persists through chase");
                if(elapsed<40)return;
                double remaining=owner.position().subtract(mount.position()).horizontalDistance();
                check(mountStart-remaining>=16&&remaining<=8,"small mount still closes16 of24 blocks in40 ticks; remaining="+remaining);
                check(mount.retireIntoDoll(rider),"mount retires safely into its rider");rider.discard();mount=null;rider=null;
                check(!titan.requestRecall(other),"foreign player cannot recall titan");
                owner.setPos(titan.position().add(90,0,0));fill(owner);owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TwoICombatDollMod.AXOLOTL_FLUTE.get()));
                owner.getMainHandItem().use(owner.level(),owner,InteractionHand.MAIN_HAND);check(titan.recalling(),"actual flute recalls from90 blocks without bringing titan close first");phase=8;elapsed=0;return;
            }
            if(phase==8){
                if(elapsed<55)return;
                check(!titan.isRemoved()&&titan.recalling()&&count(owner)==0,"full inventory keeps live waiting titan after recall animation");
                owner.getInventory().setItem(10,ItemStack.EMPTY);phase=9;elapsed=0;return;
            }
            if(phase==9){
                if(elapsed<20)return;
                check(titan.isRemoved()&&count(owner)==1,"one freed slot receives exactly one carrier");
                ItemStack returned=find(owner);check(identity.equals(WeaponData.identity(returned))&&returned.getHoverName().getString().equals("Freedom carrier"),"recall keeps original identity and custom component");
                check(CangshengAxolotlEntity.deploy(owner,stale.copy(),new Vec3(1,160,1),null)==null,"pre-deploy stale copy remains invalid");
                owner.getMainHandItem().use(owner.level(),owner,InteractionHand.MAIN_HAND);check(count(owner)==1,"second flute use never duplicates returned carrier");
                beginRemoteRecall(server);phase=10;elapsed=0;return;
            }
            if(phase==10){
                if(elapsed<15)return;
                check(staleActor.isRemoved(),"naturally ticked stale unloaded actor retires after authoritative distant recall");
                var ledger=AxolotlLeaseData.get(server);check(ledger.pending(owner.getUUID())==1&&count(owner)==0,"remote full bag retains sole pending identity");
                owner.getInventory().setItem(11,ItemStack.EMPTY);owner.getMainHandItem().use(owner.level(),owner,InteractionHand.MAIN_HAND);
                check(count(owner)==1&&remoteIdentity.equals(WeaponData.identity(find(owner)))&&ledger.pending(owner.getUUID())==0,"flute claims distant pending identity exactly once after space opens");
                owner.getMainHandItem().use(owner.level(),owner,InteractionHand.MAIN_HAND);check(count(owner)==1&&CangshengAxolotlEntity.deploy(owner,remoteStale.copy(),owner.position().add(0,0,12),null)==null,"repeated remote flute and original carrier cannot duplicate");
                beginCrossDimensionRecall(server);phase=11;elapsed=0;return;
            }
            if(phase==11){
                if(elapsed<15)return;
                completeCrossDimensionRecall(server);writeWitness(server);Files.write(Path.of("titan-freedom-trace.csv"),trace,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                LogUtils.getLogger().info("TITAN FREEDOM SERVER CHECKS PASSED: natural near/far/flying owner observation; no64-block snap; NoAI director; unchanged24-block small-mount chase; actual90-block flute, foreign rejection, full-bag safety, original identity/components and once-only return; actual256-block unloaded flute recall invalidates stale actor and preserves pending; second-JVM witness saved PID={}",ProcessHandle.current().pid());finish(server);return;
            }
        }catch(Throwable problem){LogUtils.getLogger().error("TITAN FREEDOM SERVER CHECKS FAILED phase="+phase+" elapsed="+elapsed,problem);finish(server);}
    }

    @SuppressWarnings("unchecked") private static void players(MinecraftServer server,UUID ownerId)throws Exception{
        var field=PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);players=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());
        owner=FakePlayerFactory.get(server.overworld(),new GameProfile(ownerId,"TitanObserver"));owner.setGameMode(GameType.CREATIVE);owner.getInventory().clearContent();players.put(owner.getUUID(),owner);
        other=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"TitanOther"));other.setGameMode(GameType.CREATIVE);players.put(other.getUUID(),other);
    }
    private static void loadPatch(ServerLevel level)throws Exception{
        var field=ServerLevel.class.getDeclaredField("entityManager");field.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(x,z),net.minecraft.world.level.entity.Visibility.TICKING);}
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
    }
    private static void setup(MinecraftServer server)throws Exception{
        for(String absent:List.of("useless_stretcher","useless_mod","doll_chronicle","curios"))check(!net.neoforged.fml.ModList.get().isLoaded(absent),"minimal excludes "+absent);
        check(!Files.exists(server.getWorldPath(LevelResource.ROOT).resolve(WITNESS)),"first process uses fresh world");players(server,UUID.randomUUID());var level=server.overworld();loadPatch(level);ChuanshuoUniquenessChecks.run(owner);
        for(int x=-36;x<=36;x++)for(int z=-36;z<=36;z++)level.setBlock(new BlockPos(x,159,z),Blocks.QUARTZ_BLOCK.defaultBlockState(),2);
        owner.setPos(0,160,10);other.setPos(4,160,10);ItemStack source=new ItemStack(TwoICombatDollMod.AXOLOTL.get());source.set(DataComponents.CUSTOM_NAME,Component.literal("Freedom carrier"));identity=WeaponData.ensure(source);stale=source.copy();owner.setItemInHand(InteractionHand.MAIN_HAND,source);
        check(source.use(level,owner,InteractionHand.MAIN_HAND).getResult().consumesAction()&&source.isEmpty(),"actual rightclick transfers unique titan carrier");
        titan=level.getEntitiesOfClass(CangshengAxolotlEntity.class,owner.getBoundingBox().inflate(40)).stream().filter(p->owner.getUUID().equals(p.ownerId())).findFirst().orElseThrow();
        check(!titan.mountMode()&&titan.getBbWidth()>12,"player deploy is actual huge titan");previous=titan.position();trace.add("phase,tick,x,y,z,owner_distance,vx,vy,vz");
    }
    private static void setupMount(MinecraftServer server){
        owner.setPos(-24,160,10);rider=com.customdoll.xiaoxiao.init.ModEntities.TWO_I_DOLL.get().create(server.overworld());check(rider!=null,"bundled XiaoXiao exists");
        rider.initialize(owner.getUUID(),new ItemStack(com.customdoll.xiaoxiao.init.ModItems.TWO_I_DOLL.get()));rider.setPos(-24,160,-4);check(server.overworld().addFreshEntity(rider),"real rider added");
        check(CombatActors.state(rider).equip(rider,new ItemStack(TwoICombatDollMod.AXOLOTL.get())),"real rider equips pet");check(rider.getVehicle() instanceof CangshengAxolotlEntity,"real mount created");mount=(CangshengAxolotlEntity)rider.getVehicle();check(mount.mountMode(),"small mode only when rider present");
    }
    private static void writeWitness(MinecraftServer server)throws Exception{
        owner.getInventory().clearContent();owner.setPos(300,160,18);
        var waiting=CangshengAxolotlEntity.deploy(owner,new ItemStack(TwoICombatDollMod.AXOLOTL.get()),new Vec3(20,160,18),null);check(waiting!=null,"disk pending witness deployed");UUID pendingId=waiting.leaseIdentity();waiting.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        fill(owner);owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TwoICombatDollMod.AXOLOTL_FLUTE.get()));owner.getMainHandItem().use(owner.level(),owner,InteractionHand.MAIN_HAND);
        check(AxolotlLeaseData.get(server).pending(owner.getUUID())==1,"actual distant flute leaves one safe full-bag pending for disk witness");
        owner.getInventory().clearContent();var source=new ItemStack(TwoICombatDollMod.AXOLOTL.get());source.set(DataComponents.CUSTOM_NAME,Component.literal("Saved freedom titan"));
        var active=CangshengAxolotlEntity.deploy(owner,source,new Vec3(18,160,18),null);check(active!=null,"disk witness real active titan deployed after remote recall");
        var saved=new CompoundTag();active.addAdditionalSaveData(saved);saved.putLong("PhaseStart",server.overworld().getGameTime()-80);active.readAdditionalSaveData(saved);
        witness=new CompoundTag();witness.putLong("pid",ProcessHandle.current().pid());witness.putUUID("owner",owner.getUUID());witness.putUUID("entity",active.getUUID());witness.putUUID("weapon",active.leaseIdentity());witness.putUUID("pending",pendingId);witness.put("stale",source.save(server.registryAccess()));witness.putDouble("x",active.getX());witness.putDouble("y",active.getY());witness.putDouble("z",active.getZ());
        var home=active.saveWithoutId(new CompoundTag());for(String key:List.of("TitanHomeX","TitanHomeY","TitanHomeZ"))witness.putDouble(key,home.getDouble(key));
        server.saveEverything(true,true,true);NbtIo.writeCompressed(witness,server.getWorldPath(LevelResource.ROOT).resolve(WITNESS));
    }
    private static void beginRemoteRecall(MinecraftServer server)throws Exception{
        owner.getInventory().clearContent();var source=new ItemStack(TwoICombatDollMod.AXOLOTL.get());source.set(DataComponents.CUSTOM_NAME,Component.literal("Distant carrier"));var point=owner.position().add(256,0,0);
        var level=server.overworld();level.getChunkAt(BlockPos.containing(point));level.setChunkForced(BlockPos.containing(point).getX()>>4,BlockPos.containing(point).getZ()>>4,true);
        var field=ServerLevel.class.getDeclaredField("entityManager");field.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(BlockPos.containing(point)),net.minecraft.world.level.entity.Visibility.TICKING);
        var remote=CangshengAxolotlEntity.deploy(owner,source,point,null);check(remote!=null,"real distant actor deployed");remoteIdentity=remote.leaseIdentity();remoteStale=source.copy();var saved=remote.saveWithoutId(new CompoundTag());UUID remoteId=remote.getUUID();remote.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        check(level.getEntity(remoteId)==null&&AxolotlLeaseData.get(server).valid(source,owner.getUUID(),remoteId),"actual unloaded actor is absent but lease remains active before flute");
        fill(owner);owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TwoICombatDollMod.AXOLOTL_FLUTE.get()));owner.getMainHandItem().use(level,owner,InteractionHand.MAIN_HAND);
        check(AxolotlLeaseData.get(server).pending(owner.getUUID())==1&&!AxolotlLeaseData.get(server).valid(source,owner.getUUID(),remoteId),"actual distant flute advances generation before any full-bag delivery");
        staleActor=TwoICombatDollMod.AXOLOTL_ENTITY.get().create(level);check(staleActor!=null,"stale load factory");staleActor.load(saved);check(level.addFreshEntity(staleActor),"real old actor returns from unloaded NBT");
    }
    private static void beginCrossDimensionRecall(MinecraftServer server)throws Exception{
        owner.getInventory().clearContent();var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);check(nether!=null,"real nether dimension exists");nether.getChunk(0,0);nether.setChunkForced(0,0,true);
        var field=ServerLevel.class.getDeclaredField("entityManager");field.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(nether);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(0,0),net.minecraft.world.level.entity.Visibility.TICKING);
        var proxy=new net.neoforged.neoforge.common.util.FakePlayer(nether,new GameProfile(owner.getUUID(),"TitanNether"));proxy.setGameMode(GameType.CREATIVE);proxy.setPos(0,160,0);
        crossSource=new ItemStack(TwoICombatDollMod.AXOLOTL.get());crossActor=CangshengAxolotlEntity.deploy(proxy,crossSource,new Vec3(0,160,0),null);check(crossActor!=null,"actual cross-dimension titan deployed");
        LogUtils.getLogger().info("TITAN CROSS-DIMENSION FIXTURE DEPLOYED: actor={} lookup_same={} removed={} tickCount={} valid={} pending={} count={}",crossActor.getUUID(),nether.getEntity(crossActor.getUUID())==crossActor,crossActor.isRemoved(),crossActor.tickCount,AxolotlLeaseData.get(server).valid(crossSource,owner.getUUID(),crossActor.getUUID()),AxolotlLeaseData.get(server).pending(owner.getUUID()),count(owner));
    }
    private static void completeCrossDimensionRecall(MinecraftServer server){
        var actor=crossActor;var source=crossSource;var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);var ledger=AxolotlLeaseData.get(server);UUID id=actor.leaseIdentity();
        LogUtils.getLogger().info("TITAN CROSS-DIMENSION BEFORE FLUTE: actor={} lookup_same={} removed={} tickCount={} valid={} pending={} count={}",actor.getUUID(),nether.getEntity(actor.getUUID())==actor,actor.isRemoved(),actor.tickCount,ledger.valid(source,owner.getUUID(),actor.getUUID()),ledger.pending(owner.getUUID()),count(owner));
        check(nether.getEntity(actor.getUUID())==actor&&actor.tickCount>0&&!actor.isRemoved(),"cross-dimension fixture is genuinely lookup-visible and naturally ticked before loaded-actor assertion");
        check(ledger.valid(source,owner.getUUID(),actor.getUUID())&&ledger.pending(owner.getUUID())==0,"cross-dimension fixture begins with one current active lease and no leftover pending");
        other.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TwoICombatDollMod.AXOLOTL_FLUTE.get()));other.getMainHandItem().use(other.level(),other,InteractionHand.MAIN_HAND);
        check(!actor.isRemoved()&&ledger.valid(source,owner.getUUID(),actor.getUUID()),"foreign flute cannot retire cross-dimension owner lease");
        fill(owner);owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TwoICombatDollMod.AXOLOTL_FLUTE.get()));owner.getMainHandItem().use(owner.level(),owner,InteractionHand.MAIN_HAND);
        LogUtils.getLogger().info("TITAN CROSS-DIMENSION AFTER OWNER FLUTE: actor={} lookup_same={} removed={} tickCount={} valid={} pending={} count={} free_slot={}",actor.getUUID(),nether.getEntity(actor.getUUID())==actor,actor.isRemoved(),actor.tickCount,ledger.valid(source,owner.getUUID(),actor.getUUID()),ledger.pending(owner.getUUID()),count(owner),owner.getInventory().getFreeSlot());
        check(actor.isRemoved()&&ledger.pending(owner.getUUID())==1&&count(owner)==0,"owner actual flute retires loaded other-dimension actor into full-bag pending");
        owner.getInventory().setItem(12,ItemStack.EMPTY);owner.getMainHandItem().use(owner.level(),owner,InteractionHand.MAIN_HAND);check(count(owner)==1&&id.equals(WeaponData.identity(find(owner)))&&!ledger.claimPending(owner),"cross-dimension carrier delivered once with exact identity");
        LogUtils.getLogger().info("TITAN CROSS-DIMENSION FLUTE CHECKS PASSED: real Nether actor, foreign owner refusal, authoritative loaded actor retirement, full-bag pending and one exact carrier");
    }
    private static void restart(MinecraftServer server)throws Exception{
        if(phase==0){witness=NbtIo.readCompressed(server.getWorldPath(LevelResource.ROOT).resolve(WITNESS),NbtAccounter.unlimitedHeap());check(witness.getLong("pid")!=ProcessHandle.current().pid(),"genuine distinct JVM process");players(server,witness.getUUID("owner"));loadPatch(server.overworld());anchor=new Vec3(witness.getDouble("x"),witness.getDouble("y"),witness.getDouble("z"));owner.setPos(anchor.add(100,0,0));phase=1;elapsed=0;return;}
        elapsed++;
        if(titan==null)titan=(CangshengAxolotlEntity)server.overworld().getEntity(witness.getUUID("entity"));
        if(elapsed<25)return;
        check(titan!=null&&titan.isAlive()&&titan.getUUID().equals(witness.getUUID("entity")),"same original entity loaded from real saved world");
        check(titan.position().distanceTo(anchor)<12&&titan.distanceToSqr(owner)>75*75,"reloaded titan stays local instead of snapping to distant owner");
        var home=titan.saveWithoutId(new CompoundTag());for(String key:List.of("TitanHomeX","TitanHomeY","TitanHomeZ"))check(Math.abs(home.getDouble(key)-witness.getDouble(key))<1E-8,"exact saved home persists across real restart "+key);
        var ledger=AxolotlLeaseData.get(server);check(ledger.valid(titan.sourceWeapon(),owner.getUUID(),titan.getUUID())&&titan.leaseIdentity().equals(witness.getUUID("weapon")),"saved owner/identity/active lease retained");
        var stale=ItemStack.parseOptional(server.registryAccess(),witness.getCompound("stale"));check(CangshengAxolotlEntity.deploy(owner,stale,new Vec3(0,160,0),null)==null,"saved stale active copy rejected after actual restart");
        check(ledger.pending(owner.getUUID())==1,"restart keeps exactly one pending carrier");fill(owner);check(!ledger.claimPending(owner),"restart full bag does not consume pending");owner.getInventory().setItem(10,ItemStack.EMPTY);check(ledger.claimPending(owner)&&!ledger.claimPending(owner)&&count(owner)==1,"restart pending claimed exactly once");check(WeaponData.identity(find(owner)).equals(witness.getUUID("pending")),"restart pending exact identity retained");
        check(!server.tickRateManager().isFrozen()&&!Chronostasis.active(server),"no global time/render lifecycle left active by movement checks");
        LogUtils.getLogger().info("TITAN FREEDOM REAL RESTART CHECKS PASSED: original UUID, owner, identity and local residence; distant owner never snaps; saved stale copy rejected; full-bag pending exactly once; PID {} -> {}",witness.getLong("pid"),ProcessHandle.current().pid());finish(server);
    }
    private static void record(){if(titan==null||titan.isRemoved())return;var p=titan.position();var v=titan.getDeltaMovement();check(p.distanceTo(previous)<5,"no instantaneous owner snap between actual ticks");previous=p;trace.add(String.format(Locale.ROOT,"%d,%d,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f",phase,elapsed,p.x,p.y,p.z,p.distanceTo(owner.position()),v.x,v.y,v.z));}
    private static void fill(ServerPlayer p){for(int i=0;i<p.getInventory().items.size();i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));}
    private static int count(ServerPlayer p){return p.getInventory().items.stream().filter(s->s.is(TwoICombatDollMod.AXOLOTL.get())).mapToInt(ItemStack::getCount).sum();}
    private static ItemStack find(ServerPlayer p){return p.getInventory().items.stream().filter(s->s.is(TwoICombatDollMod.AXOLOTL.get())).findFirst().orElseThrow();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError("Titan freedom: "+message);}
    private static void finish(MinecraftServer server){done=true;if(players!=null){if(owner!=null)players.remove(owner.getUUID());if(other!=null)players.remove(other.getUUID());}server.saveEverything(true,true,true);server.halt(false);}
}
