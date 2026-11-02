package com.twoi.combatdoll.test;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.twoi.combatdoll.TwoICombatDollMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Real ItemRenderer/BEWLR frames. Angle changes are visual evidence, not proof of parallax by themselves. */
final class ScarletBookRenderChecks extends Screen {
    private static final List<CompletableFuture<Void>> writes=new ArrayList<>();
    private static boolean done;
    private static Throwable failure;
    private static int standaloneTicks;
    private static boolean standaloneStarted,standaloneStopped;
    private int frames;
    private final ItemStack book=new ItemStack(TwoICombatDollMod.BOOK.get());
    private final ItemStack staff=new ItemStack(TwoICombatDollMod.STAFF.get());

    private ScarletBookRenderChecks(){super(Component.literal("Scarlet grimoire rendering evidence"));}
    static void begin(Minecraft mc){done=false;failure=null;mc.options.hideGui=false;mc.setScreen(new ScarletBookRenderChecks());}
    static boolean finished(){if(failure!=null)throw new AssertionError("scarlet book framebuffer checks",failure);return done;}
    static void awaitWrites(){CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();}
    static void standaloneTick(Minecraft mc){
        if(standaloneStopped)return;
        try{
            check(++standaloneTicks<1800,"book-only client timed out");
            if(mc.getOverlay()!=null)return;
            if(!standaloneStarted){
                if(mc.screen==null)return;
                standaloneStarted=true;mc.options.guiScale().set(1);mc.options.pauseOnLostFocus=false;mc.resizeDisplay();begin(mc);return;
            }
            if(finished()){
                awaitWrites();standaloneStopped=true;
                LogUtils.getLogger().info("SCARLET BOOK ONLY CHECKS PASSED: real GUI item/BEWLR at two model angles, red cover pixels and saved closeups; inspect images for layered parallax");
                LogUtils.getLogger().info("ASTRAL STAFF GUI CHECKS PASSED: same real GUI ItemRenderer/BEWLR path, both viewpoints, visible gold/ivory/violet material pixels, OpenGL state and saved closeups");mc.stop();
            }
        }catch(Throwable error){standaloneStopped=true;LogUtils.getLogger().error("SCARLET BOOK ONLY CHECKS FAILED",error);mc.stop();}
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial){
        if(done)return;
        try{
            gui.fill(0,0,width,height,0xFF17151C);
            if(frames>=64){renderStaffContext(gui);return;}
            boolean staffRound=frames>=32;
            int localFrame=frames%32;
            float angle=localFrame<16?-22:22;
            gui.drawCenteredString(font,(staffRound?"Astral edict":"Scarlet grimoire")+" / real GUI item renderer / yaw "+angle,width/2,24,0xFFF2D9D9);
            var pose=gui.pose();pose.pushPose();
            // Match GuiGraphics' actual GUI ItemRenderer path, placing the z=150
            // anchor BEFORE magnification. Scaling/rotating GuiGraphics.renderItem
            // itself also transformed its internal 150-depth translation and
            // clipped the item out of the GUI projection at large sizes.
            pose.translate(width*.5,height*.54,150);
            // Keep the existing book framing exactly. The slender staff uses
            // more of the screen so its physical frame/jewel can be inspected.
            float size=Math.min(width,height)*(staffRound?.83F:.64F);
            pose.scale(size,-size,size);
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            ItemStack item=staffRound?staff:book;
            var model=minecraft.getItemRenderer().getModel(item,minecraft.level,minecraft.player,0);
            boolean flat=!model.usesBlockLight();
            if(flat)Lighting.setupForFlatItems();
            minecraft.getItemRenderer().render(item,ItemDisplayContext.GUI,false,pose,gui.bufferSource(),
                    15728880,OverlayTexture.NO_OVERLAY,model);
            gui.flush();if(flat)Lighting.setupFor3DItems();
            pose.popPose();gui.flush();
            if(localFrame==12||localFrame==28){
                String viewpoint=localFrame==12?"left":"right";
                if(staffRound)captureStaff(minecraft,viewpoint);else capture(minecraft,viewpoint);
            }
            if(++frames>=64){
                LogUtils.getLogger().info("ASTRAL STAFF GUI FRAMEBUFFER CHECKS PASSED: two real ItemRenderer viewpoints after unchanged BOOK checks; inspect full-size screenshots for frame/jewel shape and clipping; proceeding to actual display contexts");
            }
        }catch(Throwable error){failure=error;done=true;}
    }
    private void renderStaffContext(GuiGraphics gui)throws Exception{
        ItemDisplayContext[] contexts=ItemDisplayContext.values();
        int index=(frames-64)/4,subframe=(frames-64)%4;
        ItemDisplayContext context=contexts[index];
        gui.drawCenteredString(font,"Astral Edict / actual ItemRenderer context: "+context.name(),width/2,20,0xFFF0E8FF);
        gui.drawCenteredString(font,"Context geometry review; real player placement is checked in the world fixture",width/2,38,0xFFC8C2D5);
        gui.drawString(font,"16px GUI slot",width-112,48,0xFFC8C2D5);
        gui.fill(width-47,67,width-21,93,0xFF342D41);
        gui.renderItem(staff,width-42,72);
        var pose=gui.pose();pose.pushPose();
        pose.translate(width*.5,height*.46,150);
        // The longer shaft needs a wider review framing. Keep the actual item
        // context transform and all clipping/pixel assertions unchanged.
        float size=Math.min(width,height)*.46F;pose.scale(size,-size,size);
        var model=minecraft.getItemRenderer().getModel(staff,minecraft.level,minecraft.player,0);
        boolean flat=!model.usesBlockLight();if(flat)Lighting.setupForFlatItems();
        minecraft.getItemRenderer().render(staff,context,false,pose,gui.bufferSource(),
                15728880,OverlayTexture.NO_OVERLAY,model);
        gui.flush();if(flat)Lighting.setupFor3DItems();pose.popPose();gui.flush();
        if(subframe==3){
            var path=minecraft.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(path);
            int visible=0,total=0,edge=0;
            try(NativeImage image=FramebufferCapture.take(minecraft)){
                image.writeToFile(path.resolve("astral-staff-context-"+context.name().toLowerCase(java.util.Locale.ROOT)+".png"));
                // Ignore fixture titles/16px thumbnail. A broad central viewport
                // detects an invisible model and obvious full-screen clipping.
                int x0=image.getWidth()/6,x1=image.getWidth()*5/6;
                int y0=image.getHeight()/8,y1=image.getHeight()*39/40;
                for(int y=y0;y<y1;y+=2)for(int x=x0;x<x1;x+=2){
                    int pixel=image.getPixelRGBA(x,y),r=FastColor.ABGR32.red(pixel),g=FastColor.ABGR32.green(pixel),b=FastColor.ABGR32.blue(pixel);
                    boolean material=Math.max(Math.abs(r-23),Math.max(Math.abs(g-21),Math.abs(b-28)))>25;
                    if(material){visible++;if(x<x0+4||x>x1-5||y<y0+4||y>y1-5)edge++;}total++;
                }
            }
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"STAFF display context has valid GL state: "+context);
            check(visible>total*.003&&visible<total*.55,"actual STAFF display context is visibly bounded: "+context+" visible="+visible+"/"+total);
            check(edge<25,"STAFF display context touches the review viewport boundary: "+context+" edge="+edge);
            LogUtils.getLogger().info("ASTRAL STAFF DISPLAY CONTEXT PASSED: context={} visible={}/{} edgePixels={}; actual ItemRenderer framebuffer saved, not proof of player bone placement",context,visible,total,edge);
        }
        if(++frames>=64+contexts.length*4){
            LogUtils.getLogger().info("ASTRAL STAFF ALL DISPLAY CONTEXT CHECKS PASSED: {} actual ItemRenderer contexts and real 16px GUI thumbnail; no reconstructed geometry; inspect saved frames for proportions",contexts.length);
            done=true;
        }
    }
    private static void capture(Minecraft mc,String angle)throws Exception{
        int glError=GL11.glGetError();
        var path=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(path);
        double fraction;
        try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            // Save BEFORE assertions, synchronously: even a failed fixture must
            // leave the exact frame that caused the failure before mc.stop().
            image.writeToFile(path.resolve("scarlet-book-gui-"+angle+".png"));
            int red=0,count=0;
            for(int y=image.getHeight()/5;y<image.getHeight()*9/10;y+=2)
                for(int x=image.getWidth()/5;x<image.getWidth()*4/5;x+=2){
                    int pixel=image.getPixelRGBA(x,y),r=FastColor.ABGR32.red(pixel),g=FastColor.ABGR32.green(pixel),b=FastColor.ABGR32.blue(pixel);
                    if(r>35&&r>g*1.45&&r>b*1.45)red++;count++;
                }
            fraction=(double)red/count;
        }
        LogUtils.getLogger().info("SCARLET BOOK GUI PIXEL SAMPLE angle={} redFraction={} glError={}; evidence saved before assertions",angle,fraction,glError);
        check(glError==GL11.GL_NO_ERROR,"GUI book has valid OpenGL state: "+glError);
        check(fraction>.01,"actual large book image contains red cover material: fraction="+fraction);
        LogUtils.getLogger().info("SCARLET BOOK GUI MATERIAL PASSED angle={} redFraction={}; actual layered-cover viewpoint image saved for manual parallax review",angle,fraction);
    }
    private static void captureStaff(Minecraft mc,String angle)throws Exception{
        int glError=GL11.glGetError();
        var path=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(path);
        int visible=0,gold=0,ivory=0,violet=0,count=0;
        try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            // Preserve the exact rendered framebuffer even when a material or
            // visibility assertion fails. No independently drawn model is used.
            image.writeToFile(path.resolve("astral-staff-gui-"+angle+".png"));
            for(int y=image.getHeight()/10;y<image.getHeight()*19/20;y+=2)
                for(int x=image.getWidth()/5;x<image.getWidth()*4/5;x+=2){
                    int pixel=image.getPixelRGBA(x,y),r=FastColor.ABGR32.red(pixel),g=FastColor.ABGR32.green(pixel),b=FastColor.ABGR32.blue(pixel);
                    if(Math.max(Math.abs(r-23),Math.max(Math.abs(g-21),Math.abs(b-28)))>25)visible++;
                    if(r>95&&g>65&&r>g*1.12&&g>b*1.30)gold++;
                    if(r>130&&g>130&&b>100&&r>b&&r<g*1.17)ivory++;
                    if(b>45&&b>r*1.15&&r>20&&b>g*1.12)violet++;
                    count++;
                }
        }
        LogUtils.getLogger().info("ASTRAL STAFF GUI PIXEL SAMPLE angle={} visible={}/{} gold={} ivory={} violet={} glError={}; evidence saved before assertions",
                angle,visible,count,gold,ivory,violet,glError);
        check(glError==GL11.GL_NO_ERROR,"GUI staff has valid OpenGL state: "+glError);
        check(visible>count*.012&&visible<count*.45,"actual large staff image occupies a visible bounded area: "+visible+"/"+count);
        check(gold>count*.002,"actual staff image contains gold frame/wrap pixels: "+gold+"/"+count);
        check(ivory>count*.004,"actual staff image contains ivory shaft/frame pixels: "+ivory+"/"+count);
        check(violet>count*.0005,"actual staff image contains violet fitting/nebula pixels: "+violet+"/"+count);
        LogUtils.getLogger().info("ASTRAL STAFF GUI MATERIAL PASSED angle={} visibleFraction={} gold={} ivory={} violet={}; saved true GUI item viewpoint for manual silhouette review",
                angle,(double)visible/count,gold,ivory,violet);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
