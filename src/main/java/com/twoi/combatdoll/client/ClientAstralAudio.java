package com.twoi.combatdoll.client;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.DollCombatAccess;
import com.twoi.combatdoll.combat.TwoIArcanaTimeline;
import com.twoi.combatdoll.combat.WeaponKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.IdentityHashMap;

/** Local, bounded sound voices. A cancelled charge stops its voice immediately. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID,value=Dist.CLIENT)
public final class ClientAstralAudio {
    private static final IdentityHashMap<LivingEntity,Voice> VOICES=new IdentityHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private static int ticks;
    private static final class Voice { Charge sound; int beat=-1; boolean reverse,charging; }
    private ClientAstralAudio() { }
    public static void observe(LivingEntity actor){
        var mc=Minecraft.getInstance();
        if(mc.level==null||actor.level()!=mc.level||mc.player==null||actor.distanceToSqr(mc.player)>80*80)return;
        var state=DollCombatAccess.state(actor);
        if(state.kind()!=WeaponKind.STAFF||state.age(actor,0)<0){removeVoice(actor);return;}
        var previous=VOICES.get(actor);
        // State boundaries are reliable ordered packets. Removing the cancelled
        // voice here also handles cancel -> new charge in the same client tick.
        // Do not compare synchronize()'s reconstructed start timestamp: ordinary
        // network jitter can move it by a tick without starting a new cast.
        if(previous!=null&&state.charging()&&!previous.charging){removeVoice(actor);previous=null;}
        if(previous!=null||VOICES.size()<4){
            var voice=VOICES.computeIfAbsent(actor,a->new Voice());voice.charging=state.charging();
        }
    }
    private static void removeVoice(LivingEntity actor){
        var voice=VOICES.remove(actor);
        if(voice!=null)stopCharge(voice);
    }
    private static void stopCharge(Voice voice){
        if(voice.sound==null)return;
        voice.sound.cancel();Minecraft.getInstance().getSoundManager().stop(voice.sound);voice.sound=null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();ticks++;
        if(lastLevel!=mc.level){VOICES.values().forEach(ClientAstralAudio::stopCharge);VOICES.clear();lastLevel=mc.level;}
        if(mc.level==null||mc.player==null)return;
        VOICES.entrySet().removeIf(entry->{
            var actor=entry.getKey();var voice=entry.getValue();var state=DollCombatAccess.state(actor);float age=state.age(actor,0);
            if(actor.isRemoved()||actor.level()!=mc.level||age<0||age>=TwoIArcanaTimeline.DURATION||actor.distanceToSqr(mc.player)>80*80){
                stopCharge(voice);return true;
            }
            if(age<TwoIArcanaTimeline.CHARGE){
                if(voice.sound==null){voice.sound=new Charge(actor);mc.getSoundManager().play(voice.sound);}
                int elapsed=(int)(state.charging()?state.chargeTicks(actor,0):age);
                int beat=elapsed<=40?elapsed/20:2+(elapsed-40)/24;
                if(beat>voice.beat){
                    voice.beat=beat;
                    mc.level.playLocalSound(actor.getX(),actor.getY()+1,actor.getZ(),TwoICombatDollMod.ASTRAL_TICK.get(),SoundSource.PLAYERS,.85F,beat%2==0?.96F:1.04F,false);
                }
                if(actor==mc.player&&state.charging()&&ticks%10==0){
                    int percent=Math.min(100,(int)(state.chargeTicks(actor,0)*100/TwoIArcanaTimeline.CHARGE));
                    mc.player.displayClientMessage(Component.translatable(percent>=100?"message.two_i_combat_doll.charge_ready":"message.two_i_combat_doll.charging",percent),true);
                }
            }else stopCharge(voice);
            if(age>=TwoIArcanaTimeline.REVERSE&&!voice.reverse){
                voice.reverse=true;
                mc.level.playLocalSound(actor.getX(),actor.getY()+1,actor.getZ(),TwoICombatDollMod.ASTRAL_REVERSE.get(),SoundSource.PLAYERS,.9F,1,false);
            }
            return false;
        });
    }
    private static final class Charge extends AbstractTickableSoundInstance {
        private final LivingEntity actor;
        Charge(LivingEntity actor){
            super(TwoICombatDollMod.ASTRAL_CHARGE.get(),SoundSource.PLAYERS,RandomSource.create());
            this.actor=actor;looping=true;delay=0;volume=.62F;pitch=1;x=actor.getX();y=actor.getY()+1;z=actor.getZ();
        }
        void cancel(){volume=0;stop();}
        @Override public void tick(){
            var mc=Minecraft.getInstance();var state=DollCombatAccess.state(actor);float age=state.age(actor,0);
            if(actor.isRemoved()||mc.level!=actor.level()||age<0||age>=TwoIArcanaTimeline.CHARGE){stop();return;}
            x=actor.getX();y=actor.getY()+1;z=actor.getZ();
        }
    }
}
