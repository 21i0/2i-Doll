package com.twoi.combatdoll;

import com.twoi.combatdoll.config.TwoIVisualConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(TwoICombatDollMod.MODID)
public final class TwoICombatDollMod {
    public static final String MODID = "two_i_combat_doll";
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,MODID);
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<com.twoi.combatdoll.entity.CombatDollEntity>> DOLL_ENTITY=
            ENTITIES.register("combat_doll",()->net.minecraft.world.entity.EntityType.Builder.of(com.twoi.combatdoll.entity.CombatDollEntity::new,
                    net.minecraft.world.entity.MobCategory.CREATURE).sized(.45F,.95F).clientTrackingRange(8).noSummon().build("two_i_combat_doll:combat_doll"));
    public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS=net.neoforged.neoforge.registries.DeferredRegister.createItems(MODID);
    public static final net.neoforged.neoforge.registries.DeferredItem<com.twoi.combatdoll.item.CombatDollItem> DOLL_ITEM=
            ITEMS.register("two_i_doll",()->new com.twoi.combatdoll.item.CombatDollItem(new net.minecraft.world.item.Item.Properties()));
    public static final DeferredHolder<SoundEvent, SoundEvent> OPEN = SOUNDS.register("two_i_arcana_open", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT = SOUNDS.register("two_i_arcana_impact", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_STOP = SOUNDS.register("time_stop", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_VERDICT = SOUNDS.register("star_verdict", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ASTRAL_TICK = SOUNDS.register("astral_tick", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ASTRAL_CHARGE = SOUNDS.register("astral_charge", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ASTRAL_REVERSE = SOUNDS.register("astral_reverse", SoundEvent::createVariableRangeEvent);
    public static final net.neoforged.neoforge.registries.DeferredItem<com.twoi.combatdoll.item.ArcanaWeaponItem> BOOK=
            ITEMS.register("boundary_grimoire",()->new com.twoi.combatdoll.item.ArcanaWeaponItem(com.twoi.combatdoll.combat.WeaponKind.BOOK));
    public static final net.neoforged.neoforge.registries.DeferredItem<com.twoi.combatdoll.item.ArcanaWeaponItem> STAFF=
            ITEMS.register("astral_edict",()->new com.twoi.combatdoll.item.ArcanaWeaponItem(com.twoi.combatdoll.combat.WeaponKind.STAFF));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<com.twoi.combatdoll.entity.CangshengAxolotlEntity>> AXOLOTL_ENTITY=
            ENTITIES.register("cangsheng_axolotl",()->net.minecraft.world.entity.EntityType.Builder.of(com.twoi.combatdoll.entity.CangshengAxolotlEntity::new,
                    net.minecraft.world.entity.MobCategory.CREATURE).sized(1.6F,1F).clientTrackingRange(16).noSummon().build("two_i_combat_doll:cangsheng_axolotl"));
    public static final net.neoforged.neoforge.registries.DeferredItem<com.twoi.combatdoll.item.CangshengAxolotlItem> AXOLOTL=
            ITEMS.register("cangsheng_axolotl",com.twoi.combatdoll.item.CangshengAxolotlItem::new);
    public static final net.neoforged.neoforge.registries.DeferredItem<com.twoi.combatdoll.item.AxolotlFluteItem> AXOLOTL_FLUTE=
            ITEMS.register("cangsheng_axolotl_flute",com.twoi.combatdoll.item.AxolotlFluteItem::new);
    public static final DeferredHolder<SoundEvent,SoundEvent> AXOLOTL_ROAR=SOUNDS.register("cangsheng_roar",SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent,SoundEvent> AXOLOTL_AWAKEN=SOUNDS.register("cangsheng_awaken",SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent,SoundEvent> AXOLOTL_IMPACT=SOUNDS.register("cangsheng_impact",SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent,SoundEvent> AXOLOTL_FLUTE_SOUND=SOUNDS.register("cangsheng_flute",SoundEvent::createVariableRangeEvent);
    public TwoICombatDollMod(IEventBus bus, ModContainer container) {
        com.twoi.combatdoll.compat.LegacyChuanshouAliases.register(
                com.customdoll.chuanshuo.init.ModItems.ITEMS,
                com.customdoll.chuanshuo.init.ModBlocks.BLOCKS,
                com.customdoll.chuanshuo.init.ModBlockEntities.BLOCK_ENTITIES);
        com.twoi.combatdoll.companion.CompanionBootstrap.init(bus,container);
        SOUNDS.register(bus);ENTITIES.register(bus);ITEMS.register(bus);
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e)->{
            e.put(DOLL_ENTITY.get(),com.twoi.combatdoll.entity.CombatDollEntity.attributes().build());
            e.put(AXOLOTL_ENTITY.get(),com.twoi.combatdoll.entity.CangshengAxolotlEntity.attributes().build());
        });
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e)->{
            if(e.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)){e.accept(DOLL_ITEM.get());e.accept(BOOK.get());e.accept(STAFF.get());e.accept(AXOLOTL.get());e.accept(AXOLOTL_FLUTE.get());}
        });
        bus.addListener(com.twoi.combatdoll.network.CombatNetwork::register);
        container.registerConfig(ModConfig.Type.CLIENT, TwoIVisualConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER,com.twoi.combatdoll.config.DollServerConfig.SPEC);
    }
}
