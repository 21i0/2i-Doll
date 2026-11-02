package com.twoi.combatdoll.combat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.UUID;

public final class WeaponData {
    public static UUID identity(ItemStack stack){var tag=tag(stack);return tag.hasUUID("identity")?tag.getUUID("identity"):null;}
    public static CompoundTag tag(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("arcana_weapon");}
    public static UUID ensure(ItemStack stack){var id=identity(stack);if(id==null){id=UUID.randomUUID();var own=tag(stack);own.putUUID("identity",id);write(stack,own);}return id;}
    public static void write(ItemStack stack,CompoundTag own){var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.put("arcana_weapon",own);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
}
