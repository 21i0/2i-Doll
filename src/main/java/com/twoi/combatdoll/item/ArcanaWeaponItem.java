package com.twoi.combatdoll.item;
import com.twoi.combatdoll.combat.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import java.util.List;

public class ArcanaWeaponItem extends Item {
    private final WeaponKind kind;
    public ArcanaWeaponItem(WeaponKind kind){super(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());this.kind=kind;}
    public WeaponKind kind(){return kind;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(!level.isClientSide){
            var state=CombatActors.state(player);
            if(state.age(player,0)>=0)return InteractionResultHolder.fail(stack);
            state.hold(stack);
            boolean begun=kind==WeaponKind.STAFF?state.beginCharge((net.minecraft.server.level.ServerPlayer)player,(net.minecraft.server.level.ServerLevel)level)
                    :state.begin(player,(net.minecraft.server.level.ServerLevel)level,true);
            if(!begun){
                long seconds=(CombatWorldData.get(player.getServer()).remaining(WeaponData.ensure(stack))+19)/20;
                player.displayClientMessage(Component.translatable(seconds>0?"message.two_i_combat_doll.cooldown":"message.two_i_combat_doll.time_occupied",seconds),true);
                return InteractionResultHolder.fail(stack);
            }
            CombatRuntime.register(player);player.getInventory().setChanged();
        }
        if(kind==WeaponKind.STAFF){player.startUsingItem(hand);return InteractionResultHolder.consume(stack);}
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public int getUseDuration(ItemStack stack,net.minecraft.world.entity.LivingEntity entity){return kind==WeaponKind.STAFF?72_000:0;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
    @Override public void releaseUsing(ItemStack stack,Level level,net.minecraft.world.entity.LivingEntity actor,int remaining){
        if(!level.isClientSide&&kind==WeaponKind.STAFF&&actor instanceof net.minecraft.server.level.ServerPlayer player)
            CombatActors.state(player).releaseCharge(player,(net.minecraft.server.level.ServerLevel)level,stack);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable(kind==WeaponKind.STAFF?"item.two_i_combat_doll.staff.tooltip":"item.two_i_combat_doll.weapon.tooltip"));
    }
    @Override public boolean shouldCauseReequipAnimation(ItemStack old,ItemStack fresh,boolean changed){return changed||!ItemStack.isSameItem(old,fresh)||!java.util.Objects.equals(WeaponData.identity(old),WeaponData.identity(fresh));}
}
