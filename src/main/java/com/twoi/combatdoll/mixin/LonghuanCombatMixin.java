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
    @Unique private LonghuanBookFlightGoal twoi$bookFlight;
    @Unique private net.minecraft.world.entity.ai.navigation.PathNavigation twoi$groundNavigation;
    @Unique private net.minecraft.world.entity.ai.control.MoveControl twoi$groundMoveControl;
    @Unique private net.minecraft.world.entity.ai.navigation.FlyingPathNavigation twoi$flyingNavigation;
    @Unique private net.minecraft.world.entity.ai.control.FlyingMoveControl twoi$flyingMoveControl;
    protected LonghuanCombatMixin(EntityType<? extends PathfinderMob> type,Level level){super(type,level);}
    @Override public TwoIGrimoireCombat combatState(){return twoi$combat;}
    @Override public java.util.UUID combatOwner(){return ownerId();}
    @Override public boolean combatBusy(){return isSharedUmbrella()||LonghuanDanceInvoker.twoi$dancing(this);}
    @Override public WeaponKind combatKind(){return WeaponKind.BOOK;}
    @Inject(method="createAttributes",at=@At("RETURN"))
    private static void twoi$flightAttribute(CallbackInfoReturnable<net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder> cir){
        cir.getReturnValue().add(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED,.6);
    }
    @Inject(method="registerGoals",at=@At("TAIL")) private void twoi$goals(CallbackInfo ci){
        goalSelector.addGoal(0,new CombatStopGoal(this));
        twoi$bookFlight=new LonghuanBookFlightGoal(this,this::twoi$flightControls);
        goalSelector.addGoal(-1,twoi$bookFlight);
    }
    @Unique private void twoi$flightControls(boolean flying){
        getNavigation().stop();
        if(flying){
            twoi$groundNavigation=navigation;twoi$groundMoveControl=moveControl;
            if(twoi$flyingNavigation==null){
                twoi$flyingNavigation=new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(this,level());
                twoi$flyingNavigation.setCanFloat(true);
                twoi$flyingMoveControl=new net.minecraft.world.entity.ai.control.FlyingMoveControl(this,40,true);
            }
            navigation=twoi$flyingNavigation;moveControl=twoi$flyingMoveControl;
        }else if(twoi$groundNavigation!=null){
            navigation=twoi$groundNavigation;moveControl=twoi$groundMoveControl;
            navigation.stop();moveControl.setWantedPosition(getX(),getY(),getZ(),0);
        }
    }
    @Inject(method="tick",at=@At("HEAD")) private void twoi$flightLifecycle(CallbackInfo ci){
        if(!level().isClientSide&&twoi$bookFlight!=null)twoi$bookFlight.refreshLifecycle();
    }
    @Override public void travel(net.minecraft.world.phys.Vec3 movement){
        if(!LonghuanBookFlightGoal.riding(this)){super.travel(movement);return;}
        if(isControlledByLocalInstance()){
            // Normal ground-mob air acceleration is only .02. Give this real
            // flight bounded acceleration and drag, retaining Entity.move collision.
            moveRelative(.12F,movement);
            move(MoverType.SELF,getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(.82));
        }
        calculateEntityAnimation(false);
    }
    @Inject(method="reclaim",at=@At("HEAD")) private void twoi$reclaim(Player player,CallbackInfoReturnable<Boolean> cir){if(!level().isClientSide)WeaponEquipment.decorate(this,stored);}
    @Inject(method="initialize",at=@At("TAIL")) private void twoi$initialize(java.util.UUID id,ItemStack stack,CallbackInfo ci){
        if(!level().isClientSide)twoi$combat.load(stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag(),0);
    }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL")) private void twoi$save(CompoundTag tag,CallbackInfo ci){
        twoi$combat.save(tag,0);if(twoi$bookFlight!=null)twoi$bookFlight.saveGravity(tag);
    }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL")) private void twoi$load(CompoundTag tag,CallbackInfo ci){twoi$combat.load(tag,0);}
}
