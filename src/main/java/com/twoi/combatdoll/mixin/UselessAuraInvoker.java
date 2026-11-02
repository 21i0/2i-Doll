package com.twoi.combatdoll.mixin;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
@Pseudo
@Mixin(targets="com.sorrowmist.useless.content.items.EndlessBeafItem",remap=false)
public interface UselessAuraInvoker {
    @Invoker("forceKillLivingEntity") static boolean twoi$execute(ItemStack stack,LivingEntity target,Player owner){throw new AssertionError();}
}
