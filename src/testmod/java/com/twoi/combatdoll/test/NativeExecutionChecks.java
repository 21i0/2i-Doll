package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.*;
import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.lang.reflect.Method;
import java.util.*;

/** Production casters and real vanilla EnderMan; no simulated damage or manually advanced spell clock. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID)
public final class NativeExecutionChecks {
    private static ServerLevel level;
    private static ServerPlayer owner,other;
    private static Map<UUID,ServerPlayer> online;
    private static LivingEntity caster;
    private static TwoIGrimoireCombat state;
    private static WeaponKind kind;
    private static List<EnderMan> enemies=List.of();
    private static final List<Mob> protectedMobs=new ArrayList<>();
    private static final Map<UUID,Integer> deaths=new HashMap<>(),drops=new HashMap<>();
    private static final Set<UUID> tracked=new HashSet<>();
    private static PlayerTeam team;
    private static Method teleport;
    private static int caseIndex,ticks;
    private static boolean setup,cast,teleported,impactVerified,passed,done;
    private static Throwable failure;
    private static long start,deadline;
    private static float ownerHealth,otherHealth;
    private static int calmTicks,angryTicks,calmBaseline,angryBaseline,angryNaturalSamples,lastAngrySample,ownerBaseline,otherBaseline;

    /** Called once per real ServerTickEvent.Post. Returns true when all three casts have completed. */
    public static boolean tick(MinecraftServer server){
        if(done)return true;
        try{
            check(++ticks<1100,"natural tick timeout case="+caseIndex);
            if(!setup){setup(server);setup=true;return false;}
            if(caster==null){prepareCase();return false;}
            if(!cast){
                if(caster instanceof CangshengAxolotlEntity pet&&pet.emerging())return false;
                check(state.age(caster,0)>=0||state.begin(caster,level,true),"actual "+kind+" actor begins");
                start=state.start();deadline=CombatWorldData.get(server).clock()+CombatWorldData.get(server).remaining(WeaponData.identity(state.weapon()));
                cast=true;return false;
            }
            float age=state.age(caster,0);
            check(CombatWorldData.get(server).clock()+CombatWorldData.get(server).remaining(WeaponData.identity(state.weapon()))==deadline,"fixed ten-minute deadline "+kind);
            check(!owner.isCreative()&&!other.isCreative()&&!owner.getAbilities().invulnerable&&!other.getAbilities().invulnerable
                    &&!owner.isInvulnerableTo(level.damageSources().generic())&&!other.isInvulnerableTo(level.damageSources().generic()),"both real survival players remain damageable "+kind);
            check(owner.tickCount==ownerBaseline&&other.tickCount==otherBaseline,"floating player fixtures are not ticked into melee range "+kind);
            check(owner.getHealth()==ownerHealth&&other.getHealth()==otherHealth,"players protected "+kind);
            check(other.distanceToSqr(caster)<=28*28,"damageable bystander remains inside actual execution radius "+kind);
            check(protectedMobs.stream().allMatch(Entity::isAlive),"pet/passive/team/prank/doll protections "+kind);
            if(!teleported&&age>=20){
                EnderMan moving=enemies.get(2);Vec3 before=moving.position();
                check((boolean)teleport.invoke(moving,12D,160D,-5D),"vanilla EnderMan.teleport(x,y,z) succeeds "+kind);
                check(moving.position().distanceToSqr(before)>4,"vanilla teleport really moved entity "+kind);
                check(TwoIGrimoireCombat.eligible(moving,owner),"teleported EnderMan retains eligibility "+kind);
                teleported=true;
            }
            int impact=kind==WeaponKind.BOOK?TwoIArcanaTimeline.bookSlamTick(0):kind==WeaponKind.STAFF?TwoIArcanaTimeline.IMPACT:160;
            if(age>=0&&age<impact){
                check(enemies.stream().allMatch(Entity::isAlive),"no early damage before actual impact "+kind);
                EnderMan angry=enemies.get(1);
                check(angry.getRemainingPersistentAngerTime()>0&&owner.getUUID().equals(angry.getPersistentAngerTarget()),"vanilla persistent anger survives until impact "+kind);
                if(angry.tickCount>lastAngrySample&&angry.isCreepy()&&angry.getTarget()==owner){angryNaturalSamples++;lastAngrySample=angry.tickCount;}
            }
            if(age>=impact+2){
                check(teleported,"native teleport happened before impact "+kind);
                check(enemies.get(0).tickCount>calmBaseline&&enemies.get(1).tickCount>angryBaseline,"both calm and angry EnderMan actually received natural AI ticks "+kind);
                check(angryNaturalSamples>=5,"angry EnderMan kept creepy appearance and owner target on at least five distinct native ticks "+kind+" samples="+angryNaturalSamples);
                check(enemies.stream().noneMatch(Entity::isAlive),"calm/angry/native-teleported EnderMan killed by "+kind);
                for(EnderMan mob:enemies){
                    check(deaths.getOrDefault(mob.getUUID(),0)==1,"exactly one native death event "+kind+" "+mob.getUUID());
                    check(drops.getOrDefault(mob.getUUID(),0)==1,"exactly one native drop event "+kind);
                }
                if(!impactVerified){
                    for(EnderMan mob:enemies)check(!AuraExecution.execute(owner,mob,state.weapon()),"dead target rejected without repeat death/drop "+kind);
                    impactVerified=true;
                }
            }
            if(CombatWorldData.get(server).clock()-start<225)return false;
            check(age<0,"presentation completes "+kind);
            check(!Chronostasis.active(server)&&!server.tickRateManager().isFrozen(),"freeze state restored "+kind);
            check(enemies.stream().noneMatch(Entity::isAlive),"all three EnderMan remain dead "+kind);
            for(EnderMan mob:enemies)check(deaths.getOrDefault(mob.getUUID(),0)==1&&drops.getOrDefault(mob.getUUID(),0)==1,"death and drop remain exactly once through presentation end "+kind);
            calmTicks=enemies.get(0).tickCount;angryTicks=enemies.get(1).tickCount;
            LogUtils.getLogger().info("NATIVE ENDERMAN WEAPON CASE PASSED: kind={} realEntityType={} calmNaturalTicks={} angryNaturalTicks={} angryCreepyTargetNaturalSamples={} nativeTeleport={} deaths={} drops={} damageableSurvivalPlayers=true useless_mod={}",
                    kind,BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ENDERMAN),calmTicks,angryTicks,angryNaturalSamples,teleported,enemies.stream().mapToInt(m->deaths.getOrDefault(m.getUUID(),0)).sum(),enemies.stream().mapToInt(m->drops.getOrDefault(m.getUUID(),0)).sum(),net.neoforged.fml.ModList.get().isLoaded("useless_mod"));
            disposeCase();
            if(++caseIndex<3)return false;
            protectedMobs.forEach(NativeExecutionChecks::discardFixture);protectedMobs.clear();
            level.getScoreboard().removePlayerFromTeam(owner.getScoreboardName(),team);level.getScoreboard().removePlayerTeam(team);
            online.remove(owner.getUUID());online.remove(other.getUUID());passed=true;done=true;
            LogUtils.getLogger().info("NATIVE ENDERMAN EXECUTION CHECKS PASSED: actual standalone 2i STAFF, bundled Longhuan BOOK and Cangsheng AXOLOTL; three calm/angry/native-teleport EnderMan each, natural spell clock, actual native death/drop events, ten-minute identity cooldown, players/pets/allies/passives/prank/dolls protected; useless_mod={}",net.neoforged.fml.ModList.get().isLoaded("useless_mod"));
            return true;
        }catch(Throwable error){
            failure=error;done=true;Chronostasis.stop(server);server.tickRateManager().setFrozen(false);
            LogUtils.getLogger().error("NATIVE ENDERMAN EXECUTION CHECKS FAILED case="+caseIndex+" age="+(state==null?-1:state.age(caster,0)),error);
            return true;
        }
    }

    @SuppressWarnings("unchecked") private static void setup(MinecraftServer server)throws Exception{
        level=server.overworld();var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);
        online=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());
        owner=damageablePlayer(server,"NativeKillOwner");other=damageablePlayer(server,"NativeKillOther");
        online.put(owner.getUUID(),owner);online.put(other.getUUID(),other);
        // Only the online UUID lookup is populated. These are deliberately not
        // inserted in the world's ticking player list, so their elevated feet
        // stay beyond melee reach without creative mode or invulnerability.
        owner.setPos(0,164,8);other.setPos(3,166,8);ownerHealth=owner.getHealth();otherHealth=other.getHealth();
        ownerBaseline=owner.tickCount;otherBaseline=other.tickCount;
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        level.setDayTime(18000);level.setWeatherParameters(60000,0,false,false);
        for(int cx=-2;cx<=2;cx++)for(int cz=-2;cz<=2;cz++)level.setChunkForced(cx,cz,true);
        var mf=ServerLevel.class.getDeclaredField("entityManager");mf.setAccessible(true);
        var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)mf.get(level);
        for(int cx=-2;cx<=2;cx++)for(int cz=-2;cz<=2;cz++)manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(cx,cz),net.minecraft.world.level.entity.Visibility.TICKING);
        for(int x=-28;x<=28;x++)for(int z=-28;z<=28;z++)level.setBlock(new BlockPos(x,159,z),Blocks.QUARTZ_BLOCK.defaultBlockState(),2);
        teleport=EnderMan.class.getDeclaredMethod("teleport",double.class,double.class,double.class);teleport.setAccessible(true);
        check(net.minecraft.world.entity.monster.Enemy.class.isAssignableFrom(EnderMan.class),"native EnderMan already belongs to Enemy; no special target-class patch required");
        team=level.getScoreboard().addPlayerTeam("native_execution_allies");level.getScoreboard().addPlayerToTeam(owner.getScoreboardName(),team);
        Mob passive=spawn(EntityType.COW,2,2);protectedMobs.add(passive);
        var pet=EntityType.WOLF.create(level);pet.setTame(true,false);pet.setOwnerUUID(other.getUUID());pet.setNoAi(true);pet.setPos(3,160,2);check(level.addFreshEntity(pet),"pet fixture added");protectedMobs.add(pet);
        Mob ally=spawn(EntityType.ZOMBIE,4,2);level.getScoreboard().addPlayerToTeam(ally.getScoreboardName(),team);protectedMobs.add(ally);
        Mob prank=spawn(EntityType.ZOMBIE,5,2);prank.getPersistentData().putUUID("useless_stretcher_2i_prank_owner",owner.getUUID());protectedMobs.add(prank);
        var doll=TwoICombatDollMod.DOLL_ENTITY.get().create(level);doll.setOwnerId(other.getUUID());doll.setNoAi(true);doll.moveTo(-3,160,2,0,0);check(level.addFreshEntity(doll),"protected 2i fixture added");protectedMobs.add(doll);
        for(Mob mob:protectedMobs){check(!TwoIGrimoireCombat.eligible(mob,owner),"protected mob excluded before native execution");check(!AuraExecution.execute(owner,mob,new ItemStack(TwoICombatDollMod.STAFF.get())),"execution entry preserves protected mob");}
    }

    private static void prepareCase()throws Exception{
        kind=caseIndex==0?WeaponKind.STAFF:caseIndex==1?WeaponKind.BOOK:WeaponKind.AXOLOTL;
        owner.setPos(0,164,8);owner.setHealth(ownerHealth);other.setPos(3,166,8);other.setHealth(otherHealth);
        cast=false;teleported=false;impactVerified=false;angryNaturalSamples=0;lastAngrySample=-1;enemies=new ArrayList<>();
        for(int i=0;i<3;i++){
            EnderMan mob=EntityType.ENDERMAN.create(level);mob.moveTo(-6+i*5,160,-6,0,0);mob.setPersistenceRequired();
            if(i==1){
                mob.setTarget(owner);mob.setRemainingPersistentAngerTime(600);mob.setPersistentAngerTarget(owner.getUUID());
                check(mob.getTarget()==owner&&mob.isCreepy()&&mob.getRemainingPersistentAngerTime()>0,"real EnderMan angry data and owner target set");
            }
            check(level.addFreshEntity(mob),"real EnderMan spawned");tracked.add(mob.getUUID());enemies.add(mob);
            check(TwoIGrimoireCombat.eligible(mob,owner),"real calm/angry EnderMan eligible before "+kind);
        }
        // Let actual vanilla AI ticks happen before casting; data below is
        // refreshed at the begin edge, not asserted from manufactured subclasses.
        calmBaseline=enemies.get(0).tickCount;angryBaseline=enemies.get(1).tickCount;
        if(kind==WeaponKind.STAFF){
            var doll=TwoICombatDollMod.DOLL_ENTITY.get().create(level);doll.setOwnerId(owner.getUUID());doll.setNoAi(true);doll.moveTo(0,160,0,0,0);check(level.addFreshEntity(doll),"real 2i caster added");
            check(doll.combatState().equip(doll,new ItemStack(TwoICombatDollMod.STAFF.get())),"real 2i staff equipped");caster=doll;
        }else if(kind==WeaponKind.BOOK){
            var doll=(LivingEntity)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("longhuan:longhuan_doll")).create(level);
            var item=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("longhuan:longhuan_doll")));
            doll.getClass().getMethod("initialize",UUID.class,ItemStack.class).invoke(doll,owner.getUUID(),item);
            ((Mob)doll).setNoAi(true);doll.moveTo(0,160,0,0,0);check(level.addFreshEntity(doll),"real bundled Longhuan caster added");
            check(CombatActors.state(doll).equip(doll,new ItemStack(TwoICombatDollMod.BOOK.get())),"real Longhuan book equipped");caster=doll;
        }else{
            caster=CangshengAxolotlEntity.deploy(owner,new ItemStack(TwoICombatDollMod.AXOLOTL.get()),new Vec3(0,160,0),null);
            check(caster!=null,"real leased Cangsheng caster deployed");((Mob)caster).setNoAi(true);
        }
        state=CombatActors.state(caster);
    }
    private static ServerPlayer damageablePlayer(MinecraftServer server,String name)throws Exception{
        GameProfile profile=new GameProfile(UUID.randomUUID(),name);
        ServerPlayer player=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        // FakePlayer overrides isInvulnerableTo to always return true. Reuse
        // only its inert network handler; keep the real ServerPlayer damage path.
        player.connection=FakePlayerFactory.get(level,profile).connection;
        player.setGameMode(GameType.SURVIVAL);player.setInvulnerable(false);player.getAbilities().invulnerable=false;
        var spawn=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");spawn.setAccessible(true);spawn.setInt(player,0);
        float health=player.getHealth();
        check(player.hurt(level.damageSources().generic(),1F)&&player.getHealth()<health,"real survival fixture can actually receive native damage: "+name);
        player.setHealth(health);player.invulnerableTime=0;player.hurtTime=0;
        return player;
    }
    private static <T extends Mob> T spawn(EntityType<T> type,double x,double z){T mob=type.create(level);mob.setNoAi(true);mob.moveTo(x,160,z,0,0);check(level.addFreshEntity(mob),"fixture added "+type);return mob;}
    private static void disposeCase(){
        calmTicks=enemies.get(0).tickCount;angryTicks=enemies.get(1).tickCount;
        enemies.forEach(Entity::discard);
        if(state!=null)state.reset();
        if(caster!=null){
            // Its normal removal workflow needs the original carrier intact.
            if(!(caster instanceof CangshengAxolotlEntity)&&state!=null)state.disarm();
            discardFixture(caster);
        }
        caster=null;state=null;
    }
    private static void discardFixture(Entity entity){
        if(entity instanceof com.twoi.combatdoll.companion.content.entity.TwoIDollEntity doll)doll.discardForReclaim();else entity.discard();
    }
    @SubscribeEvent public static void death(LivingDeathEvent event){if(tracked.contains(event.getEntity().getUUID()))deaths.merge(event.getEntity().getUUID(),1,Integer::sum);}
    @SubscribeEvent public static void drop(LivingDropsEvent event){if(tracked.contains(event.getEntity().getUUID()))drops.merge(event.getEntity().getUUID(),1,Integer::sum);}
    public static void assertPassed(){if(!passed)throw new AssertionError("Native EnderMan execution checks incomplete/failed",failure);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
