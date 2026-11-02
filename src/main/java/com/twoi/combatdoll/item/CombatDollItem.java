package com.twoi.combatdoll.item;
/** Canonical standalone item retains the original registry ID and all companion forms. */
public final class CombatDollItem extends com.twoi.combatdoll.companion.content.item.TwoIDollItem {
 public CombatDollItem(Properties properties){super();}
 @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context){
  var result=super.useOn(context);
  if(result.consumesAction()&&!context.getLevel().isClientSide&&context.getPlayer()!=null&&context.getPlayer().getAbilities().instabuild)context.getItemInHand().shrink(1);
  return result;
 }
}
