package com.twoi.combatdoll.item;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.AxolotlLeaseData;
import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class AxolotlFluteItem extends Item {
    public AxolotlFluteItem(){super(new Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(!level.isClientSide){
            boolean found=false;
            var nearbyRecalls=new java.util.HashSet<java.util.UUID>();
            for(var pet:level.getEntitiesOfClass(CangshengAxolotlEntity.class,player.getBoundingBox().inflate(128)))
                if(player.getUUID().equals(pet.ownerId())&&pet.requestRecall((ServerPlayer)player)){
                    nearbyRecalls.add(pet.getUUID());found=true;
                }
            var ledger=AxolotlLeaseData.get(player.getServer());
            found|=ledger.recallDistant((ServerPlayer)player,nearbyRecalls);
            found|=ledger.claimPending((ServerPlayer)player);
            found|=ledger.pending(player.getUUID())>0;
            if(found){
                level.playSound(null,player.blockPosition(),TwoICombatDollMod.AXOLOTL_FLUTE_SOUND.get(),SoundSource.PLAYERS,.85F,1);player.getCooldowns().addCooldown(this,20);
                if(ledger.pending(player.getUUID())>0&&player.getInventory().getFreeSlot()<0)
                    player.displayClientMessage(Component.translatable("message.two_i_combat_doll.axolotl_pending"),true);
            }
            else player.displayClientMessage(Component.translatable("message.two_i_combat_doll.axolotl_not_found"),true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
}
