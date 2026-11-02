package com.twoi.combatdoll.test;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-only readback pacing; never changes either production combat clock.
 * GPU readback can stall the client while the integrated server keeps ticking.
 * The short server-queue barrier covers only that readback, not PNG encoding,
 * disk writes, image analysis or any other client work.
 */
final class FramebufferCapture {
    private static final int TIMEOUT_SECONDS=3;
    private FramebufferCapture(){ }

    static NativeImage take(Minecraft mc){
        RenderSystem.assertOnRenderThread();
        var server=mc.getSingleplayerServer();
        if(server==null)return Screenshot.takeScreenshot(mc.getMainRenderTarget());
        if(server.isSameThread())throw new AssertionError("Framebuffer capture must never block the integrated server thread");
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        var expired=new AtomicBoolean();
        try {
            server.execute(()->{
                entered.countDown();
                try {
                    if(!release.await(TIMEOUT_SECONDS,TimeUnit.SECONDS))expired.set(true);
                }catch(InterruptedException interrupted){
                    expired.set(true);Thread.currentThread().interrupt();
                }
            });
            if(!entered.await(TIMEOUT_SECONDS,TimeUnit.SECONDS))
                throw new AssertionError("Integrated server did not enter the framebuffer readback barrier within 3 seconds");
            long started=System.nanoTime();
            NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget());
            if(expired.get()||System.nanoTime()-started>=TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)){
                image.close();
                throw new AssertionError("Framebuffer readback exceeded the 3-second integrated-server barrier");
            }
            return image;
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting the framebuffer readback barrier",interrupted);
        }finally {
            // Also releases a task that starts after a queue-entry timeout.
            release.countDown();
        }
    }
}
