package com.twoi.combatdoll.compat;

import com.twoi.combatdoll.companion.content.entity.TwoICompanionshipData;
import com.twoi.combatdoll.companion.content.leaf.LeafRewardMigration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Optional compatibility boundary. No Stretcher implementation class is linked
 * or resolved until the installed-mod check succeeds. Core saved data remains
 * owned by this mod; only an installed Stretcher's live records are shared.
 */
public final class StretcherCompanionBridge {
    private static final String ENTITY_PACKAGE = "com.sorrowmist.useless.stretcher.content.entity.";
    private static final Set<String> DATA_TYPES = Set.of("TwoIStoryData", "TwoICompanionshipData",
            "TwoIStarfieldData", "TwoIDollAcquisitionData");
    private StretcherCompanionBridge() { }

    public static boolean installed() { return ModList.get().isLoaded("useless_stretcher"); }

    public static Object data(MinecraftServer server, String name) {
        if (server == null || !installed()) return null;
        if (!DATA_TYPES.contains(name)) throw new IllegalArgumentException("Unknown legacy 2i ledger: " + name);
        try {
            return Class.forName(ENTITY_PACKAGE + name).getMethod("get", MinecraftServer.class).invoke(null, server);
        } catch (ReflectiveOperationException ex) {
            // A failed bridge must not silently reset once-only receipts.
            throw new IllegalStateException("Legacy 2i compatibility failed: " + name, ex);
        }
    }

    public static CompoundTag snapshot(MinecraftServer server, String name) {
        Object source = data(server, name);
        if (source == null) return null;
        try {
            return ((SavedData) source).save(new CompoundTag(), server.registryAccess()).copy();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Cannot safely import legacy 2i world data: " + name, ex);
        }
    }

    @SuppressWarnings("unchecked") public static <T> T field(Object source, String name) {
        try {
            var field = source.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(source);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Legacy 2i field failed: " + name, ex);
        }
    }

    private static long number(Object source, String name) {
        try {
            var method = source.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return ((Number) method.invoke(source)).longValue();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Legacy 2i record failed: " + name, ex);
        }
    }

    public static int[] story(MinecraftServer server, UUID player) {
        Object source = data(server, "TwoIStoryData");
        if (source == null) return null;
        Map<UUID, Object> entries = field(source, "players");
        Object progress = entries.get(player);
        return progress == null ? null : new int[]{(int) number(progress, "completedMask"), (int) number(progress, "lastChapter")};
    }

    public static void complete(MinecraftServer server, UUID player, int chapter) {
        Object source = data(server, "TwoIStoryData");
        if (source == null) return;
        try { source.getClass().getMethod("complete", UUID.class, int.class).invoke(source, player, chapter); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException("Legacy 2i story receipt failed", ex); }
    }

    public static boolean acquisition(MinecraftServer server, UUID doll) {
        Object source = data(server, "TwoIDollAcquisitionData");
        if (source == null) return true;
        try { return (boolean) source.getClass().getMethod("markIfNew", UUID.class).invoke(source, doll); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException("Legacy 2i acquisition receipt failed", ex); }
    }

    public static TwoICompanionshipData.Sample clock(MinecraftServer server, UUID doll,
            TwoICompanionshipData.Progress local, long time, boolean sample) {
        Object source = data(server, "TwoICompanionshipData");
        if (source == null) return null;
        Map<UUID, Object> entries = field(source, "dolls");
        Object old = entries.get(doll);
        long day = local.day(), last = local.lastSample();
        int earned = local.earned(), ticks = local.ticks();
        if (old != null) {
            long oldDay = number(old, "day");
            int oldEarned = (int) number(old, "earned"), oldTicks = (int) number(old, "ticks");
            last = Math.max(last, number(old, "lastSample"));
            if (oldDay > day) { day = oldDay; earned = oldEarned; ticks = oldTicks; }
            else if (oldDay == day) {
                if (oldEarned > earned) { earned = oldEarned; ticks = oldTicks; }
                else if (oldEarned == earned) ticks = Math.max(ticks, oldTicks);
            }
        }
        if (sample && day != Math.max(0, time / 24000L)) { day = Math.max(0, time / 24000L); earned = 0; ticks = 0; }
        boolean award = false;
        if (sample && time % TwoICompanionshipData.CHECK_INTERVAL == 0 && last < time && earned < 2) {
            ticks += TwoICompanionshipData.CHECK_INTERVAL; last = time;
            if (ticks >= TwoICompanionshipData.REWARD_INTERVAL) { ticks -= TwoICompanionshipData.REWARD_INTERVAL; earned++; award = true; }
        }
        var progress = new TwoICompanionshipData.Progress(day, earned, ticks, last);
        try {
            if (old == null || number(old, "day") != day || number(old, "earned") != earned
                    || number(old, "ticks") != ticks || number(old, "lastSample") != last) {
                Class<?> record = Class.forName(ENTITY_PACKAGE + "TwoICompanionshipData$Progress");
                entries.put(doll, record.getConstructor(long.class, int.class, int.class, long.class).newInstance(day, earned, ticks, last));
                ((SavedData) source).setDirty();
            }
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException("Legacy 2i clock receipt failed", ex); }
        return new TwoICompanionshipData.Sample(progress, award);
    }

    /** Uses the owning mod's Java type, never a second SavedData type with the same key. */
    public static LeafRewardMigration.Ledger leafLedger(MinecraftServer server) {
        if (!installed()) return null;
        try {
            Class<?> type = Class.forName("com.sorrowmist.useless.stretcher.content.leaf.StaffLeafDropData");
            Object source = type.getMethod("get", MinecraftServer.class).invoke(null, server);
            Method rolled = type.getMethod("hasRolledDoll", UUID.class), claimed = type.getMethod("hasClaimedDoll", UUID.class);
            Method begin = type.getMethod("beginDollDelivery", UUID.class), mark = type.getMethod("markDollDelivered", UUID.class);
            return new LeafRewardMigration.Ledger() {
                private Object call(Method method, UUID player) {
                    try { return method.invoke(source, player); }
                    catch (ReflectiveOperationException ex) { throw new IllegalStateException("Cannot merge historical leaf ledger", ex); }
                }
                public boolean hasRolledDoll(UUID player) { return (boolean) call(rolled, player); }
                public boolean hasClaimedDoll(UUID player) { return (boolean) call(claimed, player); }
                public boolean beginDollDelivery(UUID player) { return (boolean) call(begin, player); }
                public void markDollDelivered(UUID player) { call(mark, player); }
            };
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot access Stretcher's shared leaf reward ledger", ex);
        }
    }
}
