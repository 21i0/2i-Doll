package com.twoi.combatdoll.test;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FastColor;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Pixel evidence from a terrain-facing camera, independent of the shader's own masks. */
final class GroundSurfaceChecks {
    private static final List<CompletableFuture<Void>> writes=new ArrayList<>();
    private static Sample baseline;
    private GroundSurfaceChecks() { }

    static void baseline(Minecraft mc,String label)throws Exception {
        baseline=capture(mc,label,"floor-before");
        check(baseline.markers>.08,"foreground magenta/yellow checker is visible: "+baseline);
        check(baseline.neutral>.12,"ordinary stone is visible alongside checker: "+baseline);
    }

    static void active(Minecraft mc,String label,boolean water)throws Exception {
        Sample active=capture(mc,label,"floor-active");
        check(baseline!=null,"baseline sampled before takeover");
        check(active.markers<baseline.markers*.24,
                "takeover must replace coloured block texture, not merely overlay rings: baseline="+baseline+", active="+active);
        // The STAFF mirror can reflect pale sky/wall geometry into the terrain
        // ROI, raising neutral pixels even while the sampled floor itself is
        // strongly blue. Leave margin for that scene composition, while keeping
        // the independent marker-removal and water-colour checks below: a normal
        // floor/reload frame retains markers, and a white frame has no blue cast.
        check(active.neutral<.25,"floor is a material replacement, not a reload/white-screen frame: "+active);
        check(water?active.blue>.20:active.red>.25,
                "floor has the selected water/cracked-stone colour, not only hidden markers: "+active);
        LogUtils.getLogger().info("FLOOR TAKEOVER PIXELS PASSED: {} style={} markersBefore={} markersActive={} blueFraction={} greenFraction={} redFraction={} meanRgb={}/{}/{} neutralBefore={} neutralActive={}",
                label,water?"water":"scarlet-cracked-stone",baseline.markers,active.markers,active.blue,active.green,active.red,
                active.r,active.g,active.b,baseline.neutral,active.neutral);
    }

    static void after(Minecraft mc,String label)throws Exception {
        Sample after=capture(mc,label,"floor-after");
        check(baseline!=null&&after.markers>baseline.markers*.60,
                "original block colours return when takeover ends: baseline="+baseline+", after="+after);
        LogUtils.getLogger().info("FLOOR RESTORE PIXELS PASSED: {} markersBefore={} markersAfter={}",label,baseline.markers,after.markers);
    }

    static void awaitWrites(){CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();}

    private static Sample capture(Minecraft mc,String label,String state)throws Exception {
        var path=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(path);
        NativeImage image=FramebufferCapture.take(mc);
        Sample sample;
        try {sample=measure(image);}catch(Throwable failure){image.close();throw failure;}
        writes.add(CompletableFuture.runAsync(()->{
            try(image){image.writeToFile(path.resolve(label+"-"+state+".png"));}
            catch(Exception failure){throw new CompletionException(failure);}
        },net.minecraft.Util.ioPool()));
        LogUtils.getLogger().info("FLOOR PIXEL SAMPLE: {} {} {}",label,state,sample);
        return sample;
    }

    private static Sample measure(NativeImage image){
        int count=0,markers=0,blue=0,green=0,red=0,neutral=0;long redSum=0,greenSum=0,blueSum=0;
        // +20-degree camera: this lower rectangle is entirely platform top. Ignore
        // the outermost edge and held-item region, and sample every other pixel.
        for(int y=(int)(image.getHeight()*.70);y<(int)(image.getHeight()*.95);y+=2)
            for(int x=(int)(image.getWidth()*.06);x<(int)(image.getWidth()*.88);x+=2){
                int pixel=image.getPixelRGBA(x,y);
                int r=FastColor.ABGR32.red(pixel),g=FastColor.ABGR32.green(pixel),b=FastColor.ABGR32.blue(pixel);
                // The reference marker really is violet-magenta; scarlet material
                // has a much weaker blue channel and must not count as the marker.
                boolean magenta=r>35&&b>30&&r>g*1.65&&b>g*1.35&&r>b*.92&&b>r*.65;
                boolean yellow=r>45&&g>35&&g>r*.70&&r>g*.88&&b<Math.min(r,g)*.58;
                if(magenta||yellow)markers++;
                if(b>g*1.20&&b>r*1.05&&b>18)blue++;
                if(g>b*1.20&&g>r*1.05&&g>18)green++;
                if(r>g*1.45&&r>b*1.45&&r>18)red++;
                if(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<18&&r>25)neutral++;
                redSum+=r;greenSum+=g;blueSum+=b;count++;
            }
        check(count>1000,"terrain ROI has enough real framebuffer pixels");
        return new Sample((double)markers/count,(double)blue/count,(double)green/count,(double)red/count,(double)neutral/count,
                (double)redSum/count,(double)greenSum/count,(double)blueSum/count);
    }

    private record Sample(double markers,double blue,double green,double red,double neutral,double r,double g,double b) { }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
