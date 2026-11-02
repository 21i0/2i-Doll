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
@Mixin(targets="com.customdoll.longhuan.content.entity.DollEntity",remap=false)
public abstract class LonghuanCombatMixin extends PathfinderMob implements DollCombatAccess {
    @Unique private final TwoIGrimoireCombat twoi$combat=new TwoIGrimoireCombat();
    @Shadow public abstract java.util.UUID ownerId();
    @Shadow public abstract boolean isSharedUmbrella();
    @Shadow private ItemStack stored;
    protected LonghuanCombatMixin(EntityType<? extends PathfinderMob> type,Level level){super(type,level);}
    @Override public TwoIGrimoireCombat combatState(){return twoi$combat;}
    @Override public java.util.UUID combatOwner(){return ownerId();}
    @Override public boolean combatBusy(){return isSharedUmbrella()||LonghuanDanceInvoker.twoi$dancing(this);}
    @Override public WeaponKind combatKind(){return WeaponKind.BOOK;}
    @Inject(method="registerGoals",at=@At("TAIL")) private void twoi$goals(CallbackInfo ci){goalSelector.addGoal(0,new CombatStopGoal(this));}
    @Inject(method="reclaim",at=@At("HEAD")) private void twoi$reclaim(Player player,CallbackInfoReturnable<Boolean> cir){if(!level().isClientSide)WeaponEquipment.decorate(this,stored);}
    @Inject(method="initialize",at=@At("TAIL")) private void twoi$initialize(java.util.UUID id,ItemStack stack,CallbackInfo ci){
        if(!level().isClientSide)twoi$combat.load(stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag(),0);
    }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL")) private void twoi$save(CompoundTag tag,CallbackInfo ci){twoi$combat.save(tag,0);}
    @Inject(method="readAdditionalSaveData",at=@At("TAIL")) private void twoi$load(CompoundTag tag,CallbackInfo ci){twoi$combat.load(tag,0);}
}
