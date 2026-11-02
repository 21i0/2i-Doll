package com.customdoll.qijici.content.item;
import com.customdoll.qijici.init.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import com.customdoll.qijici.content.DollOwnership;
import com.customdoll.qijici.content.DollUmbrellaProtocol;
public final class DollItem extends BlockItem {
 public DollItem() { super(ModBlocks.TWO_I_DOLL_DISPLAY.get(), new Properties().stacksTo(1)); }
 @Override public EquipmentSlot getEquipmentSlot(ItemStack s) { return EquipmentSlot.HEAD; }
 @Override public boolean canEquip(ItemStack s, EquipmentSlot slot, LivingEntity e) { return slot == EquipmentSlot.HEAD && (!(e instanceof Player p) || DollOwnership.mayUse(s, p)); }
 @Override public InteractionResult place(net.minecraft.world.item.context.BlockPlaceContext context) {
  Player p = context.getPlayer();
  if (p != null && !DollOwnership.mayUse(context.getItemInHand(), p)) { DollOwnership.deny(p); return InteractionResult.FAIL; }
  return super.place(context);
 }
 @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
  if (!level.isClientSide && entity instanceof Player p && p.getItemBySlot(EquipmentSlot.HEAD) == stack && DollOwnership.mayUse(stack, p)) DollOwnership.claim(stack, p);
 }
 @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
  ItemStack stack = player.getItemInHand(hand);
  if (!DollOwnership.mayUse(stack, player)) { DollOwnership.deny(player); return InteractionResultHolder.fail(stack); }
  if (!level.isClientSide) { DollOwnership.claim(stack, player); DollUmbrellaProtocol.markUmbrellaParticipant(stack); }
  return InteractionResultHolder.pass(player.getItemInHand(hand));
 }
 @Override public void appendHoverText(ItemStack s, TooltipContext ctx, java.util.List<Component> lines, TooltipFlag flags) {
  lines.add(Component.translatable("tooltip.qijici.qijici_doll").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
  lines.add(Component.translatable("tooltip.qijici.controls").withStyle(net.minecraft.ChatFormatting.GRAY));
 }
 
}
