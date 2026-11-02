package com.twoi.combatdoll.item;

import com.twoi.combatdoll.combat.WeaponKind;
import com.twoi.combatdoll.entity.CangshengAxolotlEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** The player releases a living companion, never casts its companion-only takeover. */
public final class CangshengAxolotlItem extends ArcanaWeaponItem {
    public CangshengAxolotlItem(){super(WeaponKind.AXOLOTL);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(!level.isClientSide){
            var direction=player.getLookAngle().multiply(1,0,1).normalize();
            var pos=player.position().add(direction.scale(8));
            if(CangshengAxolotlEntity.deploy((ServerPlayer)player,stack,pos,null)==null){
                player.displayClientMessage(Component.translatable("message.two_i_combat_doll.axolotl_unavailable"),true);return InteractionResultHolder.fail(stack);
            }
            // Creative players also transfer the sole durable carrier.
            stack.shrink(1);player.getInventory().setChanged();
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("item.two_i_combat_doll.cangsheng_axolotl.tooltip"));
    }
}
