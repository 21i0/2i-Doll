package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.client.TwoIArcanaRenderer;
import com.twoi.combatdoll.client.TwoIArcanaPost;
import com.twoi.combatdoll.companion.client.render.TwoIStarfieldEffects;
import com.twoi.combatdoll.companion.content.entity.TwoIDollService;
import com.twoi.combatdoll.companion.content.entity.TwoIStarfieldData;
import com.twoi.combatdoll.companion.dimension.world.TwoIStarfieldChunkGenerator;
import com.twoi.combatdoll.companion.dimension.world.TwoIStarfieldDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real native invitation, dimension transition, show packet/clock and return; never shipped. */
final class StandaloneGardenClientChecks {
    private static int phase,ticks,wait,framesBefore;
    private static boolean done;
    private static final AtomicInteger ready=new AtomicInteger();
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static volatile Vec3 origin;
    private static volatile BlockPos center;
    private static volatile long showEnd;
    private static CompletableFuture<Void> reload;
    private static int returnStable;
    private static final java.util.List<String> returnSamples=new java.util.ArrayList<>();
    private StandaloneGardenClientChecks(){}

    static boolean tick(Minecraft mc)throws Exception{
        if(done)return true;
        check(++ticks<2400,"real garden timeout phase="+phase);
        if(failure.get()!=null)throw new AssertionError("garden server action",failure.get());
        if(mc.getOverlay()!=null)return false;
        if(phase==0){
            framesBefore=TwoIArcanaPost.processedFrames();
            server(mc,player->{
                origin=player.position();
                LogUtils.getLogger().info("STANDALONE GARDEN ORIGIN: server={} motion={} ground={} floor={}",origin,player.getDeltaMovement(),player.onGround(),player.serverLevel().getBlockState(BlockPos.containing(origin).below()));
                check(player.serverLevel().getBlockState(BlockPos.containing(origin).below()).is(Blocks.STONE),"fixture origin remains above its known solid landing platform");
                check(player.serverLevel().noCollision(player,player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(origin)),"fixture origin has standing collision clearance");
                check(player.level().dimension().equals(Level.OVERWORLD),"test starts in real overworld");
                check(player.getServer().getLevel(TwoIStarfieldDimensions.KEY)!=null,"own registered garden exists without legacy mod");
                check(TwoIDollService.sendPlayerToStarfield(player,true),"native invitation changes dimension");
                var data=TwoIStarfieldData.get(player.getServer());int slot=data.slotFor(player.getUUID());
                center=new BlockPos((Math.floorMod(slot,1024)-512)*TwoIStarfieldData.AREA_SPACING+8,
                        TwoIStarfieldChunkGenerator.SURFACE_Y,(Math.floorDiv(slot,1024)-512)*TwoIStarfieldData.AREA_SPACING+8);
                check(data.awaitsCourtyardEntry(player.getUUID()),"entry does not start before gate");
                var point=data.returnPoint(player.getUUID());
                check(point!=null&&new Vec3(point.x(),point.y(),point.z()).distanceToSqr(origin)<1.0E-8,"native invitation stores exact safe origin");
                LogUtils.getLogger().info("STANDALONE GARDEN SAVED RETURN: {}",point);
                ready.set(1);
            });phase=1;return false;
        }
        if(phase==1){
            if(ready.get()!=1||!mc.level.dimension().equals(TwoIStarfieldDimensions.KEY))return false;
            check(TwoIArcanaRenderer.showAge(0)<0,"arrival outside courtyard has no active show");
            capture(mc,"standalone-garden-arrival");
            server(mc,player->{
                player.connection.teleport(center.getX()+.5,center.getY(),center.getZ()+6.5,180,-26);
                ready.set(2);
            });phase=2;return false;
        }
        if(phase==2){
            if(ready.get()!=2||TwoIArcanaRenderer.showAge(0)<5)return false;
            check(mc.level.dimension().location().toString().equals("two_i_combat_doll:two_i_starfield"),"own garden key on real client");
            check(TwoIArcanaRenderer.lastVertices()>0,"own garden emits actual decoration mesh");
            capture(mc,"standalone-garden-show-live");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"live garden has valid GL state");
            server(mc,player->{showEnd=TwoIStarfieldData.get(player.getServer()).celebrationEnd(player.getUUID());
                check(showEnd>player.level().getGameTime(),"server celebration deadline is live");ready.set(3);});
            reload=mc.reloadResourcePacks();phase=3;return false;
        }
        if(phase==3){
            if(ready.get()!=3||!reload.isDone())return false;reload.join();
            check(TwoIArcanaRenderer.showAge(0)>=0,"resource reload preserves live own show clock");
            check(TwoIArcanaRenderer.lastVertices()>0,"own garden decoration mesh survives resource reload");
            check(TwoIArcanaPost.processedFrames()>framesBefore,"own garden actually processes live post-effect frames");
            phase=4;wait=0;return false;
        }
        if(phase==4){
            if(++wait<80)return false;
            server(mc,player->{
                var data=TwoIStarfieldData.get(player.getServer());
                check(data.rewardsRemaining(player.getUUID())<8,"native garden reward actually decrements lifetime ledger");
                if(!net.neoforged.fml.ModList.get().isLoaded("ae2")){
                    int count=0;for(var stack:player.getInventory().items)if(stack.is(Items.AMETHYST_SHARD))count+=stack.getCount();
                    for(var drop:player.serverLevel().getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(24)))if(drop.getItem().is(Items.AMETHYST_SHARD))count+=drop.getItem().getCount();
                    check(count>0,"actual original item fallback exists when AE2 is absent");
                }
                ready.set(4);
            });phase=5;return false;
        }
        if(phase==5){
            if(ready.get()!=4||mc.level.getGameTime()<showEnd+25)return false;
            check(TwoIStarfieldEffects.showAge(0)<0&&TwoIArcanaRenderer.showAge(0)<0,"natural expiry clears own and renderer clocks");
            capture(mc,"standalone-garden-show-expired");
            server(mc,player->{
                var data=TwoIStarfieldData.get(player.getServer());
                check(data.celebrationEnd(player.getUUID())==0&&!data.awaitsCourtyardEntry(player.getUUID()),"server lifecycle expires without re-trigger");
                check(data.rewardsRemaining(player.getUUID())==0,"all eight lifetime rewards recorded");
                var point=data.returnPoint(player.getUUID());
                check(point!=null&&new Vec3(point.x(),point.y(),point.z()).distanceToSqr(origin)<1.0E-8,"lifetime celebration retains exact original return point");
                check(TwoIDollService.returnFromStarfield(player),"native return succeeds");
                LogUtils.getLogger().info("STANDALONE GARDEN IMMEDIATE RETURN: saved={} actual={} motion={} dimension={}",point,player.position(),player.getDeltaMovement(),player.level().dimension().location());
                check(player.level().dimension().equals(Level.OVERWORLD)&&player.position().distanceToSqr(origin)<1.0E-8,"native server immediately returns to exact original safe point");ready.set(5);
            });phase=6;wait=0;return false;
        }
        if(phase==6){
            if(ready.get()!=5||!mc.level.dimension().equals(Level.OVERWORLD))return false;
            double distance=mc.player.position().distanceToSqr(origin);
            var floor=BlockPos.containing(origin).below();
            boolean loaded=mc.level.hasChunkAt(floor);
            var state=mc.level.getBlockState(floor);
            boolean solid=loaded&&state.is(Blocks.STONE)&&!state.getCollisionShape(mc.level,floor).isEmpty();
            boolean grounded=mc.player.onGround();
            returnSamples.add("tick="+(++wait)+" pos="+mc.player.position()+" d2="+distance+" motion="+mc.player.getDeltaMovement()+" loaded="+loaded+" floor="+state+" solid="+solid+" ground="+grounded);
            check(wait<=40,"bounded native return sync deadline exceeded: origin="+origin+" samples="+returnSamples);
            if(distance>=4||!solid||!grounded){returnStable=0;return false;}
            if(++returnStable<3)return false;
            LogUtils.getLogger().info("STANDALONE GARDEN CLIENT RETURN SAMPLES: origin={} {}",origin,returnSamples);
            check(TwoIStarfieldEffects.showAge(0)<0&&TwoIArcanaRenderer.showAge(0)<0,"return clears all own show state");
            check(mc.player.position().distanceToSqr(origin)<4,"return restores original safe location");
            TwoIStarfieldEffects.startShow(center,mc.level.getGameTime()+600);
            check(TwoIStarfieldEffects.showAge(0)<0,"delayed show packet rejected outside own garden");
            capture(mc,"standalone-garden-returned");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"garden exit restores valid GL state");
            server(mc,player->{
                check(TwoIDollService.sendPlayerToStarfield(player,false),"repeat visit still permitted");
                check(TwoIStarfieldData.get(player.getServer()).rewardsRemaining(player.getUUID())==0,"repeat visit does not refill rewards");
                player.connection.teleport(center.getX()+.5,center.getY(),center.getZ()+6.5,180,-26);ready.set(6);
            });phase=7;return false;
        }
        if(phase==7){
            if(ready.get()!=6||!mc.level.dimension().equals(TwoIStarfieldDimensions.KEY)||TwoIArcanaRenderer.showAge(0)<8)return false;
            capture(mc,"standalone-garden-revisit");
            server(mc,player->{check(TwoIDollService.returnFromStarfield(player),"early exit succeeds");
                check(TwoIStarfieldData.get(player.getServer()).celebrationEnd(player.getUUID())==0,"early exit cancels show deadline");ready.set(7);});
            phase=8;wait=0;return false;
        }
        if(phase==8){
            if(ready.get()!=7||!mc.level.dimension().equals(Level.OVERWORLD)||++wait<10)return false;
            check(TwoIArcanaRenderer.showAge(0)<0&&TwoIStarfieldEffects.showAge(0)<0,"early exit clears local effects");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"final GL restoration");done=true;
            LogUtils.getLogger().info("STANDALONE GARDEN CLIENT CHECKS PASSED: own dimension native invitation/gate/show packet, real decoration mesh and post-effect frames, resource reload, eight finite native rewards, natural expiry, exact safe return, replay without rewards, early exit and stale packet rejection");
        }
        return done;
    }
    @FunctionalInterface private interface Action{void run(ServerPlayer player)throws Exception;}
    private static void server(Minecraft mc,Action action){mc.getSingleplayerServer().execute(()->{try{action.run(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));}catch(Throwable ex){failure.set(ex);}});}
    private static void capture(Minecraft mc,String name)throws Exception{var directory=mc.gameDirectory.toPath().resolve("companion-port-screenshots");Files.createDirectories(directory);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(directory.resolve(name+".png"));}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError("Standalone garden: "+message);}
}
