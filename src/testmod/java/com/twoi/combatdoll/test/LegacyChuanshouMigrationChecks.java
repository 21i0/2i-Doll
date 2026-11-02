package com.twoi.combatdoll.test;

import com.customdoll.chuanshuo.content.DollOwnership;
import com.customdoll.chuanshuo.content.block.DollDisplayBlock;
import com.customdoll.chuanshuo.content.blockentity.DollDisplayBlockEntity;
import com.customdoll.chuanshuo.init.ModBlockEntities;
import com.customdoll.chuanshuo.init.ModBlocks;
import com.customdoll.chuanshuo.init.ModItems;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.compat.LegacyChuanshouAliases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Native legacy codecs and an actual .mca save/new-JVM load. Test-only, never shipped. */
@EventBusSubscriber(modid = TwoICombatDollMod.MODID)
public final class LegacyChuanshouMigrationChecks {
    private static final BlockPos POSITION = new BlockPos(1248, 80, 1248);
    private static final ChunkPos CHUNK = new ChunkPos(POSITION);
    private static final UUID OWNER = identity("owner"), DOLL_ID = identity("doll");
    private static final String FIXTURE = "legacy-chuanshou-fixture.dat";
    private static final String LEGACY_DISK = "legacy-chuanshou-old-chunk.dat";
    private static MinecraftServer checkedServer, armedServer;
    private static int oldChunkWrites;

    private LegacyChuanshouMigrationChecks() { }

