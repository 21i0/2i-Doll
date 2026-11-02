package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.client.ClientCombatClock;
import com.twoi.combatdoll.combat.CombatActors;
import com.twoi.combatdoll.combat.CombatWorldData;
import com.twoi.combatdoll.combat.TwoIArcanaTimeline;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only, test-only observations of both independent combat clocks.
 * A client publishes only scalar values. The server reads its own entity,
 * owner and state exclusively from its own thread; no client entity crosses
 * the thread boundary. An unrecognised reset is never called a duration end.
 */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID)
public final class BookClockChecks {
    private static volatile Session current;
    private record ClientSample(long clock,float age){ }
    private static final class Session {
        final LivingEntity actor;
        final String label;
        final Path directory;
        final List<String> rows=new ArrayList<>();
        volatile ClientSample client=new ClientSample(-1,-1);
        long previousStart=-1;
        boolean seen,written;
        Session(LivingEntity actor,String label,Path directory){this.actor=actor;this.label=label;this.directory=directory;}
    }
    private BookClockChecks(){ }

    static void begin(LivingEntity serverActor,String label,Path directory){
        if(serverActor.getServer()==null||!serverActor.getServer().isSameThread())
            throw new AssertionError("Clock evidence session must begin on the server thread");
        Session previous=current;
        if(previous!=null&&!previous.written)write(previous,"replaced_before_observed_end");
        current=new Session(serverActor,label,directory);
    }
    static void clientSample(float age){
        Session session=current;
        if(session!=null)session.client=new ClientSample(ClientCombatClock.now(),age);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        Session session=current;
        if(session==null||session.written||session.actor.getServer()!=event.getServer())return;
        var actor=session.actor;
        var state=CombatActors.state(actor);
        var ownerId=CombatActors.owner(actor);
        var owner=ownerId==null?null:event.getServer().getPlayerList().getPlayer(ownerId);
        long clock=CombatWorldData.get(event.getServer()).clock();
        float age=state.age(actor,0);
        long start=state.start();
        if(start>=0){session.seen=true;session.previousStart=start;}
        long elapsed=session.previousStart<0?-1:clock-session.previousStart;
        boolean busy=CombatActors.busy(actor);
        boolean removed=actor.isRemoved(),alive=actor.isAlive(),missing=owner==null;
        boolean wrongLevel=!missing&&owner.level()!=actor.level();
        boolean ownerAlive=!missing&&owner.isAlive(),spectator=!missing&&owner.isSpectator();
        double squaredDistance=missing?-1:actor.distanceToSqr(owner);
        double distance=missing?-1:Math.sqrt(squaredDistance);
        String reason="none";
        if(session.seen&&start<0){
            if(removed||!alive)reason="actor_removed_or_dead";
            else if(missing)reason="owner_missing";
            else if(wrongLevel)reason="owner_different_level";
            else if(!ownerAlive)reason="owner_dead";
            else if(spectator)reason="owner_spectator";
            else if(squaredDistance>48*48)reason="owner_outside_48_blocks";
            else if(busy)reason="actor_busy";
            else if(!state.armed())reason="disarmed";
            else if(elapsed>=TwoIArcanaTimeline.DURATION)reason="authoritative_duration_end";
            else reason="unknown_reset_before_duration";
        }
        ClientSample client=session.client;
        session.rows.add(String.format(Locale.ROOT,"%d,%.4f,%.4f,%d,%d,%d,%d,%.5f,%b,%b,%b,%b,%b,%b,%b,%b,%s",
                clock,age,client.age,start,session.previousStart,elapsed,client.clock,distance,busy,
                removed,alive,missing,wrongLevel,ownerAlive,spectator,state.armed(),reason));
        if(!reason.equals("none"))write(session,reason);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){
        Session session=current;
        if(session!=null&&!session.written&&session.actor.getServer()==event.getServer())write(session,"server_stopping_before_observed_end");
    }
    private static void write(Session session,String reason){
        session.written=true;
        try {
            Files.createDirectories(session.directory);
            var rows=new ArrayList<String>();
            rows.add("server_clock,server_age,published_client_age,server_start,last_active_start,server_elapsed_from_last_start,published_client_clock,owner_distance,busy,actor_removed,actor_alive,owner_missing,owner_wrong_level,owner_alive,owner_spectator,armed,observed_end_reason");
            rows.addAll(session.rows);
            Files.write(session.directory.resolve(session.label+"-authoritative-clock.csv"),rows,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            ClientSample client=session.client;
            LogUtils.getLogger().info("AUTHORITATIVE CLOCK EVIDENCE: {} rows={} observedEndReason={} lastServerStart={} publishedClientAge={} publishedClientClock={}",
                    session.label,session.rows.size(),reason,session.previousStart,client.age,client.clock);
        }catch(Exception failure){throw new AssertionError("Unable to preserve authoritative clock evidence: "+session.label,failure);}
    }
}
