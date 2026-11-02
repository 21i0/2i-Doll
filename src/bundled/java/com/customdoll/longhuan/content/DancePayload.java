package com.customdoll.longhuan.content;
import com.customdoll.longhuan.DollMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid=DollMod.MODID,bus=EventBusSubscriber.Bus.MOD)
public record DancePayload(UUID id,UUID entity,BlockPos block,Vec3 center,long start,boolean stop) implements CustomPacketPayload {
    public static final Type<DancePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(DollMod.MODID,"dance"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DancePayload> CODEC=StreamCodec.of((b,p)->{
        b.writeUUID(p.id);b.writeUUID(p.entity);b.writeBlockPos(p.block);
        b.writeDouble(p.center.x);b.writeDouble(p.center.y);b.writeDouble(p.center.z);
        b.writeLong(p.start);b.writeBoolean(p.stop);
    },b->new DancePayload(b.readUUID(),b.readUUID(),b.readBlockPos(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readLong(),b.readBoolean()));
    @Override public Type<DancePayload> type(){return TYPE;}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToClient(TYPE,CODEC,(p,c)->c.enqueueWork(()->com.customdoll.longhuan.client.DollDanceClient.receive(p)));
    }
}
