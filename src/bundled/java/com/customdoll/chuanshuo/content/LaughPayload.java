package com.customdoll.chuanshuo.content;

import com.customdoll.chuanshuo.DollMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

/** Only a server-to-client message. No client can select an arbitrary source over this channel. */
@EventBusSubscriber(modid = DollMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public record LaughPayload(UUID id, int entityId, UUID entity, BlockPos block, Vec3 center, long start, boolean stop)
        implements CustomPacketPayload {
    public static final Type<LaughPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DollMod.MODID, "laugh"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LaughPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeUUID(p.id); b.writeVarInt(p.entityId); b.writeUUID(p.entity); b.writeBlockPos(p.block);
        b.writeDouble(p.center.x); b.writeDouble(p.center.y); b.writeDouble(p.center.z);
        b.writeLong(p.start); b.writeBoolean(p.stop);
    }, b -> new LaughPayload(b.readUUID(), b.readVarInt(), b.readUUID(), b.readBlockPos(),
            new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readLong(), b.readBoolean()));
    @Override public Type<LaughPayload> type() { return TYPE; }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (p, c) ->
                c.enqueueWork(() -> com.customdoll.chuanshuo.client.DollLaughClient.receive(p)));
    }
}
