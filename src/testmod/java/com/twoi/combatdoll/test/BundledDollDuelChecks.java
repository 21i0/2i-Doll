package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.duel.DollDuelArena;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Private loader runtime fixture. Real ServerPlayer/registered actors/natural server ticks. */
@EventBusSubscriber(modid="two_i_combat_doll")
public final class BundledDollDuelChecks {
    private static final Map<String,String> VERSIONS=Map.ofEntries(
            Map.entry("baimo","1.21.1-1.0.0"),Map.entry("chuanshuo","1.21.1-1.0.1"),Map.entry("fenghua","1.21.1-1.0.0"),
            Map.entry("longhuan","1.21.1-1.0.3"),Map.entry("luomu","1.21.1-1.0.1"),
            Map.entry("nadir","1.21.1-1.0.0"),Map.entry("ninxin","1.21.1-1.0.1"),Map.entry("ooolala","1.21.1-1.0.0"),
            Map.entry("qijici","1.21.1-1.0.0"),Map.entry("qijiu","1.21.1-1.0.0"),Map.entry("shaniao","1.21.1-1.0.0"),Map.entry("sishier","1.21.1-1.0.1"),
            Map.entry("xiaoxiao","1.21.1-1.0.1"),Map.entry("piston","1.21.1-1.0.0"));
    private static final int[] SEQUENCE={1,2,3,0,1,0};
    private static ServerPlayer player;
    private static MinecraftServer server;
    private static Map<UUID,ServerPlayer> online;
    private static CompoundTag storyRow,oldStory;
    private static UUID duel;
    private static int ticks,index,phase;
    private static long lastAction;
    private static com.twoi.combatdoll.compat.UmbrellaPairCache.Anchor cacheAnchor;
    private static volatile boolean started,done,passed;
    private static volatile Throwable failure;
    private BundledDollDuelChecks(){}
    @SuppressWarnings("unchecked") public static void start(ServerPlayer anchor)throws Exception{
        if(started)return;started=true;server=anchor.getServer();
        for(String namespace:List.of("lonya","lonya_plush")){
            check(!net.neoforged.fml.ModList.get().isLoaded(namespace),"Removed Lonya mod identity is absent: "+namespace);
            check(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(namespace,namespace+"_doll")),"Removed Lonya doll item remains absent: "+namespace);
            check(!BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.fromNamespaceAndPath(namespace,namespace+"_doll")),"Removed Lonya entity remains absent: "+namespace);
            check(!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath(namespace,namespace+"_display")),"Removed Lonya display remains absent: "+namespace);
        }
        for(var entry:VERSIONS.entrySet()){
            var info=net.neoforged.fml.ModList.get().getModContainerById(entry.getKey()).orElseThrow().getModInfo();
            check(info.getVersion().toString().equals(entry.getValue()),"Actual mod version "+entry);
            String name=entry.getKey()+"_doll";
            check(BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(entry.getKey(),name)),"Registered item "+entry.getKey()+":"+name);
            // The remaining A-only decorative companion intentionally has no entity.
            if(!Set.of("qijici").contains(entry.getKey()))
                check(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.fromNamespaceAndPath(entry.getKey(),name)),"Registered companion actor "+entry.getKey());
        }
        UUID id=UUID.randomUUID();player=FakePlayerFactory.get(anchor.serverLevel(),new GameProfile(id,"BundledDuel"));
        player.setPos(anchor.getX()+10,anchor.getY(),anchor.getZ()+10);
        player.getInventory().clearContent();player.getPersistentData().remove(DollDuelArena.DATA);
        player.getInventory().setItem(0,new ItemStack(com.customdoll.longhuan.init.ModItems.TWO_I_DOLL.get()));
        var f=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");f.setAccessible(true);online=(Map<UUID,ServerPlayer>)f.get(server.getPlayerList());online.put(id,player);
        if(net.neoforged.fml.ModList.get().isLoaded("doll_chronicle")){
            var type=Class.forName("com.twoi.chronicle.server.ChronicleData");Object data=type.getMethod("get",MinecraftServer.class).invoke(null,server);
            storyRow=(CompoundTag)type.getMethod("row",UUID.class).invoke(data,id);oldStory=storyRow.copy();
            storyRow.putBoolean("complete",true);storyRow.putInt("route",1);storyRow.putBoolean("route_confluence",false);
            check(!DollDuelArena.begin(player),"Chronicle rejects a non-graduated direct command/API start");
            storyRow.putBoolean("route_confluence",true);
        }
        check(DollDuelArena.begin(player),"Actual registered Longhuan/Piston arena begins");
        if(net.neoforged.fml.ModList.get().isLoaded("doll_chronicle"))
            LogUtils.getLogger().info("BUNDLED CHRONICLE GRADUATION GATE CHECKS PASSED: real non-graduated begin refused, confluence graduation permits the same original actor/player API");
        else LogUtils.getLogger().info("BUNDLED STANDALONE DUEL ACCESS CHECKS PASSED: actual Longhuan/Piston arena begins without Chronicle");
        duel=row().getUUID("duel_id");check(row().getInt("turn")==1,"New fight starts round 1");
        var projections=player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,player.getBoundingBox().inflate(16),DollDuelArena::isDuelActor);
        check(projections.size()==2,"Two real registered projections joined the level");
        for(var actor:projections){
            ItemStack stored=(ItemStack)actor.getClass().getMethod("storedCopy").invoke(actor);
            check(stored.isEmpty(),"Projection contains no cloneable real doll carrier");
            check(!(boolean)actor.getClass().getMethod("reclaim",net.minecraft.world.entity.player.Player.class).invoke(actor,player),"Direct reclaim of projection cannot duplicate a doll");
            if(actor instanceof com.twoi.combatdoll.combat.DollCombatAccess){
                var book=new ItemStack(com.twoi.combatdoll.TwoICombatDollMod.BOOK.get());
                var drop=new net.minecraft.world.entity.item.ItemEntity(player.level(),actor.getX(),actor.getY(),actor.getZ(),book);
                drop.setThrower(player);check(player.serverLevel().addFreshEntity(drop),"Owner's real book drop joined");
                com.twoi.combatdoll.combat.WeaponEquipment.collect(actor);
                check(drop.isAlive()&&drop.getItem().getCount()==1&&!com.twoi.combatdoll.combat.DollCombatAccess.state(actor).armed(),"Training projection cannot swallow owner's dropped weapon");
                check(!com.twoi.combatdoll.combat.DollCombatAccess.state(actor).equip(actor,book),"Direct equipment API also rejects projections");drop.discard();
            }
        }
        cacheAnchor=new com.twoi.combatdoll.compat.UmbrellaPairCache.Anchor("regression:"+id,player.position());
        var cacheBefore=com.twoi.combatdoll.compat.UmbrellaPairCache.stats(player.level());
        var pair=com.twoi.combatdoll.compat.UmbrellaPairCache.sample(player.level(),cacheAnchor);
        check(pair==com.twoi.combatdoll.compat.UmbrellaPairCache.sample(player.level(),cacheAnchor),"Same tick/anchor returns exact cached result");
        var wrapped=com.customdoll.longhuan.content.DollUmbrellaProtocol.sample(player.level(),new com.customdoll.longhuan.content.DollUmbrellaProtocol.Anchor(cacheAnchor.id(),cacheAnchor.feet()));
        check(wrapped.canopy().equals(pair.canopy()),"Original public protocol retains equivalent canopy");
        com.twoi.combatdoll.compat.UmbrellaPairCache.sample(player.level(),new com.twoi.combatdoll.compat.UmbrellaPairCache.Anchor(cacheAnchor.id(),cacheAnchor.feet().add(.01,0,0)));
        var cacheAfter=com.twoi.combatdoll.compat.UmbrellaPairCache.stats(player.level());
        check(cacheAfter.hits()>=cacheBefore.hits()+2&&cacheAfter.misses()==cacheBefore.misses()+2&&cacheAfter.entries()<=1024,"Cross-renderer reuse, exact-position invalidation and bound");
        DollDuelArena.move(player,UUID.randomUUID(),1,0);DollDuelArena.move(player,duel,9,0);
        check(row().getInt("turn")==1,"Wrong fight/stale turn cannot act");
        DollDuelArena.move(player,3);check(row().getInt("turn")==1,"Uncharged finisher cannot act");
        DollDuelArena.move(player,0);check(row().getInt("turn")==2&&row().getInt("enemy_hp")==152,"Actual first round resolves HP on server");
        DollDuelArena.move(player,0);check(row().getInt("turn")==2,"Immediate repeat packet cannot advance a second round");
        lastAction=player.serverLevel().getGameTime();phase=1;
        LogUtils.getLogger().info("BUNDLED DOLL REGISTRY CHECKS PASSED: 14 source-integrated mod IDs, actual versions/items/actors; lonya/lonya_plush absent; Piston original material, Longhuan1.0.3 preserved");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!started||done||server!=event.getServer())return;
        try{
            check(++ticks<600,"Natural-tick duel fixture timed out");
            long now=player.serverLevel().getGameTime();if(now-lastAction<30)return;
            if(phase==1){
                var cacheBefore=com.twoi.combatdoll.compat.UmbrellaPairCache.stats(player.level());
                com.twoi.combatdoll.compat.UmbrellaPairCache.sample(player.level(),cacheAnchor);
                var cacheAfter=com.twoi.combatdoll.compat.UmbrellaPairCache.stats(player.level());
                check(cacheAfter.tick()==now&&cacheAfter.misses()==cacheBefore.misses()+1,"Natural game tick invalidates cached scan");
                CompoundTag before=row().copy();DollDuelArena.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
                check(row().getBoolean("active"),"Logout preserves active fight");
                check(DollDuelArena.begin(player),"Resume loads actual persistent fight");
                check(row().getUUID("duel_id").equals(duel)&&row().getInt("turn")==before.getInt("turn")&&row().getInt("enemy_hp")==before.getInt("enemy_hp"),"Resume keeps exact identity/turn/HP");
                phase=2;
            }
            if(phase==2&&index<SEQUENCE.length){
                int turn=row().getInt("turn"),move=SEQUENCE[index++];DollDuelArena.move(player,duel,turn,move);
                check(row().getInt("turn")==turn+1,"Natural next round is accepted");
                DollDuelArena.move(player,duel,turn,move);DollDuelArena.move(player,move);
                check(row().getInt("turn")==turn+1,"Stale turn and immediate repeat are both ignored");
                lastAction=now;if(index<SEQUENCE.length)return;phase=3;return;
            }
            if(phase==3){
                check(row().getInt("outcome")==1&&row().getInt("enemy_hp")==0,"Real rounds reached actual Piston defeat");
                check(DollDuelArena.hasPistonWin(player),"Validated personal win receipt exists");
                check(row().getUUID("piston_win_player").equals(player.getUUID())&&row().getUUID("piston_win_duel").equals(duel)&&row().getInt("piston_win_rounds")==7,"Receipt stamps exact owner/fight/rounds");
                check(player.getInventory().getItem(0).getCount()==1,"Training did not consume or duplicate the original doll");
                ServerPlayer clone=new net.neoforged.neoforge.common.util.FakePlayer(player.serverLevel(),new GameProfile(player.getUUID(),"DuelClone"));
                DollDuelArena.clone(new PlayerEvent.Clone(clone,player,true));check(DollDuelArena.hasPistonWin(clone),"Death clone preserves validated win receipt");
                CompoundTag saved=player.saveWithoutId(new CompoundTag());ServerPlayer restored=new net.neoforged.neoforge.common.util.FakePlayer(player.serverLevel(),new GameProfile(player.getUUID(),"DuelReload"));
                restored.load(saved);check(DollDuelArena.hasPistonWin(restored),"Actual player NBT save/load preserves validated receipt");
                var forged=restored.getPersistentData().getCompound(DollDuelArena.DATA);forged.putUUID("piston_win_player",UUID.randomUUID());
                check(!DollDuelArena.hasPistonWin(restored),"Another player's receipt is rejected");
                passed=true;done=true;cleanup();
                LogUtils.getLogger().info("BUNDLED PISTON DUEL CHECKS PASSED: real registered ServerPlayer+actors; graduation gate; natural ticks; 7 resolved rounds; UUID/stale/rapid/energy rejection; empty projections; exact resume; validated owner/round win; Clone and actual player NBT load persist receipt; shared umbrella cache hit/position/natural-tick invalidation/bound");
            }
        }catch(Throwable problem){failure=problem;done=true;cleanup();LogUtils.getLogger().error("BUNDLED PISTON DUEL CHECKS FAILED",problem);}
    }
    private static CompoundTag row(){return player.getPersistentData().getCompound(DollDuelArena.DATA);}
    public static boolean finished(){return done;}
    public static void assertPassed(){if(!done||!passed)throw new AssertionError("Bundled duel runtime checks incomplete/failed",failure);}
    private static void cleanup(){
        if(player!=null){DollDuelArena.logout(new PlayerEvent.PlayerLoggedOutEvent(player));if(online!=null)online.remove(player.getUUID());}
        if(storyRow!=null&&oldStory!=null){for(String key:List.copyOf(storyRow.getAllKeys()))storyRow.remove(key);for(String key:oldStory.getAllKeys())storyRow.put(key,oldStory.get(key).copy());}
    }
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError("Bundled companion: "+why);}
}
