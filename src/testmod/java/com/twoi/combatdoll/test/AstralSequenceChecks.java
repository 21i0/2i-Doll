package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.client.ClientTimeStop;
import com.twoi.combatdoll.client.BlackHoleMotion;
import com.twoi.combatdoll.client.TwoIArcanaPost;
import com.twoi.combatdoll.client.TwoIArcanaRenderer;
import com.twoi.combatdoll.combat.CombatActors;
import com.twoi.combatdoll.combat.TwoIArcanaTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Records real frame/authoritative stage pairs for visual motion review.
 *
 * This deliberately does not infer a successful reflection or reverse orbit
 * merely from the presence of a renderer API. The original framebuffer images
 * and their matching camera/stage records remain the primary visual evidence.
 */
final class AstralSequenceChecks {
    private static final Map<String,List<String>> rows=new HashMap<>();
    private static final Map<String,Integer> lastSample=new HashMap<>();
    private AstralSequenceChecks() { }

    static void observe(Minecraft mc,String label,LivingEntity actor,float age) {
        // The client renderer deliberately drops its bounded motion cache at
        // the exact duration boundary. The client-tick observer can run just
        // before that render cleanup, so do not demand a stale frame sample.
        if(age>=TwoIArcanaTimeline.DURATION)return;
        int sample=(int)age/4;
        if(age<0||lastSample.getOrDefault(label,-1)==sample)return;
        lastSample.put(label,sample);
        var state=CombatActors.state(actor);
        var target=state.target(actor);var hole=state.blackHole(actor);var anchor=state.stageAnchor(actor);
        var motion=BlackHoleMotion.snapshot(actor);
        // The renderer refreshes its real caster inventory every five ticks;
        // the first synchronized start can precede that first rendered frame.
        check(motion!=null||age<12,"real STAFF renderer sampled inertial scene: "+label+" age="+age);
        if(motion==null)return;
        check(motion.trailPoints()<=24&&BlackHoleMotion.trailPointCount()<=TwoIArcanaRenderer.quality()*2+1,
                "actual frame has bounded inertial trail geometry: "+label);
        if(age>=96&&age<162){
            check(motion.position().equals(hole)&&motion.velocity().lengthSqr()==0&&!motion.settling(),
                    "real stopped-stage black hole is exactly fixed after release: "+label+" age="+age);
            check(BlackHoleMotion.trailPointCount()==0,"inertia trail is gone during real fixed celestial stage: "+label+" age="+age);
        }
        rows.computeIfAbsent(label,key->new ArrayList<>()).add(String.format(Locale.ROOT,
                "%.4f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%s,%.4f,%.4f,%.4f,%s,%d,%d,%.7f,%.7f,%.7f,%.7f,%s,%d,%d",
                age,target.x,target.y,target.z,hole.x,hole.y,hole.z,anchor.x,anchor.y,anchor.z,state.charging(),state.chargeExtra(actor,0),mc.player.getYRot(),mc.player.getXRot(),
                ClientTimeStop.active(),TwoIArcanaPost.processedFrames(),TwoIArcanaRenderer.lastVertices(),
                motion.position().x,motion.position().y,motion.position().z,motion.velocity().length(),motion.settling(),motion.trailPoints(),BlackHoleMotion.trailPointCount()));
    }

    static void finish(Minecraft mc,String label)throws Exception {
        List<String> samples=rows.get(label);
        check(samples!=null&&samples.size()>30,"staff sequence has enough real frame samples: "+label);
        check(hasAge(samples,92,101)&&hasAge(samples,113,123),"actual frozen-stage frames were observed: "+label);
        check(hasAge(samples,185,192)&&hasAge(samples,197,204),"actual reverse-stage frames were observed without a reload gap: "+label);
        Path directory=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(directory);
        List<String> output=new ArrayList<>();
        output.add("age,target_x,target_y,target_z,hole_x,hole_y,hole_z,anchor_x,anchor_y,anchor_z,charging,charge_extra,camera_yaw,camera_pitch,client_time_stop,post_frames,vertices,presented_hole_x,presented_hole_y,presented_hole_z,presented_speed,settling,trail_history_points,uploaded_trail_points");
        output.addAll(samples);
        Files.write(directory.resolve(label+"-astral-frame-states.csv"),output,StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE_NEW);
        LogUtils.getLogger().info("ASTRAL FRAME EVIDENCE SAVED: {} {} samples; visual orbit, stationary interval, reverse and mirror assessment require paired screenshots",label,samples.size());
    }

    private static boolean hasAge(List<String> samples,float from,float to){
        return samples.stream().mapToDouble(row->Double.parseDouble(row.substring(0,row.indexOf(',')))).anyMatch(age->age>=from&&age<to);
    }

    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
