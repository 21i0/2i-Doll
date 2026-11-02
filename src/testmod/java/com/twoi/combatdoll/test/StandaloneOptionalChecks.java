package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.AuraExecution;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Calls the real execution API against actual registered targets; no instrumentation in production. */
public final class StandaloneOptionalChecks {
    private StandaloneOptionalChecks(){}
    public static void run(ServerPlayer owner){
        var level=owner.serverLevel();
        boolean optional=ModList.get().isLoaded("useless_mod");
        var contextType=BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("useless_mod:force_kill_enabled"));
        if(optional)check(contextType!=null,"installed UselessMod registers the actual force-kill context, so execute must use its Invoker branch");
        var target=EntityType.ZOMBIE.create(level);var cow=EntityType.COW.create(level);var wolf=EntityType.WOLF.create(level);
        var companion=TwoICombatDollMod.DOLL_ENTITY.get().create(level);
        check(target!=null&&cow!=null&&wolf!=null&&companion!=null,"actual registered target and protected actor types");
        target.setNoAi(true);cow.setNoAi(true);wolf.setNoAi(true);companion.setNoAi(true);
        wolf.setTame(true,true);wolf.setOwnerUUID(owner.getUUID());companion.setOwnerId(owner.getUUID());
        for(var actor:java.util.List.of(target,cow,wolf,companion)){
            actor.moveTo(owner.getX()+3,owner.getY(),owner.getZ()+3,0,0);check(level.addFreshEntity(actor),"real target actor joins level");
        }
        var weapon=new ItemStack(TwoICombatDollMod.STAFF.get());
        try{
            check(AuraExecution.execute(owner,target,weapon)&&!target.isAlive(),"actual hostile target executes through selected native/optional branch");
            check(!AuraExecution.execute(owner,cow,weapon)&&cow.isAlive(),"passive actor rejected before execution");
            check(!AuraExecution.execute(owner,wolf,weapon)&&wolf.isAlive(),"owner's tame actor rejected before execution");
            check(!AuraExecution.execute(owner,companion,weapon)&&companion.isAlive(),"canonical companion rejected before execution");
            if(optional){
                check(!weapon.has(contextType),"optional Invoker uses a copied force-kill context and leaves the original weapon unchanged");
                LogUtils.getLogger().info("STANDALONE USELESS AURA INVOKER CHECKS PASSED: installed force_kill_enabled context selects real upstream Invoker; actual hostile execution, passive/tame/companion rejection, original weapon context unchanged");
            }else LogUtils.getLogger().info("STANDALONE NATIVE AURA EXECUTION CHECKS PASSED: no UselessMod; actual hostile execution and passive/tame/companion rejection");
        }finally{
            target.discard();cow.discard();wolf.discard();companion.discardForReclaim();
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError("Standalone execution: "+message);}
}
