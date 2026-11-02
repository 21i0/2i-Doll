package com.twoi.combatdoll.test;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.client.TwoIArcanaPost;
import com.twoi.combatdoll.client.TwoIArcanaRenderer;
import com.twoi.combatdoll.combat.TwoIArcanaTimeline;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Original rendered frames and their actual mesh/camera/timeline measurements.
 * Screenshots remain the visual evidence; a positive mesh counter alone does
 * not prove attractive mountains or that a tip visibly reaches its floor.
 */
final class BookSlamVisualChecks {
    private static final Map<String,List<String>> rows=new HashMap<>();
    private static final Map<String,Integer> lastTick=new HashMap<>();
    private static final Map<String,Integer> waveMask=new HashMap<>();
    private static final Set<String> shots=new HashSet<>();
    private static final Set<String> emptyPortalWitness=new HashSet<>(),emergingPortalWitness=new HashSet<>();
    private static final List<CompletableFuture<Void>> writes=new ArrayList<>();
    private BookSlamVisualChecks(){ }

    static void observe(Minecraft mc,String label,float age,boolean player)throws Exception {
        // Hold each mountain view for four actual ticks before its original
        // screenshot. Restore the eye-facing view well before pixel ROI reads.
        if(age>=112&&age<120)camera(mc,180,-12,player);
        else if(age>=120&&age<128)camera(mc,155,-12,player);
        else if(age>=128&&age<136)camera(mc,205,-12,player);
        else if(age>=136&&age<144||age>=151&&age<160||age>=168&&age<198)
            camera(mc,180,-16,player);
        else if(age>=112)camera(mc,180,player?-49:-34,player);

        captureWindow(mc,label,"mountain-front",age,116,119);
        captureWindow(mc,label,"mountain-left-25",age,124,127);
        captureWindow(mc,label,"mountain-right-25",age,132,135);
        // The revised BOOK presentation must establish a separate dark portal
        // before any tube emerges. These are original framebuffer samples, not
        // reconstructed meshes or labels added after the fact.
        captureWindow(mc,label,"portal-opening",age,61,65);
        captureWindow(mc,label,"portal-empty-open",age,68,75);
        captureWindow(mc,label,"portal-emergence",age,80,89);
        for(int i=0;i<TwoIArcanaTimeline.bookSlamCount();i++){
            int hit=TwoIArcanaTimeline.bookSlamTick(i);
            captureWindow(mc,label,"slam-"+(i+1)+"-windup",age,hit-4,hit-1);
            captureWindow(mc,label,"slam-"+(i+1)+"-contact",age,hit+1,hit+4);
            // Rebound lasts until just before the next scheduled wave. A
            // framebuffer read can stall the render thread while the server
            // continues; do not discard a valid late rebound frame merely
            // because the earlier three-tick subwindow was skipped.
            captureWindow(mc,label,"slam-"+(i+1)+"-rebound",age,hit+5,
                    hit+(i==TwoIArcanaTimeline.bookSlamCount()-1?17:11));
        }

        int tick=(int)age;
        if(lastTick.getOrDefault(label,-1)==tick)return;
        lastTick.put(label,tick);
        int count=TwoIArcanaRenderer.lastTentacleCount();
        int tubeVertices=TwoIArcanaRenderer.lastTentacleVertices();
        int portals=TwoIArcanaRenderer.lastPortalCount(),portalVertices=TwoIArcanaRenderer.lastPortalVertices();
        int openingOnly=TwoIArcanaRenderer.lastPortalOpeningOnlyCount();
        float eyeGap=TwoIArcanaRenderer.lastPortalMinEyeGap(),rootError=TwoIArcanaRenderer.lastPortalMaxRootError();
        float rootRadiusRatio=TwoIArcanaRenderer.lastPortalMaxRootRadiusRatio();
        int actualWave=TwoIArcanaRenderer.lastBookSlamIndex();
        int expectedWave=TwoIArcanaTimeline.bookSlamAt(age);
        rows.computeIfAbsent(label,key->new ArrayList<>()).add(String.format(Locale.ROOT,
                "%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%.7f,%d,%d,%d,%d,%d,%d,%d,%d,%.7f,%.7f,%.7f",
                age,mc.player.getYRot(),mc.player.getXRot(),mc.gameRenderer.getMainCamera().getYRot(),mc.gameRenderer.getMainCamera().getXRot(),mc.options.fov().get(),expectedWave,actualWave,
                TwoIArcanaTimeline.bookSlamStrength(age),count,tubeVertices,TwoIArcanaRenderer.lastVertices(),
                TwoIArcanaPost.processedFrames(),TwoIArcanaRenderer.floorFrames(),portals,portalVertices,openingOnly,eyeGap,rootError,rootRadiusRatio));
        if(age>=68&&age<75){
            check(portals>0&&portalVertices>0&&openingOnly==portals&&count==0&&tubeVertices==0,
                    "separate visible portals open before any real tube emerges "+label+" age="+age+" portals="+portals+" tubes="+count);
            emptyPortalWitness.add(label);
        }
        if(age>=86&&age<96&&portals>0&&count>0)emergingPortalWitness.add(label);
        if(age>=96&&age<198){
            int expectedPortals=TwoIArcanaRenderer.quality()*2+2,expectedCount=expectedPortals*2;
            check(count==expectedCount,"actual six-sided tube count "+label+" age="+age+" count="+count+" expected="+expectedCount);
            check(portals==expectedPortals&&portalVertices>0&&portalVertices<=portals*1600,
                    "finite independently emitted dark portal geometry "+label+" age="+age+" portals="+portals+" vertices="+portalVertices);
            check(Float.isFinite(eyeGap)&&eyeGap>=14&&Float.isFinite(rootError)&&rootError<.001F
                            &&Float.isFinite(rootRadiusRatio)&&rootRadiusRatio<.36F,
                    "portal mouths remain detached from the eye and contain their behind-mouth tube roots "+label
                            +" age="+age+" eyeGap="+eyeGap+" rootPlaneError="+rootError+" rootRadiusRatio="+rootRadiusRatio);
            // Each tube emits twelve sections of six quads plus a closed root.
            // A short-lived,
            // eight-segment landing ring adds a bounded number of quad vertices.
            check(tubeVertices>=count*288&&tubeVertices<=count*320,
                    "real tube geometry stays within mesh/ring budget "+label+" age="+age+" vertices="+tubeVertices);
            check(TwoIArcanaRenderer.lastVertices()<80000,"BOOK complete frame geometry remains bounded");
        }
        if(expectedWave>=0&&age-TwoIArcanaTimeline.bookSlamTick(expectedWave)>=1
                &&age-TwoIArcanaTimeline.bookSlamTick(expectedWave)<5){
            check(actualWave==expectedWave,"rendered slam follows shared server milestone "+label+" age="+age
                    +" actual="+actualWave+" expected="+expectedWave);
            waveMask.merge(label,1<<expectedWave,(a,b)->a|b);
        }
    }

