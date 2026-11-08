package com.twoi.combatdoll.test;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid=TwoICombatDollMod.MODID)
public final class CombatTestBootstrap {
    private static int ticks;
    private static boolean done;
    @SubscribeEvent public static void server(ServerTickEvent.Post event) {
        if(Boolean.getBoolean("two_i_combat_doll.doll_uv_catalog_test")){
            if(event.getServer().isDedicatedServer())DollUvCatalogServerChecks.tick(event.getServer());
            return;
        }
        if(Boolean.getBoolean("two_i_combat_doll.titan_freedom_test")){
            if(event.getServer().isDedicatedServer())TitanFreedomServerChecks.tick(event.getServer());
            return;
        }
        if(Boolean.getBoolean("two_i_combat_doll.native_fix_test")){
            if(event.getServer().isDedicatedServer())NativeFixServerChecks.tick(event.getServer());
            return;
        }
        if(Boolean.getBoolean("two_i_combat_doll.standalone_test")){
            StandaloneServerChecks.tick(event.getServer());return;
        }
        if(Boolean.getBoolean("two_i_combat_doll.server_test")&&Boolean.getBoolean("two_i_combat_doll.axolotl_test")){
            AxolotlServerChecks.tick(event.getServer());return;
        }
        if(Boolean.getBoolean("two_i_combat_doll.server_test")&&Boolean.getBoolean("two_i_combat_doll.weapons_test")){
            WeaponServerChecks.tick(event.getServer());return;
        }
        if (!Boolean.getBoolean("two_i_combat_doll.server_test") || done || ++ticks<100) return;
        done=true;
        try { TwoIArcanaServerChecks.run(event.getServer().overworld()); }
        catch(Throwable failure) { LogUtils.getLogger().error("2I COMBAT DOLL SERVER CHECKS FAILED",failure); }
        finally { event.getServer().halt(false); }
    }
    @EventBusSubscriber(modid=TwoICombatDollMod.MODID,value=Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            if(Boolean.getBoolean("two_i_combat_doll.doll_uv_catalog_test")){
                DollUvCatalogClientChecks.tick(net.minecraft.client.Minecraft.getInstance());return;
            }
            if(Boolean.getBoolean("two_i_combat_doll.titan_freedom_test")){
                TitanFreedomClientChecks.tick(net.minecraft.client.Minecraft.getInstance());return;
            }
            if(Boolean.getBoolean("two_i_combat_doll.native_fix_test")){
                NativeFixClientChecks.tick(net.minecraft.client.Minecraft.getInstance());return;
            }
            if(Boolean.getBoolean("two_i_combat_doll.companion_port_test"))return;
            // Export-only stops at the title screen. Starting an integrated
            // world concurrently with that stop races server initialization.
            if(Boolean.getBoolean("two_i_combat_doll.model_mesh_export_only"))return;
            if(Boolean.getBoolean("two_i_combat_doll.axolotl_test"))return;
            if (Boolean.getBoolean("two_i_combat_doll.client_test")) {
                if(Boolean.getBoolean("two_i_combat_doll.book_only_test"))ScarletBookRenderChecks.standaloneTick(net.minecraft.client.Minecraft.getInstance());
                else if(Boolean.getBoolean("two_i_combat_doll.weapons_test"))WeaponClientChecks.tick(net.minecraft.client.Minecraft.getInstance());
                else TwoIArcanaClientChecks.tick(net.minecraft.client.Minecraft.getInstance());
            }
        }
    }
}
