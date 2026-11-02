package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.*;
import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;

/** Real server ticks, actual item transfers, a real optional XiaoXiao passenger and persistent lease checks. */
public final class AxolotlServerChecks {
    private static int ticks,phase,elapsed;
    private static boolean done;
    private static ServerPlayer owner,other;
    private static Map<UUID,ServerPlayer> players;
    private static CangshengAxolotlEntity pet;
    private static LivingEntity doll;
    private static ItemStack source,oldCopy,returned;
    private static UUID identity;
    private static List<Mob> targets;
    private static List<Mob> lateTargets;
    private static Mob passive,tame;
    private static long start,day;
    private static Vec3 hopStart;
    private static boolean sawHop;
    private static double chaseStart,chaseEnd,settleMin=Double.POSITIVE_INFINITY,settleMax;
    private static boolean sawChaseHop;
    private static final List<String> followTrace=new ArrayList<>();
    public static void tick(MinecraftServer server){
        if(done)return;
        try{
            require(++ticks<1600,"timeout phase="+phase+" elapsed="+elapsed);if(ticks<100)return;
            if(Boolean.getBoolean("two_i_combat_doll.axolotl_restart_test")){restart(server);return;}
            if(phase==0){setup(server);phase=1;elapsed=0;return;}
            elapsed++;ServerLevel level=server.overworld();
            if(phase==1){
                sawHop|=Math.abs(pet.getY()-hopStart.y)>.02||pet.getDeltaMovement().y>.05;
                if(elapsed<60)return;
                require(sawHop,"real titan has jumping movement, not a static rendering");
                targets=monsters(level,5);start=CombatWorldData.get(server).clock();day=level.getDayTime();
                require(pet.combatState().begin(pet,level,false),"deployed living titan initiates enemy attack");start=pet.combatState().start();phase=2;elapsed=0;return;
            }
            if(phase==2){
                float age=pet.combatState().age(pet,0);
                require(!Chronostasis.active(server)&&!server.tickRateManager().isFrozen(),"axolotl never starts time stop");
                require(passive.isAlive()&&tame.isAlive()&&pet.isAlive(),"passive/tame/self protected");
                require(CombatWorldData.get(server).clock()+CombatWorldData.get(server).remaining(identity)==start+CombatWorldData.COOLDOWN,"one fixed cooldown deadline through all attack phases");
                if(age>=0&&age<160)require(targets.stream().allMatch(Entity::isAlive),"windup does no damage before160");
                if(CombatWorldData.get(server).clock()-start>=162){
                    require(targets.stream().noneMatch(Entity::isAlive),"160tick actual impact executes enemies");
                    require(level.getDayTime()>day,"daylight continues through dream scene");
                    if(lateTargets==null){lateTargets=monsters(level,3);for(int i=0;i<3;i++)pet.combatState().tick(pet,level);}
                    require(lateTargets.stream().allMatch(Entity::isAlive),"spent impact never repeats against late arrivals or duplicate callbacks");
                }
                if(elapsed<225)return;
                require(pet.combatState().age(pet,0)<0,"AX presentation ends at220");
                require(pet.isAlive()&&!pet.isRemoved(),"titan remains until owner flute recall");
                lateTargets.forEach(Entity::discard);
                require(CombatWorldData.get(server).remaining(identity)>11_700,"single ten-minute identity cooldown");
                require(!pet.requestRecall(other),"other player cannot recall");
                fill(owner);owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TwoICombatDollMod.AXOLOTL_FLUTE.get()));
                owner.getMainHandItem().use(level,owner,InteractionHand.MAIN_HAND);require(pet.recalling(),"real flute starts recall animation");phase=3;elapsed=0;return;
            }
            if(phase==3){
                if(elapsed<65)return;
                require(!pet.isRemoved()&&pet.recalling(),"full inventory retains waiting living pet after recall animation");
                require(count(owner)==0,"full inventory never drops or duplicates pet");
                owner.getInventory().setItem(10,ItemStack.EMPTY);phase=4;elapsed=0;return;
            }
            if(phase==4){
                if(elapsed<25)return;
                require(pet.isRemoved()&&count(owner)==1,"one empty slot claims exactly one pet");returned=find(owner);
                require(identity.equals(WeaponData.identity(returned)),"recall keeps original weapon identity");
                require(AxolotlLeaseData.generation(returned)>AxolotlLeaseData.generation(oldCopy),"return invalidates stale carrier generation");
                require(CangshengAxolotlEntity.deploy(owner,oldCopy,new Vec3(1,160,1),null)==null,"old pre-deploy copy cannot spawn after recall");
                var roundtrip=AxolotlLeaseData.load(AxolotlLeaseData.get(server).save(new CompoundTag(),level.registryAccess()),level.registryAccess());
                require(roundtrip.pending(owner.getUUID())==0,"lease serialization does not invent pending rewards");
                owner.getInventory().clearContent();source=returned.copy();pet=CangshengAxolotlEntity.deploy(owner,source,new Vec3(2,160,1),null);
                require(pet!=null&&!pet.combatState().begin(pet,level,true),"pet can be released during cooldown but cannot attack again");
                require(CangshengAxolotlEntity.deploy(owner,source.copy(),new Vec3(4,160,1),null)==null,"active identity cannot deploy twice");
                CompoundTag saved=new CompoundTag();pet.addAdditionalSaveData(saved);var reloaded=TwoICombatDollMod.AXOLOTL_ENTITY.get().create(level);reloaded.readAdditionalSaveData(saved);
                require(reloaded.combatState().age(reloaded,0)<0&&identity.equals(reloaded.leaseIdentity()),"entity load cancels cast and preserves source identity");
                fill(owner);pet.remove(Entity.RemovalReason.DISCARDED);
                require(AxolotlLeaseData.get(server).pending(owner.getUUID())==1,"unexpected destruction creates one save-owned pending return");
                var savedLedger=AxolotlLeaseData.get(server).save(new CompoundTag(),level.registryAccess());
                require(AxolotlLeaseData.load(savedLedger,level.registryAccess()).pending(owner.getUUID())==1,"pending return survives saved-data reload");
                require(!AxolotlLeaseData.get(server).claimPending(owner),"full inventory blocks pending claim without loss");
                owner.getInventory().setItem(11,ItemStack.EMPTY);require(AxolotlLeaseData.get(server).claimPending(owner),"pending reward can be safely claimed");
                require(!AxolotlLeaseData.get(server).claimPending(owner)&&count(owner)==1,"pending claim runs once");
                owner.getInventory().clearContent();
                if(!net.neoforged.fml.ModList.get().isLoaded("xiaoxiao")){finish(server,"standalone; xiaoxiao absent");return;}
                setupDoll(level);phase=5;elapsed=0;return;
            }
            if(phase==5){
                if(elapsed<50)return;
                require(doll.getVehicle() instanceof CangshengAxolotlEntity,"owner drop equips XiaoXiao on a real rideable axolotl");
                pet=(CangshengAxolotlEntity)doll.getVehicle();require(pet.mountMode()&&pet.getPassengers().contains(doll),"passenger relation is authoritative");
                require(doll instanceof DollCombatAccess&&CombatActors.state(doll).kind()==WeaponKind.AXOLOTL,"optional XiaoXiao bridge uses third kind");
                require(!TwoIGrimoireCombat.eligible((Mob)doll,owner),"XiaoXiao is protected from all companion executions");
                pet.teleportTo(0,160,-14);pet.setDeltaMovement(Vec3.ZERO);pet.setOnGround(true);owner.setPos(0,160,10);
                chaseStart=owner.position().subtract(pet.position()).horizontalDistance();
                require(Math.abs(chaseStart-24)<.001,"natural follow uses the fixed24-block level path");
                followTrace.add("phase,tick,clock,x,y,z,horizontal_distance,vx,vy,vz,on_ground");
                phase=6;elapsed=0;recordFollow("chase");return;
            }
            if(phase==6){
                recordFollow("chase");sawChaseHop|=pet.getY()>160.03||pet.getDeltaMovement().y>.05;
                require(doll.getVehicle()==pet,"XiaoXiao stays a real passenger while chasing");
                if(elapsed<40)return;
                chaseEnd=owner.position().subtract(pet.position()).horizontalDistance();
                require(chaseStart-chaseEnd>=16&&chaseEnd<=8,"40 natural ticks close at least16 of24 blocks; remaining="+chaseEnd);
                require(sawChaseHop,"faster follow retains physical hopping instead of sliding");
                phase=7;elapsed=0;return;
            }
            if(phase==7){
                recordFollow("settle");double distance=owner.position().subtract(pet.position()).horizontalDistance();
                if(elapsed>=20){settleMin=Math.min(settleMin,distance);settleMax=Math.max(settleMax,distance);}
                require(distance>3.7,"follow brakes before reaching or pushing through the owner");
                if(elapsed<40)return;
                require(distance<=4.7&&settleMax-settleMin<.35&&pet.getDeltaMovement().horizontalDistance()<.03,
                        "near-owner stationary follow settles without direction jitter: distance="+distance+" range="+(settleMax-settleMin));
                java.nio.file.Files.write(java.nio.file.Path.of("axolotl-mount-follow.csv"),followTrace,java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE_NEW);
                LogUtils.getLogger().info("CANGSHENG MOUNT FOLLOW CHECKS PASSED: start={} after40={} progress40={} settledDistance={} last20Spread={} physicalHop={} samples={}",
                        chaseStart,chaseEnd,chaseStart-chaseEnd,distance,settleMax-settleMin,sawChaseHop,followTrace.size()-1);
                owner.setPos(doll.getX(),doll.getY(),doll.getZ()+2);other.setPos(doll.getX()+1,doll.getY(),doll.getZ()+2);
                require(owner.distanceToSqr(doll)<36&&other.distanceToSqr(doll)<36,"reclaim cases are inside actual owner-interaction range");
                fill(owner);require(!(boolean)doll.getClass().getMethod("reclaim",net.minecraft.world.entity.player.Player.class).invoke(doll,owner),"full inventory rejects doll reclaim");
                require(!pet.isRemoved()&&doll.getVehicle()==pet,"failed full reclaim leaves mount unchanged");
                require(!(boolean)doll.getClass().getMethod("reclaim",net.minecraft.world.entity.player.Player.class).invoke(doll,other),"other player cannot reclaim doll");
                require(!pet.isRemoved()&&doll.getVehicle()==pet,"unauthorized reclaim leaves mount unchanged");
                owner.getInventory().setItem(12,ItemStack.EMPTY);
                require((boolean)doll.getClass().getMethod("reclaim",net.minecraft.world.entity.player.Player.class).invoke(doll,owner),"owner reclaims rider into original doll item");
                require(doll.isRemoved()&&pet.isRemoved(),"successful doll reclaim retires both deployed entities");
                var carrier=owner.getInventory().items.stream().filter(WeaponEquipment::hasStoredWeapon).findFirst().orElseThrow();
                require(WeaponEquipment.hasStoredWeapon(carrier),"returned doll is sole pet carrier");
                for(int i=0;i<owner.getInventory().items.size();i++)if(owner.getInventory().getItem(i)==carrier)owner.getInventory().setItem(i,ItemStack.EMPTY);
                owner.getInventory().setItem(13,ItemStack.EMPTY);owner.setShiftKeyDown(true);
                owner.setItemInHand(InteractionHand.MAIN_HAND,carrier);
                var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(owner,InteractionHand.MAIN_HAND);
                WeaponEquipment.retrieve(event);owner.setShiftKeyDown(false);
                require(count(owner)==1&&!WeaponEquipment.hasStoredWeapon(carrier),"reclaimed doll retrieves pet once, after doll reclaim");
                staleCarrierAfterUnload(level);
                finish(server,"actual XiaoXiao owner-drop pickup/passenger,24block path40tick pursuit and40tick quiet settling,full and unauthorized reclaim/successful carrier recovery,unloaded stale carrier cleanup and genuine re-pickup; same-generation ACTIVE carrier retained");
            }
        }catch(Throwable failure){done=true;LogUtils.getLogger().error("CANGSHENG AXOLOTL SERVER CHECKS FAILED phase="+phase+" elapsed="+elapsed,failure);if(players!=null){if(owner!=null)players.remove(owner.getUUID());if(other!=null)players.remove(other.getUUID());}server.halt(false);}
    }
    @SuppressWarnings("unchecked") private static void setup(MinecraftServer server)throws Exception{
        var level=server.overworld();var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);players=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());
        owner=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"AxolotlOwner"));other=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"AxolotlOther"));players.put(owner.getUUID(),owner);players.put(other.getUUID(),other);
        owner.setPos(0,160,8);other.setPos(3,160,8);owner.getInventory().clearContent();
        var managerField=ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)managerField.get(level);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){level.setChunkForced(x,z,true);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(x,z),net.minecraft.world.level.entity.Visibility.TICKING);}
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)level.setBlock(new BlockPos(x,159,z),Blocks.QUARTZ_BLOCK.defaultBlockState(),2);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(6000);
        require(!TwoICombatDollMod.AXOLOTL_ENTITY.get().canSummon(),"commands cannot summon carrierless pet");
        var fresh=TwoICombatDollMod.AXOLOTL_ENTITY.get().create(level);
        require(Math.abs(fresh.getBbWidth()-12.8F)<.001F&&Math.abs(fresh.getBbHeight()-7F)<.001F,"unsynchronized default titan has correct constructor dimensions on both sides");
        source=new ItemStack(TwoICombatDollMod.AXOLOTL.get());WeaponData.ensure(source);identity=WeaponData.identity(source);oldCopy=source.copy();owner.setItemInHand(InteractionHand.MAIN_HAND,source);
        require(source.use(level,owner,InteractionHand.MAIN_HAND).getResult().consumesAction()&&source.isEmpty(),"actual player rightclick transfers the unique item into a living titan");
        pet=level.getEntitiesOfClass(CangshengAxolotlEntity.class,owner.getBoundingBox().inflate(40)).stream().filter(p->owner.getUUID().equals(p.ownerId())).findFirst().orElseThrow();
        require(!pet.mountMode()&&pet.emerging(),"standalone player gets emerging titan, not player spell");
        require(CombatActors.state(owner).age(owner,0)<0&&!Chronostasis.active(server),"rightclick never casts or freezes as player");
        require(!pet.hurt(level.damageSources().genericKill(),Float.MAX_VALUE)&&pet.getHealth()>0,"lethal damage cannot harm pet");
        pet.setHealth(0);pet.kill();require(pet.isAlive()&&!pet.isRemoved(),"direct lethal health writes and kill cannot bypass pet protection");
        var capture=new CompoundTag();capture.putString("id","two_i_combat_doll:cangsheng_axolotl");var nested=new CompoundTag();nested.put("CapturedEntity",capture);
        require(com.twoi.combatdoll.entity.CaptureGuards.containsDoll(nested),"nested jar/amber restore cannot clone protected axolotl");
        require(CangshengAxolotlEntity.deploy(other,oldCopy.copy(),new Vec3(3,160,1),null)==null,"other player cannot reuse bound identity");
        require(CangshengAxolotlEntity.deploy(owner,oldCopy.copy(),new Vec3(3,160,1),null)==null,"stale creative copy rejected while active");
        passive=EntityType.COW.create(level);passive.setNoAi(true);passive.setPos(3,160,2);level.addFreshEntity(passive);
        var wolf=EntityType.WOLF.create(level);wolf.setTame(true,false);wolf.setOwnerUUID(owner.getUUID());wolf.setNoAi(true);wolf.setPos(4,160,2);level.addFreshEntity(wolf);tame=wolf;hopStart=pet.position();
    }
    private static void setupDoll(ServerLevel level)throws Exception{
        var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("xiaoxiao","xiaoxiao_doll"));
        if(type==null||type==EntityType.PIG){type=BuiltInRegistries.ENTITY_TYPE.entrySet().stream().filter(e->e.getKey().location().getNamespace().equals("xiaoxiao")).map(Map.Entry::getValue).findFirst().orElseThrow();}
        doll=(LivingEntity)type.create(level);require(doll instanceof DollCombatAccess,"XiaoXiao mixin applies without hard dependency");
        ItemStack dollItem=BuiltInRegistries.ITEM.entrySet().stream().filter(e->e.getKey().location().getNamespace().equals("xiaoxiao")&&e.getKey().location().getPath().contains("doll")).map(e->new ItemStack(e.getValue())).findFirst().orElseThrow();
        doll.getClass().getMethod("initialize",UUID.class,ItemStack.class).invoke(doll,owner.getUUID(),dollItem);doll.setPos(1,160,2);level.addFreshEntity(doll);
        var wrong=new ItemEntity(level,1,160,2,new ItemStack(TwoICombatDollMod.AXOLOTL.get()));wrong.setThrower(other);level.addFreshEntity(wrong);WeaponEquipment.collect(doll);
        require(!CombatActors.state(doll).armed(),"XiaoXiao rejects another player's thrown pet");wrong.discard();
        var drop=new ItemEntity(level,1,160,2,new ItemStack(TwoICombatDollMod.AXOLOTL.get()));drop.setThrower(owner);level.addFreshEntity(drop);WeaponEquipment.collect(doll);
        require(drop.isRemoved()&&CombatActors.state(doll).armed(),"only owner's thrown pet is transferred, never copied");
    }
    private static void staleCarrierAfterUnload(ServerLevel level)throws Exception{
        owner.getInventory().clearContent();owner.setPos(0,160,4);setupDoll(level);
        require(doll.getVehicle() instanceof CangshengAxolotlEntity,"stale-carrier fixture starts with the real mount");
        var mount=(CangshengAxolotlEntity)doll.getVehicle();var ledger=AxolotlLeaseData.get(level.getServer());
        var oldCarrier=CombatActors.state(doll).weapon().copy();UUID oldIdentity=WeaponData.identity(oldCarrier);
        doll.stopRiding();AxolotlEquipment.restoreMount(doll);
        require(CombatActors.state(doll).armed()&&oldIdentity.equals(WeaponData.identity(CombatActors.state(doll).weapon()))
                        &&ledger.valid(mount.sourceWeapon(),owner.getUUID(),mount.getUUID())&&!mount.isRemoved(),
                "same-generation ACTIVE carrier awaiting its real mount is never mistaken for a stale copy");
        require(doll.startRiding(mount,true),"legal active passenger relation restores to the same actual mount");
        var unloaded=new CompoundTag();doll.saveWithoutId(unloaded);var type=doll.getType();UUID unloadedId=doll.getUUID();
        doll.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        require(level.getEntity(unloadedId)==null,"fixture doll is absent from the loaded entity map before mount destruction");
        fill(owner);mount.remove(Entity.RemovalReason.DISCARDED);
        require(ledger.pending(owner.getUUID())==1&&!ledger.currentCarrier(oldCarrier,owner.getUUID()),
                "destroyed mount advances the lease generation while the absent doll retains its old carrier");
        var restored=(LivingEntity)type.create(level);restored.load(unloaded);require(level.addFreshEntity(restored),"saved unloaded doll can rejoin the actual level");
        require(CombatActors.state(restored).armed(),"saved doll really contains the stale carrier before reconciliation");
        AxolotlEquipment.restoreMount(restored);
        require(!CombatActors.state(restored).armed()&&!restored.isPassenger(),"stale loaded carrier is cleared instead of retrying it forever");
        require(!ledger.claimPending(owner),"stale carrier cleanup never consumes a full-inventory pending reward");
        owner.getInventory().setItem(10,ItemStack.EMPTY);require(ledger.claimPending(owner),"genuine pending pet remains claimable after stale-carrier cleanup");
        var genuine=find(owner);require(oldIdentity.equals(WeaponData.identity(genuine))
                &&AxolotlLeaseData.generation(genuine)>AxolotlLeaseData.generation(oldCarrier),"recovered pet has the same identity and a newer generation");
        owner.getInventory().setItem(10,ItemStack.EMPTY);
        var drop=new ItemEntity(level,restored.getX(),restored.getY(),restored.getZ(),genuine);drop.setThrower(owner);level.addFreshEntity(drop);
        WeaponEquipment.collect(restored);
        require(drop.isRemoved()&&restored.getVehicle() instanceof CangshengAxolotlEntity replacement
                        &&oldIdentity.equals(replacement.leaseIdentity())&&ledger.valid(replacement.sourceWeapon(),owner.getUUID(),replacement.getUUID()),
                "doll with cleared stale carrier can truly pick up the recovered pet and ride its new authoritative mount");
        require(CangshengAxolotlEntity.deploy(owner,oldCarrier,new Vec3(4,160,2),null)==null,"unloaded old carrier cannot deploy after genuine re-pickup");
        owner.setPos(restored.getX(),restored.getY(),restored.getZ()+2);
        require((boolean)restored.getClass().getMethod("reclaim",net.minecraft.world.entity.player.Player.class).invoke(restored,owner),"regression fixture safely reclaims its rider and replacement mount");
        require(ledger.pending(owner.getUUID())==0,"stale-carrier regression leaves no extra pending return");
        LogUtils.getLogger().info("CANGSHENG STALE CARRIER CHECKS PASSED: actual unload, retired generation, stale loaded equipment cleared, pending reward retained, genuine owner re-pickup/riding; legal ACTIVE generation preserved");
    }
    private static List<Mob> monsters(ServerLevel level,int count){var result=new ArrayList<Mob>();for(int i=0;i<count;i++){
        var m=EntityType.ZOMBIE.create(level);m.setNoAi(true);m.setPos(-2+i,160,2);m.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));level.addFreshEntity(m);result.add(m);
    }return result;}
    private static void fill(ServerPlayer player){for(int i=0;i<player.getInventory().items.size();i++)player.getInventory().setItem(i,new ItemStack(Items.STONE,64));}
    private static int count(ServerPlayer player){return player.getInventory().items.stream().filter(s->s.is(TwoICombatDollMod.AXOLOTL.get())).mapToInt(ItemStack::getCount).sum();}
    private static ItemStack find(ServerPlayer player){return player.getInventory().items.stream().filter(s->s.is(TwoICombatDollMod.AXOLOTL.get())).findFirst().orElseThrow();}
    private static void recordFollow(String phase){
        Vec3 velocity=pet.getDeltaMovement();double distance=owner.position().subtract(pet.position()).horizontalDistance();
        followTrace.add(String.format(Locale.ROOT,"%s,%d,%d,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%s",phase,elapsed,
                CombatWorldData.get(pet.getServer()).clock(),pet.getX(),pet.getY(),pet.getZ(),distance,velocity.x,velocity.y,velocity.z,pet.onGround()));
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void finish(MinecraftServer server,String details)throws Exception{
        owner.getInventory().clearContent();var activeStack=new ItemStack(TwoICombatDollMod.AXOLOTL.get());
        var active=CangshengAxolotlEntity.deploy(owner,activeStack,new Vec3(2,160,1),null);require(active!=null,"restart fixture deploys active pet");
        // End the spawn prelude for this fixture, then persist an interrupted committed cast.
        var saved=new CompoundTag();active.addAdditionalSaveData(saved);saved.putLong("PhaseStart",server.overworld().getGameTime()-80);active.readAdditionalSaveData(saved);
        require(active.combatState().begin(active,server.overworld(),true),"restart fixture reserves one ten-minute cooldown");
        var pendingStack=new ItemStack(TwoICombatDollMod.AXOLOTL.get());var pending=CangshengAxolotlEntity.deploy(owner,pendingStack,new Vec3(3,160,1),null);
        require(pending!=null,"restart fixture deploys pending pet");UUID pendingId=pending.leaseIdentity();pending.remove(Entity.RemovalReason.DISCARDED);
        var marker=new CompoundTag();marker.putUUID("owner",owner.getUUID());marker.putUUID("active",active.getUUID());marker.putUUID("weapon",active.leaseIdentity());
        marker.putUUID("pending",pendingId);marker.putLong("remaining",CombatWorldData.get(server).remaining(active.leaseIdentity()));marker.put("stale",activeStack.save(server.registryAccess()));
        net.minecraft.nbt.NbtIo.writeCompressed(marker,java.nio.file.Path.of("axolotl-restart.dat"));
        done=true;players.remove(owner.getUUID());players.remove(other.getUUID());
        LogUtils.getLogger().info("CANGSHENG AXOLOTL SERVER CHECKS PASSED: real deployment, owner identity/generation lease, no summon/damage/capture, live hop,160 single impact,220 end, no time stop, protected allies, ten-minute cooldown, flute animation/full-inventory wait, unexpected removal pending, saved-data roundtrip, claim once; {}",details);server.halt(false);}
    @SuppressWarnings("unchecked") private static void restart(MinecraftServer server)throws Exception{
        if(phase==0){
            var marker=net.minecraft.nbt.NbtIo.readCompressed(java.nio.file.Path.of("axolotl-restart.dat"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);players=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());
            owner=FakePlayerFactory.get(server.overworld(),new GameProfile(marker.getUUID("owner"),"AxolotlOwner"));players.put(owner.getUUID(),owner);owner.setPos(0,160,8);owner.getInventory().clearContent();
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){server.overworld().setChunkForced(x,z,true);server.overworld().getChunk(x,z);}
            identity=marker.getUUID("weapon");oldCopy=ItemStack.parseOptional(server.registryAccess(),marker.getCompound("stale"));source=oldCopy.copy();
            pet=(CangshengAxolotlEntity)server.overworld().getEntity(marker.getUUID("active"));phase=1;elapsed=0;return;
        }
        if(++elapsed<40)return;
        if(pet==null)pet=server.overworld().getEntitiesOfClass(CangshengAxolotlEntity.class,owner.getBoundingBox().inflate(64)).stream().filter(p->identity.equals(p.leaseIdentity())).findFirst().orElseThrow();
        require(pet.isAlive()&&pet.combatState().age(pet,0)<0,"fresh JVM restores active pet without replaying interrupted attack");
        require(CombatWorldData.get(server).remaining(identity)>11_000,"fresh JVM retains reserved cooldown");
        require(AxolotlLeaseData.get(server).valid(pet.sourceWeapon(),owner.getUUID(),pet.getUUID()),"fresh JVM retains exact active entity lease");
        require(CangshengAxolotlEntity.deploy(owner,source,new Vec3(1,160,2),null)==null,"fresh JVM rejects stale active carrier");
        require(AxolotlLeaseData.get(server).pending(owner.getUUID())==1,"fresh JVM retains exactly one pending pet");
        fill(owner);require(!AxolotlLeaseData.get(server).claimPending(owner),"restart pending full inventory retains reward");owner.getInventory().setItem(10,ItemStack.EMPTY);
        require(AxolotlLeaseData.get(server).claimPending(owner)&&!AxolotlLeaseData.get(server).claimPending(owner)&&count(owner)==1,"restart pending claim is exactly once");
        done=true;players.remove(owner.getUUID());LogUtils.getLogger().info("CANGSHENG AXOLOTL RESTART CHECKS PASSED: fresh JVM active lease, cooldown, no interrupted replay, stale copy rejection and full-inventory pending claim once");server.halt(false);
    }
}
