package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Fixture-only existing-progress player, so an optional pack's auto-opening cannot replace the UI under test. */
final class StandaloneChronicleFixtureChecks {
    private static boolean requested,done;
    private static volatile boolean prepared;
    private static int ticks;
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    static boolean tick(Minecraft mc)throws Exception{
        if(!ModList.get().isLoaded("doll_chronicle")||done)return true;
        if(failure.get()!=null)throw new AssertionError("Chronicle fixture preparation",failure.get());
        if(++ticks>200)throw new AssertionError("Chronicle known-state setup timed out: screen="+screen(mc)+" overlay="+overlay(mc));
        if(!requested){
            requested=true;
            UUID playerId=mc.player.getUUID();
            LogUtils.getLogger().info("STANDALONE CHRONICLE FIXTURE OBSERVED: screen={} overlay={}; only fresh fixture player's prologue receipt is prepared, no pack sources/config/routes changed",screen(mc),overlay(mc));
            mc.getSingleplayerServer().execute(()->{
                try{
                    var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayer(playerId);
                    Class<?> dataType=Class.forName("com.twoi.chronicle.server.ChronicleData");
                    Object data=dataType.getMethod("get",MinecraftServer.class).invoke(null,server);
                    var row=(CompoundTag)dataType.getMethod("row",UUID.class).invoke(data,playerId);
                    var evidence=new CompoundTag();evidence.putUUID("player",playerId);evidence.put("before",row.copy());
                    check(!row.getBoolean("complete")&&row.getInt("route")==-1&&row.getInt("job")==-1,"fresh fixture has no completed or chosen route to overwrite");
                    Class<?> controller=Class.forName("com.twoi.chronicle.server.PrologueController");
                    int complete=controller.getField("COMPLETE").getInt(null);
                    row.putBoolean("complete",true);row.putBoolean("opening_seen",true);row.putInt("stage",complete);
                    dataType.getMethod("changed").invoke(data);
                    // Public controller cleanup also sends its real COMPLETE packet. Its
                    // own client handler clears only the known opening/choice/boarding UI.
                    controller.getMethod("cancel",ServerPlayer.class).invoke(null,player);
                    var state=(CompoundTag)controller.getMethod("state",ServerPlayer.class).invoke(null,player);
                    check(state.getInt("stage")==complete&&row.getInt("route")==-1&&row.getInt("job")==-1,"public controller is complete without route choice or pack reward");
                    evidence.put("after",row.copy());evidence.put("controller_state",state);
                    var path=server.getWorldPath(LevelResource.ROOT).resolve("standalone-chronicle-fixture-preparation.dat");
                    check(!Files.exists(path),"fresh setup never overwrites prior fixture evidence");NbtIo.writeCompressed(evidence,path);
                    LogUtils.getLogger().info("STANDALONE CHRONICLE FIXTURE SERVER READY: player={} stage={} route=-1 job=-1 evidence={}",playerId,complete,path);
                    prepared=true;
                }catch(Throwable ex){failure.set(ex);}
            });return false;
        }
        if(!prepared)return false;
        Object state=Class.forName("com.twoi.chronicle.client.ChronicleClient").getMethod("state").invoke(null);
        int stage=(int)state.getClass().getMethod("stage").invoke(state);
        int complete=Class.forName("com.twoi.chronicle.server.PrologueController").getField("COMPLETE").getInt(null);
        if(stage!=complete||mc.screen!=null)return false;
        LogUtils.getLogger().info("STANDALONE CHRONICLE CLIENT FIXTURE READY: real COMPLETE packet received, stage={} screen={} overlay={}; original Piston graduation checks use a separate player",stage,screen(mc),overlay(mc));
        done=true;return true;
    }
    static String screen(Minecraft mc){return mc.screen==null?"null":mc.screen.getClass().getName()+" title="+mc.screen.getTitle().getString();}
    static String overlay(Minecraft mc){return mc.getOverlay()==null?"null":mc.getOverlay().getClass().getName();}
    private static void check(boolean value,String why){if(!value)throw new AssertionError("Chronicle isolated fixture: "+why);}
}
