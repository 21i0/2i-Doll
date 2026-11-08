package com.twoi.combatdoll.test;

import com.customdoll.luomu.client.render.DollSkin;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Focused real resource/renderer regression. Only compiled with combatTests. */
@EventBusSubscriber(modid = "two_i_combat_doll", value = Dist.CLIENT)
public final class DollUvCatalogClientChecks {
    private static final List<String> NAMES = List.of("two_i_combat_doll", "baimo", "chuanshuo", "fenghua",
            "longhuan", "luomu", "magua", "shuangshi", "qiaotou", "canmeng", "nadir", "ninxin", "ooolala",
            "piston", "qijici", "qijiu", "shaniao", "sishier", "xiaoxiao");
    private static final Map<String, String> LUOMU_SHA = Map.of(
            "skin.png", "42e9a4e1619a2b1bcd8ecb9474ddf7d6b333f89da58c88a462a22799ebc35fca",
            "skin_blink_half.png", "aebb7d3002ae45b44c172028fadbc0048f9c56f3a4fe0906a18a2a911753f56c",
            "skin_blink_closed.png", "9d994db5bed67974b2ff0af252cf6e9d1ebaa026c40fa99d20ecc1ab9818467a");
    private static final AtomicBoolean serverReady = new AtomicBoolean();
    private static final AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    private static int ticks, phase, wait;
    private static boolean requested, serverStarted, done;
    private static CompletableFuture<?> reload;
    private static Preview preview;
    private DollUvCatalogClientChecks() { }

    @SubscribeEvent
    public static void integrated(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("two_i_combat_doll.doll_uv_catalog_test")
                || event.getServer().isDedicatedServer() || serverStarted || done) return;
        if (event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        serverStarted = true;
        try {
            ServerPlayer owner = event.getServer().getPlayerList().getPlayers().getFirst();
            owner.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
            owner.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
            owner.serverLevel().setDayTime(6000);
            DollUvCatalogServerChecks.run(owner);
            serverReady.set(true);
        } catch (Throwable failure) {
            serverFailure.set(failure);
        }
    }

