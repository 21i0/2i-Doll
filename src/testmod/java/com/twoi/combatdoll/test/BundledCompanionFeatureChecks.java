package com.twoi.combatdoll.test;

import com.customdoll.chuanshuo.content.DollLaugh;
import com.customdoll.chuanshuo.content.DollOwnership;
import com.customdoll.chuanshuo.content.entity.DollEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Integrated original handlers and natural ticks; never changes the world clock or ships. */
@EventBusSubscriber(modid="two_i_combat_doll")
public final class BundledCompanionFeatureChecks {
    private static ServerLevel level;
    private static ServerPlayer owner,guest;
    private static DollEntity doll;
    private static ItemStack carrier;
    private static BlockPos laughPos,spinPos,secondPos;
    private static Pig moving,originalNoAi,boundary,fresh;
    private static final List<Pig> pigs=new ArrayList<>();
    private static final Map<BlockPos,BlockState> previous=new LinkedHashMap<>();
    private static long began;
    private static int ticks,phase;
    private static volatile boolean started,done,passed;
    private static volatile Throwable failure;
    private static volatile UUID liveDoll;

    private BundledCompanionFeatureChecks() { }
    public static boolean started(){return started;}
    public static boolean finished(){return done;}
    public static UUID liveDoll(){return liveDoll;}
    public static long began(){return began;}
    public static void assertPassed(){if(!done||!passed)throw new AssertionError("Bundled companion features incomplete/failed",failure);}

