package com.twoi.combatdoll.companion.network;
import net.minecraft.client.Minecraft;
public final class ClientStateReceiver {
    public static void handleTwoIDialogue(Network.TwoIDialoguePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.twoi.combatdoll.companion.client.TwoIDialogueScreen screen
                && screen.entityId() == payload.entityId()) {
            screen.updateGiftStatus(payload.inviteReady(), payload.giftScore());
            return;
        }
        if (minecraft.screen instanceof com.twoi.combatdoll.companion.client.TwoIStoryScreen story
                && story.entityId() == payload.entityId()) {
            story.updateGiftStatus(payload.inviteReady(), payload.giftScore());
            return;
        }
        if (!payload.open()) return;
        minecraft.setScreen(new com.twoi.combatdoll.companion.client.TwoIDialogueScreen(
                payload.entityId(), payload.inviteReady(), payload.giftScore()));
    }

    public static void handleTwoIItemDialogue(Network.TwoIItemDialoguePayload payload) {
        Minecraft.getInstance().setScreen(com.twoi.combatdoll.companion.client.TwoIDialogueScreen.forItem(
                payload.offhand() ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND,
                payload.bond(), payload.invitationUsed()));
    }

    public static void handleTwoIStoryState(Network.TwoIStoryStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.twoi.combatdoll.companion.client.TwoIDialogueScreen screen
                && screen.entityId() == payload.entityId()) {
            if (payload.result() == 0) {
                screen.openStory(payload.sessionId(), payload.chapter(), payload.bond(), payload.round());
            } else if (payload.result() == 2 && payload.sessionId() == 0L) {
                screen.onStoryUnavailable();
            }
        } else if (minecraft.screen instanceof com.twoi.combatdoll.companion.client.TwoIStoryScreen screen
                && screen.entityId() == payload.entityId()) {
            if (payload.result() == 1) {
                screen.onChoiceResult(payload.sessionId(), payload.bond(), payload.round());
            } else if (payload.result() == 2) {
                screen.onStoryUnavailable(payload.sessionId(), payload.round());
            }
        }
    }

    public static void handleTwoIStoryLibrary(Network.TwoIStoryLibraryStatePayload payload) {
        if (Minecraft.getInstance().screen instanceof
                com.twoi.combatdoll.companion.client.TwoIStoryLibraryScreen screen) {
            screen.acceptLibrary(payload.requestId(), payload.completedMask());
        }
    }
}