    public static void tick(Minecraft mc) {
        if (done) return;
        try {
            check(++ticks < 9000, "client deadline phase=" + phase);
            if (serverFailure.get() != null) throw new AssertionError("integrated production interaction checks", serverFailure.get());
            if (mc.getOverlay() != null) return;
            if (!requested) {
                if (mc.screen == null) return;
                requested = true;
                for (String absent : List.of("useless_stretcher", "useless_mod", "doll_chronicle", "curios"))
                    check(!ModList.get().isLoaded(absent), "minimal environment excludes " + absent);
                Set<String> own = Set.copyOf(NAMES);
                for (var mod : ModList.get().getMods())
                    check(own.contains(mod.getModId()) || Set.of("minecraft", "neoforge").contains(mod.getModId()),
                            "unexpected external provider " + mod.getModId());
                for (String name : NAMES) check(ModList.get().isLoaded(name), "current source-integrated identity " + name);
                mc.options.guiScale().set(1); mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(5); mc.options.framerateLimit().set(60); mc.options.hideGui = false;
                mc.resizeDisplay();
                mc.createWorldOpenFlows().createFreshLevel("doll-uv-catalog-" + System.currentTimeMillis(),
                        new LevelSettings("Doll UV and catalog focused regression", GameType.CREATIVE, false,
                                Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(211105L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                return;
            }
            if (mc.level == null || mc.player == null || !serverReady.get()) return;
            if (phase == 0) {
                if (++wait < 25) return;
                checkLuomuTextures(mc, "before-reload");
                checkAllArmModels(mc, "before-reload");
                reload = mc.reloadResourcePacks(); phase = 1; wait = 0; return;
            }
            if (phase == 1) {
                if (!reload.isDone()) return;
                reload.join();
                if (++wait < 5) return;
                checkLuomuTextures(mc, "after-reload");
                checkAllArmModels(mc, "after-reload");
                preview = new Preview(); mc.setScreen(preview); phase = 2; return;
            }
            if (phase == 2 && preview.finished) {
                check(mc.screen == null, "production renderer preview closes cleanly");
                check(org.lwjgl.opengl.GL11.glGetError() == org.lwjgl.opengl.GL11.GL_NO_ERROR, "valid final GL state");
                done = true;
                LogUtils.getLogger().info("DOLL UV CATALOG CLIENT CHECKS PASSED: 19 production model arm widths 2.25 and sleeve transforms standing/sitting before+after real resource reload; three Luomu source SHA and normalized/dynamic base UV alpha; all 19 actual palm/head/display render APIs and 18 existing companion entity renderers front+back (qijici baseline has no entity), Luomu close front/side/back framebuffer; integrated real Longhuan catalog/deploy/full-bag exactly-once recall; minimal environment");
                mc.stop();
            }
        } catch (Throwable failure) {
            done = true;
            LogUtils.getLogger().error("DOLL UV CATALOG CLIENT CHECKS FAILED", failure);
            mc.stop();
        }
    }

    private static void checkLuomuTextures(Minecraft mc, String stage) throws Exception {
        for (String filename : List.of("skin.png", "skin_blink_half.png", "skin_blink_closed.png")) {
            var location = ResourceLocation.fromNamespaceAndPath("luomu", "textures/entity/" + filename);
            byte[] bytes;
            try (var input = mc.getResourceManager().open(location)) { bytes = input.readAllBytes(); }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            check(hash.equals(LUOMU_SHA.get(filename)), "runtime corrected source SHA " + filename);
            try (var source = NativeImage.read(new ByteArrayInputStream(bytes)); var normalized = DollSkin.normalizedCopy(source)) {
                check(source.getWidth() == 64 && source.getHeight() == 64 && DollSkin.usesSlimArms(source),
                        "three corrected source frames are recognized as Alex " + filename);
                for (int y = 28; y <= 30; y++) check(source.getPixelRGBA(55, y) >>> 24 == 0,
                        "unused Alex padding pixel stays transparent " + filename + "/55," + y);
                baseArmsOpaque(normalized, filename + "/normalized");
                float blink = filename.equals("skin.png") ? 0 : filename.equals("skin_blink_half.png") ? .5F : 1;
                var dynamicLocation = DollSkin.texture(blink);
                check(!dynamicLocation.equals(com.customdoll.luomu.client.render.DollRenderer.TEXTURE),
                        "real reload installed normalized dynamic texture " + filename);
                var texture = mc.getTextureManager().getTexture(dynamicLocation);
                check(texture instanceof DynamicTexture, "normalized production texture is a real DynamicTexture");
                NativeImage pixels = ((DynamicTexture) texture).getPixels();
                check(pixels != null && pixels.getWidth() == 64 && pixels.getHeight() == 64, "dynamic CPU pixels exist");
                baseArmsOpaque(pixels, filename + "/dynamic");
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
                    check(pixels.getPixelRGBA(x, y) == normalized.getPixelRGBA(x, y),
                            "live dynamic image exactly equals reviewed normalization " + filename + "/" + x + "," + y);
                LogUtils.getLogger().info("LUOMU UV FRAME CHECKS PASSED: stage={} frame={} sha256={} slim=true dynamic={} base_alpha255=true", stage, filename, hash, dynamicLocation);
            }
        }
    }

    private static void baseArmsOpaque(NativeImage image, String why) {
        int[][] faces = {{4, 0, 4, 4}, {8, 0, 4, 4}, {0, 4, 4, 12}, {4, 4, 4, 12}, {8, 4, 4, 12}, {12, 4, 4, 12}};
        for (int[] origin : new int[][]{{40, 16}, {32, 48}}) for (int[] face : faces)
            for (int y = 0; y < face[3]; y++) for (int x = 0; x < face[2]; x++) {
                int sx = origin[0] + face[0] + x, sy = origin[1] + face[1] + y;
                check(image.getPixelRGBA(sx, sy) >>> 24 == 255, "opaque complete base arm face " + why + "/" + sx + "," + sy);
            }
    }

    private static void checkAllArmModels(Minecraft mc, String stage) throws Exception {
        for (String name : NAMES) {
            Class<?> renderer = api(name, "DollRenderer");
            var layer = (ModelLayerLocation) renderer.getField("LAYER").get(null);
            Class<?> modelType = Class.forName(renderer.getName() + "$Model");
            var model = (PlayerModel<?>) modelType.getConstructor(ModelPart.class).newInstance(mc.getEntityModels().bakeLayer(layer));
            Method standing = modelType.getMethod("setupStanding", float.class, float.class, float.class,
                    float.class, float.class, int.class, float.class);
            Method sitting = modelType.getMethod("setupDecoration", float.class);
            for (int sample = 0; sample < 6; sample++) {
                standing.invoke(model, sample * .7F, sample % 2 == 0 ? 0F : .5F, 20F + sample * 13F, 8F, -4F, 0, 0F);
                armWidth(model, name + "/standing/" + sample);
                sitting.invoke(model, 20F + sample * 13F);
                armWidth(model, name + "/sitting/" + sample);
            }
            LogUtils.getLogger().info("DOLL SLIM ARM MODEL CHECKS PASSED: stage={} doll={} naked_width=2.25 standing_samples=6 sitting_samples=6 sleeve_transform=matched", stage, name);
        }
    }

    private static void armWidth(PlayerModel<?> model, String why) {
        for (var pair : List.of(new ModelPart[]{model.leftArm, model.leftSleeve}, new ModelPart[]{model.rightArm, model.rightSleeve})) {
            ModelPart arm = pair[0], sleeve = pair[1];
            var cube = arm.getRandomCube(RandomSource.create(0));
            check(Math.abs((cube.maxX - cube.minX) * arm.xScale - 2.25F) < .0001F, "effective naked arm width " + why);
            check(Math.abs(arm.xScale - sleeve.xScale) < .0001F && Math.abs(arm.yScale - sleeve.yScale) < .0001F
                            && Math.abs(arm.zScale - sleeve.zScale) < .0001F
                            && Math.abs(arm.xRot - sleeve.xRot) < .0001F && Math.abs(arm.yRot - sleeve.yRot) < .0001F
                            && Math.abs(arm.zRot - sleeve.zRot) < .0001F,
                    "sleeve copies slim arm scale and animated rotation " + why);
        }
    }

    private static Class<?> api(String namespace, String simpleName) throws ClassNotFoundException {
        if (!namespace.equals("two_i_combat_doll"))
            return Class.forName("com.customdoll." + namespace + ".client.render." + simpleName);
        return Class.forName((simpleName.equals("DollHeadLayer") || simpleName.equals("DollDisplayRenderer")
                ? "com.twoi.combatdoll.companion.client.render.TwoI" : "com.twoi.combatdoll.client.TwoI") + simpleName);
    }

    /** Calls production render APIs; screenshots are clearly labelled as fixture previews. */
    private static final class Preview extends Screen {
        private final List<LivingEntity> actors = new ArrayList<>();
        private final List<ItemStack> items = new ArrayList<>();
        private final List<BlockEntity> displays = new ArrayList<>();
        private int page, rendered;
        private boolean finished;
        private Preview() { super(Component.literal("Production doll slim-arm renderer previews")); }

        @Override protected void init() {
            try {
                for (String name : NAMES) {
                    boolean twoi = name.equals("two_i_combat_doll");
                    var itemId = ResourceLocation.fromNamespaceAndPath(name, twoi ? "two_i_doll" : name + "_doll");
                    var entityId = ResourceLocation.fromNamespaceAndPath(name, twoi ? "combat_doll" : name + "_doll");
                    var displayId = ResourceLocation.fromNamespaceAndPath(name, twoi ? "two_i_doll_display" : name + "_display");
                    var item = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
                    boolean hasEntity = BuiltInRegistries.ENTITY_TYPE.containsKey(entityId);
                    check(hasEntity != name.equals("qijici"), "existing companion-entity capability is unchanged " + name);
                    var actor = hasEntity ? (LivingEntity) BuiltInRegistries.ENTITY_TYPE.get(entityId).create(minecraft.level) : null;
                    check(!item.isEmpty() && (!hasEntity || (actor != null && minecraft.getEntityRenderDispatcher().getRenderer(actor) != null)),
                            "production entity/item renderer registered when companion exists " + name);
                    actors.add(actor); items.add(item);
                    var state = BuiltInRegistries.BLOCK.get(displayId).defaultBlockState();
                    BlockEntity display = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(displayId).create(BlockPos.ZERO, state);
                    check(display != null && minecraft.getBlockEntityRenderDispatcher().getRenderer(display) != null,
                            "production display renderer registered " + name);
                    display.getClass().getMethod("setDoll", ItemStack.class).invoke(display, item);
                    displays.add(display);
                }
            } catch (Throwable error) { throw new AssertionError("Production preview setup", error); }
        }

        @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
            try {
                gui.fill(0, 0, width, height, 0xFF17151C);
                boolean close = page < 3;
                int group = close ? 0 : (page - 3) / 2;
                float angle = close ? new float[]{0, 90, 180}[page] : (page - 3) % 2 == 0 ? 0 : 180;
                gui.drawCenteredString(font, "PRODUCTION RENDER APIs / " + (close ? "Luomu UV close view" : "all 19 slim-arm dolls, group " + (group + 1))
                        + " / view " + (int) angle, width / 2, 10, 0xFFEADDF5);
                String[] headers = {"Entity", "Palm", "Head", "Display block"};
                for (int column = 0; column < 4; column++)
                    gui.drawCenteredString(font, headers[column], (int) ((column + .5F) * width / 4F), 30, 0xFFDFCEDF);
                int from = close ? NAMES.indexOf("luomu") : group * 4;
                int to = close ? from + 1 : Math.min(NAMES.size(), from + 4);
                int rows = close ? 1 : 4;
                int rowHeight = (height - 75) / rows;
                for (int index = from; index < to; index++) {
                    int top = 55 + (index - from) * rowHeight;
                    gui.drawString(font, NAMES.get(index), 8, top, 0xFFFFFFFF);
                    for (int column = 0; column < 4; column++)
                        renderForm(gui, index, column, top + 13, rowHeight - 18, angle);
                }
                gui.drawCenteredString(font, "Isolated production-renderer previews; no shader or mannequin replacements", width / 2, height - 14, 0xFFAC9CAA);
                gui.flush();
                check(org.lwjgl.opengl.GL11.glGetError() == org.lwjgl.opengl.GL11.GL_NO_ERROR, "per-page renderer GL state");
                if (++rendered == 25) {
                    String name = close ? "luomu-close-" + (int) angle
                            : "all-dolls-group-" + (group + 1) + "-view-" + (int) angle;
                    capture(minecraft, name);
                    LogUtils.getLogger().info("DOLL UV CATALOG FRAMEBUFFER SAVED: {}.png names={} view={}", name, NAMES.subList(from, to), angle);
                    rendered = 0; page++;
                    if (page == 13) { finished = true; minecraft.setScreen(null); }
                }
            } catch (Throwable failure) {
                serverFailure.set(failure); minecraft.setScreen(null);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void renderForm(GuiGraphics gui, int index, int column, int top, int rowHeight, float angle) throws Exception {
            String name = NAMES.get(index);
            int cellWidth = width / 4;
            float cx = (column + .5F) * cellWidth;
            float base = top + rowHeight * .78F;
            float scale = Math.min(cellWidth * .79F, rowHeight * .91F);
            var actor = actors.get(index);
            var item = items.get(index);
            if (column == 0) {
                if (actor == null) {
                    gui.drawCenteredString(font, "No companion entity in baseline", (int) cx, top + rowHeight / 2, 0xFFAC9CAA);
                    return;
                }
                actor.yBodyRot = actor.yBodyRotO = actor.yHeadRot = actor.yHeadRotO = 180;
                actor.setYRot(180); actor.setXRot(0);
                InventoryScreen.renderEntityInInventory(gui, cx, base, scale, new Vector3f(),
                        new Quaternionf().rotateZ((float) Math.PI).rotateY(angle * (float) Math.PI / 180),
                        new Quaternionf(), actor);
                return;
            }
            PoseStack pose = gui.pose();
            pose.pushPose();
            try {
                pose.translate(cx, base, 150);
                if (column == 2) {
                    pose.scale(scale, scale, scale);
                    pose.mulPose(Axis.YP.rotationDegrees(180 + angle));
                    var playerModel = new PlayerModel<LivingEntity>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
                    playerModel.head.render(pose, gui.bufferSource().getBuffer(RenderType.entitySolid(minecraft.player.getSkin().texture())),
                            15728880, OverlayTexture.NO_OVERLAY);
                    api(name, "DollHeadLayer").getMethod("renderOnHead", ItemStack.class, ModelPart.class, PoseStack.class,
                            MultiBufferSource.class, int.class).invoke(null, item, playerModel.head, pose, gui.bufferSource(), 15728880);
                } else {
                    pose.scale(scale, -scale, scale);
                    pose.mulPose(Axis.YP.rotationDegrees(180 + angle));
                    if (column == 1) {
                        api(name, "DollHandRenderer").getMethod("renderOnPalm", ItemStack.class, HumanoidArm.class,
                                net.minecraft.client.resources.PlayerSkin.class, boolean.class, boolean.class,
                                PoseStack.class, MultiBufferSource.class, int.class).invoke(null, item, HumanoidArm.RIGHT,
                                minecraft.player.getSkin(), true, true, pose, gui.bufferSource(), 15728880);
                    } else {
                        pose.translate(-.5, 0, -.5);
                        BlockEntityRenderer renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(displays.get(index));
                        renderer.render(displays.get(index), 0, pose, gui.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY);
                    }
                }
                gui.flush();
            } finally { pose.popPose(); }
        }

        @Override public boolean isPauseScreen() { return false; }
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        var folder = mc.gameDirectory.toPath().resolve("doll-uv-catalog-screenshots");
        Files.createDirectories(folder);
        try (var shot = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot.writeToFile(folder.resolve(name + ".png"));
        }
    }

    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError("Doll UV/catalog client: " + reason);
    }
}
