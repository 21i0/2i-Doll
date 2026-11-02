package com.twoi.combatdoll.combat;
import com.twoi.combatdoll.mixin.UselessAuraInvoker;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** The same per-target forced execution used by upstream's aura, with an independent native fallback. */
public final class AuraExecution {
    @SuppressWarnings("unchecked") public static boolean execute(ServerPlayer owner,Mob target,ItemStack weapon){
        if(!TwoIGrimoireCombat.eligible(target,owner))return false;
        if(ModList.get().isLoaded("useless_mod")){
            var type=(DataComponentType<Boolean>)(Object)BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("useless_mod:force_kill_enabled"));
            if(type!=null){var context=weapon.copy();context.set(type,true);return UselessAuraInvoker.twoi$execute(context,target,owner);}
        }
        target.invulnerableTime=0;target.setInvulnerable(false);if(target instanceof WitherBoss wither)wither.setInvulnerableTicks(0);
        var source=target.damageSources().playerAttack(owner);target.setLastHurtByPlayer(owner);
        if(target instanceof EnderDragon dragon)dragon.hurt(dragon.head,source,Float.MAX_VALUE);else target.hurt(source,Float.MAX_VALUE);
        if(target.isAlive()){target.setHealth(0);target.die(source);}return target.isDeadOrDying()||target.isRemoved();
    }
}
