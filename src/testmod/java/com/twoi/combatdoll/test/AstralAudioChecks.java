package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.client.ClientAstralAudio;
import com.twoi.combatdoll.client.ClientCombatClock;
import com.twoi.combatdoll.client.ClientTimeStop;
import com.twoi.combatdoll.combat.CombatActors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Test-only observation of requested sounds AND real allocated audio sources.
 * Does not replace sounds, inspect pixels to invent audibility, or emit any
 * production logs. Actual speaker/headphone listening remains a separate check.
 */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID,value=Dist.CLIENT)
public final class AstralAudioChecks {
    private record SoundRow(String label,String event,String name,long tick,float volume,float pitch,boolean relative) { }
    private record TickRow(long tick,float age,boolean charging,boolean freeze,boolean chargeVoice,boolean chargeActive) { }
    private static final ConcurrentLinkedQueue<SoundRow> SOUNDS=new ConcurrentLinkedQueue<>();
    private static final List<TickRow> TICKS=new ArrayList<>();
    private static volatile String label;
    private static LivingEntity actor;
    private static SoundInstance cancelledVoice;
    private static Field voices;
    private AstralAudioChecks() { }

    static void begin(Minecraft mc,String name,LivingEntity owner){
        label=name;actor=owner;SOUNDS.clear();TICKS.clear();cancelledVoice=null;
        LogUtils.getLogger().info("ASTRAL AUDIO OBSERVATION STARTED: {} audio={}",name,mc.getSoundManager().getDebugString());
    }

    @SubscribeEvent public static void queued(PlaySoundEvent event){
        var sound=event.getSound();record("requested",sound);
    }
    @SubscribeEvent public static void source(PlaySoundSourceEvent event){record("source_started",event.getSound());}
    private static void record(String event,SoundInstance sound){
        String current=label;
        if(current==null||sound==null||!sound.getLocation().getNamespace().equals(TwoICombatDollMod.MODID))return;
        String name=sound.getLocation().getPath();
        if(!(name.equals("astral_charge")||name.equals("astral_tick")||name.equals("time_stop")||name.equals("astral_reverse")||name.equals("star_verdict")))return;
        // PlaySoundEvent fires before SoundEngine resolves the Sound asset.
        // AbstractSoundInstance#getVolume/getPitch dereference that unresolved
        // asset. Only inspect those values after a real channel was allocated.
        boolean resolved=event.equals("source_started");
        SOUNDS.add(new SoundRow(current,event,name,ClientCombatClock.now(),resolved?sound.getVolume():Float.NaN,resolved?sound.getPitch():Float.NaN,sound.isRelative()));
    }

    @SubscribeEvent public static void sample(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(label==null||actor==null||mc.level==null)return;
        var state=CombatActors.state(actor);var sound=chargeSound(actor);
        TICKS.add(new TickRow(ClientCombatClock.now(),state.age(actor,0),state.charging(),ClientTimeStop.active(),sound!=null,sound!=null&&mc.getSoundManager().isActive(sound)));
    }

    static void rememberCancelledVoice(){cancelledVoice=chargeSound(actor);}
    static void cancelled(Minecraft mc){
        check(chargeSound(actor)==null,"cancelled charge voice removed from bounded voice map");
        // Stop processing is asynchronous; a source map entry may remain briefly
        // even after Channel::stop, so use the tickable stop flag as well.
        if(cancelledVoice instanceof net.minecraft.client.resources.sounds.TickableSoundInstance tickable)
            check(tickable.isStopped(),"cancelled tickable voice is explicitly stopped");
        check(!ClientTimeStop.active(),"short cancellation never declares time stop");
        check(SOUNDS.stream().noneMatch(r->r.name.equals("time_stop")),"no time-stop sound on short cancellation");
        LogUtils.getLogger().info("ASTRAL AUDIO CANCELLATION PASSED: {} stoppedTickable={} no declaration",label,cancelledVoice!=null);
    }

    static void finish(Minecraft mc,String name)throws Exception{
        check(name.equals(label),"audio evidence round label");
        var sounds=new ArrayList<>(SOUNDS);var ticks=new ArrayList<>(TICKS);
        check(sounds.stream().filter(r->r.event.equals("requested")&&r.name.equals("time_stop")).count()==1,"one time-stop declaration per committed cast");
        check(sounds.stream().filter(r->r.event.equals("requested")&&r.name.equals("astral_reverse")).count()==1,"one reverse sound per committed cast");
        for(String id:List.of("astral_charge","astral_tick","time_stop","astral_reverse","star_verdict"))
            check(sounds.stream().anyMatch(r->r.event.equals("source_started")&&r.name.equals(id)),"actual audio source allocated for "+id);
        check(ticks.stream().anyMatch(r->r.charging||r.age>=0&&r.age<44),"prelude sampled");
        check(ticks.stream().anyMatch(r->r.chargeActive),"charge voice was active in sound manager");
        check(ticks.stream().filter(r->r.freeze&&r.age>=48&&r.age<150).noneMatch(r->r.chargeVoice),"charge loop stops after declaration");
        long stopTick=sounds.stream().filter(r->r.event.equals("requested")&&r.name.equals("time_stop")).findFirst().orElseThrow().tick;
        check(ticks.stream().anyMatch(r->r.freeze&&Math.abs(r.tick-stopTick)<=2),"declaration and freeze onset within two observed client ticks");
        var directory=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(directory);
        var soundText=new ArrayList<String>();soundText.add("label,event,sound,client_tick,volume,pitch,relative");
        for(var row:sounds)soundText.add(String.format(Locale.ROOT,"%s,%s,%s,%d,%.5f,%.5f,%s",row.label,row.event,row.name,row.tick,row.volume,row.pitch,row.relative));
        Files.write(directory.resolve(name+"-astral-audio-events.csv"),soundText,StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
        var tickText=new ArrayList<String>();tickText.add("client_tick,age,charging,freeze,charge_voice_present,charge_sound_manager_active");
        for(var row:ticks)tickText.add(String.format(Locale.ROOT,"%d,%.5f,%s,%s,%s,%s",row.tick,row.age,row.charging,row.freeze,row.chargeVoice,row.chargeActive));
        Files.write(directory.resolve(name+"-astral-audio-ticks.csv"),tickText,StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
        LogUtils.getLogger().info("ASTRAL AUDIO CHECKS PASSED: {} {} sound events, {} ticks; five actual audio sources, one declaration, one reverse, stopped charge and freeze-onset alignment",name,sounds.size(),ticks.size());
        label=null;actor=null;cancelledVoice=null;
    }

    private static SoundInstance chargeSound(LivingEntity owner){
        if(owner==null)return null;
        try{
            if(voices==null){voices=ClientAstralAudio.class.getDeclaredField("VOICES");voices.setAccessible(true);}
            var voice=((Map<?,?>)voices.get(null)).get(owner);if(voice==null)return null;
            var sound=voice.getClass().getDeclaredField("sound");sound.setAccessible(true);return (SoundInstance)sound.get(voice);
        }catch(ReflectiveOperationException error){throw new AssertionError("test-only charge voice observation",error);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
