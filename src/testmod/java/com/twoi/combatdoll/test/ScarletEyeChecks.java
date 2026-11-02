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

/** The same stationary camera must show an open eye, a narrower bright sclera, and reopening. */
final class ScarletEyeChecks {
    private static String current;
    private static double open,closed,reopened;
    private static int lastAge;
    private static boolean checked;
    private static final List<CompletableFuture<Void>> writes=new ArrayList<>();
    private ScarletEyeChecks() { }
    static void observe(Minecraft mc,String label,float age)throws Exception{
        if(!label.equals(current)){current=label;open=0;closed=1;reopened=0;lastAge=-1;checked=false;}
        int tick=(int)age;
        if(tick!=lastAge){
            lastAge=tick;
            if(tick>=96&&tick<=99)open=Math.max(open,sample(mc,label,"open",tick));
            if(tick>=147&&tick<=150)closed=Math.min(closed,sample(mc,label,"blink",tick));
            if(tick>=164&&tick<=167)reopened=Math.max(reopened,sample(mc,label,"reopened",tick));
        }
        if(age>=168&&!checked){
            checked=true;
            check(open>.03,"stationary eye camera sees a substantial warm sclera before blinking: "+open);
            check(closed<open*.65,"visible warm eye area contracts during blink: open="+open+", closed="+closed);
            check(reopened>closed*1.4&&reopened>open*.50,"warm eye area returns after blink: open="+open+", closed="+closed+", reopened="+reopened);
            LogUtils.getLogger().info("SCARLET EYE BLINK PIXELS PASSED: {} open={} closed={} reopened={}; stationary right-sclera ROI, same FOV and camera",label,open,closed,reopened);
        }
    }
    static void awaitWrites(){CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();}
    private static double sample(Minecraft mc,String label,String state,int age)throws Exception{
        var path=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(path);
        NativeImage image=FramebufferCapture.take(mc);
        int bright=0,count=0;
        // The fixture aims directly at the eye with FOV 100. Sample the right
        // sclera, clear of the dark pupil and the six-point frame outside it.
        for(int y=(int)(image.getHeight()*.46);y<image.getHeight()*.54;y++)
            for(int x=(int)(image.getWidth()*.58);x<image.getWidth()*.66;x++){
                int pixel=image.getPixelRGBA(x,y),r=FastColor.ABGR32.red(pixel),g=FastColor.ABGR32.green(pixel),b=FastColor.ABGR32.blue(pixel);
                if(r>55&&g>28&&b>25&&g>r*.32&&b>r*.30)bright++;count++;
            }
        check(count>100,"eye ROI samples actual framebuffer pixels");
        double fraction=(double)bright/count;
        writes.add(CompletableFuture.runAsync(()->{try(image){image.writeToFile(path.resolve(label+"-eye-area-"+state+"-"+age+".png"));}catch(Exception error){throw new CompletionException(error);}},net.minecraft.Util.ioPool()));
        LogUtils.getLogger().info("SCARLET EYE PIXEL SAMPLE: {} age={} state={} warmScleraFraction={}",label,age,state,fraction);
        return fraction;
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