    public static void start(ServerPlayer anchor) {
        if(started)return;
        started=true;level=anchor.serverLevel();
        try {
            laughPos=anchor.blockPosition().offset(4,0,0);
            spinPos=laughPos.offset(40,0,0);secondPos=spinPos.offset(10,0,0);
            owner=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"BundledFeatureOwner"));
            guest=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"BundledFeatureGuest"));
            owner.setPos(laughPos.getX(),laughPos.getY(),laughPos.getZ()+2);
            guest.setPos(owner.position());owner.setGameMode(GameType.SURVIVAL);
            carrier=new ItemStack(com.customdoll.chuanshuo.init.ModItems.TWO_I_DOLL.get());
            carrier.set(DataComponents.CUSTOM_NAME,Component.literal("Integrated laugh memory"));
            var extension=new CompoundTag();extension.putUUID("test_identity",UUID.randomUUID());extension.putString("extension","retained");
            carrier.set(DataComponents.CUSTOM_DATA,CustomData.of(extension));DollOwnership.claim(carrier,owner);
            block(laughPos.below(),Blocks.STONE.defaultBlockState());
            block(laughPos.above(4),Blocks.GLASS.defaultBlockState());
            block(laughPos,com.customdoll.chuanshuo.init.ModBlocks.TWO_I_DOLL_DISPLAY.get().defaultBlockState());
            var display=(com.customdoll.chuanshuo.content.blockentity.DollDisplayBlockEntity)level.getBlockEntity(laughPos);
            check(display!=null,"registered new Chuangshuo display exists");display.setDoll(carrier);
            check(!DollLaugh.start(guest,display),"visitor cannot start owner display laughter");
            owner.gameMode.handleBlockBreakAction(laughPos,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,Direction.UP,level.getMaxBuildHeight(),1);
            check(level.getBlockEntity(laughPos)==display&&DollLaugh.activeCount(level)==1,"real survival left block action starts one laugh and preserves block");
            owner.setGameMode(GameType.CREATIVE);
            owner.gameMode.handleBlockBreakAction(laughPos,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,Direction.UP,level.getMaxBuildHeight(),2);
            check(level.getBlockEntity(laughPos)==display&&DollLaugh.activeCount(level)==1,"creative repeated left block action cannot break or duplicate session");
            owner.setGameMode(GameType.SURVIVAL);fill(owner);
            click(laughPos,owner,true);
            check(level.getBlockEntity(laughPos)==display&&ItemStack.isSameItemSameComponents(carrier,display.copyDoll()),"full inventory retains entire laughing block carrier");
            owner.getInventory().setItem(0,ItemStack.EMPTY);click(laughPos,owner,true);
            check(level.getBlockState(laughPos).isAir()&&owner.getInventory().countItem(carrier.getItem())==1,"free slot recalls display exactly once");
            check(ItemStack.isSameItemSameComponents(carrier,owner.getInventory().getItem(0)),"display recall retains owner/custom identity/name/data");
            DollLaugh.tick(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(()->true,level));
            check(DollLaugh.activeCount(level)==0,"removed display releases its ephemeral performance");

            doll=com.customdoll.chuanshuo.init.ModEntities.TWO_I_DOLL.get().create(level);
            check(doll!=null,"new B entity registered");doll.initialize(owner.getUUID(),carrier);doll.setNoGravity(true);doll.setNoAi(true);
            doll.setPos(laughPos.getX()+.5,laughPos.getY(),laughPos.getZ()+.5);
            check(level.addFreshEntity(doll),"original Chuangshuo actor added");liveDoll=doll.getUUID();
            var refused=new AttackEntityEvent(guest,doll);NeoForge.EVENT_BUS.post(refused);
            check(refused.isCanceled()&&!DollLaugh.isLaughing(doll),"guest attack remains harmless and cannot start laughter");
            var attack=new AttackEntityEvent(owner,doll);NeoForge.EVENT_BUS.post(attack);
            check(attack.isCanceled()&&DollLaugh.isLaughing(doll)&&doll.getHealth()==20,"original owner attack handler starts harmless entity laughter");
            check(!DollLaugh.start(owner,doll),"repeated entity attacks cannot stack/restart sound");
            fill(owner);check(!doll.reclaim(owner)&&!doll.isRemoved()&&ItemStack.isSameItemSameComponents(carrier,doll.storedCopy()),"full inventory safely keeps laughing entity and all data");
            check(!doll.reclaim(guest),"visitor cannot recall owner's new B actor");
            var saved=new CompoundTag();doll.save(saved);
            var decoded=com.customdoll.chuanshuo.init.ModEntities.TWO_I_DOLL.get().create(level);decoded.load(saved);
            check(!DollLaugh.isLaughing(decoded)&&ItemStack.isSameItemSameComponents(carrier,decoded.storedCopy()),"entity codec retains carrier and never persists transient laugh");
            began=level.getGameTime();
            beginSpin();phase=1;
        }catch(Throwable problem){fail(problem);}
    }

    private static void beginSpin() {
        placeSpin(spinPos);placeSpin(secondPos);
        double y=spinPos.getY()+.5,z=spinPos.getZ()+.5;
        moving=pig(spinPos.getX()+5.5,y,z,false);
        originalNoAi=pig(spinPos.getX()+3.5,y,z+1,true);
        boundary=pig(spinPos.getX()+8.6,y,z,false);
        click(spinPos,guest,false);check(!spin(spinPos).isSpinning()&&!moving.isNoAi(),"visitor cannot activate Luomu aura");
        click(spinPos,owner,false);
        check(spin(spinPos).isSpinning()&&moving.isNoAi()&&originalNoAi.isNoAi(),"owner spinning display pauses nearby real mobs immediately");
        check(!boundary.isNoAi(),"spherical eight-block boundary stays unpaused outside radius");
        click(secondPos,owner,false);click(spinPos,owner,false);
        check(moving.isNoAi(),"overlapping active source keeps the shared mob paused");
        level.removeBlock(secondPos,false);
        check(!moving.isNoAi()&&originalNoAi.isNoAi(),"last source removal restores false and preserves original true NoAI");
        click(spinPos,owner,false);
        check(moving.isNoAi(),"same source can start again");
        moving.setPos(spinPos.getX()+20,y,z);boundary.setPos(spinPos.getX()+6.5,y,z);
        owner.setPos(laughPos.getX(),laughPos.getY(),laughPos.getZ()+2);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!started||done||level==null||event.getServer()!=level.getServer())return;
        try {
            check(++ticks<340,"natural-tick feature fixture timeout, phase="+phase);
            long age=level.getGameTime()-began;
            if(phase==1&&ticks>=3) {
                check(!moving.isNoAi()&&boundary.isNoAi(),"natural level ticks restore exiting mobs and pause entering mobs");
                fresh=pig(spinPos.getX()+4,spinPos.getY()+.5,spinPos.getZ()+.5,false);
                check(!fresh.isNoAi(),"real fresh spawn starts at its original AI state");phase=2;return;
            }
            if(phase==2) {
                check(fresh.isNoAi(),"next natural level tick applies aura to fresh mob");
                var paused=new CompoundTag();boundary.save(paused);
                level.removeBlock(spinPos,false);
                check(!boundary.isNoAi()&&!fresh.isNoAi()&&originalNoAi.isNoAi(),"source removal restores each exact original state");
                var recovered=EntityType.PIG.create(level);check(recovered!=null,"recovery pig factory");recovered.load(paused);recovered.setUUID(UUID.randomUUID());
                check(recovered.isNoAi(),"actual serialized paused entity contains NoAI");
                check(level.addFreshEntity(recovered)&&!recovered.isNoAi(),"join handler repairs marker after real entity codec reload");pigs.add(recovered);
                placeSpin(spinPos);click(spinPos,owner,false);
                check(boundary.isNoAi(),"restart source pauses existing mobs");
                spin(spinPos).onChunkUnloaded();check(!boundary.isNoAi(),"actual source unload callback releases affected mobs");
                spin(spinPos).onLoad();check(boundary.isNoAi(),"onLoad callback reinstates still-spinning source");
                check(level.destroyBlock(spinPos,false)&&!boundary.isNoAi()&&originalNoAi.isNoAi(),"actual block destruction releases aura without changing preexisting NoAI");
                owner.setPos(laughPos.getX(),laughPos.getY(),laughPos.getZ()+2);phase=3;
                LogUtils.getLogger().info("BUNDLED LUOMU SPIN AI CHECKS PASSED: original owner/guest interaction; spherical eight-block range; natural movement/spawn ticks; overlapping sources; stop/remove/break/unload/reload; saved marker and exact original NoAI recovery");
            }
            if(phase==3) {
                if(age<DollLaugh.DURATION) {
                    check(DollLaugh.isLaughing(doll)&&DollLaugh.activeCount(level)==1,"performance remains alive through the natural 240-tick duration");
                    return;
                }
                check(DollLaugh.activeCount(level)==0&&!DollLaugh.isLaughing(doll),"original level tick expires laughter at 240 natural ticks");
                check(!doll.isRemoved()&&doll.getHealth()==20&&ItemStack.isSameItemSameComponents(carrier,doll.storedCopy()),"finished performance retains actor and complete carrier");
                check(DollLaugh.start(owner,doll),"finished performance can be replayed");
                check(!doll.reclaim(owner)&&!doll.isRemoved(),"full inventory still safely blocks recall after replay");
                owner.getInventory().setItem(0,ItemStack.EMPTY);
                check(doll.reclaim(owner)&&doll.isRemoved(),"free slot recalls actor during active replay");
                check(owner.getInventory().countItem(carrier.getItem())==1&&ItemStack.isSameItemSameComponents(carrier,owner.getInventory().getItem(0)),"entity recall returns one exact owner/identity/data carrier");phase=4;return;
            }
            if(phase==4) {
                check(DollLaugh.activeCount(level)==0&&!DollLaugh.isLaughing(doll),"next natural tick cancels recalled actor animation and sound session");
                passed=true;done=true;cleanup();
                LogUtils.getLogger().info("BUNDLED CHUANSHUO LAUGH CHECKS PASSED: actual survival/creative left-block actions and owner/guest entity attack events; no damage/stacked replay; both full-inventory recall paths; exact item components; ephemeral codec; natural 240-tick expiry and active-replay recall cleanup");
                LogUtils.getLogger().info("BUNDLED COMPANION FEATURE CHECKS PASSED: final Chuangshuo1.0.1 laughter and Luomu1.0.1 AI aura run in the integrated mod without changing world time");
            }
        }catch(Throwable problem){fail(problem);}
    }

    private static void block(BlockPos pos,BlockState state) {
        level.getChunkAt(pos);previous.putIfAbsent(pos.immutable(),level.getBlockState(pos));
        level.setBlockAndUpdate(pos,state);
    }
    private static void placeSpin(BlockPos pos) {
        block(pos.below(),Blocks.STONE.defaultBlockState());
        block(pos,com.customdoll.luomu.init.ModBlocks.TWO_I_DOLL_DISPLAY.get().defaultBlockState());
        var stack=new ItemStack(com.customdoll.luomu.init.ModItems.TWO_I_DOLL.get());
        com.customdoll.luomu.content.DollOwnership.claim(stack,owner);spin(pos).setDoll(stack);
    }
    private static com.customdoll.luomu.content.blockentity.DollDisplayBlockEntity spin(BlockPos pos) {
        return (com.customdoll.luomu.content.blockentity.DollDisplayBlockEntity)level.getBlockEntity(pos);
    }
    private static Pig pig(double x,double y,double z,boolean noAi) {
        Pig pig=EntityType.PIG.create(level);check(pig!=null,"pig factory");
        pig.setPos(x,y,z);pig.setNoGravity(true);pig.setNoAi(noAi);pig.setPersistenceRequired();
        check(level.addFreshEntity(pig),"real live pig added");pigs.add(pig);return pig;
    }
    private static void click(BlockPos pos,ServerPlayer player,boolean sneak) {
        player.setPos(pos.getX(),pos.getY(),pos.getZ()+2);player.setShiftKeyDown(sneak);
        try {level.getBlockState(pos).useWithoutItem(level,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));}
        finally {player.setShiftKeyDown(false);}
    }
    private static void fill(ServerPlayer player) {
        player.getInventory().clearContent();
        for(int slot=0;slot<player.getInventory().items.size();slot++)player.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
    }
    private static void fail(Throwable problem) {
        failure=problem;done=true;cleanup();LogUtils.getLogger().error("BUNDLED COMPANION FEATURE CHECKS FAILED",problem);
    }
    private static void check(boolean value,String why) {
        if(!value)throw new AssertionError("Bundled companion feature: "+why);
    }
    private static void cleanup() {
        if(level==null)return;
        if(doll!=null&&!doll.isRemoved())doll.discard();
        for(Pig pig:pigs)if(!pig.isRemoved())pig.discard();
        for(var entry:previous.entrySet())level.setBlockAndUpdate(entry.getKey(),entry.getValue());
        if(owner!=null)owner.getInventory().clearContent();
        if(guest!=null)guest.getInventory().clearContent();
    }
    public static boolean belongsTo(MinecraftServer server){return level!=null&&level.getServer()==server;}
}
