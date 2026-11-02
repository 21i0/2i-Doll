package com.customdoll.longhuan.client;

import com.customdoll.longhuan.DollMod;
import com.customdoll.longhuan.content.*;
import com.customdoll.longhuan.content.entity.DollEntity;
import com.customdoll.longhuan.content.blockentity.DollDisplayBlockEntity;
import com.customdoll.longhuan.init.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

@EventBusSubscriber(modid=DollMod.MODID,value=Dist.CLIENT)
public final class DollDanceClient {
    private static final Map<UUID,Performance> ACTIVE=new LinkedHashMap<>();
    private static ClientLevel world;
    private static int renderedMobs;
    private static int renderedPlayers;
    private static final class Performance {
        DancePayload state;
        Vec3 center;
        DanceSound sound;
        boolean removed;
        boolean resolved=true;
        Performance(DancePayload p){state=p;center=p.center();}
        boolean entitySource(){return !state.entity().equals(new UUID(0,0));}
        double elapsed(float partial){return world.getGameTime()+partial-state.start();}
        boolean resolve() {
            if(entitySource()) {
                for(var entity:world.entitiesForRendering()) if(entity.getUUID().equals(state.entity())) {
                    center=entity.position();return entity instanceof DollEntity && entity.isAlive()&&!entity.isRemoved();
                }
                // A source outside tracking range must not leave nearby mobs dancing.
                return false;
            }
            center=state.center();
            return world.getBlockEntity(state.block()) instanceof DollDisplayBlockEntity b && !b.copyDoll().isEmpty();
        }
    }
    public static void receive(DancePayload p) {
        var c=Minecraft.getInstance();
        if(c.level==null)return;
        if(world!=c.level){clear();world=c.level;}
        var old=ACTIVE.get(p.id());
        if(p.stop()) {if(old!=null)old.removed=true;return;}
        if(old!=null){old.state=p;return;}
        double elapsed=world.getGameTime()-p.start();
        if(elapsed< -40||elapsed>=DollDance.DURATION||ACTIVE.size()>=32)return;
        var s=new Performance(p);ACTIVE.put(p.id(),s);
        // Vanilla sound streams cannot seek. Late observers join the synchronized dance
        // silently, rather than hearing the song start at the wrong point.
        if(elapsed<20) {s.sound=new DanceSound(s);c.getSoundManager().play(s.sound);}
    }
    private static void clear() {
        for(var s:ACTIVE.values())if(s.sound!=null)Minecraft.getInstance().getSoundManager().stop(s.sound);
        ACTIVE.clear();world=null;
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e) {
        var c=Minecraft.getInstance();
        if(c.level!=world){clear();world=c.level;}
        if(world==null)return;
        for(var it=ACTIVE.values().iterator();it.hasNext();) {
            var s=it.next();
            if(s.elapsed(0)>=DollDance.DURATION) {if(s.sound!=null)c.getSoundManager().stop(s.sound);it.remove();}
            else {
                s.resolved=s.resolve();
                if(!s.resolved&&s.elapsed(0)>40&&s.sound!=null)c.getSoundManager().stop(s.sound);
                if(s.removed&&s.sound!=null)c.getSoundManager().stop(s.sound);
            }
        }
    }
    public static float elapsed(LivingEntity mob,float partial) {
        if(world==null||mob.level()!=world||!mob.isAlive()||mob.isSleeping()
            ||mob instanceof Player p&&p.isSpectator())return -1;
        for(var s:ACTIVE.values())if(!s.removed&&s.resolved&&s.elapsed(partial)>=0
            &&mob.position().distanceToSqr(s.center)<=DollDance.RADIUS*DollDance.RADIUS)
            return (float)s.elapsed(partial);
        return -1;
    }
    public static float elapsed(DollDisplayBlockEntity b,float partial) {
        if(world==null||b.getLevel()!=world)return -1;
        for(var s:ACTIVE.values())if(!s.removed&&!s.entitySource()&&s.state.block().equals(b.getBlockPos()))
            return (float)s.elapsed(partial);
        return -1;
    }
    /** Shared 140 BPM sway, two-beat alternating tumble, with gentle entry/exit. */
    public static void transform(PoseStack pose,float ticks,float pivotHeight) {
        if(ticks<0||ticks>=DollDance.DURATION)return;
        float envelope=(float)Mth.smoothstep(Mth.clamp(ticks/8,0,1))
            *(float)Mth.smoothstep(Mth.clamp((DollDance.DURATION-ticks)/12,0,1));
        float beat=ticks*(140F/60F/20F);
        float wave=Mth.sin(beat*Mth.TWO_PI);
        float phrase=beat/8;
        float phase=phrase-(float)Math.floor(phrase);
        float tumble=phase>.50F&&phase<.95F?(float)Mth.smoothstep((phase-.50F)/.45F)*360:0;
        // Full turns finish at identity before the next phrase, avoiding angle wrap snaps.
        pose.translate(0,pivotHeight+Math.abs(wave)*.08F*envelope,0);
        pose.mulPose(Axis.ZP.rotationDegrees((wave*32+tumble)*envelope));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(beat*Mth.PI)*18*envelope));
        pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(beat*Mth.PI)*22*envelope));
        pose.translate(0,-pivotHeight,0);
        bounce(pose,ticks,1);
    }
    /** Continuous compression followed by overshoot; no accumulating bone scales. */
    public static float springY(float ticks,float strength) {
        if(ticks<0||ticks>=DollDance.DURATION)return 1;
        float fade=(float)Mth.smoothstep(Mth.clamp(ticks/8,0,1))
            *(float)Mth.smoothstep(Mth.clamp((DollDance.DURATION-ticks)/12,0,1));
        float phase=ticks*(140F/60F/20F)*Mth.TWO_PI;
        return 1+strength*fade*(-.24F*Mth.cos(phase)+.09F*Mth.sin(phase*2));
    }
    public static void bounce(PoseStack pose,float ticks,float strength) {
        float y=springY(ticks,strength);
        float width=1/Mth.sqrt(y);
        // Feet anchor, approximately constant volume: squash wide, stretch narrow.
        // Whole pose includes skin overlay, armor, held items and attached dolls.
        pose.scale(width,y,width);
    }
    public static void animateMob(LivingEntity e,PoseStack pose,float partial) {
        float t=elapsed(e,partial);
        if(t>=0){
            if(e instanceof Player) {bounce(pose,t,.8F);renderedPlayers++;}
            else {transform(pose,t,e.getBbHeight()*.5F);renderedMobs++;}
        }
    }
    public static int activeCount(){return (int)ACTIVE.values().stream().filter(s->!s.removed).count();}
    public static int renderedMobs(){return renderedMobs;}
    public static int renderedPlayers(){return renderedPlayers;}
    public static boolean hasSound(){return ACTIVE.values().stream().anyMatch(s->!s.removed&&s.sound!=null
        &&Minecraft.getInstance().getSoundManager().isActive(s.sound));}
    private static final class DanceSound extends AbstractTickableSoundInstance {
        private final Performance performance;
        DanceSound(Performance p){
            super(ModEntities.DANCE.get(),SoundSource.RECORDS,net.minecraft.util.RandomSource.create());
            performance=p;volume=.85F;pitch=1;looping=false;
            x=p.center.x;y=p.center.y+.5;z=p.center.z;
        }
        @Override public void tick(){
            if(world==null||performance.removed||performance.elapsed(0)>=DollDance.DURATION){stop();return;}
            x=performance.center.x;y=performance.center.y+.5;z=performance.center.z;
        }
    }
}