    static void finish(Minecraft mc,String label)throws Exception {
        // Preserve the observed ages/camera/mesh data even if a required frame
        // is missing. Failed sampling is evidence, not a reason to lose CSV.
        List<String> samples=rows.getOrDefault(label,List.of());
        var directory=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(directory);
        List<String> output=new ArrayList<>();
        output.add("age,requested_camera_yaw,requested_camera_pitch,last_rendered_camera_yaw,last_rendered_camera_pitch,camera_fov,shared_slam_index,rendered_slam_index,shared_slam_strength,tentacles,tube_and_landing_vertices,total_vertices,post_frames,floor_frames,portals,portal_vertices,opening_only_portals,min_portal_eye_gap,max_root_plane_error,max_root_radius_ratio");
        output.addAll(samples);
        Files.write(directory.resolve(label+"-slam-frame-states.csv"),output,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        awaitWrites();
        check(waveMask.getOrDefault(label,0)==(1<<TwoIArcanaTimeline.bookSlamCount())-1,
                "all eight real rendered slam milestones observed "+label+" mask="+waveMask.get(label));
        for(int i=1;i<=TwoIArcanaTimeline.bookSlamCount();i++)for(String stage:List.of("windup","contact","rebound"))
            check(shots.contains(label+"-slam-"+i+"-"+stage),"original framebuffer for every slam stage "+label+"/"+i+"/"+stage);
        for(String angle:List.of("front","left-25","right-25"))
            check(shots.contains(label+"-mountain-"+angle),"mountain angle captured "+label+"/"+angle);
        for(String stage:List.of("opening","empty-open","emergence"))
            check(shots.contains(label+"-portal-"+stage),"portal stage captured "+label+"/"+stage);
        check(emptyPortalWitness.contains(label)&&emergingPortalWitness.contains(label),
                "actual portal-only interval precedes tube emergence "+label);
        check(samples.size()>100,"BOOK timeline contains enough actual rendered samples");
        LogUtils.getLogger().info("BOOK SLAM VISUAL CHECKS PASSED: {} all eight real shared/rendered milestones, 24 windup/contact/rebound original frames, opening/empty/emergence portal stages with actual portal-only witness, detached portal mouths and behind-mouth contained tube roots, front/left/right mountain views, emitted six-sided tube counts and bounded mesh; {} actual tick samples; visual portal occlusion/contact/mountain quality require paired PNG review",label,samples.size());
    }

    static void awaitWrites(){CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();}
    private static void camera(Minecraft mc,float yaw,float pitch,boolean player){
        mc.options.hideGui=!player;mc.options.fov().set(100);
        mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
    }
    private static void captureWindow(Minecraft mc,String label,String event,float age,int from,int to)throws Exception{
        if(age<from||age>=to||!shots.add(label+"-"+event))return;
        var directory=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(directory);
        NativeImage image=FramebufferCapture.take(mc);
        String filename=label+"-"+event+"-age-"+String.format(Locale.ROOT,"%07.3f",age)+".png";
        writes.add(CompletableFuture.runAsync(()->{try(image){image.writeToFile(directory.resolve(filename));}
            catch(Exception error){throw new CompletionException(error);}},net.minecraft.Util.ioPool()));
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
