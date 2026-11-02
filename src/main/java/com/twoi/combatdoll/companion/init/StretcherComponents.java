package com.twoi.combatdoll.companion.init;

import com.mojang.serialization.Codec;
import com.twoi.combatdoll.companion.content.entity.TwoIDollMemory;
import com.twoi.combatdoll.companion.UselessStretcherMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Data components for the wondrous staff's time-acceleration control. */
public final class StretcherComponents {
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, UselessStretcherMod.MODID);

    /** Rotation of the 2i mascot while it is held as an item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TWO_I_ROTATION =
            integerComponent("two_i_rotation");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TWO_I_GIFT_SCORE =
            integerComponent("two_i_gift_score");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TWO_I_BOND =
            integerComponent("two_i_bond");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> TWO_I_OWNER =
            register("two_i_owner", builder -> builder.persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(StreamCodec.of((buffer, value) -> buffer.writeUUID(value),
                            buffer -> buffer.readUUID())));
    /** Stable physical identity distinguishes two held dolls when the player swaps hands. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> TWO_I_DOLL_ID =
            register("two_i_doll_id", builder -> builder.persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(StreamCodec.of((buffer, value) -> buffer.writeUUID(value),
                            buffer -> buffer.readUUID())));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TwoIDollMemory>> TWO_I_MEMORY =
            register("two_i_memory", builder -> builder.persistent(TwoIDollMemory.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> TWO_I_INVITATION_USED =
            register("two_i_invitation_used", builder -> builder.persistent(Codec.BOOL)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean)));

    public static void init(IEventBus bus) { DATA_COMPONENTS.register(bus); }
    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name, UnaryOperator<DataComponentType.Builder<T>> operator) {
        return DATA_COMPONENTS.register(name, () -> operator.apply(DataComponentType.builder()).build());
    }

    private static DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> integerComponent(String name) {
        return register(name, builder -> builder
                .persistent(Codec.INT)
                .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt)));
    }
}
