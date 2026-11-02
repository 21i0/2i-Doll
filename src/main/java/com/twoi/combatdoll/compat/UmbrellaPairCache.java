package com.twoi.combatdoll.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One loaded-only protocol query per anchor/position/tick across every bundled renderer. */
public final class UmbrellaPairCache {
    private static final int LIMIT=1024;
    private static final double RANGE=1.65;
    private static final ResourceLocation ID=ResourceLocation.parse("doll_protocol:umbrella_participants");
    private static final TagKey<Block> BLOCKS=TagKey.create(Registries.BLOCK,ID);
    private static final TagKey<EntityType<?>> ENTITIES=TagKey.create(Registries.ENTITY_TYPE,ID);
    // Values contain coordinates and immutable protocol results, never a Level,
    // Entity, lambda, resource manager or other back-reference to the weak key.
    private static final Map<Level,Bucket> LEVELS=new WeakHashMap<>();
    public record Anchor(String id,Vec3 feet){}
    public record Pair(Anchor self,Anchor partner,Vec3 canopy,boolean leader){}
    public record Stats(long tick,long hits,long misses,int entries){}
    private static final class Bucket {
        long tick=Long.MIN_VALUE,hits,misses;
        final LinkedHashMap<Anchor,Pair> entries=new LinkedHashMap<>();
    }
    private UmbrellaPairCache(){}
    private static Bucket bucket(Level level){synchronized(LEVELS){return LEVELS.computeIfAbsent(level,ignored->new Bucket());}}
    public static Pair sample(Level level,Anchor self){
        Bucket bucket=bucket(level);
        synchronized(bucket){
            long time=level.getGameTime();
            if(time!=bucket.tick){bucket.entries.clear();bucket.tick=time;}
            Pair cached=bucket.entries.get(self);
            if(cached!=null){bucket.hits++;return cached;}
            bucket.misses++;Pair result=scan(level,self);
            if(bucket.entries.size()>=LIMIT)bucket.entries.remove(bucket.entries.keySet().iterator().next());
            bucket.entries.put(self,result);return result;
        }
    }
    /** Read-only counters for profiling and private runtime regression checks. */
    public static Stats stats(Level level){Bucket b=bucket(level);synchronized(b){return new Stats(b.tick,b.hits,b.misses,b.entries.size());}}
    private static Pair scan(Level level,Anchor self){
        List<Anchor> nearby=new ArrayList<>();nearby.add(self);BlockPos origin=BlockPos.containing(self.feet());
        for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-3,-1,-3),origin.offset(3,1,3)))
            if(level.hasChunkAt(pos)&&level.getBlockState(pos).is(BLOCKS)){
                Anchor a=new Anchor("block:"+pos.asLong(),new Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.5));if(!a.id().equals(self.id()))nearby.add(a);
            }
        for(Entity entity:level.getEntities((Entity)null,new AABB(self.feet(),self.feet()).inflate(RANGE*2),e->e.isAlive()&&e.getType().is(ENTITIES))){
            Anchor a=new Anchor("entity:"+entity.getUUID(),entity.position());if(!a.id().equals(self.id()))nearby.add(a);
        }
        Anchor partner=nearest(self,nearby);if(partner!=null&&!self.equals(nearest(partner,nearby)))partner=null;
        Vec3 center=partner==null?self.feet():self.feet().add(partner.feet()).scale(.5);
        double ground=partner==null?self.feet().y:Math.max(self.feet().y,partner.feet().y);
        return new Pair(self,partner,new Vec3(center.x,ground+1.25,center.z),partner==null||self.id().compareTo(partner.id())<0);
    }
    private static Anchor nearest(Anchor anchor,List<Anchor> candidates){
        return candidates.stream().filter(other->!anchor.id().equals(other.id())&&Math.abs(other.feet().y-anchor.feet().y)<=.55&&other.feet().distanceToSqr(anchor.feet())<=RANGE*RANGE)
                .min(Comparator.<Anchor>comparingDouble(other->other.feet().distanceToSqr(anchor.feet())).thenComparing(Anchor::id)).orElse(null);
    }
}
