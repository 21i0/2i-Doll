package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.client.TwoIArcanaPost;
import com.twoi.combatdoll.client.TwoIArcanaRenderer;
import com.twoi.combatdoll.client.compat.LegacyStarfieldVisuals;
import com.twoi.combatdoll.companion.client.render.TwoIStarfieldEffects;
import com.twoi.combatdoll.companion.content.entity.TwoIStarfieldData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Actual creative catalog and optional original garden visits, after the native standalone visit. */
final class StandaloneOptionalClientChecks {
    private static int phase,ticks,postBefore,wait;
    private static boolean done;
    private static final AtomicInteger ready=new AtomicInteger();
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static volatile Vec3 origin;
    private static volatile BlockPos center;
    private StandaloneOptionalClientChecks(){}

    static boolean tick(Minecraft mc)throws Exception{
        if(done)return true;
        check(++ticks<1200,"optional garden timeout phase="+phase);
        if(failure.get()!=null)throw new AssertionError("optional garden server action",failure.get());
        if(mc.getOverlay()!=null)return false;
        if(phase==0){
            if(ModList.get().isLoaded("jei")&&!checkJeiCatalog())return false;
            CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(),true,mc.level.registryAccess());
            var oldKey=ResourceLocation.parse("useless_stretcher:two_i_doll");
            boolean legacyItem=BuiltInRegistries.ITEM.containsKey(oldKey);
            var old=BuiltInRegistries.ITEM.get(oldKey);
            for(var tab:CreativeModeTabs.allTabs()){
                for(var stack:tab.getDisplayItems())check(!legacyItem||!stack.is(old),"old doll absent from actual parent catalog");
                for(var stack:tab.getSearchTabDisplayItems())check(!legacyItem||!stack.is(old),"old doll absent from actual search catalog");
            }
            long canonical=CreativeModeTabs.searchTab().getDisplayItems().stream().filter(stack->stack.is(TwoICombatDollMod.DOLL_ITEM.get())).count();
            check(canonical==1,"actual creative search contains exactly one canonical doll, count="+canonical);
            LogUtils.getLogger().info("STANDALONE CREATIVE CATALOG CHECKS PASSED: actual rebuilt parent/search catalogs contain one canonical 2i, no legacy 2i");
            if(!ModList.get().isLoaded("useless_stretcher")){done=true;return true;}
            postBefore=TwoIArcanaPost.processedFrames();
            server(mc,player->{
                origin=player.position();
                check(player.level().dimension().equals(Level.OVERWORLD),"legacy test starts after native return");
                check(TwoIStarfieldData.get(player.getServer()).rewardsRemaining(player.getUUID())==0,"native lifetime budget exhausted before old visit");
                Class<?> service=service();
                check((boolean)service.getMethod("sendPlayerToStarfield",ServerPlayer.class,boolean.class).invoke(null,player,false),"original service enters original garden");
                var oldData=Class.forName("com.sorrowmist.useless.stretcher.content.entity.TwoIStarfieldData");
                Object data=oldData.getMethod("get",MinecraftServer.class).invoke(null,player.getServer());
                int slot=(int)oldData.getMethod("slotFor",java.util.UUID.class).invoke(data,player.getUUID());
                var area=service.getDeclaredMethod("areaCenter",int.class);area.setAccessible(true);center=(BlockPos)area.invoke(null,slot);
                check((int)oldData.getMethod("rewardsRemaining",java.util.UUID.class).invoke(data,player.getUUID())==0,"original garden reuses exhausted native budget");
                ready.set(1);
            });phase=1;return false;
        }
        if(phase==1){
            if(ready.get()!=1||!LegacyStarfieldVisuals.isGarden(mc.level))return false;
            check(TwoIStarfieldEffects.showAge(0)<0,"native show clock stays inactive in original garden");
            server(mc,player->{player.connection.teleport(center.getX()+.5,center.getY(),center.getZ()+6.5,180,-26);ready.set(2);});
            phase=2;return false;
        }
        if(phase==2){
            if(ready.get()!=2||TwoIArcanaRenderer.showAge(0)<5)return false;
            check(LegacyStarfieldVisuals.showAge(mc.level,0)>=5,"actual original show packet feeds optional visual clock");
            check(TwoIStarfieldEffects.showAge(0)<0,"original show never contaminates own clock");
            check(TwoIArcanaRenderer.lastVertices()>0&&TwoIArcanaPost.processedFrames()>postBefore,"original garden uses actual mesh and post-processing");
            capture(mc,"standalone-legacy-garden-live");phase=3;return false;
        }
        if(phase==3){
            if(++wait<20)return false;
            server(mc,player->{check((boolean)service().getMethod("returnFromStarfield",ServerPlayer.class).invoke(null,player),"original return succeeds");ready.set(3);});
            phase=4;wait=0;return false;
        }
        if(phase==4){
            if(ready.get()!=3||!mc.level.dimension().equals(Level.OVERWORLD)||++wait<10)return false;
            check(mc.player.position().distanceToSqr(origin)<4,"old return restores exact original safe location");
            check(TwoIArcanaRenderer.showAge(0)<0&&TwoIStarfieldEffects.showAge(0)<0,"all visual clocks inactive after old garden exit");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"original garden exit restores GL state");
            capture(mc,"standalone-legacy-garden-returned");done=true;
            LogUtils.getLogger().info("STANDALONE LEGACY GARDEN CLIENT CHECKS PASSED: original native service entry/gate/show packet, separated own/old clocks, actual decoration and post-process, shared exhausted reward budget, exact safe return and GL restoration");
        }
        return done;
    }
    /** Query the actual optional runtime without adding JEI to the project's compile/runtime dependencies. */
    private static boolean checkJeiCatalog()throws Exception{
        var optional=(java.util.Optional<?>)Class.forName("mezz.jei.common.Internal").getMethod("getOptionalJeiRuntime").invoke(null);
        if(optional.isEmpty())return false;
        Object runtime=optional.orElseThrow();
        Class<?> runtimeApi=Class.forName("mezz.jei.api.runtime.IJeiRuntime");
        Class<?> managerApi=Class.forName("mezz.jei.api.runtime.IIngredientManager");
        Object manager=runtimeApi.getMethod("getIngredientManager").invoke(runtime);
        var stacks=(java.util.Collection<?>)managerApi.getMethod("getAllItemStacks").invoke(manager);
        Object visibility=runtimeApi.getMethod("getIngredientVisibility").invoke(runtime);
        Object type=Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
        var visible=Class.forName("mezz.jei.api.runtime.IIngredientVisibility").getMethod("isIngredientVisible",Class.forName("mezz.jei.api.ingredients.IIngredientType"),Object.class);
        var oldKey=ResourceLocation.parse("useless_stretcher:two_i_doll");
        boolean legacyItem=BuiltInRegistries.ITEM.containsKey(oldKey);
        check(legacyItem==ModList.get().isLoaded("useless_stretcher"),"JEI case has a legacy registered item exactly when Stretcher is installed");
        var old=BuiltInRegistries.ITEM.get(oldKey);
        int canonical=0,legacy=0,canonicalVisible=0,legacyVisible=0;
        for(Object value:stacks){
            var stack=(ItemStack)value;
            if(stack.is(TwoICombatDollMod.DOLL_ITEM.get())){canonical++;if((boolean)visible.invoke(visibility,type,stack))canonicalVisible++;}
            if(legacyItem&&stack.is(old)){legacy++;if((boolean)visible.invoke(visibility,type,stack))legacyVisible++;}
        }
        check(canonical==1&&canonicalVisible==1&&legacyVisible==0,"actual JEI IngredientManager/visibility must show one official 2i: canonical="+canonical+" visible="+canonicalVisible+" legacy="+legacy+" visible="+legacyVisible);
        Object filter=runtimeApi.getMethod("getIngredientFilter").invoke(runtime);
        Class<?> filterApi=Class.forName("mezz.jei.api.runtime.IIngredientFilter");
        check(((String)filterApi.getMethod("getFilterText").invoke(filter)).isEmpty(),"fresh JEI filter has no hidden search restriction");
        var filtered=(java.util.List<?>)filterApi.getMethod("getFilteredItemStacks").invoke(filter);
        long filteredCanonical=filtered.stream().map(ItemStack.class::cast).filter(stack->stack.is(TwoICombatDollMod.DOLL_ITEM.get())).count();
        long filteredLegacy=filtered.stream().map(ItemStack.class::cast).filter(stack->legacyItem&&stack.is(old)).count();
        check(filteredCanonical==1&&filteredLegacy==0,"actual JEI empty search list contains one canonical and zero old dolls");
        LogUtils.getLogger().info("STANDALONE JEI CATALOG CHECKS PASSED: actual IngredientManager canonical={} visible={} legacy={} visible={}; empty search canonical={} legacy={}; legacyRegistered={}",canonical,canonicalVisible,legacy,legacyVisible,filteredCanonical,filteredLegacy,legacyItem);
        return true;
    }
    private static Class<?> service()throws ClassNotFoundException{return Class.forName("com.sorrowmist.useless.stretcher.content.entity.TwoIDollService");}
    @FunctionalInterface private interface Action{void run(ServerPlayer player)throws Exception;}
    private static void server(Minecraft mc,Action action){mc.getSingleplayerServer().execute(()->{try{action.run(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));}catch(Throwable ex){failure.set(ex);}});}
    private static void capture(Minecraft mc,String name)throws Exception{var directory=mc.gameDirectory.toPath().resolve("companion-port-screenshots");Files.createDirectories(directory);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(directory.resolve(name+".png"));}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError("Standalone optional: "+message);}
}
