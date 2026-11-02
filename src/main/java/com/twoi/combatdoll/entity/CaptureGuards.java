package com.twoi.combatdoll.entity;
import net.minecraft.nbt.*;
import java.util.ArrayDeque;

public final class CaptureGuards {
    public static boolean containsDoll(CompoundTag root){
        var pending=new ArrayDeque<Tag>();pending.add(root);int checked=0;
        while(!pending.isEmpty()&&checked++<4096){
            Tag value=pending.removeFirst();
            if(value instanceof CompoundTag tag){
                String id=tag.getString("id");
                if(id.equals("two_i_combat_doll:combat_doll")||id.equals("two_i_combat_doll:cangsheng_axolotl")
                        ||id.startsWith("xiaoxiao:")&&id.contains("doll"))return true;
                for(String key:tag.getAllKeys()){var child=tag.get(key);if(child instanceof CompoundTag||child instanceof ListTag)pending.add(child);}
            } else if(value instanceof ListTag list)for(Tag child:list)if(child instanceof CompoundTag||child instanceof ListTag)pending.add(child);
        }return false;
    }
}
