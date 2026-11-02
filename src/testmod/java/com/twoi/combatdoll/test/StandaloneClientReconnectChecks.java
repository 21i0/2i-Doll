package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.companion.client.render.TwoIStarfieldEffects;
import com.twoi.combatdoll.companion.content.entity.*;
import com.twoi.combatdoll.companion.content.leaf.LeafRewardMigration;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Native Dev logout/login twice in the same on-disk world, using actual periodic pending delivery. */
final class StandaloneClientReconnectChecks {
    private static final AtomicInteger ready=new AtomicInteger();
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    private static int phase,ticks,wait,slot,dialogWait,dialogCount;
    private static boolean done,opening;
    private static Screen observedDialog;
    private static volatile UUID owner,leafId,recoveryId;
    private static volatile ItemStack expectedRecovery;
    private static volatile MinecraftServer previousServer;
    private static volatile String world;
    private StandaloneClientReconnectChecks(){}
    static boolean started(){return phase>0||done;}

    static boolean tick(Minecraft mc)throws Exception{
        if(done)return true;
        check(++ticks<2400,"native reconnect timeout phase="+phase);
        if(failure.get()!=null)throw new AssertionError("native reconnect server action",failure.get());
        if(mc.getOverlay()!=null)return false;
        if(opening)return false;
        if((phase==2||phase==6)&&mc.player==null&&handleLoadScreen(mc))return false;
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return false;
        if(phase==0){
            server(mc,player->{
                var server=player.getServer();owner=player.getUUID();previousServer=server;
                world=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
                check(server.getPlayerList().getPlayer(owner)==player,"actual logged-in Dev player, not a synthetic player");
                check(player.getInventory().getItem(34).isEmpty()&&player.getInventory().getItem(35).isEmpty(),"reserved claim slots start empty and do not discard existing test carriers");
                for(int index=0;index<player.getInventory().getContainerSize();index++)if(player.getInventory().getItem(index).isEmpty())player.getInventory().setItem(index,new ItemStack(Items.STONE,64));
                check(player.getInventory().getFreeSlot()<0,"actual complete main inventory filled");
                var ledger=LeafRewardMigration.ledger(server);check(!ledger.hasRolledDoll(owner)&&ledger.beginDollDelivery(owner),"real Dev begins its one native leaf roll");
                var migration=LeafRewardMigration.prepare(server);migration.rememberPending(owner);leafId=migration.identity(owner);recoveryId=UUID.randomUUID();
                expectedRecovery=new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());
                expectedRecovery.set(StretcherComponents.TWO_I_OWNER.get(),owner);expectedRecovery.set(StretcherComponents.TWO_I_DOLL_ID.get(),recoveryId);
                expectedRecovery.set(StretcherComponents.TWO_I_BOND.get(),67);expectedRecovery.set(StretcherComponents.TWO_I_INVITATION_USED.get(),true);
                expectedRecovery.set(StretcherComponents.TWO_I_MEMORY.get(),new TwoIDollMemory(new TwoIDollMemory.Cooldowns(17,23,37,53,0,0,-1,false),new TwoIDollMemory.Interaction(0,1,2,3,1,2100,-1,"minecraft:stone",0,1)));
                expectedRecovery.set(DataComponents.CUSTOM_NAME,Component.literal("Native reconnect memory"));var extra=new CompoundTag();extra.putString("extension","keep complete native reconnect carrier");expectedRecovery.set(DataComponents.CUSTOM_DATA,CustomData.of(extra));
                check(TwoIDollRecoveryData.queue(player,expectedRecovery),"native Dev recovery carrier durably queued");
                for(int chapter=0;chapter<6;chapter++)TwoIStoryData.get(server).complete(owner,chapter);
                slot=TwoIStarfieldData.get(server).slotFor(owner);checkWorldState(player);
                var record=new CompoundTag();record.putUUID("owner",owner);record.putUUID("leaf",leafId);record.putUUID("recovery",recoveryId);record.put("expected_recovery",expectedRecovery.save(server.registryAccess()));record.putInt("story_mask",63);record.putInt("garden_slot",slot);
                var path=server.getWorldPath(LevelResource.ROOT).resolve("standalone-native-reconnect-evidence.dat");check(!Files.exists(path),"fresh native reconnect evidence");NbtIo.writeCompressed(record,path);
                server.saveEverything(true,true,true);
                // Return and disconnect on the next client tick: the production
                // garden shutdown lifecycle must settle its actual new work.
                check(TwoIDollService.sendPlayerToStarfield(player,false)&&TwoIDollService.returnFromStarfield(player),"native garden revisit and immediate return before actual logout");
                player.getInventory().setChanged();player.containerMenu.broadcastChanges();ready.set(1);
            });phase=1;return false;
        }
        if(phase==1){if(ready.get()!=1)return false;phase=2;wait=0;reopen(mc);return false;}
        if(phase==2){
            if(mc.getSingleplayerServer()==previousServer||++wait<30)return false;
            check(mc.player.getUUID().equals(owner),"same real client profile after first native login");
            server(mc,player->{
                previousServer=player.getServer();check(player.getInventory().getFreeSlot()<0,"native player inventory loaded full from disk");
                check(count(player,leafId)==0&&count(player,recoveryId)==0,"full native login did not leak either carrier");
                check(LeafRewardMigration.ledger(player.getServer()).hasPendingDoll(owner)&&TwoIDollRecoveryData.get(player.getServer()).pending(recoveryId),"both native world pending records survive real logout/login");
                checkWorldState(player);player.getInventory().setItem(35,ItemStack.EMPTY);player.containerMenu.broadcastChanges();ready.set(2);
            });phase=3;wait=0;return false;
        }
        if(phase==3){
            if(ready.get()!=2||++wait<45)return false;
            server(mc,player->{
                long leaves=count(player,leafId),recoveries=count(player,recoveryId);check(leaves+recoveries==1&&leaves<=1&&recoveries<=1,"one freed slot receives exactly one real periodic pending delivery");
                player.getInventory().setItem(34,ItemStack.EMPTY);player.containerMenu.broadcastChanges();ready.set(3);
            });phase=4;wait=0;return false;
        }
        if(phase==4){
            if(ready.get()!=3||++wait<45)return false;
            server(mc,player->{verifyClaimed(player);checkWorldState(player);player.getServer().saveEverything(true,true,true);ready.set(4);});phase=5;return false;
        }
        if(phase==5){
            if(ready.get()!=4)return false;
            check(count(mc.player,leafId)==1&&count(mc.player,recoveryId)==1,"both carriers actually synchronized to the native client inventory");
            phase=6;wait=0;reopen(mc);return false;
        }
        if(phase==6){
            if(mc.getSingleplayerServer()==previousServer||++wait<60)return false;
            server(mc,player->{verifyClaimed(player);checkWorldState(player);check(!LeafRewardMigration.ledger(player.getServer()).beginDollDelivery(owner),"native second login cannot reset once-only roll");ready.set(5);});phase=7;return false;
        }
        if(phase==7){
            if(ready.get()!=5)return false;
            check(count(mc.player,leafId)==1&&count(mc.player,recoveryId)==1,"second native login shows one of each exact identity");
            check(TwoIStarfieldEffects.showAge(0)<0,"native reconnect has no stale garden show state");done=true;
            LogUtils.getLogger().info("STANDALONE NATIVE CLIENT RECONNECT CHECKS PASSED: actual Dev profile, immediate native garden return/logout, same disk world opened twice, full inventory/pending leaf+recovery loaded, one slot at a time real periodic delivery, exact UUID/owner/full components, second native login no duplicates, six chapters and exhausted garden assignment preserved");
        }
        return done;
    }
    private static void reopen(Minecraft mc){opening=true;try{mc.level.disconnect();mc.disconnect(new TitleScreen());mc.createWorldOpenFlows().openWorld(world,()->failure.set(new AssertionError("native existing-world load failed: "+world)));}finally{opening=false;}}
    private static boolean handleLoadScreen(Minecraft mc)throws Exception{
        Screen screen=mc.screen;if(screen==null)return false;
        if(screen instanceof GenericMessageScreen||screen instanceof ProgressScreen||screen instanceof LevelLoadingScreen||screen instanceof ReceivingLevelScreen)return true;
        if(screen!=observedDialog){observedDialog=screen;dialogWait=0;return true;}
        if(++dialogWait<2)return true; // Allow this actual screen to render before evidence capture.
        String titleKey=screen.getTitle().getContents() instanceof TranslatableContents translated?translated.getKey():"<literal>";
        var directory=mc.gameDirectory.toPath().resolve("companion-port-screenshots");Files.createDirectories(directory);
        var screenshot=directory.resolve("native-reconnect-dialog-"+(++dialogCount)+"-phase-"+phase+".png");
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(screenshot);}
        if(screen instanceof BackupConfirmScreen&&titleKey.equals("selectWorld.backupQuestion.experimental")){
            for(var child:screen.children())if(child instanceof Button button&&button.getMessage().getContents() instanceof TranslatableContents translated&&translated.getKey().equals("selectWorld.backupJoinConfirmButton")){
                check(button.active,"known experimental backup-and-continue button is active");
                LogUtils.getLogger().info("STANDALONE NATIVE RECONNECT DIALOG OBSERVED: class={} titleKey={} title={} actionKey={} action={} screenshot={}",screen.getClass().getName(),titleKey,screen.getTitle().getString(),translated.getKey(),button.getMessage().getString(),screenshot);
                observedDialog=null;button.onPress();return true;
            }
            throw new AssertionError("Known experimental backup dialog has no expected backup-and-continue button; screenshot="+screenshot);
        }
        throw new AssertionError("Unexpected native world-load screen class="+screen.getClass().getName()+" titleKey="+titleKey+" title="+screen.getTitle().getString()+" screenshot="+screenshot);
    }
    private static void checkWorldState(ServerPlayer player){var server=player.getServer();check(TwoIStoryData.get(server).completedMask(owner)==63,"all six real Dev chapters persisted");var garden=TwoIStarfieldData.get(server);check(garden.slotFor(owner)==slot&&garden.rewardsRemaining(owner)==0&&garden.returnPoint(owner)==null&&garden.celebrationEnd(owner)==0&&!garden.awaitsCourtyardEntry(owner),"native garden slot/exhausted budget/finished return and show persisted");}
    private static void verifyClaimed(ServerPlayer player){
        check(count(player,leafId)==1&&count(player,recoveryId)==1,"exactly one complete carrier of each identity after native login");
        var ledger=LeafRewardMigration.ledger(player.getServer());var recovery=TwoIDollRecoveryData.get(player.getServer());check(ledger.hasClaimedDoll(owner)&&!ledger.hasPendingDoll(owner)&&recovery.claimed(recoveryId)&&!recovery.pending(recoveryId),"both actual durable claims consumed once");
        var leaf=find(player,leafId);check(owner.equals(leaf.get(StretcherComponents.TWO_I_OWNER.get())),"native leaf retains correct owner");
        check(ItemStack.isSameItemSameComponents(expectedRecovery,find(player,recoveryId)),"native recovery preserves complete memory, owner, identity, bond, invitation, custom name and extension");
    }
    private static long count(Player player,UUID id){long result=0;for(int i=0;i<player.getInventory().getContainerSize();i++)if(LeafRewardMigration.receiptMatches(player.getInventory().getItem(i),id))result+=player.getInventory().getItem(i).getCount();return result;}
    private static ItemStack find(Player player,UUID id){for(int i=0;i<player.getInventory().getContainerSize();i++)if(LeafRewardMigration.receiptMatches(player.getInventory().getItem(i),id))return player.getInventory().getItem(i);return ItemStack.EMPTY;}
    @FunctionalInterface private interface Action{void run(ServerPlayer player)throws Exception;}
    private static void server(Minecraft mc,Action action){mc.getSingleplayerServer().execute(()->{try{action.run(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));}catch(Throwable ex){failure.set(ex);}});}
    private static void check(boolean value,String message){if(!value)throw new AssertionError("Native client reconnect: "+message);}
}
