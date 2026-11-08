package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.Map;
import java.util.UUID;

/** Native regression only; no client linkage and never packaged in production. */
public final class NativeFixServerChecks {
    private static int ticks,phase;
    private static boolean done;
    private static Map<UUID,ServerPlayer> online;
    private static ServerPlayer owner;
    @SuppressWarnings("unchecked")
    public static void tick(MinecraftServer server){
        if(done||++ticks<100)return;
        try{
            if(ticks>5000)throw new AssertionError("Native fixes timeout phase="+phase);
            if(phase==0){
                for(String absent:java.util.List.of("useless_stretcher","doll_chronicle","curios"))
                    check(!net.neoforged.fml.ModList.get().isLoaded(absent),"Unexpected integration "+absent);
                LogUtils.getLogger().info("NATIVE FIX ENVIRONMENT: independent=true useless_mod={}",net.neoforged.fml.ModList.get().isLoaded("useless_mod"));
                var field=PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);
                online=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());
                owner=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"NativeFacing"));
                owner.setGameMode(GameType.CREATIVE);owner.setPos(0,160,8);online.put(owner.getUUID(),owner);
                var level=server.overworld();
                var managerField=net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);
                var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)managerField.get(level);
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
                    level.setChunkForced(x,z,true);
                    manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(x,z),net.minecraft.world.level.entity.Visibility.TICKING);
                }
                NativeMountFacingChecks.begin(owner);phase=1;return;
            }
            if(phase==1){
                NativeMountFacingChecks.tick();if(!NativeMountFacingChecks.finished())return;
                NativeMountFacingChecks.assertPassed();NativeMountFacingChecks.cleanup();online.remove(owner.getUUID());phase=2;
            }
            if(phase==2){
                if(!NativeExecutionChecks.tick(server))return;
                NativeExecutionChecks.assertPassed();
                check(!server.tickRateManager().isFrozen(),"Global tick manager restored");
                check(!com.twoi.combatdoll.combat.Chronostasis.active(server),"Native time stop restored");
                LogUtils.getLogger().info("NATIVE FIX SERVER CHECKS PASSED: natural mount facing/director ownership; real EnderMan three weapons; protection; native time state restored");
                finish(server);
            }
        }catch(Throwable error){LogUtils.getLogger().error("NATIVE FIX SERVER CHECKS FAILED",error);finish(server);}
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void finish(MinecraftServer server){done=true;if(online!=null&&owner!=null)online.remove(owner.getUUID());server.saveEverything(true,true,true);server.halt(false);}
}
