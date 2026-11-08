package com.twoi.combatdoll.mixin;

import com.twoi.combatdoll.combat.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.customdoll.xiaoxiao.content.entity.DollEntity",remap=false)
public abstract class XiaoxiaoCombatMixin extends PathfinderMob implements DollCombatAccess {
    @Unique private final TwoIGrimoireCombat twoi$combat=new TwoIGrimoireCombat();
    @Shadow public abstract java.util.UUID ownerId();
    @Shadow public abstract boolean isSharedUmbrella();
    @Shadow private ItemStack stored;
    @Shadow private boolean reclaimed;
    protected XiaoxiaoCombatMixin(EntityType<? extends PathfinderMob> type,Level level){super(type,level);}
    @Override public TwoIGrimoireCombat combatState(){return twoi$combat;}
    @Override public java.util.UUID combatOwner(){return ownerId();}
    // The doll stores the carrier; its real living mount is the sole spell actor.
    @Override public boolean combatBusy(){return isSharedUmbrella()||twoi$combat.armed();}
    @Override public WeaponKind combatKind(){return WeaponKind.AXOLOTL;}
    @Inject(method="registerGoals",at=@At("TAIL")) private void twoi$goals(CallbackInfo ci){goalSelector.addGoal(0,new CombatStopGoal(this));}
    @Inject(method="tick",at=@At("TAIL")) private void twoi$mountedFacing(CallbackInfo ci){
        if(getVehicle() instanceof com.twoi.combatdoll.entity.CangshengAxolotlEntity mount)mount.alignPassengerFacing(this);
    }
    // Run only on the successful branch, after the target mod reserves its inventory slot.
    @Inject(method="reclaim",at=@At(value="FIELD",target="Lcom/customdoll/xiaoxiao/content/entity/DollEntity;reclaimed:Z",opcode=org.objectweb.asm.Opcodes.PUTFIELD))
    private void twoi$reclaim(Player player,CallbackInfoReturnable<Boolean> cir){
        if(!level().isClientSide&&!reclaimed&&!stored.isEmpty()&&isAlive()&&!isRemoved()){
            AxolotlEquipment.beforeReclaim(this,player);WeaponEquipment.decorate(this,stored);
        }
    }
    @Inject(method="initialize",at=@At("TAIL")) private void twoi$initialize(java.util.UUID id,ItemStack stack,CallbackInfo ci){
        if(!level().isClientSide)twoi$combat.load(stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag(),0);
    }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL")) private void twoi$save(CompoundTag tag,CallbackInfo ci){twoi$combat.save(tag,0);}
    @Inject(method="readAdditionalSaveData",at=@At("TAIL")) private void twoi$load(CompoundTag tag,CallbackInfo ci){twoi$combat.load(tag,0);}
}