    private static UUID identity(String key) {
        return UUID.nameUUIDFromBytes(("twoi-legacy-chuanshou-" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("Legacy chuanshou migration: " + message);
    }

    private static boolean persistenceRun() {
        return Boolean.getBoolean("two_i_combat_doll.standalone_test")
                && Boolean.getBoolean("two_i_combat_doll.standalone_minimal")
                && !Boolean.getBoolean("two_i_combat_doll.standalone_recovery_fault_test");
    }

    private static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    private static ItemStack expected() {
        ItemStack stack = new ItemStack(ModItems.TWO_I_DOLL.get());
        CompoundTag data = new CompoundTag(), extension = new CompoundTag();
        data.putUUID("doll_owner", OWNER);
        data.putUUID("doll_identity", DOLL_ID);
        extension.putString("unknown_valid_key", "preserve unchanged");
        extension.putInt("counter", 37);
        data.put("third_party_extension", extension);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Legacy owner carrier"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Keep this lore"))));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static CompoundTag oldItem(ServerLevel level) {
        CompoundTag encoded = (CompoundTag) expected().save(level.registryAccess());
        encoded.putString("id", LegacyChuanshouAliases.LEGACY_ITEM.toString());
        return encoded;
    }

    private static CompoundTag oldState() {
        CompoundTag tag = new CompoundTag(), properties = new CompoundTag();
        tag.putString("Name", LegacyChuanshouAliases.LEGACY_DISPLAY.toString());
        properties.putString("rotation", "11");
        tag.put("Properties", properties);
        return tag;
    }

    private static CompoundTag oldBlockEntity(ServerLevel level) {
        CompoundTag tag = new CompoundTag(), persistent = new CompoundTag();
        tag.putString("id", LegacyChuanshouAliases.LEGACY_DISPLAY.toString());
        tag.putInt("x", POSITION.getX()); tag.putInt("y", POSITION.getY()); tag.putInt("z", POSITION.getZ());
        tag.put("Doll", oldItem(level));
        tag.putBoolean("Spinning", true); tag.putFloat("SpinAngle", 37.5F); tag.putLong("SpinStart", 300);
        persistent.putString("legacy_extension", "keep block entity persistent data");
        tag.put("NeoForgeData", persistent);
        return tag;
    }

    private static void assertCarrier(ItemStack actual) {
        check(ItemStack.isSameItemSameComponents(expected(), actual),
                "complete canonical item, owner/UUID/custom extension/name/lore/glint retained");
        check(OWNER.equals(DollOwnership.get(actual)), "new owner guard recognizes the old owner component");
        check(DOLL_ID.equals(actual.get(DataComponents.CUSTOM_DATA).copyTag().getUUID("doll_identity")),
                "old doll UUID retained");
    }

    private static void assertDisplay(BlockEntity entity, ServerLevel level) {
        check(entity instanceof DollDisplayBlockEntity, "old BE type resolves to the new B display class");
        DollDisplayBlockEntity display = (DollDisplayBlockEntity) entity;
        assertCarrier(display.copyDoll());
        check(display.getBlockState().is(ModBlocks.TWO_I_DOLL_DISPLAY.get())
                && display.getBlockState().getValue(DollDisplayBlock.ROTATION) == 11,
                "old palette block name and rotation resolve to the new B display");
        check(display.isSpinning() && Math.abs(display.rotationAt(400) - 217.5F) < .001F,
                "spinning flag, start clock and angle retained");
        CompoundTag saved = display.saveWithFullMetadata(level.registryAccess());
        check(saved.getString("id").equals(LegacyChuanshouAliases.DISPLAY.toString())
                && saved.getCompound("Doll").getString("id").equals(LegacyChuanshouAliases.ITEM.toString()),
                "native save canonicalizes both registry names");
        check(saved.getCompound("NeoForgeData").getString("legacy_extension").equals("keep block entity persistent data"),
                "other valid block entity persistent data retained");
    }

    public static void run(ServerPlayer player) throws Exception {
        MinecraftServer server = player.server;
        if (checkedServer == server) return;
        checkedServer = server;
        ServerLevel level = server.overworld();
        check(ModList.get().isLoaded("chuanshuo") && !ModList.get().isLoaded("chuanshou"),
                "only the new public mod identity is loaded");
        check(!BuiltInRegistries.ITEM.containsKey(LegacyChuanshouAliases.LEGACY_ITEM)
                && !BuiltInRegistries.BLOCK.containsKey(LegacyChuanshouAliases.LEGACY_DISPLAY)
                && !BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(LegacyChuanshouAliases.LEGACY_DISPLAY),
                "aliases do not recreate old item/block/BE registrations");
        check(BuiltInRegistries.ITEM.get(LegacyChuanshouAliases.LEGACY_ITEM) == ModItems.TWO_I_DOLL.get()
                && BuiltInRegistries.BLOCK.get(LegacyChuanshouAliases.LEGACY_DISPLAY) == ModBlocks.TWO_I_DOLL_DISPLAY.get()
                && BuiltInRegistries.BLOCK_ENTITY_TYPE.get(LegacyChuanshouAliases.LEGACY_DISPLAY) == ModBlockEntities.TWO_I_DOLL_DISPLAY.get(),
                "all three official registry aliases resolve");
        ItemStack decoded = ItemStack.parse(level.registryAccess(), oldItem(level)).orElseThrow();
        assertCarrier(decoded);
        check(((CompoundTag) decoded.save(level.registryAccess())).getString("id").equals(LegacyChuanshouAliases.ITEM.toString()),
                "native ItemStack codec saves the new canonical ID");
        BlockState state = BlockState.CODEC.parse(NbtOps.INSTANCE, oldState()).getOrThrow();
        assertDisplay(BlockEntity.loadStatic(POSITION, state, oldBlockEntity(level), level.registryAccess()), level);
        LogUtils.getLogger().info("LEGACY CHUANSHOU CODEC CHECKS PASSED: official item/block/BE aliases; only chuanshuo registration; full original components and display state");
        if (!persistenceRun()) return;
        if (Boolean.getBoolean("two_i_combat_doll.standalone_restart_test")) verifyNativeRestart(level);
        else seedNativeChunk(level, state);
    }

    private static void seedNativeChunk(ServerLevel level, BlockState state) throws Exception {
        MinecraftServer server = level.getServer();
        Path receipt = root(server).resolve(FIXTURE), diskCopy = root(server).resolve(LEGACY_DISK);
        check(!Files.exists(receipt) && !Files.exists(diskCopy), "new fixture never overwrites previous migration evidence");
        check(level.getBlockState(POSITION).isAir(), "private test chunk position is vacant");
        check(level.setBlock(POSITION, state, 3), "new B display placed into a real LevelChunk");
        BlockEntity display = level.getBlockEntity(POSITION);
        check(display != null, "actual placed display has a real BE");
        display.loadWithComponents(oldBlockEntity(level), level.registryAccess());
        display.setChanged();
        assertDisplay(display, level);
        CompoundTag marker = new CompoundTag();
        marker.putLong("pid", ProcessHandle.current().pid());
        marker.putLong("position", POSITION.asLong()); marker.put("expected_item", expected().save(level.registryAccess()));
        Files.createDirectories(receipt.getParent()); NbtIo.writeCompressed(marker, receipt);
        armedServer = server;
        server.saveEverything(true, true, true);
        check(oldChunkWrites > 0, "native ChunkMap save event wrote legacy registry names");
        CompoundTag actualDisk = level.getChunkSource().chunkMap.read(CHUNK).join().orElseThrow();
        assertLegacyRaw(actualDisk);
        NbtIo.writeCompressed(actualDisk, diskCopy);
        LogUtils.getLogger().info("LEGACY CHUANSHOU OLD CHUNK SAVED: pid={} chunk={} old-name writes={}; native region store flushed; old snapshot={}",
                ProcessHandle.current().pid(), CHUNK, oldChunkWrites, diskCopy);
    }

    /** Changes only this opt-in fixture's outgoing native chunk NBT, not runtime registrations. */
    @SubscribeEvent public static void saveOldNames(ChunkDataEvent.Save event) {
        if (!persistenceRun() || Boolean.getBoolean("two_i_combat_doll.standalone_restart_test")
                || !(event.getLevel() instanceof ServerLevel level) || level.getServer() != armedServer
                || level != armedServer.overworld() || !event.getChunk().getPos().equals(CHUNK)) return;
        CompoundTag chunk = event.getData();
        for (Tag raw : chunk.getList("sections", Tag.TAG_COMPOUND)) {
            CompoundTag section = (CompoundTag) raw;
            for (Tag entry : section.getCompound("block_states").getList("palette", Tag.TAG_COMPOUND)) {
                CompoundTag state = (CompoundTag) entry;
                if (state.getString("Name").equals(LegacyChuanshouAliases.DISPLAY.toString()))
                    state.putString("Name", LegacyChuanshouAliases.LEGACY_DISPLAY.toString());
            }
        }
        for (Tag raw : chunk.getList("block_entities", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (tag.getInt("x") != POSITION.getX() || tag.getInt("y") != POSITION.getY() || tag.getInt("z") != POSITION.getZ()) continue;
            if (tag.getString("id").equals(LegacyChuanshouAliases.DISPLAY.toString())) {
                tag.putString("id", LegacyChuanshouAliases.LEGACY_DISPLAY.toString());
                CompoundTag item = tag.getCompound("Doll");
                if (item.getString("id").equals(LegacyChuanshouAliases.ITEM.toString()))
                    item.putString("id", LegacyChuanshouAliases.LEGACY_ITEM.toString());
            }
        }
        assertLegacyRaw(chunk);
        oldChunkWrites++;
    }

    private static void assertLegacyRaw(CompoundTag chunk) {
        int palette = 0, blockEntities = 0;
        for (Tag raw : chunk.getList("sections", Tag.TAG_COMPOUND))
            for (Tag entry : ((CompoundTag) raw).getCompound("block_states").getList("palette", Tag.TAG_COMPOUND))
                if (((CompoundTag) entry).getString("Name").equals(LegacyChuanshouAliases.LEGACY_DISPLAY.toString())) palette++;
        for (Tag raw : chunk.getList("block_entities", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (tag.getInt("x") != POSITION.getX() || tag.getInt("y") != POSITION.getY() || tag.getInt("z") != POSITION.getZ()) continue;
            if (tag.getString("id").equals(LegacyChuanshouAliases.LEGACY_DISPLAY.toString())
                    && tag.getCompound("Doll").getString("id").equals(LegacyChuanshouAliases.LEGACY_ITEM.toString())) blockEntities++;
        }
        check(palette == 1 && blockEntities == 1, "actual region NBT contains old palette, old BE id and old nested item id exactly once");
    }

    private static void verifyNativeRestart(ServerLevel level) throws Exception {
        MinecraftServer server = level.getServer();
        CompoundTag marker = NbtIo.readCompressed(root(server).resolve(FIXTURE), NbtAccounter.unlimitedHeap());
        check(marker.getLong("pid") != ProcessHandle.current().pid(), "persisted chunk must be opened by a genuinely new Java process");
        check(marker.getLong("position") == POSITION.asLong(), "same saved test chunk and block position");
        assertLegacyRaw(NbtIo.readCompressed(root(server).resolve(LEGACY_DISK), NbtAccounter.unlimitedHeap()));
        // I/O-worker raw read does not decode the palette or BE. The following Level calls do.
        CompoundTag beforeDecode = level.getChunkSource().chunkMap.read(CHUNK).join().orElseThrow();
        assertLegacyRaw(beforeDecode);
        check(level.getBlockState(POSITION).is(ModBlocks.TWO_I_DOLL_DISPLAY.get()), "real native chunk palette loads as new B block");
        BlockEntity restored = level.getBlockEntity(POSITION);
        assertDisplay(restored, level);
        ItemStack original = ItemStack.parse(level.registryAccess(), marker.get("expected_item")).orElseThrow();
        check(ItemStack.isSameItemSameComponents(original, ((DollDisplayBlockEntity) restored).copyDoll()),
                "new JVM preserves first-process complete item carrier through actual chunk/BE load");
        restored.setChanged();
        server.saveEverything(true, true, true);
        CompoundTag canonical = level.getChunkSource().chunkMap.read(CHUNK).join().orElseThrow();
        String encoded = canonical.toString();
        check(!encoded.contains("chuanshou:"), "new JVM native save permanently canonicalizes old registry names");
        LogUtils.getLogger().info("LEGACY CHUANSHOU NATIVE RESTART CHECKS PASSED: first-pid={} current-pid={} actual old .mca palette/BE/item loaded and saved as new B; owner/UUID/extensions/name/lore/glint/rotation/spin retained",
                marker.getLong("pid"), ProcessHandle.current().pid());
    }
}
