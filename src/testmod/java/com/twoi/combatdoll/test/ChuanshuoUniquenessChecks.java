package com.twoi.combatdoll.test;

import com.customdoll.chuanshuo.content.DollOwnership;
import com.customdoll.chuanshuo.content.DollUmbrellaProtocol;
import com.customdoll.chuanshuo.content.entity.DollEntity;
import com.customdoll.chuanshuo.init.ModBlocks;
import com.customdoll.chuanshuo.init.ModEntities;
import com.customdoll.chuanshuo.init.ModItems;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.twoi.combatdoll.compat.LegacyChuanshouAliases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Actual catalog and legacy-item deployment probes. Test-only, never shipped. */
public final class ChuanshuoUniquenessChecks {
    private ChuanshuoUniquenessChecks() { }

    private static boolean chuanshuo(String namespace) {
        return namespace.equals("chuanshuo") || namespace.equals("chuanshou");
    }

    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError("Chuanshuo uniqueness: " + reason);
    }

    public static void run(ServerPlayer anchor) throws Exception {
        var level = anchor.serverLevel();
        check(ModList.get().isLoaded("chuanshuo") && !ModList.get().isLoaded("chuanshou"),
                "only the companion-capable B identity is loaded");
        var items = BuiltInRegistries.ITEM.keySet().stream().filter(id -> chuanshuo(id.getNamespace())).toList();
        check(items.equals(List.of(LegacyChuanshouAliases.ITEM)), "one real item registration, got " + items);
        check(BuiltInRegistries.BLOCK.keySet().stream().filter(id -> chuanshuo(id.getNamespace())).count() == 1,
                "one display block registration");
        check(ModBlocks.TWO_I_DOLL_DISPLAY.get().asItem() == ModItems.TWO_I_DOLL.get(),
                "display block and carrier use the exact same BlockItem, not separate visible items");
        check(BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(id -> chuanshuo(id.getNamespace())).count() == 1
                        && BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.TWO_I_DOLL.get()).equals(LegacyChuanshouAliases.ITEM),
                "the single remaining item has its registered companion entity");
        var recipes = level.getRecipeManager().getRecipes().stream()
                .filter(holder -> chuanshuo(holder.id().getNamespace())).toList();
        check(recipes.size() == 1 && recipes.getFirst().id().equals(LegacyChuanshouAliases.ITEM)
                        && recipes.getFirst().value().getResultItem(level.registryAccess()).is(ModItems.TWO_I_DOLL.get()),
                "one canonical acquisition recipe and no retired display-only recipe");
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        var tools = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.TOOLS_AND_UTILITIES);
        check(tools != null && tools.getDisplayItems().stream().filter(stack ->
                        chuanshuo(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())).count() == 1,
                "actual creative tools catalog shows only one Chuanshuo item");
        check(CreativeModeTabs.searchTab().getDisplayItems().stream().filter(stack ->
                        chuanshuo(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())).count() == 1,
                "actual creative search catalog shows only one Chuanshuo item");
        for (var tab : CreativeModeTabs.allTabs()) {
            check(tab.getDisplayItems().stream().noneMatch(stack ->
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("chuanshou")),
                    "retired item absent from every parent catalog");
            check(tab.getSearchTabDisplayItems().stream().noneMatch(stack ->
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("chuanshou")),
                    "retired item absent from every search catalog");
        }
        deployMigratedCarrier(anchor);
        LogUtils.getLogger().info("CHUANSHUO UNIQUENESS CHECKS PASSED: one real item/display BlockItem/entity/recipe; actual creative tools and search catalogs; retired A item aliases into deployable B carrier; owner/identity/name/extension and full-inventory exactly-once recall retained");
    }

    private static void deployMigratedCarrier(ServerPlayer anchor) {
        var level = anchor.serverLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ChuanshuoUnique"));
        var guest = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ChuanshuoVisitor"));
        BlockPos center = anchor.blockPosition().offset(24, 0, 24);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        DollEntity actor = null;
        try {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 3; z++) for (int y = -1; y <= 3; y++) {
                BlockPos pos = center.offset(x, y, z);
                check(level.getBlockEntity(pos) == null, "private deployment area has no block entity to replace");
                before.put(pos, level.getBlockState(pos));
                level.setBlockAndUpdate(pos, y == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            owner.getInventory().clearContent(); guest.getInventory().clearContent();
            owner.setGameMode(GameType.SURVIVAL); guest.setGameMode(GameType.SURVIVAL);
            owner.setPos(center.getX() + .5, center.getY(), center.getZ() + .5);
            guest.setPos(owner.position()); owner.setYRot(0); owner.setXRot(0); owner.setShiftKeyDown(true);
            ItemStack expected = new ItemStack(ModItems.TWO_I_DOLL.get());
            CompoundTag custom = new CompoundTag();
            UUID identity = UUID.randomUUID(); custom.putUUID("doll_identity", identity);
            custom.putString("retained_extension", "old A becomes a real companion");
            expected.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
            expected.set(DataComponents.CUSTOM_NAME, Component.literal("Legacy Chuanshou memory"));
            DollOwnership.claim(expected, owner); DollUmbrellaProtocol.markUmbrellaParticipant(expected);
            CompoundTag old = (CompoundTag) expected.save(level.registryAccess());
            old.putString("id", LegacyChuanshouAliases.LEGACY_ITEM.toString());
            ItemStack migrated = ItemStack.parse(level.registryAccess(), old).orElseThrow();
            check(ItemStack.isSameItemSameComponents(expected, migrated), "legacy codec preserves complete carrier");
            owner.setItemInHand(InteractionHand.MAIN_HAND, migrated);
            var result = ModItems.TWO_I_DOLL.get().use(level, owner, InteractionHand.MAIN_HAND);
            check(result.getResult().consumesAction() && owner.getMainHandItem().isEmpty(),
                    "real sneak-use consumes legacy-converted item into the companion");
            var entities = level.getEntitiesOfClass(DollEntity.class, new AABB(center).inflate(5),
                    entity -> owner.getUUID().equals(entity.ownerId()));
            check(entities.size() == 1, "sneak-use creates exactly one actual companion");
            actor = entities.getFirst();
            check(ItemStack.isSameItemSameComponents(expected, actor.storedCopy()), "actual deployed companion keeps full old carrier data");
            check(!actor.reclaim(guest) && !actor.isRemoved(), "visitor cannot reclaim upgraded companion");
            for (int slot = 0; slot < owner.getInventory().items.size(); slot++)
                owner.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
            check(!actor.reclaim(owner) && !actor.isRemoved()
                            && ItemStack.isSameItemSameComponents(expected, actor.storedCopy()),
                    "full backpack retains actual companion and all memory");
            owner.getInventory().setItem(0, ItemStack.EMPTY);
            check(actor.reclaim(owner) && actor.isRemoved(), "owner can retrieve converted item once after freeing space");
            check(owner.getInventory().countItem(ModItems.TWO_I_DOLL.get()) == 1
                            && ItemStack.isSameItemSameComponents(expected, owner.getInventory().getItem(0)),
                    "one canonical item returned with the old owner/UUID/name/extension");
            check(!actor.reclaim(owner) && owner.getInventory().countItem(ModItems.TWO_I_DOLL.get()) == 1,
                    "repeated recall cannot duplicate the converted carrier");
        } finally {
            if (actor != null && !actor.isRemoved()) actor.discard();
            owner.setShiftKeyDown(false); owner.getInventory().clearContent(); guest.getInventory().clearContent();
            var saved = new ArrayList<>(before.entrySet());
            for (int i = saved.size() - 1; i >= 0; i--) level.setBlockAndUpdate(saved.get(i).getKey(), saved.get(i).getValue());
        }
    }
}
