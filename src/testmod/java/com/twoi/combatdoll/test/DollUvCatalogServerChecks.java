package com.twoi.combatdoll.test;

import com.customdoll.longhuan.content.DollOwnership;
import com.customdoll.longhuan.content.DollUmbrellaProtocol;
import com.customdoll.longhuan.content.entity.DollEntity;
import com.customdoll.longhuan.init.ModBlocks;
import com.customdoll.longhuan.init.ModEntities;
import com.customdoll.longhuan.init.ModItems;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Focused real registry/interaction probes; never included in production. */
public final class DollUvCatalogServerChecks {
    private static final ResourceLocation LONGHUAN = ResourceLocation.parse("longhuan:longhuan_doll");
    private static int ticks;
    private static boolean done;
    private DollUvCatalogServerChecks() { }

    public static void tick(MinecraftServer server) {
        if (done || ++ticks < 100) return;
        done = true;
        try {
            var owner = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.randomUUID(), "UvCatalogAnchor"));
            owner.setPos(0.5, 80, 0.5);
            run(owner);
            LogUtils.getLogger().info("DOLL UV CATALOG SERVER CHECKS PASSED: actual Longhuan item/display/entity/recipe and creative/search uniqueness; real owner-only deployment and full-bag exactly-once recall with retained identity and components");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("DOLL UV CATALOG SERVER CHECKS FAILED", failure);
        } finally {
            server.halt(false);
        }
    }

    /** Also called by the integrated client fixture, with no shutdown side effect. */
    public static void run(ServerPlayer anchor) {
        var level = anchor.serverLevel();
        var ids = BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals("longhuan")).toList();
        check(ids.equals(List.of(LONGHUAN)), "one real Longhuan item registration, got " + ids);
        check(BuiltInRegistries.BLOCK.keySet().stream().filter(id -> id.getNamespace().equals("longhuan")).count() == 1,
                "one Longhuan display block");
        check(ModBlocks.TWO_I_DOLL_DISPLAY.get().asItem() == ModItems.TWO_I_DOLL.get(),
                "display block uses the same carrier BlockItem");
        check(BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(id -> id.getNamespace().equals("longhuan")).toList().equals(List.of(LONGHUAN))
                        && BuiltInRegistries.ENTITY_TYPE.get(LONGHUAN) == ModEntities.TWO_I_DOLL.get(),
                "canonical item has exactly one registered companion entity");
        var recipes = level.getRecipeManager().getRecipes().stream().filter(recipe ->
                recipe.id().getNamespace().equals("longhuan")).toList();
        check(recipes.size() == 1 && recipes.getFirst().id().equals(LONGHUAN)
                        && recipes.getFirst().value().getResultItem(level.registryAccess()).is(ModItems.TWO_I_DOLL.get()),
                "one canonical Longhuan acquisition recipe");
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        var tools = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.TOOLS_AND_UTILITIES);
        check(tools != null && tools.getDisplayItems().stream().filter(stack -> stack.is(ModItems.TWO_I_DOLL.get())).count() == 1,
                "actual tools catalog has exactly one Longhuan");
        check(CreativeModeTabs.searchTab().getDisplayItems().stream().filter(stack -> stack.is(ModItems.TWO_I_DOLL.get())).count() == 1,
                "actual search catalog has exactly one Longhuan");
        LogUtils.getLogger().info("LONGHUAN REAL CATALOG CHECKS PASSED: items={} block={} entity={} recipe={}; one tools/search entry; no second registration removed", ids,
                BuiltInRegistries.BLOCK.getKey(ModBlocks.TWO_I_DOLL_DISPLAY.get()),
                BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.TWO_I_DOLL.get()), recipes.getFirst().id());
        deployment(anchor);
    }

    private static void deployment(ServerPlayer anchor) {
        var level = anchor.serverLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "LonghuanUvOwner"));
        var visitor = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "LonghuanUvGuest"));
        BlockPos center = anchor.blockPosition().offset(24, 0, 24);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        DollEntity actor = null;
        UUID identity = UUID.randomUUID();
        try {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 3; z++) for (int y = -1; y <= 3; y++) {
                BlockPos pos = center.offset(x, y, z);
                check(level.getBlockEntity(pos) == null, "fresh interaction fixture contains no block entity");
                before.put(pos, level.getBlockState(pos));
                level.setBlockAndUpdate(pos, y == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            owner.getInventory().clearContent(); visitor.getInventory().clearContent();
            owner.setGameMode(GameType.SURVIVAL); visitor.setGameMode(GameType.SURVIVAL);
            owner.setPos(center.getX() + .5, center.getY(), center.getZ() + .5);
            visitor.setPos(owner.position()); owner.setYRot(0); owner.setXRot(0); owner.setShiftKeyDown(true);
            var expected = new ItemStack(ModItems.TWO_I_DOLL.get());
            CompoundTag custom = new CompoundTag();
            custom.putUUID("doll_identity", identity);
            custom.putString("retained_extension", "Longhuan canonical companion");
            expected.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
            expected.set(DataComponents.CUSTOM_NAME, Component.literal("Longhuan companion memory"));
            DollOwnership.claim(expected, owner); DollUmbrellaProtocol.markUmbrellaParticipant(expected);
            owner.setItemInHand(InteractionHand.MAIN_HAND, expected.copy());
            var result = ModItems.TWO_I_DOLL.get().use(level, owner, InteractionHand.MAIN_HAND);
            check(result.getResult().consumesAction() && owner.getMainHandItem().isEmpty(),
                    "real sneak-use moves the single carrier into companion form");
            var actors = level.getEntitiesOfClass(DollEntity.class, new AABB(center).inflate(5),
                    entity -> owner.getUUID().equals(entity.ownerId()));
            check(actors.size() == 1, "real item use joins exactly one companion");
            actor = actors.getFirst();
            check(ItemStack.isSameItemSameComponents(expected, actor.storedCopy()), "deployment retains the entire input carrier");
            check((Object) actor instanceof com.twoi.combatdoll.combat.DollCombatAccess, "canonical companion retains its optional book combat adapter");
            check(!actor.reclaim(visitor) && !actor.isRemoved(), "visitor cannot reclaim the owner companion");
            retained(actor.storedCopy(), owner.getUUID(), identity);
            for (int slot = 0; slot < owner.getInventory().items.size(); slot++)
                owner.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
            check(!actor.reclaim(owner) && !actor.isRemoved(), "full backpack keeps the live companion safe");
            retained(actor.storedCopy(), owner.getUUID(), identity);
            owner.getInventory().setItem(0, ItemStack.EMPTY);
            check(actor.reclaim(owner) && actor.isRemoved(), "free slot allows canonical companion recall");
            check(owner.getInventory().countItem(ModItems.TWO_I_DOLL.get()) == 1, "exactly one returned carrier");
            retained(owner.getInventory().getItem(0), owner.getUUID(), identity);
            check(!actor.reclaim(owner) && owner.getInventory().countItem(ModItems.TWO_I_DOLL.get()) == 1,
                    "repeated recall cannot duplicate the carrier");
            LogUtils.getLogger().info("LONGHUAN REAL DEPLOY RECALL CHECKS PASSED: canonical carrier owner={} identity={} retained; no outsider recall; full-bag live retention; one returned carrier, second recall rejected", owner.getUUID(), identity);
        } finally {
            if (actor != null && !actor.isRemoved()) actor.discard();
            owner.setShiftKeyDown(false); owner.getInventory().clearContent(); visitor.getInventory().clearContent();
            var saved = new ArrayList<>(before.entrySet());
            for (int index = saved.size() - 1; index >= 0; index--)
                level.setBlockAndUpdate(saved.get(index).getKey(), saved.get(index).getValue());
        }
    }

    private static void retained(ItemStack stack, UUID owner, UUID identity) {
        check(stack.is(ModItems.TWO_I_DOLL.get()) && stack.getCount() == 1, "retained carrier identity");
        check(owner.equals(DollOwnership.get(stack)), "retained owner");
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        check(data.hasUUID("doll_identity") && data.getUUID("doll_identity").equals(identity)
                        && data.getString("retained_extension").equals("Longhuan canonical companion"), "retained custom identity/memory");
        check(stack.getOrDefault(DataComponents.CUSTOM_NAME, Component.empty()).getString().equals("Longhuan companion memory"),
                "retained custom name");
    }

    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError("Doll UV/catalog server: " + reason);
    }
}
