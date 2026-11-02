package com.twoi.combatdoll.client;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.WeaponEquipment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
@EventBusSubscriber(modid=TwoICombatDollMod.MODID,value=Dist.CLIENT)
public final class WeaponTooltip {
    @SubscribeEvent public static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e){
        if(WeaponEquipment.hasStoredWeapon(e.getItemStack()))e.getToolTip().add(net.minecraft.network.chat.Component.translatable("item.two_i_combat_doll.weapon.reclaim").withStyle(net.minecraft.ChatFormatting.AQUA));
    }
}
