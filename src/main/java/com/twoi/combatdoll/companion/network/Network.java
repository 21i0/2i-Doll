package com.twoi.combatdoll.companion.network;
import com.twoi.combatdoll.companion.UselessStretcherMod;
import com.twoi.combatdoll.companion.init.StretcherComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Companion-only protocol; no AE2 or stretcher classes are loaded. */
public final class Network {
    public record TwoIDialoguePayload(int entityId, boolean inviteReady, int giftScore, boolean open) implements CustomPacketPayload {
        public static final Type<TwoIDialoguePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_dialogue"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIDialoguePayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIDialoguePayload::entityId,
                        ByteBufCodecs.BOOL, TwoIDialoguePayload::inviteReady,
                        ByteBufCodecs.VAR_INT, TwoIDialoguePayload::giftScore,
                        ByteBufCodecs.BOOL, TwoIDialoguePayload::open,
                        TwoIDialoguePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIItemDialoguePayload(boolean offhand, int bond, boolean invitationUsed) implements CustomPacketPayload {
        public static final Type<TwoIItemDialoguePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_item_dialogue"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIItemDialoguePayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, TwoIItemDialoguePayload::offhand,
                        ByteBufCodecs.VAR_INT, TwoIItemDialoguePayload::bond,
                        ByteBufCodecs.BOOL, TwoIItemDialoguePayload::invitationUsed, TwoIItemDialoguePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIDollReclaimPayload(int entityId) implements CustomPacketPayload {
        public static final Type<TwoIDollReclaimPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_reclaim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIDollReclaimPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIDollReclaimPayload::entityId,
                        TwoIDollReclaimPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIDollGiftPayload(int entityId) implements CustomPacketPayload {
        public static final Type<TwoIDollGiftPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_gift"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIDollGiftPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIDollGiftPayload::entityId,
                        TwoIDollGiftPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIDollInvitePayload(int entityId) implements CustomPacketPayload {
        public static final Type<TwoIDollInvitePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_invite"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIDollInvitePayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIDollInvitePayload::entityId,
                        TwoIDollInvitePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIDollPatPayload(int entityId) implements CustomPacketPayload {
        public static final Type<TwoIDollPatPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_pat"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIDollPatPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIDollPatPayload::entityId,
                        TwoIDollPatPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIDollTalkPayload(int entityId) implements CustomPacketPayload {
        public static final Type<TwoIDollTalkPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_talk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIDollTalkPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIDollTalkPayload::entityId,
                        TwoIDollTalkPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIDollStoryStartPayload(int entityId) implements CustomPacketPayload {
        public static final Type<TwoIDollStoryStartPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_story_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIDollStoryStartPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIDollStoryStartPayload::entityId,
                        TwoIDollStoryStartPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIInteractionPayload(int entityId, boolean active) implements CustomPacketPayload {
        public static final Type<TwoIInteractionPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_interaction"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIInteractionPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIInteractionPayload::entityId,
                        ByteBufCodecs.BOOL, TwoIInteractionPayload::active, TwoIInteractionPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIStoryLibraryRequestPayload(int requestId) implements CustomPacketPayload {
        public static final Type<TwoIStoryLibraryRequestPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_story_library_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIStoryLibraryRequestPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIStoryLibraryRequestPayload::requestId,
                        TwoIStoryLibraryRequestPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIStoryLibraryStatePayload(int requestId, int completedMask) implements CustomPacketPayload {
        public static final Type<TwoIStoryLibraryStatePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_story_library_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIStoryLibraryStatePayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIStoryLibraryStatePayload::requestId,
                        ByteBufCodecs.VAR_INT, TwoIStoryLibraryStatePayload::completedMask,
                        TwoIStoryLibraryStatePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIDollStoryPayload(int entityId, long sessionId, int choice) implements CustomPacketPayload {
        public static final Type<TwoIDollStoryPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_story_choice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIDollStoryPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIDollStoryPayload::entityId,
                        ByteBufCodecs.VAR_LONG, TwoIDollStoryPayload::sessionId,
                        ByteBufCodecs.VAR_INT, TwoIDollStoryPayload::choice,
                        TwoIDollStoryPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIStoryStatePayload(int entityId, long sessionId, int chapter, int bond, int result, int round)
            implements CustomPacketPayload {
        public static final Type<TwoIStoryStatePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_story_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIStoryStatePayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TwoIStoryStatePayload::entityId,
                        ByteBufCodecs.VAR_LONG, TwoIStoryStatePayload::sessionId,
                        ByteBufCodecs.VAR_INT, TwoIStoryStatePayload::chapter,
                        ByteBufCodecs.VAR_INT, TwoIStoryStatePayload::bond,
                        ByteBufCodecs.VAR_INT, TwoIStoryStatePayload::result,
                        ByteBufCodecs.VAR_INT, TwoIStoryStatePayload::round,
                        TwoIStoryStatePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIDollDeployPayload(boolean offhand) implements CustomPacketPayload {
        public static final Type<TwoIDollDeployPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_deploy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIDollDeployPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, TwoIDollDeployPayload::offhand,
                        TwoIDollDeployPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TwoIDollRemoteReclaimPayload() implements CustomPacketPayload {
        public static final Type<TwoIDollRemoteReclaimPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "two_i_remote_reclaim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TwoIDollRemoteReclaimPayload> STREAM_CODEC =
                StreamCodec.of((buf, value) -> {}, buf -> new TwoIDollRemoteReclaimPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(TwoIDialoguePayload.TYPE, TwoIDialoguePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientStateReceiver.handleTwoIDialogue(payload)));
        registrar.playToClient(TwoIItemDialoguePayload.TYPE, TwoIItemDialoguePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientStateReceiver.handleTwoIItemDialogue(payload)));
        registrar.playToServer(TwoIDollReclaimPayload.TYPE, TwoIDollReclaimPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTwoIDollReclaim(payload, context)));
        registrar.playToServer(TwoIDollGiftPayload.TYPE, TwoIDollGiftPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTwoIDollGift(payload, context)));
        registrar.playToServer(TwoIDollInvitePayload.TYPE, TwoIDollInvitePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTwoIDollInvite(payload, context)));
        registrar.playToServer(TwoIDollPatPayload.TYPE, TwoIDollPatPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTwoIDollPat(payload, context)));
        registrar.playToServer(TwoIDollTalkPayload.TYPE, TwoIDollTalkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTwoIDollTalk(payload, context)));
        registrar.playToServer(TwoIDollStoryStartPayload.TYPE, TwoIDollStoryStartPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTwoIDollStoryStart(payload, context)));
        registrar.playToServer(TwoIDollStoryPayload.TYPE, TwoIDollStoryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTwoIDollStory(payload, context)));
        registrar.playToClient(TwoIStoryStatePayload.TYPE, TwoIStoryStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientStateReceiver.handleTwoIStoryState(payload)));
        registrar.playToServer(TwoIStoryLibraryRequestPayload.TYPE, TwoIStoryLibraryRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        int completed = com.twoi.combatdoll.companion.content.entity.TwoIStoryData
                                .get(player.serverLevel().getServer()).completedMask(player.getUUID());
                        PacketDistributor.sendToPlayer(player,
                                new TwoIStoryLibraryStatePayload(payload.requestId(), completed));
                    }
                }));
        registrar.playToClient(TwoIStoryLibraryStatePayload.TYPE, TwoIStoryLibraryStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientStateReceiver.handleTwoIStoryLibrary(payload)));
        registrar.playToServer(TwoIInteractionPayload.TYPE, TwoIInteractionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.serverLevel().getEntity(payload.entityId()) instanceof
                            com.twoi.combatdoll.companion.content.entity.TwoIDollEntity doll) {
                        doll.keepInteraction(player, payload.active());
                    }
                }));
        registrar.playToServer(TwoIDollDeployPayload.TYPE, TwoIDollDeployPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTwoIDollDeploy(payload, context)));
        registrar.playToServer(TwoIDollRemoteReclaimPayload.TYPE, TwoIDollRemoteReclaimPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTwoIDollRemoteReclaim(context)));
    }

    public static void sendTwoIDialogue(ServerPlayer player, int entityId, boolean inviteReady, int giftScore) {
        PacketDistributor.sendToPlayer(player, new TwoIDialoguePayload(entityId, inviteReady, giftScore, true));
    }

    public static void sendTwoIItemDialogue(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (!player.getUUID().equals(stack.get(StretcherComponents.TWO_I_OWNER.get()))) return;
        PacketDistributor.sendToPlayer(player, new TwoIItemDialoguePayload(hand == InteractionHand.OFF_HAND,
                com.twoi.combatdoll.companion.content.item.TwoIDollItem.getBond(stack),
                stack.getOrDefault(StretcherComponents.TWO_I_INVITATION_USED.get(), false)));
    }

    public static void updateTwoIDialogue(ServerPlayer player, int entityId, boolean inviteReady, int giftScore) {
        PacketDistributor.sendToPlayer(player, new TwoIDialoguePayload(entityId, inviteReady, giftScore, false));
    }

    public static void sendTwoIDollReclaim(int entityId) {
        PacketDistributor.sendToServer(new TwoIDollReclaimPayload(entityId));
    }

    public static void sendTwoIDollGift(int entityId) {
        PacketDistributor.sendToServer(new TwoIDollGiftPayload(entityId));
    }

    public static void sendTwoIDollInvite(int entityId) {
        PacketDistributor.sendToServer(new TwoIDollInvitePayload(entityId));
    }

    public static void sendTwoIDollPat(int entityId) {
        PacketDistributor.sendToServer(new TwoIDollPatPayload(entityId));
    }

    public static void sendTwoIDollTalk(int entityId) {
        PacketDistributor.sendToServer(new TwoIDollTalkPayload(entityId));
    }

    public static void sendTwoIDollStoryStart(int entityId) {
        PacketDistributor.sendToServer(new TwoIDollStoryStartPayload(entityId));
    }

    public static void requestTwoIStoryLibrary(int requestId) {
        PacketDistributor.sendToServer(new TwoIStoryLibraryRequestPayload(requestId));
    }

    public static void sendTwoIInteraction(int entityId, boolean active) {
        PacketDistributor.sendToServer(new TwoIInteractionPayload(entityId, active));
    }

    public static void sendTwoIDollStory(int entityId, long sessionId, int choice) {
        PacketDistributor.sendToServer(new TwoIDollStoryPayload(entityId, sessionId, choice));
    }

    public static void sendTwoIStoryState(ServerPlayer player, int entityId, long sessionId,
                                         int chapter, int bond, int result) {
        sendTwoIStoryState(player, entityId, sessionId, chapter, bond, result, 0);
    }

    public static void sendTwoIStoryState(ServerPlayer player, int entityId, long sessionId,
                                         int chapter, int bond, int result, int round) {
        PacketDistributor.sendToPlayer(player, new TwoIStoryStatePayload(entityId, sessionId, chapter, bond, result, round));
    }

    public static void sendTwoIDollDeploy(InteractionHand hand) {
        PacketDistributor.sendToServer(new TwoIDollDeployPayload(hand == InteractionHand.OFF_HAND));
    }

    public static void sendTwoIDollRemoteReclaim() {
        PacketDistributor.sendToServer(new TwoIDollRemoteReclaimPayload());
    }

    private static void handleTwoIDollReclaim(TwoIDollReclaimPayload payload,
                                               net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Entity target = player.serverLevel().getEntity(payload.entityId());
        if (target instanceof com.twoi.combatdoll.companion.content.entity.TwoIDollEntity doll
                && !doll.isRemoved() && doll.distanceToSqr(player) <= 64.0D) {
            doll.reclaim(player);
        }
    }

    private static void handleTwoIDollGift(TwoIDollGiftPayload payload,
                                           net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Entity target = player.serverLevel().getEntity(payload.entityId());
        if (target instanceof com.twoi.combatdoll.companion.content.entity.TwoIDollEntity doll) doll.acceptGift(player);
    }

    private static void handleTwoIDollInvite(TwoIDollInvitePayload payload,
                                             net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Entity target = player.serverLevel().getEntity(payload.entityId());
        if (target instanceof com.twoi.combatdoll.companion.content.entity.TwoIDollEntity doll) doll.acceptInvitation(player);
    }

    private static void handleTwoIDollPat(TwoIDollPatPayload payload,
                                          net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Entity target = player.serverLevel().getEntity(payload.entityId());
        if (target instanceof com.twoi.combatdoll.companion.content.entity.TwoIDollEntity doll)
            doll.patBy(player);
    }

    private static void handleTwoIDollTalk(TwoIDollTalkPayload payload,
                                           net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Entity target = player.serverLevel().getEntity(payload.entityId());
        if (target instanceof com.twoi.combatdoll.companion.content.entity.TwoIDollEntity doll)
            doll.talkTo(player);
    }

    private static void handleTwoIDollStoryStart(TwoIDollStoryStartPayload payload,
                                                 net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Entity target = player.serverLevel().getEntity(payload.entityId());
        if (target instanceof com.twoi.combatdoll.companion.content.entity.TwoIDollEntity doll) {
            doll.startStory(player);
        } else {
            sendTwoIStoryState(player, payload.entityId(), 0L, 0, 0, 2);
        }
    }

    private static void handleTwoIDollStory(TwoIDollStoryPayload payload,
                                            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (payload.choice() < 0 || payload.choice() >= 9) {
            sendTwoIStoryState(player, payload.entityId(), payload.sessionId(), 0, 0, 2,
                    Math.clamp(payload.choice() / 3, 0, 2));
            return;
        }
        Entity target = player.serverLevel().getEntity(payload.entityId());
        if (target instanceof com.twoi.combatdoll.companion.content.entity.TwoIDollEntity doll) {
            doll.storyChoice(player, payload.sessionId(), payload.choice());
        } else {
            sendTwoIStoryState(player, payload.entityId(), payload.sessionId(), 0, 0, 2, payload.choice() / 3);
        }
    }

    private static void handleTwoIDollDeploy(TwoIDollDeployPayload payload,
                                             net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        com.twoi.combatdoll.companion.content.item.TwoIDollItem.deploy(player,
                payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    private static void handleTwoIDollRemoteReclaim(
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (com.twoi.combatdoll.companion.content.entity.TwoIDollService.returnFromStarfield(player)) return;
        var start = player.getEyePosition();
        var direction = player.getLookAngle().normalize();
        com.twoi.combatdoll.companion.content.entity.TwoIDollEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var doll : com.twoi.combatdoll.companion.content.entity.TwoIDollEntity
                .trackedFor(player.getServer(), player.getUUID())) {
            if (doll.isRemoved() || doll.level() != player.level()) continue;
            var to = doll.getBoundingBox().getCenter().subtract(start);
            double projection = to.dot(direction);
            if (projection < 0.0D || projection >= bestDistance) continue;
            var point = start.add(direction.scale(projection));
            if (!doll.getBoundingBox().inflate(0.6D).contains(point)) continue;
            var target = doll.getBoundingBox().getCenter();
            if (!hasLoadedLine(player.serverLevel(), start, target)) continue;
            var visible = player.serverLevel().clip(new net.minecraft.world.level.ClipContext(start,
                    target, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            if (visible.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            best = doll;
            bestDistance = projection;
        }
        if (best != null) {
            best.reclaimRemote(player);
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "msg.two_i_combat_doll.two_i.reclaim_not_found"), true);
        }
    }

    private static boolean hasLoadedLine(net.minecraft.server.level.ServerLevel level,
                                         net.minecraft.world.phys.Vec3 start,
                                         net.minecraft.world.phys.Vec3 end) {
        var delta = end.subtract(start);
        int steps = (int) Math.ceil(delta.length() / 8.0D);
        for (int i = 1; i < steps; i++) {
            var point = start.add(delta.scale(i / (double) steps));
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(point))) return false;
        }
        return true;
    }
}
