package com.twoi.combatdoll.combat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Save-owned single carrier ledger. A copied stack or captured entity never gets a second lease. */
public final class AxolotlLeaseData extends SavedData {
    private static final int AVAILABLE=0,ACTIVE=1,PENDING=2;
    private static final class Lease {
        UUID owner,entity;long generation;int state;ItemStack stack=ItemStack.EMPTY;
    }
    private final Map<UUID,Lease> leases=new HashMap<>();
    private final Set<UUID> pendingOwners=new HashSet<>();
    public static AxolotlLeaseData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(AxolotlLeaseData::new,AxolotlLeaseData::load),"two_i_axolotl_leases");}
    public static long generation(ItemStack stack){return WeaponData.tag(stack).getLong("axolotl_generation");}
    private static void generation(ItemStack stack,long value){var tag=WeaponData.tag(stack);tag.putLong("axolotl_generation",value);WeaponData.write(stack,tag);}
    public boolean acquire(ItemStack stack,UUID owner,UUID entity){
        var binding=WeaponData.tag(stack);
        if(binding.hasUUID("owner")&&!owner.equals(binding.getUUID("owner")))return false;
        UUID id=WeaponData.ensure(stack);Lease row=leases.get(id);
        if(row!=null&&(row.state!=AVAILABLE||row.generation!=generation(stack)||!owner.equals(row.owner)))return false;
        if(row==null){row=new Lease();row.owner=owner;row.generation=generation(stack);leases.put(id,row);}
        binding=WeaponData.tag(stack);binding.putUUID("owner",owner);WeaponData.write(stack,binding);
        row.generation++;generation(stack,row.generation);row.entity=entity;row.state=ACTIVE;row.stack=stack.copyWithCount(1);setDirty();return true;
    }
    public boolean valid(ItemStack stack,UUID owner,UUID entity){
        Lease row=leases.get(WeaponData.identity(stack));return row!=null&&row.state==ACTIVE&&Objects.equals(row.owner,owner)
                &&Objects.equals(row.entity,entity)&&row.generation==generation(stack);
    }
    /** ACTIVE carriers can await an unloaded mount; older retired copies cannot. */
    public boolean currentCarrier(ItemStack stack,UUID owner){
        Lease row=leases.get(WeaponData.identity(stack));
        return row==null||((row.state==AVAILABLE||row.state==ACTIVE)&&Objects.equals(row.owner,owner)&&row.generation==generation(stack));
    }
    public void snapshot(ItemStack stack,UUID entity){
        var row=leases.get(WeaponData.identity(stack));if(row!=null&&row.state==ACTIVE&&Objects.equals(row.entity,entity)){
            row.stack=stack.copyWithCount(1);setDirty();
        }
    }
    /** Retire the deployed form and invalidate all old carrier copies. */
    public ItemStack release(ItemStack source,UUID entity,boolean pending){
        var row=leases.get(WeaponData.identity(source));
        if(row==null||row.state!=ACTIVE||!Objects.equals(row.entity,entity)||generation(source)!=row.generation)return ItemStack.EMPTY;
        var result=source.copyWithCount(1);row.generation++;generation(result,row.generation);row.entity=null;
        row.state=pending?PENDING:AVAILABLE;row.stack=pending?result.copy():ItemStack.EMPTY;if(pending)pendingOwners.add(row.owner);setDirty();return result;
    }
    public int pending(UUID owner){return (int)leases.values().stream().filter(row->row.state==PENDING&&owner.equals(row.owner)).count();}
    public boolean claimPending(ServerPlayer player){
        if(!pendingOwners.contains(player.getUUID()))return false;
        int slot=player.getInventory().getFreeSlot();if(slot<0)return false;
        for(var row:leases.values())if(row.state==PENDING&&player.getUUID().equals(row.owner)&&!row.stack.isEmpty()){
            ItemStack item=row.stack.copy();row.stack=ItemStack.EMPTY;row.state=AVAILABLE;setDirty();
            if(pending(player.getUUID())==0)pendingOwners.remove(player.getUUID());
            player.getInventory().setItem(slot,item);player.getInventory().setChanged();return true;
        }
        return false;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var list=new ListTag();leases.forEach((id,row)->{var t=new CompoundTag();t.putUUID("id",id);t.putUUID("owner",row.owner);
            if(row.entity!=null)t.putUUID("entity",row.entity);t.putLong("generation",row.generation);t.putInt("state",row.state);
            if(!row.stack.isEmpty())t.put("stack",row.stack.save(registries));list.add(t);});tag.put("leases",list);return tag;
    }
    public static AxolotlLeaseData load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new AxolotlLeaseData();for(var value:tag.getList("leases",Tag.TAG_COMPOUND)){var t=(CompoundTag)value;
            if(!t.hasUUID("id")||!t.hasUUID("owner"))continue;var row=new Lease();row.owner=t.getUUID("owner");
            row.entity=t.hasUUID("entity")?t.getUUID("entity"):null;row.generation=t.getLong("generation");row.state=t.getInt("state");
            row.stack=ItemStack.parseOptional(registries,t.getCompound("stack"));data.leases.put(t.getUUID("id"),row);
            if(row.state==PENDING)data.pendingOwners.add(row.owner);
        }return data;
    }
}
