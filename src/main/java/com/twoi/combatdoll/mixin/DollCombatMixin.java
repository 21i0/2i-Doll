package com.twoi.combatdoll.mixin;

import com.twoi.combatdoll.combat.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity", remap = false)
public abstract class DollCombatMixin extends PathfinderMob implements DollCombatAccess {
    @Unique private final TwoIGrimoireCombat twoi$combat = new TwoIGrimoireCombat();
    @Shadow public abstract java.util.UUID getOwnerId();
    @Shadow public abstract boolean isInteractionHeld();
    @Shadow public abstract boolean hasSharedUmbrella();
    protected DollCombatMixin(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }
    @Override public TwoIGrimoireCombat combatState() { return twoi$combat; }
    @Override public java.util.UUID combatOwner() { return getOwnerId(); }
    @Override public boolean combatBusy() {
        return isInteractionHeld() || hasSharedUmbrella();
    }
    @Inject(method="tick", at=@At("TAIL"))
    private void twoi$tick(CallbackInfo ci) {
        if (twoi$combat.age(this, 0) >= 0) {
            getNavigation().stop();
            setDeltaMovement(0, isNoGravity() ? 0 : getDeltaMovement().y, 0);
        }
    }
    @Inject(method="registerGoals", at=@At("TAIL"))
    private void twoi$goal(CallbackInfo ci) {
        goalSelector.addGoal(0, new CombatStopGoal(this));
    }
    @Inject(method="toDollItem", at=@At("RETURN"))
    private void twoi$save(CallbackInfoReturnable<ItemStack> ci) {
        if (level().isClientSide) return;
        ItemStack item = ci.getReturnValue();
        var tag = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        twoi$combat.save(tag, level().getGameTime());
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    @Inject(method="restoreFromItem", at=@At("TAIL"))
    private void twoi$load(ItemStack item, CallbackInfo ci) {
        twoi$combat.load(item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(), level().getGameTime());
    }
    @Inject(method="readAdditionalSaveData", at=@At("HEAD"))
    private void twoi$clear(net.minecraft.nbt.CompoundTag tag, CallbackInfo ci) { twoi$combat.reset(); }
}
