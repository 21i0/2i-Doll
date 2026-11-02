package com.twoi.combatdoll.combat;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.item.ArcanaWeaponItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid=TwoICombatDollMod.MODID)
public final class WeaponEquipment {
    public static void collect(LivingEntity actor){
        if(com.twoi.combatdoll.duel.DollDuelArena.isDuelActor(actor))return;
        if(actor instanceof com.twoi.combatdoll.entity.CangshengAxolotlEntity)return;
        if(!(actor instanceof DollCombatAccess doll)||actor.level().isClientSide()||CombatActors.state(actor).armed()||doll.combatOwner()==null)return;
        for(var drop:actor.level().getEntitiesOfClass(ItemEntity.class,actor.getBoundingBox().inflate(2.5),e->e.isAlive()&&e.getItem().getItem() instanceof ArcanaWeaponItem)){
            var thrower=drop.getOwner();if(thrower==null||!thrower.getUUID().equals(doll.combatOwner()))continue;
            if(CombatActors.state(actor).equip(actor,drop.getItem())){
                drop.getItem().shrink(1);if(drop.getItem().isEmpty())drop.discard();
                var owner=actor.getServer().getPlayerList().getPlayer(doll.combatOwner());
                if(owner!=null)owner.displayClientMessage(Component.translatable("message.two_i_combat_doll.equipped",actor.getDisplayName()),true);break;
            }
        }
    }
    public static ItemStack decorate(LivingEntity actor,ItemStack result){
        if(actor.level().isClientSide()||result.isEmpty())return result;
        var tag=result.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();CombatActors.state(actor).save(tag,0);
        result.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return result;
    }
    public static boolean hasStoredWeapon(ItemStack item){return item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("two_i_combat_doll").contains("weapon");}
    @SubscribeEvent public static void retrieve(PlayerInteractEvent.RightClickItem e){
        if(!e.getEntity().isShiftKeyDown()||!hasStoredWeapon(e.getItemStack()))return;
        e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);if(e.getLevel().isClientSide())return;
        var player=e.getEntity();var item=e.getItemStack();var inv=player.getInventory();var tag=item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        var weapon=ItemStack.parseOptional(e.getLevel().registryAccess(),tag.getCompound("two_i_combat_doll").getCompound("weapon"));
        if(weapon.isEmpty()||!(weapon.getItem() instanceof ArcanaWeaponItem))return;
        var binding=WeaponData.tag(weapon);
        if(binding.hasUUID("owner")&&!binding.getUUID("owner").equals(player.getUUID())){
            player.displayClientMessage(Component.translatable("message.two_i_combat_doll.owner_only"),true);return;
        }
        if(inv.getFreeSlot()<0){player.displayClientMessage(Component.translatable("message.two_i_combat_doll.inventory_full"),true);return;}
        if(inv.add(weapon)){tag.remove("two_i_combat_doll");item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));inv.setChanged();}
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post e){
        if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer player&&player.tickCount%20==0)
            AxolotlLeaseData.get(player.getServer()).claimPending(player);
        if(e.getEntity() instanceof LivingEntity actor&&actor instanceof DollCombatAccess access&&access.combatKind()==WeaponKind.AXOLOTL
                &&!(actor instanceof com.twoi.combatdoll.entity.CangshengAxolotlEntity)&&!actor.level().isClientSide()&&actor.tickCount%10==0)
            AxolotlEquipment.restoreMount(actor);
        if(e.getEntity() instanceof LivingEntity actor&&actor instanceof DollCombatAccess&&!actor.level().isClientSide()
                &&Math.floorMod(actor.tickCount+actor.getId(),10)==0&&!CombatActors.state(actor).armed())collect(actor);
    }
}
