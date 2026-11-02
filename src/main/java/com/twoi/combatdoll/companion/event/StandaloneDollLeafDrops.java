package com.twoi.combatdoll.companion.event;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.companion.config.StretcherConfig;
import com.twoi.combatdoll.companion.content.item.TwoIDollAcquisition;
import com.twoi.combatdoll.companion.content.leaf.LeafRewardMigration;
import com.twoi.combatdoll.companion.content.leaf.StandaloneLeafDropData;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** One standalone leaf chance; full inventories retain a world-saved claim, never a despawning item. */
@EventBusSubscriber(modid="two_i_combat_doll")
public final class StandaloneDollLeafDrops {
    private StandaloneDollLeafDrops(){}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void drops(BlockDropsEvent event){
        if(ModList.get().isLoaded("useless_stretcher")||event.isCanceled()
                ||!(event.getBreaker() instanceof ServerPlayer player)||player.isCreative()||player.isSpectator()
                ||!player.isAlive()||player.isRemoved()||!leaf(event.getState())||!StretcherConfig.enableDollLeafDrop())return;
        var migration=LeafRewardMigration.prepare(player.getServer());
        var data=LeafRewardMigration.ledger(player.getServer());
        if(!data.hasRolledDoll(player.getUUID())&&event.getLevel().getRandom().nextDouble()<StretcherConfig.dollLeafDropProbability()
                &&data.beginDollDelivery(player.getUUID()))migration.rememberPending(player.getUUID());
        deliver(player,data);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(ModList.get().isLoaded("useless_stretcher")||event.getServer().getTickCount()%20!=0)return;
        var data=LeafRewardMigration.ledger(event.getServer());
        for(var player:event.getServer().getPlayerList().getPlayers())if(data.hasPendingDoll(player.getUUID())&&player.isAlive()&&!player.isRemoved()){
            if(!deliver(player,data)&&event.getServer().getTickCount()%200==0)
                player.displayClientMessage(Component.literal("2i玩偶已为你保留，请腾出一个背包空位领取。"),true);
        }
    }
    public static boolean deliver(ServerPlayer player,StandaloneLeafDropData data){
        return deliver(player,LeafRewardMigration.standalone(data));
    }
    public static boolean deliver(ServerPlayer player,LeafRewardMigration.Ledger data){
        UUID owner=player.getUUID();if(!data.hasPendingDoll(owner))return false;
        var migration=LeafRewardMigration.prepare(player.getServer());migration.rememberPending(owner);
        UUID identity=migration.identity(owner);
        // Both typed generations and original CUSTOM_DATA are receipts if insertion
        // reached disk before the corresponding claim. Keep the old random ID.
        if(LeafRewardMigration.hasReceipt(player,identity)){
            data.markDollDelivered(owner);migration.markClaimed(owner);return true;
        }
        int slot=player.getInventory().getFreeSlot();if(slot<0)return false;
        var reward=new ItemStack(TwoICombatDollMod.DOLL_ITEM.get());
        reward.set(StretcherComponents.TWO_I_OWNER.get(),owner);reward.set(StretcherComponents.TWO_I_DOLL_ID.get(),identity);
        player.getInventory().setItem(slot,reward);data.markDollDelivered(owner);migration.markClaimed(owner);
        player.getInventory().setChanged();player.containerMenu.broadcastChanges();TwoIDollAcquisition.notifyAcquired(player,reward);return true;
    }
    public static UUID rewardIdentity(UUID owner){return UUID.nameUUIDFromBytes(("two_i_combat_doll:standalone_leaf:"+owner).getBytes(StandardCharsets.UTF_8));}
    private static boolean leaf(BlockState state){
        return state.is(BlockTags.LEAVES)||state.getBlock() instanceof LeavesBlock
                ||state.hasProperty(BlockStateProperties.DISTANCE)&&state.hasProperty(BlockStateProperties.PERSISTENT)
                ||state.getBlockHolder().unwrapKey().map(key->key.location().getPath().contains("leaves")).orElse(false);
    }
}
