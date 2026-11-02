package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.*;
import com.twoi.combatdoll.entity.CombatDollEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import java.util.*;

/** Runs against natural server ticks, not a hand-advanced world clock. */
@net.neoforged.fml.common.EventBusSubscriber(modid=TwoICombatDollMod.MODID)
final class WeaponServerChecks {
    private static int ticks,phase,elapsed;
    private static boolean done;
    private static ServerPlayer owner,other;
    private static Map<UUID,ServerPlayer> players;
    private static CombatDollEntity doll;
    private static List<Mob> monsters;
    private static List<Mob> staffLateTargets=List.of();
    private static Mob cow,pet,remote;
    private static long game,day,netherGame,clock;
    private static int remoteTicks,localTicks,cook;
    private static AbstractFurnaceBlockEntity furnace;
    private static UUID weaponId;
    private static ItemStack book;
    private static ItemStack restartCarrier;
    private static ItemStack chargedStaff;
    private static net.minecraft.world.phys.Vec3 firstChargeEye,lockedEye;
    private static final Map<UUID,Integer> deaths=new HashMap<>(),drops=new HashMap<>();
    @net.neoforged.bus.api.SubscribeEvent public static void died(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e){if(Boolean.getBoolean("two_i_combat_doll.weapons_test"))deaths.merge(e.getEntity().getUUID(),1,Integer::sum);}
    @net.neoforged.bus.api.SubscribeEvent public static void drops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent e){if(Boolean.getBoolean("two_i_combat_doll.weapons_test"))drops.merge(e.getEntity().getUUID(),1,Integer::sum);}
    static void tick(MinecraftServer server){
        if(done)return;
        try{
            check(++ticks<2400,"server timeout phase="+phase);
            if(ticks<100)return;
            if(Boolean.getBoolean("two_i_combat_doll.weapon_restart_test")){restart(server);return;}
            ServerLevel level=server.overworld();elapsed++;
            if(phase==0){setup(server);phase=1;elapsed=0;return;}
            if(phase==1&&elapsed>=35){
                check(doll.combatState().age(doll,0)<0&&!doll.combatState().armed(),"unarmed 2i never starts combat");
                equipment(level);cooldownSerialization(level);
                doll.keepInteraction(owner,true);
                book=new ItemStack(TwoICombatDollMod.BOOK.get());owner.setItemInHand(InteractionHand.MAIN_HAND,book);
                check(book.use(level,owner,InteractionHand.MAIN_HAND).getResult().consumesAction(),"player can cast the book");
                BookSlamServerChecks.begin(level,owner,other,players,monsters,book,cow,pet,deaths,drops);
                phase=2;elapsed=0;return;
            }
            if(phase==2){
                if(BookSlamServerChecks.tick()){
                    check(CombatActors.state(owner).age(owner,0)<0,"player spell ends");
                    var clone=book.copy();CombatActors.state(owner).hold(clone);
                    check(!CombatActors.state(owner).begin(owner,level,true),"copied identity cannot bypass cooldown");
                    monsters.forEach(Entity::discard);monsters=monsters(level,3);doll.keepInteraction(owner,false);phase=3;elapsed=0;
                }return;
            }
            if(phase==3){
                float age=doll.combatState().age(doll,0);
                if(age>=48&&clock==0){
                    check(Chronostasis.active(server)&&server.tickRateManager().isFrozen(),"staff enables native server-wide freeze");
                    check(!server.tickRateManager().isEntityFrozen(doll)&&!server.tickRateManager().isEntityFrozen(owner),"caster and owner continue");
                    check(server.tickRateManager().isEntityFrozen(other)&&server.tickRateManager().isEntityFrozen(cow),"other players and mobs freeze");
                    game=level.getGameTime();day=level.getDayTime();netherGame=remote.level().getGameTime();
                    remoteTicks=remote.tickCount;localTicks=cow.tickCount;clock=CombatWorldData.get(server).clock();cook=cookTime();
                }
                if(age>=90&&age<124){
                    check(clock>0&&CombatWorldData.get(server).clock()>clock+40,"spell clock advances while world stops");
                    check(level.getGameTime()==game&&level.getDayTime()==day&&remote.level().getGameTime()==netherGame,"all dimensions and daylight stop");
                    check(remote.tickCount==remoteTicks&&cow.tickCount==localTicks,"local and cross-dimension entity ticks stop");
                    check(cookTime()==cook,"real furnace stops");
                }
                if(age>=140){
                    check(monsters.stream().noneMatch(Entity::isAlive),"staff executes during frozen world");check(cow.isAlive()&&pet.isAlive(),"staff preserves protected actors");
                    check(monsters.stream().allMatch(m->deaths.getOrDefault(m.getUUID(),0)<=1&&drops.getOrDefault(m.getUUID(),0)<=1),"staff single impact never duplicates death/drop events");
                    if(staffLateTargets.isEmpty())staffLateTargets=monsters(level,3);
                    check(staffLateTargets.stream().allMatch(Entity::isAlive),"staff stays single-impact: late arrivals survive BOOK slam milestones");
                }
                if(age>=178){check(!Chronostasis.active(server)&&!server.tickRateManager().isFrozen(),"global time automatically restored");check(level.getGameTime()>game,"world time resumes");}
                if(elapsed>250&&age<0){
                    check(clock>0,"natural staff attack actually ran");
                    var item=doll.toDollItem();restartCarrier=item.copy();check(WeaponEquipment.hasStoredWeapon(item),"reclaimed 2i carries staff");
                    var restored=TwoICombatDollMod.DOLL_ENTITY.get().create(level);restored.restoreFromItem(item);
                    check(restored.combatState().armed()&&WeaponData.identity(restored.combatState().weapon()).equals(weaponId),"staff identity survives doll item roundtrip");
                    var saved=new CompoundTag();doll.addAdditionalSaveData(saved);restored.readAdditionalSaveData(saved);
                    check(restored.combatState().age(restored,0)<0&&!restored.combatState().begin(restored,level,true),"entity reload cancels pending cast and retains cooldown");
                    retrieve(level,item);doll.discard();staffLateTargets.forEach(Entity::discard);
                    owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TwoICombatDollMod.STAFF.get()));
                    chargedStaff=owner.getMainHandItem();
                    check(chargedStaff.use(level,owner,InteractionHand.MAIN_HAND).getResult().consumesAction(),"player staff starts real item use");
                    check(CombatActors.state(owner).charging()&&owner.isUsingItem(),"player is actually holding charge");
                    phase=4;elapsed=0;
                }return;
            }
            if(phase==4){
                var state=CombatActors.state(owner);
                if(elapsed==8){
                    check(state.charging()&&!Chronostasis.active(server),"prediction alone does not freeze server");
                    firstChargeEye=state.blackHole(owner);
                    owner.setYRot(65);owner.setXRot(-38);state.updateChargeAim(owner);
                    var moved=state.blackHole(owner);
                    check(moved.distanceToSqr(firstChargeEye)>.01,"held charge follows authoritative player rotation");
                    check(Double.isFinite(moved.x)&&Double.isFinite(moved.y)&&Double.isFinite(moved.z)&&moved.distanceToSqr(state.stageAnchor(owner))<=80*80+.01,"aim remains inside bounded stage");
                }
                if(elapsed==20){
                    owner.releaseUsingItem();
                    check(!state.charging()&&state.age(owner,0)<0,"short hold cancels without casting");
                    check(CombatWorldData.get(server).remaining(WeaponData.identity(chargedStaff))==0,"short hold reserves no cooldown");
                    check(!Chronostasis.active(server),"cancelled charge never freezes the server");
                    var cancelled=state.blackHole(owner);owner.setYRot(-120);state.updateChargeAim(owner);
                    check(state.blackHole(owner).distanceToSqr(cancelled)<.000001,"idle look updates are ignored");
                }
                if(elapsed==24){
                    check(chargedStaff.use(level,owner,InteractionHand.MAIN_HAND).getResult().consumesAction(),"same staff can charge immediately after cancellation");
                }
                if(elapsed==34){owner.setYRot(160);owner.setXRot(-52);state.updateChargeAim(owner);}
                if(elapsed==78){
                    check(state.charging()&&owner.isUsingItem()&&state.chargeTicks(owner,0)>=44,"full charge can keep holding");
                    check(state.age(owner,0)>=42&&state.age(owner,0)<=43,"held visual age stops before time-stop strike");
                    check(!Chronostasis.active(server)&&!server.tickRateManager().isFrozen(),"holding a full charge does not start time stop");
                    check(CombatWorldData.get(server).remaining(WeaponData.identity(chargedStaff))==0,"full hold does not reserve cooldown until release");
                    var replacement=new ItemStack(TwoICombatDollMod.STAFF.get());var replacementId=WeaponData.ensure(replacement);
                    owner.setItemInHand(InteractionHand.MAIN_HAND,replacement);owner.releaseUsingItem();
                    check(!state.charging()&&state.age(owner,0)<0&&!Chronostasis.active(server),"full hold followed by another staff identity cancels without casting");
                    check(CombatWorldData.get(server).remaining(WeaponData.identity(chargedStaff))==0&&CombatWorldData.get(server).remaining(replacementId)==0,"swapped staff neither steals a charge nor reserves either identity cooldown");
                    owner.setItemInHand(InteractionHand.MAIN_HAND,chargedStaff);
                }
                if(elapsed==82){
                    check(chargedStaff.use(level,owner,InteractionHand.MAIN_HAND).getResult().consumesAction(),"original staff restarts after swapped-hand cancellation");
                }
                if(elapsed==136){
                    check(state.charging()&&owner.isUsingItem()&&state.chargeTicks(owner,0)>=44,"original staff full charge remains held");
                    lockedEye=state.blackHole(owner);owner.releaseUsingItem();
                    check(!state.charging()&&state.age(owner,0)>=44,"full release enters committed cast at freeze milestone");
                    check(CombatWorldData.get(server).remaining(WeaponData.identity(chargedStaff))>11990,"full release reserves ten-minute identity cooldown");
                    phase=5;elapsed=0;
                }return;
            }
            if(phase==5){
                if(elapsed==8){
                    var state=CombatActors.state(owner);owner.setYRot(-65);owner.setXRot(30);state.updateChargeAim(owner);
                    check(state.blackHole(owner).distanceToSqr(lockedEye)<.000001,"released hole remains locked despite later rotation");
                }
                if(elapsed<30)return;
                check(Chronostasis.active(server),"second independent staff starts freeze");
                players.remove(owner.getUUID());Chronostasis.tick(server);
                check(!Chronostasis.active(server)&&!server.tickRateManager().isFrozen(),"owner disconnect releases freeze");players.put(owner.getUUID(),owner);
                CombatActors.state(owner).reset();
                server.tickRateManager().setFrozen(true);check(Chronostasis.start(owner,3),"lease over administrative freeze");
                Chronostasis.stop(server);check(server.tickRateManager().isFrozen(),"administrative frozen state is preserved");server.tickRateManager().setFrozen(false);
                check(Chronostasis.start(owner,40),"recovery lease starts");Chronostasis.actorLeft(owner);
                check(!Chronostasis.active(server)&&!server.tickRateManager().isFrozen(),"caster removal releases freeze");
                check(level.getBlockState(new BlockPos(1,159,1)).is(Blocks.QUARTZ_BLOCK),"spell leaves terrain intact");
                var marker=new CompoundTag();marker.putUUID("owner",owner.getUUID());marker.putUUID("weapon",weaponId);marker.putLong("clock",CombatWorldData.get(server).clock());marker.putLong("remaining",CombatWorldData.get(server).remaining(weaponId));marker.put("carrier",restartCarrier.save(level.registryAccess()));
                marker.putUUID("book_weapon",BookSlamServerChecks.originalBookIdentity());marker.putLong("book_remaining",CombatWorldData.get(server).remaining(BookSlamServerChecks.originalBookIdentity()));marker.put("interrupted_book",BookSlamServerChecks.interruptedBookSave());
                net.minecraft.nbt.NbtIo.writeCompressed(marker,java.nio.file.Path.of("weapons-restart.dat"));
                LogUtils.getLogger().info("2I WEAPON SERVER CHECKS PASSED: eight real BOOK slams with per-wave64 caps/new arrivals/protection/one cooldown/cancel-load-disconnect-reclaim recovery, STAFF remains one124 impact, natural ticks, real staff use/hold/early cancellation/full release, swapped-hand identity rejection, authoritative bounded aim, position lock, no pre-release cooldown/freeze, unarmed protection, thrower/kind ownership, native cross-dimension freeze, furnace/time/mobs/players, recovery and identity serialization; longhuan={} useless_mod={}",Boolean.getBoolean("two_i_combat_doll.with_longhuan"),net.neoforged.fml.ModList.get().isLoaded("useless_mod"));
                done=true;players.remove(owner.getUUID());players.remove(other.getUUID());server.halt(false);
            }
        }catch(Throwable failure){LogUtils.getLogger().error("2I WEAPON SERVER CHECKS FAILED phase="+phase+" elapsed="+elapsed,failure);done=true;Chronostasis.stop(server);server.tickRateManager().setFrozen(false);server.halt(false);}
    }
    @SuppressWarnings("unchecked") private static void setup(MinecraftServer server)throws Exception{
        var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);players=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());
        var level=server.overworld();owner=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"WeaponOwner"));other=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"WeaponOther"));
        players.put(owner.getUUID(),owner);players.put(other.getUUID(),other);owner.setPos(1,160,5);other.setPos(3,160,5);
        force(level);for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)level.setBlock(new BlockPos(x,159,z),Blocks.QUARTZ_BLOCK.defaultBlockState(),2);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(18000);
        doll=TwoICombatDollMod.DOLL_ENTITY.get().create(level);doll.setOwnerId(owner.getUUID());doll.setNoAi(true);doll.setPos(1,160,1);level.addFreshEntity(doll);
        cow=EntityType.COW.create(level);cow.setNoAi(true);cow.setPos(3,160,1);level.addFreshEntity(cow);
        var wolf=EntityType.WOLF.create(level);wolf.setTame(true,false);wolf.setOwnerUUID(owner.getUUID());wolf.setNoAi(true);wolf.setPos(4,160,1);level.addFreshEntity(wolf);pet=wolf;
        var nether=server.getLevel(Level.NETHER);force(nether);remote=EntityType.COW.create(nether);remote.setNoAi(true);remote.setNoGravity(true);remote.setPos(1,180,1);nether.addFreshEntity(remote);
        var pos=new BlockPos(6,160,1);level.setBlock(pos,Blocks.FURNACE.defaultBlockState(),3);furnace=(AbstractFurnaceBlockEntity)level.getBlockEntity(pos);furnace.setItem(0,new ItemStack(Items.RAW_IRON,64));furnace.setItem(1,new ItemStack(Items.COAL,64));
        monsters=monsters(level,70);
    }
    private static void force(ServerLevel level)throws Exception{
        var f=ServerLevel.class.getDeclaredField("entityManager");f.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)f.get(level);
        for(int x=-1;x<=0;x++)for(int z=-1;z<=0;z++){level.setChunkForced(x,z,true);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(x,z),net.minecraft.world.level.entity.Visibility.TICKING);}
    }
    private static List<Mob> monsters(ServerLevel level,int n){
        var list=new ArrayList<Mob>();for(int i=0;i<n;i++){var m=EntityType.ZOMBIE.create(level);m.setNoAi(true);m.setPos(-9+(i%10)*2,160,-9+(i/10)*2);m.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));level.addFreshEntity(m);list.add(m);}
        check(list.stream().allMatch(m->m.distanceToSqr(owner)<24*24),"all bounded-impact targets really lie inside search/impact radius");
        return list;
    }
    private static void equipment(ServerLevel level)throws Exception{
        check(!doll.combatState().equip(doll,new ItemStack(TwoICombatDollMod.BOOK.get())),"2i rejects book");
        var wrong=new ItemEntity(level,1,160,1,new ItemStack(TwoICombatDollMod.STAFF.get()));wrong.setThrower(other);level.addFreshEntity(wrong);WeaponEquipment.collect(doll);check(!doll.combatState().armed()&&wrong.isAlive(),"other thrower rejected");wrong.discard();
        var drop=new ItemEntity(level,1,160,1,new ItemStack(TwoICombatDollMod.STAFF.get()));drop.setThrower(owner);level.addFreshEntity(drop);WeaponEquipment.collect(doll);
        check(doll.combatState().armed()&&drop.isRemoved(),"owner drop equips exactly once");weaponId=WeaponData.identity(doll.combatState().weapon());
        if(Boolean.getBoolean("two_i_combat_doll.with_longhuan")){
            var e=(PathfinderMob)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("longhuan:longhuan_doll")).create(level);
            var item=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("longhuan:longhuan_doll")));
            e.getClass().getMethod("initialize",UUID.class,ItemStack.class).invoke(e,owner.getUUID(),item);e.moveTo(1,160,3,0,0);level.addFreshEntity(e);
            check(e instanceof DollCombatAccess,"real longhuan optional bridge");check(!CombatActors.state(e).equip(e,new ItemStack(TwoICombatDollMod.STAFF.get())),"longhuan rejects staff");
            var d=new ItemEntity(level,1,160,3,new ItemStack(TwoICombatDollMod.BOOK.get()));d.setThrower(owner);level.addFreshEntity(d);WeaponEquipment.collect(e);check(CombatActors.state(e).armed()&&d.isRemoved(),"real longhuan collects book");
            check((boolean)e.getClass().getMethod("reclaim",net.minecraft.world.entity.player.Player.class).invoke(e,owner),"real longhuan reclaim");
            check(e.isRemoved(),"reclaimed longhuan fixture cannot remain as a second caster");
            var carrier=owner.getInventory().items.stream().filter(WeaponEquipment::hasStoredWeapon).findFirst().orElseThrow();check(WeaponEquipment.hasStoredWeapon(carrier),"book is in reclaimed longhuan");retrieve(level,carrier);owner.getInventory().clearContent();
            LogUtils.getLogger().info("LONGHUAN WEAPON CHECKS PASSED: original JAR mixin, collection, wrong kind, real reclaim and book extraction");
        }
        if(Boolean.getBoolean("two_i_combat_doll.with_stretcher")){
            var original=StretcherGardenFixture.spawn(owner);original.moveTo(1,160,3,0,0);level.addFreshEntity(original);check(original instanceof DollCombatAccess,"original 2i optional bridge");
            check(CombatActors.state(original).equip(original,new ItemStack(TwoICombatDollMod.STAFF.get())),"original 2i equips staff");
            var carrier=(ItemStack)original.getClass().getMethod("toDollItem").invoke(original);check(WeaponEquipment.hasStoredWeapon(carrier),"original 2i saves staff into carrier");
            check((boolean)original.getClass().getMethod("discardForReclaim").invoke(original)&&original.isRemoved(),"original 2i fixture uses authorised reclaim cleanup and cannot remain as a second caster");
        }
    }
    private static void retrieve(ServerLevel level,ItemStack item){
        other.setShiftKeyDown(true);var foreign=item.copy();other.setItemInHand(InteractionHand.MAIN_HAND,foreign);WeaponEquipment.retrieve(new PlayerInteractEvent.RightClickItem(other,InteractionHand.MAIN_HAND));check(WeaponEquipment.hasStoredWeapon(foreign),"foreign player cannot extract bound weapon");other.setShiftKeyDown(false);
        owner.setShiftKeyDown(true);owner.setItemInHand(InteractionHand.MAIN_HAND,item);for(int i=1;i<36;i++)owner.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        WeaponEquipment.retrieve(new PlayerInteractEvent.RightClickItem(owner,InteractionHand.MAIN_HAND));check(WeaponEquipment.hasStoredWeapon(item),"full inventory preserves equipped weapon");
        owner.getInventory().setItem(4,ItemStack.EMPTY);WeaponEquipment.retrieve(new PlayerInteractEvent.RightClickItem(owner,InteractionHand.MAIN_HAND));
        check(!WeaponEquipment.hasStoredWeapon(item)&&owner.getInventory().getItem(4).getItem() instanceof com.twoi.combatdoll.item.ArcanaWeaponItem,"extract after reclaim transfers weapon once");
        WeaponEquipment.retrieve(new PlayerInteractEvent.RightClickItem(owner,InteractionHand.MAIN_HAND));check(owner.getInventory().getItem(4).getCount()==1,"repeat extraction never duplicates weapon");
        owner.setShiftKeyDown(false);owner.getInventory().clearContent();
    }
    private static void cooldownSerialization(ServerLevel level){
        var data=new CombatWorldData();var id=UUID.randomUUID();check(data.reserve(id)&&!data.reserve(id),"cooldown reserves once");
        for(int i=0;i<321;i++)data.tick();var restored=CombatWorldData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
        check(restored.clock()==321&&restored.remaining(id)==11679,"ledger NBT preserves clock and deadline");
        for(int i=0;i<11678;i++)restored.tick();check(!restored.reserve(id),"cooldown is still active one tick before ten minutes");restored.tick();check(restored.reserve(id),"exactly 12000 ticks unlocks next cast");
    }
    private static int cookTime(){return furnace.saveWithoutMetadata(furnace.getLevel().registryAccess()).getShort("CookTime");}
    @SuppressWarnings("unchecked") private static void restart(MinecraftServer server)throws Exception{
        var marker=net.minecraft.nbt.NbtIo.readCompressed(java.nio.file.Path.of("weapons-restart.dat"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var data=CombatWorldData.get(server);var id=marker.getUUID("weapon");long elapsed=data.clock()-marker.getLong("clock");
        check(elapsed>=90&&elapsed<500,"saved spell clock really loaded in a new process");
        check(data.remaining(id)==marker.getLong("remaining")-elapsed,"on-disk ledger retains exact remaining cooldown");
        var carrier=ItemStack.parseOptional(server.registryAccess(),marker.getCompound("carrier"));
        var restored=TwoICombatDollMod.DOLL_ENTITY.get().create(server.overworld());restored.restoreFromItem(carrier);
        check(restored.combatState().armed()&&id.equals(WeaponData.identity(restored.combatState().weapon())),"weapon identity survives real process restart");
        var f=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");f.setAccessible(true);var map=(Map<UUID,ServerPlayer>)f.get(server.getPlayerList());
        var p=FakePlayerFactory.get(server.overworld(),new GameProfile(marker.getUUID("owner"),"RestartOwner"));map.put(p.getUUID(),p);
        check(!restored.combatState().begin(restored,server.overworld(),true),"restart cannot bypass ten-minute cooldown");map.remove(p.getUUID());
        if(marker.hasUUID("book_weapon")){
            map.put(p.getUUID(),p);var bookId=marker.getUUID("book_weapon");
            check(data.remaining(bookId)==marker.getLong("book_remaining")-elapsed,"real restart retains the BOOK one-reservation deadline");
            var interrupted=new TwoIGrimoireCombat();interrupted.load(marker.getCompound("interrupted_book"),0);
            check(interrupted.armed()&&bookId.equals(WeaponData.identity(interrupted.weapon()))&&interrupted.age(p,0)<0,"book snapshot from after first slam never restores pending/finished waves");
            check(!interrupted.begin(p,server.overworld(),true),"interrupted book cannot replay by restarting the process");
            owner=p;owner.setPos(1,160,5);var arrivals=monsters(server.overworld(),3);for(int duplicate=0;duplicate<3;duplicate++)interrupted.tick(p,server.overworld());
            check(arrivals.stream().allMatch(Entity::isAlive),"restart/load never executes a saved book wave against new enemies");arrivals.forEach(Entity::discard);map.remove(p.getUUID());
        }
        check(!Chronostasis.active(server)&&!server.tickRateManager().isFrozen(),"no frozen lease survives process restart");
        LogUtils.getLogger().info("2I WEAPON RESTART CHECKS PASSED: real saved-data file, exact STAFF/BOOK deadlines, carried weapon identity, interrupted book snapshot cannot repeat past/pending waves, no repeated cast or persistent frozen state; remaining={}",data.remaining(id));done=true;server.halt(false);
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
